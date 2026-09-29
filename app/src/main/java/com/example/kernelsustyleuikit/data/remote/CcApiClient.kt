package com.example.kernelsustyleuikit.data.remote

import com.example.kernelsustyleuikit.data.model.CcAlert
import com.example.kernelsustyleuikit.data.model.CcAlertLog
import com.example.kernelsustyleuikit.data.model.CcChannelSetting
import com.example.kernelsustyleuikit.data.model.CcDevice
import com.example.kernelsustyleuikit.data.model.CcDeviceStats
import com.example.kernelsustyleuikit.data.model.CcDeviceStatus
import com.example.kernelsustyleuikit.data.model.CcFunction
import com.example.kernelsustyleuikit.data.model.CcMode
import com.example.kernelsustyleuikit.data.model.CcProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import android.net.Network
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** daemon 返回了非 2xx 状态码 */
class CcHttpException(val code: Int, val bodyText: String) :
    IOException("HTTP $code ${bodyText.take(200)}")

/**
 * CoolerControl daemon 5.x 的 REST 客户端。
 *
 * **每个端点一个实例**（自带 CookieJar），因为会话是 per-endpoint 的。
 * 用户名固定 `CCAdmin`，认证走 HTTP Basic，成功后 daemon 下发 `cc` 会话 Cookie。
 */
class CcApiClient private constructor(
    val endpoint: String,
    private val baseUrl: HttpUrl,
    val cookieJar: CcCookieJar,
    network: Network? = null,
) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        // 内网地址显式绑定 Wi-Fi：否则系统可能把请求路由到移动数据，导致内网不可达
        .apply { network?.let { socketFactory(it.socketFactory) } }
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    /** 供 SSE 复用同一套 Cookie（SSE 需要独立的 readTimeout=0 客户端） */
    fun newSseClient(): OkHttpClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun baseHttpUrl(): HttpUrl = baseUrl

    // ---------- URL / 请求构造 ----------

    private fun url(vararg segments: String): HttpUrl {
        val builder = baseUrl.newBuilder()
        segments.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    private fun get(vararg segments: String): Request =
        Request.Builder().url(url(*segments)).get().build()

    private fun post(vararg segments: String, body: RequestBody = EMPTY_BODY): Request =
        Request.Builder().url(url(*segments)).post(body).build()

    private fun put(vararg segments: String, body: RequestBody = EMPTY_BODY): Request =
        Request.Builder().url(url(*segments)).put(body).build()

    /** `/settings` 只接受 PATCH，用 PUT 会 405 */
    private fun patch(vararg segments: String, body: RequestBody = EMPTY_BODY): Request =
        Request.Builder().url(url(*segments)).patch(body).build()

    private fun delete(vararg segments: String): Request =
        Request.Builder().url(url(*segments)).delete().build()

    private fun jsonBody(json: String): RequestBody = json.toRequestBody(JSON_MEDIA)

    private suspend fun execute(request: Request): Result<String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (response.isSuccessful) {
                    Result.success(text)
                } else {
                    Result.failure(CcHttpException(response.code, text))
                }
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private suspend fun executeJson(request: Request): String = execute(request).getOrThrow()

    // ---------- 连通性 / 会话 ----------

    /** `GET /handshake`：仅用于判断端点是否可达 */
    suspend fun handshake(): Boolean = execute(get("handshake")).isSuccess

    /**
     * 尽力从 `/handshake` 取 daemon 版本。
     *
     * 该端点官方未定义响应体，因此按「响应体为 JSON 则取 version 字段 ->
     * 响应体为短纯文本则直接采用 -> 退回 `Server` 响应头」的顺序容错。
     */
    suspend fun daemonVersion(): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(get("handshake")).execute().use { response ->
                val body = response.body.string().trim()
                when {
                    body.isEmpty() -> response.header("Server")
                        ?.substringAfterLast('/')
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }

                    body.startsWith("{") -> runCatching {
                        JSONObject(body).optString("version").takeIf { it.isNotEmpty() }
                    }.getOrNull()

                    body.length < 64 -> body

                    else -> null
                }
            }
        }.getOrNull()
    }

    /** `POST /login` + HTTP Basic */
    suspend fun login(password: String): Result<Unit> {
        val request = Request.Builder()
            .url(url("login"))
            .post(EMPTY_BODY)
            .header("Authorization", Credentials.basic(USERNAME, password))
            .build()
        return execute(request).map { }
    }

    /** `POST /verify-session`：冷启动时快速判断本地会话是否仍然有效 */
    suspend fun verifySession(): Boolean =
        execute(post("verify-session")).isSuccess

    suspend fun logout(): Result<Unit> = execute(post("logout")).map { }

    // ---------- 读 ----------

    suspend fun devices(): List<CcDevice> =
        CcJson.parseDevices(executeJson(get("devices")))

    suspend fun status(): List<CcDeviceStatus> =
        CcJson.parseStatus(executeJson(get("status")))

    suspend fun stats(): List<CcDeviceStats> =
        CcJson.parseStats(executeJson(get("stats")))

    suspend fun alerts(): Pair<List<CcAlert>, List<CcAlertLog>> =
        CcJson.parseAlerts(executeJson(get("alerts")))

    /** 原始 `/alerts` 响应 —— 更新报警必须整体回传，不能经模型往返 */
    suspend fun alertsRaw(): JSONObject =
        JSONObject(executeJson(get("alerts")))

    suspend fun modes(): List<CcMode> =
        CcJson.parseModes(executeJson(get("modes")))

    suspend fun activeModeUid(): String? =
        CcJson.parseActiveModeUid(executeJson(get("modes-active")))

    suspend fun profiles(): List<CcProfile> =
        CcJson.parseProfiles(executeJson(get("profiles")))

    suspend fun functions(): List<CcFunction> =
        CcJson.parseFunctions(executeJson(get("functions")))

    /** 通道级已应用设置 —— 判断「当前绑定了哪条曲线」的唯一正确来源 */
    suspend fun channelSettings(deviceUid: String): List<CcChannelSetting> =
        CcJson.parseChannelSettings(executeJson(get("devices", deviceUid, "settings")))

    /** 原始 `/modes` 响应 —— 模式定义里含各通道绑定的 `profile_uid` */
    suspend fun modesRaw(): JSONObject = JSONObject(executeJson(get("modes")))

    /** 原始 `/devices/health` 响应 */
    suspend fun devicesHealthRaw(): String = executeJson(get("devices", "health"))

    // ---------- 写 ----------

    /**
     * 手动固定转速。
     *
     * 官方 `SettingManualRequest` 要求字段名 **`speed_fixed`** 且为整数：
     * 写成 `duty` 或传浮点都会被 400 拒绝。
     */
    suspend fun setManual(deviceUid: String, channel: String, speed: Int): Result<Unit> =
        execute(
            put(
                "devices", deviceUid, "settings", channel, "manual",
                body = jsonBody(JSONObject().put("speed_fixed", speed).toString()),
            )
        ).map { }

    /** 给通道绑定曲线（也是「恢复曲线控制」的正确做法） */
    suspend fun setProfile(deviceUid: String, channel: String, profileUid: String): Result<Unit> =
        execute(
            put(
                "devices", deviceUid, "settings", channel, "profile",
                body = jsonBody(JSONObject().put("profile_uid", profileUid).toString()),
            )
        ).map { }

    /**
     * 重置通道为硬件默认。
     *
     * 注意语义：**只清除设置记录，不会把风扇交回曲线** ——
     * 「恢复曲线控制」必须改用 [setProfile]。
     */
    suspend fun resetChannel(deviceUid: String, channel: String): Result<Unit> =
        execute(put("devices", deviceUid, "settings", channel, "reset")).map { }

    suspend fun setActiveMode(modeUid: String): Result<Unit> =
        execute(post("modes-active", modeUid)).map { }

    /** 更新报警规则：必须回传完整原始对象，缺字段会被 422 拒绝 */
    suspend fun updateAlert(raw: JSONObject): Result<Unit> =
        execute(put("alerts", body = jsonBody(raw.toString()))).map { }

    /** 设备重命名（写入 daemon，与网页端共享）；传 null 恢复默认 */
    suspend fun renameDevice(deviceUid: String, name: String?): Result<Unit> {
        val payload = JSONObject()
        if (name.isNullOrBlank()) payload.put("name", JSONObject.NULL) else payload.put("name", name)
        return execute(
            put("settings", "devices", deviceUid, "overrides", body = jsonBody(payload.toString()))
        ).map { }
    }

    /** 通道重命名；传 null 恢复默认 */
    suspend fun renameChannel(deviceUid: String, channel: String, label: String?): Result<Unit> {
        val payload = JSONObject()
        if (label.isNullOrBlank()) payload.put("label", JSONObject.NULL) else payload.put("label", label)
        return execute(
            put(
                "settings", "devices", deviceUid, "channels", channel, "overrides",
                body = jsonBody(payload.toString()),
            )
        ).map { }
    }

    /**
     * 压力测试启动。
     *
     * `kind` 取 cpu / gpu / ram / drive。注意 **drive 必须带 `device_path`**，
     * 否则会被 400 拒绝（官方 `StartDriveStressRequest` 把它标为 required）；
     * `gpu_id` 省略表示对所有 GPU 施压。
     */
    suspend fun startStressTest(
        kind: String,
        durationSecs: Int? = null,
        threadCount: Int? = null,
        gpuId: String? = null,
        devicePath: String? = null,
        backend: String? = null,
    ): Result<Unit> {
        val payload = JSONObject()
        durationSecs?.let { payload.put("duration_secs", it) }
        threadCount?.let { payload.put("thread_count", it) }
        gpuId?.let { payload.put("gpu_id", it) }
        devicePath?.let { payload.put("device_path", it) }
        backend?.let { payload.put("backend", it) }
        return execute(post("stress-test", kind, body = jsonBody(payload.toString()))).map { }
    }

    suspend fun stopStressTest(kind: String): Result<Unit> =
        execute(delete("stress-test", kind)).map { }

    /** 可选的压力测试目标，用于让用户指定具体 GPU / 磁盘 */
    suspend fun stressGpus(): Result<String> = execute(get("stress-test", "gpus"))

    suspend fun stressDrives(): Result<String> = execute(get("stress-test", "drives"))

    /**
     * 硬件支持报告（纯文本）。daemon 返回 `{"report": "..."}`，
     * 这里直接摊平成文本，调用方不必再解一层 JSON。
     */
    suspend fun hardwareReport(): Result<String> =
        execute(get("hardware-report"))
            .map { text ->
                runCatching { JSONObject(text).optString("report") }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: text
            }

    /** 电源模式集成状态：`{available, active, modes}` */
    suspend fun powerProfiles(): Result<String> = execute(get("power-profiles"))

    /** 网页端做过的全部重命名；名称的唯一权威来源 */
    suspend fun settingsOverrides(): Result<String> = execute(get("settings", "overrides"))

    /** 网页端的界面设置（时间格式、图表外观、压力测试后端、通道颜色等） */
    suspend fun uiSettings(): Result<String> = execute(get("settings", "ui"))

    /**
     * 写回界面设置。
     *
     * **必须回传完整对象**：daemon 是整体替换语义，缺字段会被重置成默认值。
     */
    suspend fun updateUiSettings(raw: JSONObject): Result<Unit> =
        execute(put("settings", "ui", body = jsonBody(raw.toString()))).map { }

    /** daemon 全局设置（轮询间隔、启动行为、集成开关等） */
    suspend fun daemonSettings(): Result<String> = execute(get("settings"))

    /** 写回 daemon 全局设置；同样要求完整对象，且只接受 PATCH */
    suspend fun updateDaemonSettings(raw: JSONObject): Result<Unit> =
        execute(patch("settings", body = jsonBody(raw.toString()))).map { }

    /** 写入「电源模式 -> 散热模式」映射；未列出的电源模式视为不映射 */
    suspend fun setPowerProfileModes(modes: JSONObject): Result<Unit> =
        execute(
            put(
                "power-profiles", "modes",
                body = jsonBody(JSONObject().put("modes", modes).toString()),
            )
        ).map { }

    companion object {
        const val USERNAME = "CCAdmin"

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(null)

        /**
         * @param endpoint 形如 `http://10.0.0.53:11987`
         * @param storeKey 会话存储键，形如 `serverId|endpoint`
         * @param network  可选的网络绑定（内网地址传 Wi-Fi 网络，见 [CcNetworkBinder]）
         */
        fun create(endpoint: String, storeKey: String, network: Network? = null): CcApiClient? {
            val normalized = normalizeEndpoint(endpoint) ?: return null
            val url = normalized.toHttpUrlOrNull() ?: return null
            return CcApiClient(normalized, url, CcCookieJar(url, storeKey), network)
        }

        /**
         * 补全协议：`10.0.0.53:11987` -> `http://...`（私网/本地），
         * 其余默认 `https://`。
         */
        fun normalizeEndpoint(input: String): String? {
            val raw = input.trim().trimEnd('/')
            if (raw.isEmpty()) return null
            if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
            val host = raw.substringBefore('/').substringBefore(':')
            val isPrivate = host == "localhost" || host == "127.0.0.1" ||
                host.startsWith("10.") || host.startsWith("192.168.") ||
                Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(host)
            return (if (isPrivate) "http://" else "https://") + raw
        }
    }
}
