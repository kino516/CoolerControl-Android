package com.example.kernelsustyleuikit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.CcGraph
import com.example.kernelsustyleuikit.data.local.CcPrefs
import com.example.kernelsustyleuikit.data.model.CcAlert
import com.example.kernelsustyleuikit.data.model.CcAlertLog
import com.example.kernelsustyleuikit.data.model.CcChannelStats
import com.example.kernelsustyleuikit.data.model.CcEndpointKind
import com.example.kernelsustyleuikit.data.model.CcMode
import com.example.kernelsustyleuikit.data.model.CcProfile
import com.example.kernelsustyleuikit.data.model.CcSpeedOptions
import com.example.kernelsustyleuikit.data.repository.CcSseState
import com.example.kernelsustyleuikit.data.session.CcSessionState
import com.example.kernelsustyleuikit.templateApp
import com.example.kernelsustyleuikit.ui.component.cc.CcSegment
import com.example.kernelsustyleuikit.ui.util.CcFormat
import com.example.kernelsustyleuikit.ui.screen.home.HomeCardUi
import com.example.kernelsustyleuikit.ui.screen.home.HomeUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 主页 ViewModel。
 *
 * 与 UI Kit 既有约定一致：**无参构造**，依赖通过 [CcGraph] 取
 * （`AndroidViewModel` 在 Pager 页面内创建时会因 CreationExtras 缺 APPLICATION_KEY 而闪退）。
 */
class HomeViewModel : ViewModel() {

    private val repo = CcGraph.repository
    private val prefs = CcGraph.prefs
    private val session = CcGraph.session

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    /**
     * 加载态由 ViewModel 显式管理。
     *
     * 早先是靠「已连接 && 设备列表为空」推断的，但那样有两个问题：
     * 内网拉取太快时动画一闪而过，以及拉取失败时会永远停在加载态。
     */
    private val _loading = MutableStateFlow(false)

    init {
        viewModelScope.launch { repo.devices.collect { rebuild() } }
        viewModelScope.launch { repo.latestStatus.collect { rebuild() } }
        // 网页端改了名字要立刻反映到卡片标题上
        viewModelScope.launch { repo.overrides.collect { rebuild() } }
        viewModelScope.launch { repo.alerts.collect { rebuild() } }
        viewModelScope.launch { repo.alertLogs.collect { rebuild() } }
        viewModelScope.launch { repo.modes.collect { rebuild() } }
        viewModelScope.launch { repo.activeModeUid.collect { rebuild() } }
        viewModelScope.launch { repo.profiles.collect { rebuild() } }
        viewModelScope.launch { repo.sseState.collect { rebuild() } }
        // historyTick 与 latestStatus 在 CcRepository.applyStatus() 里是同一次写入，
        // 两个流各挂一个 collect 只会让每个 SSE 事件触发两次全量重建（每秒级热路径）。
        // rebuild() 内直接读 repo.historyTick.value 即可拿到同样的值。
        viewModelScope.launch {
            session.state.collect { state ->
                // 刚连上时补一次全量拉取。ViewModel 往往在登录完成**之前**就创建了，
                // init 里那次 refreshAll 会被 isConnected 挡掉；若不在这里补，
                // 主页就会一直停在加载态，直到用户手动切一次页面。
                if (state.isConnected && repo.devices.value.isEmpty() && !_loading.value) {
                    loadWithFeedback()
                }
                rebuild()
            }
        }

        viewModelScope.launch {
            repo.startRealtime()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            // 已经在加载中就直接跳过，避免和启动时那次并发
            if (_loading.value) return@launch

            // 有数据时静默刷新：每次切回主页都闪一下加载动画很打扰。
            // 只有在还没拿到任何设备（首次启动，或上次拉取失败）时才显示动画。
            if (repo.devices.value.isEmpty()) {
                loadWithFeedback()
            } else {
                repo.refreshAll()
            }
        }
    }

    /**
     * 拉取数据，并让加载态至少显示 [MIN_LOADING_MS]。
     *
     * 内网环境下整轮拉取往往只要几十毫秒，动画会一闪而过、看起来像闪屏；
     * 给个显示下限反而更自然，也避免用户根本没察觉就跳过了加载。
     */
    private suspend fun loadWithFeedback() {
        _loading.value = true
        rebuild()
        val startedAt = System.currentTimeMillis()
        try {
            repo.refreshAll()
        } finally {
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < MIN_LOADING_MS) delay(MIN_LOADING_MS - elapsed)
            _loading.value = false
            rebuild()
        }
    }

    fun setEditMode(enabled: Boolean) {
        _editMode.value = enabled
    }

    // ---------- 卡片管理 ----------

    fun setCardOrder(order: List<String>) {
        prefs.dashboardCards = order
        rebuild()
    }

    fun removeCard(cardId: String) {
        prefs.dashboardCards = currentOrder().filterNot { it == cardId }
        rebuild()
    }

    fun addCard(cardId: String) {
        val current = currentOrder()
        if (cardId in current) return
        prefs.dashboardCards = current + cardId
        rebuild()
    }

    /** 编辑模式下「添加卡片」的可选项：当前未显示的卡片 */
    fun availableCards(): List<Pair<String, String>> {
        val shown = currentOrder().toSet()
        return buildPool().filterKeys { it !in shown }.map { (id, card) -> id to card.title }
    }

    private fun currentOrder(): List<String> =
        prefs.dashboardCards.ifEmpty { _uiState.value.cards.map { it.id } }

    // ---------- 对话框数据 ----------

    fun statsFor(deviceUid: String, channel: String): CcChannelStats? =
        repo.stats.value.firstOrNull { it.uid == deviceUid }
            ?.let { it.temps[channel] ?: it.channels[channel] }

    fun trendFor(
        deviceUid: String,
        channel: String,
        windowMs: Long = DEFAULT_DETAIL_WINDOW_MS,
    ): List<Double> = repo.historyFor("$deviceUid|$channel", windowMs).map { it.value }

    fun profiles(): List<CcProfile> = repo.profiles.value

    fun profileName(profileUid: String?): String? = repo.profileByUid(profileUid)?.name

    fun boundProfileName(deviceUid: String, channel: String): String? =
        profileName(repo.boundProfileUid(deviceUid, channel))

    fun speedOptionsFor(deviceUid: String, channel: String): CcSpeedOptions? =
        repo.device(deviceUid)?.channels?.get(channel)?.speedOptions

    fun deviceName(deviceUid: String): String? = repo.device(deviceUid)?.name

    fun deviceType(deviceUid: String): String = repo.device(deviceUid)?.type.orEmpty()

    fun latestRpm(deviceUid: String, channel: String): Int? = repo.latestRpm(deviceUid, channel)

    fun latestDuty(deviceUid: String, channel: String): Double? = repo.latestDuty(deviceUid, channel)

    fun latestTemp(deviceUid: String, channel: String): Double? = repo.latestTemp(deviceUid, channel)

    fun modes(): List<CcMode> = repo.modes.value

    fun activeModeUid(): String? = repo.activeModeUid.value

    fun alerts(): List<CcAlert> = repo.alerts.value

    fun alertLogs(): List<CcAlertLog> = repo.alertLogs.value

    // ---------- 写操作（统一提示）----------

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    fun applyManual(deviceUid: String, channel: String, speed: Int) {
        viewModelScope.launch {
            repo.setManual(deviceUid, channel, speed)
                .onSuccess { _message.value = templateApp.getString(R.string.cc_applied) }
                .onFailure { _message.value = it.message }
        }
    }

    fun selectProfile(deviceUid: String, channel: String, profileUid: String) {
        viewModelScope.launch {
            repo.setProfile(deviceUid, channel, profileUid)
                .onFailure { _message.value = it.message }
        }
    }

    fun restoreCurve(deviceUid: String, channel: String) {
        viewModelScope.launch {
            repo.restoreCurve(deviceUid, channel)
                .onFailure { _message.value = it.message }
        }
    }

    fun switchMode(modeUid: String) {
        viewModelScope.launch {
            repo.setActiveMode(modeUid).onFailure { _message.value = it.message }
        }
    }

    // ---------- 内部 ----------

    private fun rebuild() {
        val sessionState = session.state.value
        _uiState.value = HomeUiState(
            connected = sessionState.isConnected,
            errorMessage = sessionState.errorMessage,
            daemonVersion = sessionState.daemonVersion,
            endpointLabel = endpointLabel(sessionState),
            sseConnected = repo.sseState.value == CcSseState.Connected,
            deviceCount = repo.devices.value.size,
            modeCount = repo.modes.value.size,
            alertTotal = repo.alerts.value.size,
            alertActive = repo.alerts.value.count { it.isActive },
            cards = orderedCards(),
            historyTick = repo.historyTick.value,
            isLoading = _loading.value,
        )
    }

    /** 当前所有可渲染的卡片（按 id 索引） */
    private fun buildPool(): Map<String, HomeCardUi> {
        val pool = LinkedHashMap<String, HomeCardUi>()
        val statusMap = repo.latestStatus.value

        repo.devices.value.forEach { device ->
            val status = statusMap[device.uid] ?: return@forEach
            val deviceLabel = device.name

            status.temps.forEach { temp ->
                val id = CcPrefs.cardIdTemp(device.uid, temp.name)
                pool[id] = HomeCardUi.Temperature(
                    id = id,
                    // 温度显示名来自 /devices 的 info.temps（/status 只有内部名）
                    title = device.channelDisplayName(temp.name),
                    subtitle = deviceLabel,
                    deviceUid = device.uid,
                    channel = temp.name,
                    deviceType = device.type,
                    value = temp.temp,
                    trend = repo.historyFor("${device.uid}|${temp.name}", TREND_WINDOW_MS)
                        .map { it.value },
                )
            }

            // 判定风扇的唯一依据是「有转速读数」
            status.channels.filter { it.isFan }.forEach { fan ->
                val id = CcPrefs.cardIdFan(device.uid, fan.name)
                pool[id] = HomeCardUi.Fan(
                    id = id,
                    title = device.channelDisplayName(fan.name),
                    subtitle = deviceLabel,
                    deviceUid = device.uid,
                    channel = fan.name,
                    rpm = fan.rpm,
                    duty = fan.duty,
                )
            }
        }

        val alerts = repo.alerts.value
        pool[CcPrefs.CARD_ALERT] = HomeCardUi.AlertSummary(
            id = CcPrefs.CARD_ALERT,
            title = templateApp.getString(R.string.cc_card_alerts),
            total = alerts.size,
            active = alerts.count { it.isActive },
            recent = repo.alertLogs.value.take(3),
        )
        pool[CcPrefs.CARD_MODE] = HomeCardUi.ModeSummary(
            id = CcPrefs.CARD_MODE,
            title = templateApp.getString(R.string.cc_card_mode),
            currentMode = repo.activeMode()?.name,
            currentModeUid = repo.activeMode()?.uid,
            modeCount = repo.modes.value.size,
            // 模式列表随卡片一起下发，首页就能直接切换 ——
            // 交互与冷却页的分段控件完全一致，不必再弹选择对话框
            modes = repo.modes.value.map { CcSegment(it.uid, it.name) },
        )
        pool[CcPrefs.CARD_OVERVIEW] = HomeCardUi.Overview(
            id = CcPrefs.CARD_OVERVIEW,
            title = templateApp.getString(R.string.cc_card_network),
            deviceCount = repo.devices.value.size,
            alertCount = alerts.size,
            daemonVersion = session.state.value.daemonVersion,
            endpointLabel = endpointLabel(session.state.value),
            connected = session.state.value.isConnected,
            sseConnected = repo.sseState.value == CcSseState.Connected,
        )
        return pool
    }

    private fun orderedCards(): List<HomeCardUi> {
        val pool = buildPool()
        val stored = prefs.dashboardCards
        if (stored.isEmpty()) {
            // 首次进入：按设备顺序生成默认布局并持久化
            val default = pool.keys.toList()
            prefs.dashboardCards = default
            return default.mapNotNull { pool[it] }
        }
        // 已定制过：严格按用户顺序渲染（已删除的卡片不会自己回来）
        return stored.mapNotNull { pool[it] }
    }

    private fun endpointLabel(state: CcSessionState): String? {
        val url = state.endpointUrl ?: return null
        val kind = when (state.endpointKind) {
            CcEndpointKind.Lan -> templateApp.getString(R.string.cc_endpoint_lan)
            CcEndpointKind.Wan -> templateApp.getString(R.string.cc_endpoint_wan)
            null -> null
        }
        val host = url.substringAfter("://").substringBefore('/')
        val latency = state.latencyMs?.let { "${it}ms" }
        return listOfNotNull(kind, host, latency).joinToString(" · ")
    }

    private companion object {
        /** 卡片内迷你趋势线的窗口 */
        const val TREND_WINDOW_MS = 60_000L

        /** 温度详情对话框内曲线的窗口 */
        const val DEFAULT_DETAIL_WINDOW_MS = 5 * 60 * 1000L

        /**
         * 加载动画的最短显示时长。
         *
         * 内网整轮拉取通常只要几十毫秒，没有这个下限的话动画会一闪而过，
         * 用户只会看到界面「闪了一下」，反而像是渲染出问题。
         */
        const val MIN_LOADING_MS = 900L
    }
}
