package com.example.kernelsustyleuikit.data.session

import com.example.kernelsustyleuikit.data.local.CcCrypto
import com.example.kernelsustyleuikit.data.local.CcPrefs
import com.example.kernelsustyleuikit.data.model.CcEndpoint
import com.example.kernelsustyleuikit.data.model.CcEndpointKind
import com.example.kernelsustyleuikit.data.model.CcProbeResult
import com.example.kernelsustyleuikit.data.model.CcServer
import com.example.kernelsustyleuikit.data.remote.CcApiClient
import com.example.kernelsustyleuikit.data.remote.CcHttpException
import com.example.kernelsustyleuikit.data.remote.CcNetworkBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class CcSessionPhase {
    Idle,
    Probing,
    Connecting,
    Connected,
    Error,
}

data class CcSessionState(
    val phase: CcSessionPhase = CcSessionPhase.Idle,
    val serverName: String? = null,
    val endpointUrl: String? = null,
    val endpointKind: CcEndpointKind? = null,
    val latencyMs: Long? = null,
    val daemonVersion: String? = null,
    val errorMessage: String? = null,
    /** 需要用户重新输入密码（密码错误 / 凭据不可解密） */
    val requiresLogin: Boolean = false,
    val probeResults: List<CcProbeResult> = emptyList(),
) {
    val isConnected: Boolean get() = phase == CcSessionPhase.Connected
}

/**
 * 会话管理：多端点择优、登录、冷启动免登录、静默重登。
 *
 * 会话是 **per-endpoint** 的，内外网是否共享取决于反向代理拓扑，因此切换端点时
 * 依次尝试「当前端点已存 Cookie -> 其它端点已存 Cookie -> 已保存密码静默重登」。
 */
class CcSessionManager(private val prefs: CcPrefs) {

    private val _state = MutableStateFlow(CcSessionState())
    val state: StateFlow<CcSessionState> = _state.asStateFlow()

    private var client: CcApiClient? = null
    private var server: CcServer? = null
    private var password: String? = null
    private var endpoint: CcEndpoint? = null

    fun api(): CcApiClient? = client

    fun currentServer(): CcServer? = server

    fun currentEndpoint(): CcEndpoint? = endpoint

    /** 首次登录（登录页调用），成功后保存服务器配置（密码为 Keystore 密文） */
    suspend fun login(server: CcServer, plainPassword: String): Result<Unit> {
        val encrypted = CcCrypto.encrypt(plainPassword)
            ?: return fail("无法加密密码，请重试")
        val saved = server.copy(passwordEnc = encrypted)
        return connect(saved, plainPassword, persistOnSuccess = true)
    }

    /** 冷启动：直接使用本地配置建立会话，无需用户输入密码 */
    suspend fun restore(): Result<Unit> {
        val saved = prefs.activeServer()
            ?: return fail("尚未配置服务器")
        val plain = CcCrypto.decrypt(saved.passwordEnc)
            ?: return fail("已保存的凭据无法解密，请重新登录", requiresLogin = true)
        return connect(saved, plain, persistOnSuccess = false)
    }

    /** 重新择优（网络变化 / 诊断页「重新检测」） */
    suspend fun reselect(): Result<Unit> {
        val saved = server ?: prefs.activeServer() ?: return fail("尚未配置服务器")
        val plain = password ?: CcCrypto.decrypt(saved.passwordEnc) ?: return fail("凭据不可用")
        return connect(saved, plain, persistOnSuccess = false)
    }

    /** 强制使用指定端点（诊断页「强制使用外网」） */
    suspend fun forceEndpoint(target: CcEndpoint): Result<Unit> {
        val saved = server ?: prefs.activeServer() ?: return fail("尚未配置服务器")
        val plain = password ?: CcCrypto.decrypt(saved.passwordEnc) ?: return fail("凭据不可用")
        return connectTo(saved, plain, target, latencyMs = null, persistOnSuccess = false)
    }

    /** 写操作前确保会话有效，必要时静默重登 */
    suspend fun ensureSession(): Boolean {
        val api = client ?: return false
        if (api.verifySession()) return true
        val plain = password ?: server?.let { CcCrypto.decrypt(it.passwordEnc) } ?: return false
        val result = api.login(plain)
        if (result.isSuccess) {
            _state.update { it.copy(phase = CcSessionPhase.Connected, errorMessage = null) }
            return true
        }
        _state.update { it.copy(phase = CcSessionPhase.Error, errorMessage = "登录已过期，请重新输入密码") }
        return false
    }

    fun clear() {
        client?.cookieJar?.clear()
        client = null
        server = null
        password = null
        endpoint = null
        _state.value = CcSessionState()
    }

    // ---------- 内部流程 ----------

    private suspend fun connect(
        server: CcServer,
        plainPassword: String,
        persistOnSuccess: Boolean,
    ): Result<Unit> {
        _state.update {
            it.copy(
                phase = CcSessionPhase.Probing,
                serverName = server.name,
                errorMessage = null,
            )
        }

        val results = CcEndpointProbe.probeAll(server.endpoints)
        _state.update { it.copy(probeResults = results) }

        val best = CcEndpointProbe.selectBest(results)
            ?: return fail(buildFailureMessage(results))

        return connectTo(server, plainPassword, best.endpoint, best.latencyMs, persistOnSuccess)
    }

    /**
     * 连接失败时给出**事实**，而不是猜测原因。
     *
     * 早先这里会断定「缺少本地网络权限」，但实测某些 ROM（MIUI / Android 17）
     * 上 `checkSelfPermission` 对该权限可能始终返回 DENIED，而系统实际是放行的；
     * 那样会把「网络不通」误报成「缺权限」，让用户白跑一趟系统设置页。
     * 现在把权限状态与每个端点的探测结果一并列出，便于直接定位。
     */
    private fun buildFailureMessage(results: List<CcProbeResult>): String {
        val permission = if (CcNetworkBinder.hasLocalNetworkPermission()) "已授予" else "未授予"
        val lines = results.joinToString("\n") { result ->
            val kind = if (result.endpoint.kind == CcEndpointKind.Lan) "内网" else "外网"
            val outcome = if (result.reachable) "${result.latencyMs}ms" else "无响应"
            "$kind ${result.endpoint.url} → $outcome"
        }
        return buildString {
            append("无法连接到服务器")
            append("\n「本地网络」权限：")
            append(permission)
            if (lines.isNotEmpty()) {
                append('\n')
                append(lines)
            }
        }
    }

    private suspend fun connectTo(
        server: CcServer,
        plainPassword: String,
        endpoint: CcEndpoint,
        latencyMs: Long?,
        persistOnSuccess: Boolean,
    ): Result<Unit> {
        _state.update {
            it.copy(
                phase = CcSessionPhase.Connecting,
                endpointUrl = endpoint.url,
                endpointKind = endpoint.kind,
                latencyMs = latencyMs,
            )
        }

        val api = CcApiClient.create(
            endpoint = endpoint.url,
            storeKey = "${server.id}|${endpoint.url}",
            // 内网端点绑定 Wi-Fi，避免被系统路由到移动数据
            network = CcNetworkBinder.networkFor(endpoint.url),
        ) ?: return fail("地址无效：${endpoint.url}")

        // 1) 当前端点已保存的会话
        val sessionValid = !api.cookieJar.isEmpty() && api.verifySession()

        // 2) 其它端点已保存的会话（拓扑共享时可零成本切换）
        val reused = sessionValid || tryReuseOtherEndpoint(server, api)

        // 3) 静默重登
        val loggedIn = reused || api.login(plainPassword).let { result ->
            if (result.isFailure) {
                val error = result.exceptionOrNull()
                if (error is CcHttpException && error.code == 401) {
                    return fail("密码错误，请重新输入", requiresLogin = true)
                }
                if (error is CcHttpException && error.code == 429) {
                    return fail("请求过于频繁，请稍后再试")
                }
                return fail("登录失败：${error?.message ?: "未知错误"}")
            }
            true
        }

        if (!loggedIn) return fail("登录失败")

        this.client = api
        this.server = server
        this.password = plainPassword
        this.endpoint = endpoint

        prefs.lastEndpointUrl = endpoint.url
        if (persistOnSuccess) {
            prefs.activeServerId = server.id
            prefs.addServer(server)
        }

        val version = api.daemonVersion()
        _state.update {
            it.copy(
                phase = CcSessionPhase.Connected,
                daemonVersion = version ?: it.daemonVersion,
                errorMessage = null,
                requiresLogin = false,
            )
        }
        return Result.success(Unit)
    }

    private suspend fun tryReuseOtherEndpoint(server: CcServer, target: CcApiClient): Boolean {
        for (candidate in server.endpoints) {
            if (candidate.url == target.endpoint) continue
            val other = CcApiClient.create(candidate.url, "${server.id}|${candidate.url}") ?: continue
            val header = other.cookieJar.exportHeader() ?: continue
            target.cookieJar.importHeader(header)
            if (target.verifySession()) return true
        }
        return false
    }

    private fun fail(message: String, requiresLogin: Boolean = false): Result<Unit> {
        _state.update {
            it.copy(
                phase = CcSessionPhase.Error,
                errorMessage = message,
                requiresLogin = requiresLogin,
            )
        }
        return Result.failure(IllegalStateException(message))
    }
}
