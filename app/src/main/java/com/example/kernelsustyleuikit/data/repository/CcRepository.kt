package com.example.kernelsustyleuikit.data.repository

import android.net.ConnectivityManager
import android.net.Network
import com.example.kernelsustyleuikit.data.local.CcPrefs
import com.example.kernelsustyleuikit.data.model.CcAlert
import com.example.kernelsustyleuikit.data.model.CcAlertLog
import com.example.kernelsustyleuikit.data.model.CcDaemonSettings
import com.example.kernelsustyleuikit.data.model.CcDevice
import com.example.kernelsustyleuikit.data.model.CcDeviceStats
import com.example.kernelsustyleuikit.data.model.CcDeviceStatus
import com.example.kernelsustyleuikit.data.model.CcFunction
import com.example.kernelsustyleuikit.data.model.CcMode
import com.example.kernelsustyleuikit.data.model.CcOverrides
import com.example.kernelsustyleuikit.data.model.CcPowerProfiles
import com.example.kernelsustyleuikit.data.model.CcProfile
import com.example.kernelsustyleuikit.data.model.CcStatus
import com.example.kernelsustyleuikit.data.model.CcStressTarget
import com.example.kernelsustyleuikit.data.model.CcUiSettings
import com.example.kernelsustyleuikit.data.remote.CcApiClient
import com.example.kernelsustyleuikit.data.remote.CcJson
import com.example.kernelsustyleuikit.data.remote.CcSseClient
import com.example.kernelsustyleuikit.data.remote.CcSseFrame
import com.example.kernelsustyleuikit.data.session.CcSessionManager
import com.example.kernelsustyleuikit.templateApp
import com.example.kernelsustyleuikit.ui.util.CcFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** SSE 实时流状态 */
enum class CcSseState {
    Disconnected,
    Connecting,
    Connected,
}

/** 内存历史采样点 */
data class CcSample(
    val timestampMs: Long,
    val value: Double,
)

/** 设备健康摘要（来自 `/devices/health` 与 SSE health 事件） */
data class CcHealthSummary(
    val failsafe: Int = 0,
    val unreachable: Int = 0,
    val missing: Int = 0,
    val staleSource: Int = 0,
) {
    val hasIssue: Boolean
        get() = failsafe > 0 || unreachable > 0 || missing > 0 || staleSource > 0
}

/**
 * CoolerControl 客户端的**单一数据源**。
 *
 * - 对外暴露 `StateFlow`，界面只从这里取数，不直接持有 `CcApiClient`
 * - 维护单条 SSE 长连接（指数退避重连 1s -> 30s）
 * - 前台运行期间在内存里累积曲线历史（官方 5.x 没有历史时序接口）
 * - 所有写操作后回读受影响的通道设置，否则依赖它的界面不会更新
 */
class CcRepository(
    private val prefs: CcPrefs,
    val session: CcSessionManager,
) {

    // ---------- 数据流 ----------

    private val _devices = MutableStateFlow<List<CcDevice>>(emptyList())
    val devices: StateFlow<List<CcDevice>> = _devices.asStateFlow()

    /** deviceUid -> 最新快照 */
    private val _latestStatus = MutableStateFlow<Map<String, CcStatus>>(emptyMap())
    val latestStatus: StateFlow<Map<String, CcStatus>> = _latestStatus.asStateFlow()

    private val _stats = MutableStateFlow<List<CcDeviceStats>>(emptyList())
    val stats: StateFlow<List<CcDeviceStats>> = _stats.asStateFlow()

    private val _alerts = MutableStateFlow<List<CcAlert>>(emptyList())
    val alerts: StateFlow<List<CcAlert>> = _alerts.asStateFlow()

    private val _alertLogs = MutableStateFlow<List<CcAlertLog>>(emptyList())
    val alertLogs: StateFlow<List<CcAlertLog>> = _alertLogs.asStateFlow()

    /** 原始 `/alerts` 响应，更新报警时必须整体回传 */
    private val _rawAlerts = MutableStateFlow<JSONObject?>(null)
    val rawAlerts: StateFlow<JSONObject?> = _rawAlerts.asStateFlow()

    private val _modes = MutableStateFlow<List<CcMode>>(emptyList())
    val modes: StateFlow<List<CcMode>> = _modes.asStateFlow()

    private val _activeModeUid = MutableStateFlow<String?>(null)
    val activeModeUid: StateFlow<String?> = _activeModeUid.asStateFlow()

    private val _profiles = MutableStateFlow<List<CcProfile>>(emptyList())
    val profiles: StateFlow<List<CcProfile>> = _profiles.asStateFlow()

    private val _functions = MutableStateFlow<List<CcFunction>>(emptyList())
    val functions: StateFlow<List<CcFunction>> = _functions.asStateFlow()

    /** deviceUid -> (channel -> profileUid)：通道级曲线绑定 */
    private val _channelProfileUids = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val channelProfileUids: StateFlow<Map<String, Map<String, String>>> =
        _channelProfileUids.asStateFlow()

    private val _health = MutableStateFlow(CcHealthSummary())
    val health: StateFlow<CcHealthSummary> = _health.asStateFlow()

    private val _sseState = MutableStateFlow(CcSseState.Disconnected)
    val sseState: StateFlow<CcSseState> = _sseState.asStateFlow()

    /** 采样累积节拍，驱动曲线重绘 */
    private val _historyTick = MutableStateFlow(0L)
    val historyTick: StateFlow<Long> = _historyTick.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    // ---------- 内存历史（前台累积，上限 65 分钟）----------

    private val history = ConcurrentHashMap<String, ArrayDeque<CcSample>>()

    private var sseJob: Job? = null

    // ---------- 全量刷新 ----------

    suspend fun refreshAll() {
        val api = session.api() ?: return
        _refreshing.value = true
        try {
            withContext(Dispatchers.IO) {
                runCatching {
                    _devices.value = api.devices()
                    _stats.value = api.stats()
                    _modes.value = api.modes()
                    _activeModeUid.value = api.activeModeUid()
                    _profiles.value = api.profiles()
                    _functions.value = api.functions()
                    refreshAlertsInternal(api)
                    refreshAllChannelSettings(api)
                    refreshHealthInternal(api)
                }
                // 界面设置决定设备排序，单独包一层 runCatching：
                // 放在上面同一个块里的话，前面任何一步抛异常都会把它一起跳过
                runCatching { refreshUiSettings() }
            }
            runCatching { refreshStatusOnce() }
        } finally {
            _refreshing.value = false
        }
    }

    /** 只刷新元数据（设备 / 模式 / 曲线 / 重命名），用于写操作后的回读 */
    suspend fun refreshMetadata() {
        val api = session.api() ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                _devices.value = api.devices()
                _modes.value = api.modes()
                _activeModeUid.value = api.activeModeUid()
                _profiles.value = api.profiles()
                // 重命名表要和设备一起回读，否则改完名字界面还是旧的
                api.settingsOverrides().onSuccess { _overrides.value = CcOverrides.parse(it) }
                // 界面设置决定时间与频率怎么渲染，同样随元数据一起拉
                refreshUiSettings()
                refreshDaemonSettings()
            }
        }
    }

    suspend fun refreshAlerts() {
        val api = session.api() ?: return
        withContext(Dispatchers.IO) { runCatching { refreshAlertsInternal(api) } }
    }

    suspend fun refreshStats() {
        val api = session.api() ?: return
        withContext(Dispatchers.IO) { runCatching { _stats.value = api.stats() } }
    }

    suspend fun refreshHealth() {
        val api = session.api() ?: return
        withContext(Dispatchers.IO) { runCatching { refreshHealthInternal(api) } }
    }

    suspend fun refreshStatusOnce() {
        val api = session.api() ?: return
        val list = withContext(Dispatchers.IO) { runCatching { api.status() }.getOrDefault(emptyList()) }
        if (list.isNotEmpty()) applyStatus(list)
    }

    /** 回读单个设备的通道设置 —— 写操作后必须调用 */
    suspend fun refreshChannelSettings(deviceUid: String) {
        val api = session.api() ?: return
        val settings = withContext(Dispatchers.IO) {
            runCatching { api.channelSettings(deviceUid) }.getOrDefault(emptyList())
        }
        val map = settings.mapNotNull { s -> s.profileUid?.let { s.channelName to it } }.toMap()
        _channelProfileUids.update { it + (deviceUid to map) }
    }

    private suspend fun refreshAllChannelSettings(api: CcApiClient) {
        val result = HashMap<String, Map<String, String>>()
        _devices.value.forEach { device ->
            runCatching {
                val settings = api.channelSettings(device.uid)
                result[device.uid] = settings.mapNotNull { s ->
                    s.profileUid?.let { s.channelName to it }
                }.toMap()
            }
        }
        if (result.isNotEmpty()) _channelProfileUids.value = result
    }

    private suspend fun refreshAlertsInternal(api: CcApiClient) {
        val raw = runCatching { api.alertsRaw() }.getOrNull()
        if (raw != null) {
            _rawAlerts.value = raw
            val (alertList, logList) = CcJson.parseAlerts(raw.toString())
            _alerts.value = alertList
            // 报警日志量大（约 1000 条），只保留最近 50 条
            _alertLogs.value = logList.takeLast(50).reversed()
        }
    }

    private suspend fun refreshHealthInternal(api: CcApiClient) {
        runCatching {
            val text = api.devicesHealthRaw()
            val obj = JSONObject(text)
            _health.value = CcHealthSummary(
                failsafe = obj.optJSONArray("failsafe")?.length() ?: 0,
                unreachable = obj.optJSONArray("unreachable")?.length() ?: 0,
                missing = obj.optJSONArray("missing")?.length() ?: 0,
                staleSource = obj.optJSONArray("stale_source")?.length() ?: 0,
            )
        }
    }

    // ---------- SSE ----------

    /** 进程级作用域：SSE 不应随某个页面的 ViewModel 销毁而断开 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 当前 SSE 连接。
     *
     * [stopRealtime] 需要它来**显式打断**阻塞中的 socket 读 —— 只取消协程
     * 无法让 `readUtf8Line()` 返回，连接与线程会一直留着。
     */
    private var sseClient: CcSseClient? = null

    fun startRealtime() {
        if (sseJob?.isActive == true) return
        sseJob = appScope.launch {
            var backoffMs = INITIAL_BACKOFF_MS
            while (isActive) {
                val api = session.api()
                if (api == null) {
                    delay(backoffMs)
                    // 会话失效时同样要退避：否则会以 1 秒周期无限空转、持续唤醒 CPU
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    continue
                }
                val sse = CcSseClient(api.newSseClient(), api.baseHttpUrl())
                sseClient = sse
                try {
                    _sseState.value = CcSseState.Connecting
                    sse.stream().collect { frame ->
                        if (_sseState.value != CcSseState.Connected) {
                            _sseState.value = CcSseState.Connected
                        }
                        backoffMs = INITIAL_BACKOFF_MS
                        handleFrame(frame)
                    }
                    _sseState.value = CcSseState.Disconnected
                } catch (e: CancellationException) {
                    // 取消信号必须继续向上传播：CancellationException 继承自
                    // IllegalStateException，会被下面的 Exception 分支吞掉，
                    // 那样结构化并发的取消传播就被破坏了
                    throw e
                } catch (_: Exception) {
                    _sseState.value = CcSseState.Disconnected
                } finally {
                    // 只清理自己注册的那次，避免覆盖下一轮循环新建的连接
                    if (sseClient === sse) sseClient = null
                }
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    fun stopRealtime() {
        // 顺序很重要：先取出引用并打断底层 socket，最后才取消协程。
        // Job.cancel() 只把 Job 置为 cancelling，阻塞在 readUtf8Line() 上的线程
        // 收不到任何信号（OkHttp 的 socket 读也不响应 Thread.interrupt），
        // 必须显式 cancel 才能让它立刻以 IOException 返回。
        val client = sseClient
        val job = sseJob
        sseClient = null
        sseJob = null
        client?.cancel()
        job?.cancel()
        _sseState.value = CcSseState.Disconnected
    }

    // ---------- 网络变化监听 ----------

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** 网络变化触发的重新择优。抖动时会被取消重来，保证同一时刻只有一轮 */
    private var reselectJob: Job? = null

    /**
     * 监听网络变化（切 Wi-Fi / 移动数据 / 从息屏唤醒），防抖后重新择优。
     *
     * 场景：手机离开家里 Wi-Fi 时，内网端点会失效，需要自动落到外网地址。
     */
    fun observeNetworkChanges() {
        if (networkCallback != null) return
        val manager = templateApp.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // 网络抖动会连续回调，必须先取消上一轮 ——
                // delay 只是推迟开始时间，并不构成防抖；多轮 reselect 并发进入
                // connect() 会互相覆盖 client/server/endpoint，表现为「切了又被切回去」
                reselectJob?.cancel()
                reselectJob = appScope.launch {
                    delay(NETWORK_DEBOUNCE_MS)
                    session.reselect()
                }
            }
        }
        // 注册成功才记录，否则注册失败后会被永久标记为「已注册」而不再重试
        if (runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess) {
            networkCallback = callback
        }
    }

    fun stopObservingNetworkChanges() {
        val callback = networkCallback ?: return
        val manager = templateApp.getSystemService(ConnectivityManager::class.java)
        runCatching { manager?.unregisterNetworkCallback(callback) }
        networkCallback = null
    }

    private fun handleFrame(frame: CcSseFrame) {
        when (frame.event) {
            "status" -> {
                val parsed = runCatching { CcJson.parseStatusEvent(frame.data) }.getOrNull()
                if (parsed != null) applyStatus(parsed)
            }

            "alert" -> {
                val log = CcJson.parseAlertLogEvent(frame.data) ?: return
                _alertLogs.update { current -> (listOf(log) + current).take(50) }
            }

            "mode" -> {
                CcJson.parseActiveModeEvent(frame.data)?.let { uid -> _activeModeUid.value = uid }
            }

            "health" -> {
                runCatching {
                    val obj = JSONObject(frame.data)
                    _health.value = CcHealthSummary(
                        failsafe = obj.optJSONArray("failsafe")?.length() ?: 0,
                        unreachable = obj.optJSONArray("unreachable")?.length() ?: 0,
                        missing = obj.optJSONArray("missing")?.length() ?: 0,
                        staleSource = obj.optJSONArray("stale_source")?.length() ?: 0,
                    )
                }
            }

            else -> Unit // log / notification / system / missing / stale-source / failsafe / unreachable
        }
    }

    private fun applyStatus(list: List<CcDeviceStatus>) {
        val now = System.currentTimeMillis()
        val map = HashMap<String, CcStatus>()
        list.forEach { device ->
            val status = device.status ?: return@forEach
            map[device.uid] = status
            status.temps.forEach { temp ->
                appendHistory("${device.uid}|${temp.name}", now, temp.temp)
            }
            status.channels.forEach { channel ->
                channel.rpm?.let { appendHistory("${device.uid}|${channel.name}|rpm", now, it.toDouble()) }
                channel.duty?.let { appendHistory("${device.uid}|${channel.name}|duty", now, it) }
            }
        }
        if (map.isNotEmpty()) _latestStatus.value = map
        _historyTick.value = now
    }

    private fun appendHistory(key: String, timestampMs: Long, value: Double) {
        val deque = history.getOrPut(key) { ArrayDeque() }
        synchronized(deque) {
            deque.addLast(CcSample(timestampMs, value))
            val cutoff = timestampMs - HISTORY_WINDOW_MS
            while (deque.isNotEmpty() && deque.first().timestampMs < cutoff) {
                deque.removeFirst()
            }
        }
    }

    /** 取某个 key 在指定时间窗内的采样（时间升序） */
    fun historyFor(key: String, windowMs: Long): List<CcSample> {
        val deque = history[key] ?: return emptyList()
        val cutoff = System.currentTimeMillis() - windowMs
        synchronized(deque) {
            return deque.filter { it.timestampMs >= cutoff }
        }
    }

    fun clearHistory() {
        history.clear()
    }

    // ---------- 便捷取值 ----------

    fun device(uid: String): CcDevice? = _devices.value.firstOrNull { it.uid == uid }

    fun latestTemp(deviceUid: String, channel: String): Double? =
        _latestStatus.value[deviceUid]?.temps?.firstOrNull { it.name == channel }?.temp

    fun latestRpm(deviceUid: String, channel: String): Int? =
        _latestStatus.value[deviceUid]?.channels?.firstOrNull { it.name == channel }?.rpm

    fun latestDuty(deviceUid: String, channel: String): Double? =
        _latestStatus.value[deviceUid]?.channels?.firstOrNull { it.name == channel }?.duty

    /** 通道当前绑定的曲线（通道级覆盖优先于模式定义） */
    fun boundProfileUid(deviceUid: String, channel: String): String? =
        _channelProfileUids.value[deviceUid]?.get(channel)

    fun profileByUid(uid: String?): CcProfile? =
        uid?.let { target -> _profiles.value.firstOrNull { it.uid == target } }

    fun activeMode(): CcMode? =
        _activeModeUid.value?.let { uid -> _modes.value.firstOrNull { it.uid == uid } }

    // ---------- 写操作（统一收口）----------

    suspend fun setManual(deviceUid: String, channel: String, speed: Int): Result<Unit> =
        write { api ->
            api.setManual(deviceUid, channel, speed).also { result ->
                if (result.isSuccess) {
                    refreshChannelSettings(deviceUid)
                    refreshStatusOnce()
                }
            }
        }

    suspend fun setProfile(deviceUid: String, channel: String, profileUid: String): Result<Unit> =
        write { api ->
            api.setProfile(deviceUid, channel, profileUid).also { result ->
                if (result.isSuccess) {
                    refreshChannelSettings(deviceUid)
                    // 曲线名称要立刻反映到界面，因此连元数据一起回读
                    refreshMetadata()
                }
            }
        }

    /**
     * 恢复曲线控制。
     *
     * `/reset` 只清除设置记录、**不会**把风扇交回曲线，正确做法是按模式定义里
     * 该通道绑定的 `profile_uid` 重新绑定；查不到才退回 `/reset`。
     */
    suspend fun restoreCurve(deviceUid: String, channel: String): Result<Unit> =
        write { api ->
            val fromMode = profileUidFromMode(deviceUid, channel)
            val result = if (fromMode != null) {
                api.setProfile(deviceUid, channel, fromMode)
            } else {
                api.resetChannel(deviceUid, channel)
            }
            if (result.isSuccess) {
                refreshChannelSettings(deviceUid)
                refreshStatusOnce()
            }
            result
        }

    private suspend fun profileUidFromMode(deviceUid: String, channel: String): String? {
        val api = session.api() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val raw = api.modesRaw()
                val modes = raw.optJSONArray("modes") ?: return@runCatching null
                val activeUid = _activeModeUid.value
                for (i in 0 until modes.length()) {
                    val mode = modes.optJSONObject(i) ?: continue
                    if (activeUid != null && mode.optString("uid") != activeUid) continue
                    val settings = mode.optJSONArray("device_settings") ?: continue
                    for (j in 0 until settings.length()) {
                        val entry = settings.optJSONArray(j) ?: continue
                        if (entry.length() < 2) continue
                        if (entry.optString(0) != deviceUid) continue
                        val channelSettings = entry.optJSONArray(1) ?: continue
                        for (k in 0 until channelSettings.length()) {
                            val cs = channelSettings.optJSONObject(k) ?: continue
                            if (cs.optString("channel_name") == channel) {
                                val uid = cs.optString("profile_uid")
                                if (uid.isNotEmpty()) return@runCatching uid
                            }
                        }
                    }
                }
                null
            }.getOrNull()
        }
    }

    suspend fun setActiveMode(modeUid: String): Result<Unit> =
        write { api ->
            api.setActiveMode(modeUid).also { result ->
                if (result.isSuccess) {
                    refreshMetadata()
                    refreshAllChannelSettings(api)
                }
            }
        }

    /** 更新报警规则：基于原始 JSON 改键后整体回传（缺字段会被 422 拒绝） */
    suspend fun updateAlert(
        alertUid: String,
        mutate: (JSONObject) -> Unit,
    ): Result<Unit> = write { api ->
        val raw = _rawAlerts.value ?: api.alertsRaw()
        val alerts = raw.optJSONArray("alerts")
            ?: return@write Result.failure(IllegalStateException("报警数据不可用"))

        var target: JSONObject? = null
        for (i in 0 until alerts.length()) {
            val item = alerts.optJSONObject(i) ?: continue
            if (item.optString("uid") == alertUid) {
                target = item
                break
            }
        }
        val alert = target ?: return@write Result.failure(IllegalStateException("未找到该报警"))

        mutate(alert)
        api.updateAlert(alert).also { result ->
            if (result.isSuccess) refreshAlerts()
        }
    }

    suspend fun renameDevice(deviceUid: String, name: String?): Result<Unit> =
        write { api ->
            api.renameDevice(deviceUid, name).also { result ->
                if (result.isSuccess) refreshMetadata()
            }
        }

    suspend fun renameChannel(deviceUid: String, channel: String, label: String?): Result<Unit> =
        write { api ->
            api.renameChannel(deviceUid, channel, label).also { result ->
                if (result.isSuccess) refreshMetadata()
            }
        }

    /**
     * 启动压力测试。
     *
     * drive 必须给出 `devicePath`，否则 daemon 会以 400 拒绝。
     */
    suspend fun startStressTest(
        kind: String,
        gpuId: String? = null,
        devicePath: String? = null,
    ): Result<Unit> = write { api ->
        api.startStressTest(
            kind = kind,
            gpuId = gpuId,
            devicePath = devicePath,
            // 后端跟随网页端配置（built_in / stress_ng），不传则由 daemon 取默认
            backend = _uiSettings.value.stressBackendFor(kind),
        )
    }

    suspend fun stopStressTest(kind: String): Result<Unit> =
        write { api -> api.stopStressTest(kind) }

    // ---------- 硬件报告 / 电源模式 / 压力测试目标 ----------

    private suspend fun <T> read(block: suspend (CcApiClient) -> Result<T>): Result<T> {
        val api = session.api()
            ?: return Result.failure(IllegalStateException("尚未连接服务器"))
        if (!session.ensureSession()) {
            return Result.failure(IllegalStateException("登录已过期，请重新输入密码"))
        }
        return block(api)
    }

    private val _hardwareReport = MutableStateFlow<String?>(null)
    val hardwareReport: StateFlow<String?> = _hardwareReport.asStateFlow()

    /**
     * 网页端的重命名表。
     *
     * `/devices` 与 `/status` 只给内部名（`temp1`/`fan2`），所有对外显示的名称
     * 都必须先经过这里合并，否则界面会露出英文内部名。
     */
    private val _overrides = MutableStateFlow(CcOverrides.EMPTY)
    val overrides: StateFlow<CcOverrides> = _overrides.asStateFlow()

    suspend fun refreshOverrides(): Result<Unit> =
        read { api -> api.settingsOverrides() }
            .onSuccess { _overrides.value = CcOverrides.parse(it) }
            .map { }

    /**
     * 按网页端主菜单顺序排列的设备列表。
     *
     * 顺序来源是 `/settings/ui` 的 `menuOrder` —— 网页端拖拽主菜单的结果就存在那里。
     * 未出现在 `menuOrder` 中的设备（例如刚接入、网页端还没刷新）排在末尾并按名称排序，
     * 这样新设备不会凭空消失，也不会插进已有顺序中间。
     */
    fun orderedDevices(): List<CcDevice> {
        val devices = _devices.value
        if (devices.isEmpty()) return emptyList()

        val order = _uiSettings.value.menuOrder
        if (order.isEmpty()) return devices

        val rank = order.withIndex().associate { (position, id) -> id to position }
        return devices.sortedWith(
            compareBy({ rank[it.uid] ?: Int.MAX_VALUE }, { it.name })
        )
    }

    // ---------- 界面设置（/settings/ui）----------

    private val _uiSettings = MutableStateFlow(CcUiSettings())
    val uiSettings: StateFlow<CcUiSettings> = _uiSettings.asStateFlow()

    /** `/settings/ui` 的原文。写回是整体替换语义，必须基于它改字段再回传 */
    private var rawUiSettings: String? = null

    suspend fun refreshUiSettings(): Result<Unit> =
        read { api -> api.uiSettings() }
            .onSuccess { text ->
                rawUiSettings = text
                val parsed = CcUiSettings.parse(text)
                _uiSettings.value = parsed
                // 同步到展示层：这三项直接决定时间、频率与曲线怎么渲染
                CcFormat.time24 = parsed.time24
                CcFormat.frequencyPrecision = parsed.frequencyPrecision
                CcFormat.chartLineScale = parsed.chartLineScale
            }
            .map { }

    /**
     * 修改界面设置：把最近一次拉取的完整对象解析出来，应用 [block] 的改动后整体 PUT 回去，
     * 最后重新拉取以确认落库结果（daemon 缺字段会重置，所以不能只发差量）。
     */
    suspend fun updateUiSettings(block: (JSONObject) -> Unit): Result<Unit> {
        val snapshot = rawUiSettings
            ?: return Result.failure(IllegalStateException("界面设置尚未加载，请稍后重试"))
        val json = runCatching { JSONObject(snapshot) }.getOrNull()
            ?: return Result.failure(IllegalStateException("界面设置解析失败"))

        block(json)
        return write { api -> api.updateUiSettings(json) }
            .onSuccess { refreshUiSettings() }
    }

    // ---------- daemon 全局设置（/settings）----------

    private val _daemonSettings = MutableStateFlow(CcDaemonSettings())
    val daemonSettings: StateFlow<CcDaemonSettings> = _daemonSettings.asStateFlow()

    private var rawDaemonSettings: String? = null

    suspend fun refreshDaemonSettings(): Result<Unit> =
        read { api -> api.daemonSettings() }
            .onSuccess { text ->
                rawDaemonSettings = text
                _daemonSettings.value = CcDaemonSettings.parse(text)
            }
            .map { }

    /**
     * 修改 daemon 全局设置。
     *
     * 与 [updateUiSettings] **不同**：`/settings` 是 PATCH 的**差量语义** —— 实测只发
     * `{"liquidctl_integration":true}` 就能改单个字段，其余字段不受影响。
     * 所以这里直接发差量，**不要求先拿到完整快照**；否则页面尚未拉取过数据时，
     * 每次写入都会因「快照为空」而静默失败。
     */
    suspend fun updateDaemonSettings(patch: JSONObject): Result<Unit> =
        write { api -> api.updateDaemonSettings(patch) }
            .onSuccess { refreshDaemonSettings() }

    private val _powerProfiles = MutableStateFlow<CcPowerProfiles?>(null)
    val powerProfiles: StateFlow<CcPowerProfiles?> = _powerProfiles.asStateFlow()

    private val _stressGpus = MutableStateFlow<List<CcStressTarget>>(emptyList())
    val stressGpus: StateFlow<List<CcStressTarget>> = _stressGpus.asStateFlow()

    private val _stressDrives = MutableStateFlow<List<CcStressTarget>>(emptyList())
    val stressDrives: StateFlow<List<CcStressTarget>> = _stressDrives.asStateFlow()

    suspend fun refreshHardwareReport(): Result<Unit> =
        read { api -> api.hardwareReport() }
            .onSuccess { _hardwareReport.value = it }
            .map { }

    suspend fun refreshPowerProfiles(): Result<Unit> =
        read { api -> api.powerProfiles() }
            .onSuccess { raw -> _powerProfiles.value = CcPowerProfiles.parse(raw) }
            .map { }

    suspend fun setPowerProfileModes(profileToModeUid: Map<String, String>): Result<Unit> =
        write { api ->
            val modes = JSONObject().apply {
                profileToModeUid.forEach { (profile, modeUid) -> put(profile, modeUid) }
            }
            api.setPowerProfileModes(modes).also { result ->
                if (result.isSuccess) refreshPowerProfiles()
            }
        }

    suspend fun refreshStressTargets(): Result<Unit> =
        read { api -> api.stressGpus() }
            .onSuccess { raw -> _stressGpus.value = CcStressTarget.parseList(raw, isGpu = true) }
            .let { gpuResult ->
                read { api -> api.stressDrives() }
                    .onSuccess { raw -> _stressDrives.value = CcStressTarget.parseList(raw, isGpu = false) }
                    .map { }
                    .takeIf { it.isSuccess } ?: gpuResult.map { }
            }

    private suspend fun write(
        block: suspend (CcApiClient) -> Result<Unit>,
    ): Result<Unit> {
        val api = session.api()
            ?: return Result.failure(IllegalStateException("尚未连接服务器"))
        if (!session.ensureSession()) {
            return Result.failure(IllegalStateException("登录已过期，请重新输入密码"))
        }
        return block(api)
    }

    companion object {
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L

        /** 网络变化后的防抖时间 */
        private const val NETWORK_DEBOUNCE_MS = 2_000L

        /** 官方无历史时序接口，曲线只能前台累积；上限 65 分钟 */
        const val HISTORY_WINDOW_MS = 65 * 60 * 1000L
    }
}
