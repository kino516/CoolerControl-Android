package com.example.kernelsustyleuikit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.CcGraph
import com.example.kernelsustyleuikit.data.repository.CcRepository
import com.example.kernelsustyleuikit.templateApp
import com.example.kernelsustyleuikit.ui.screen.monitor.MonitorSection
import com.example.kernelsustyleuikit.ui.screen.monitor.MonitorSeries
import com.example.kernelsustyleuikit.ui.screen.monitor.MonitorStatRow
import com.example.kernelsustyleuikit.ui.screen.monitor.MonitorUiState
import com.example.kernelsustyleuikit.ui.screen.monitor.RenameChannelUi
import com.example.kernelsustyleuikit.ui.screen.monitor.DeviceDetailUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/**
 * 监控页 ViewModel：时间温度表、报警规则编辑与静默、最近报警、历史温度统计。
 *
 * 时间温度表的数据只能在**前台运行期间**累积（官方 5.x 没有历史时序接口），
 * 因此内存窗口上限为 65 分钟。
 */
class MonitorViewModel : ViewModel() {

    private val repo = CcGraph.repository
    private val prefs = CcGraph.prefs
    private val session = CcGraph.session

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val rangeMinutes = MutableStateFlow(prefs.monitorRangeMinutes)
    private val hiddenSeries = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch { repo.devices.collect { rebuild() } }
        viewModelScope.launch { repo.latestStatus.collect { rebuild() } }
        viewModelScope.launch { repo.stats.collect { rebuild() } }
        viewModelScope.launch { repo.alerts.collect { rebuild() } }
        viewModelScope.launch { repo.alertLogs.collect { rebuild() } }
        viewModelScope.launch { repo.health.collect { rebuild() } }
        // 说明同 HomeViewModel：historyTick 由 applyStatus() 与 latestStatus 同时写入，
        // 再单独 collect 一次会让每个 SSE 事件重建两遍整份 MonitorUiState
        viewModelScope.launch { rangeMinutes.collect { rebuild() } }
        viewModelScope.launch { hiddenSeries.collect { rebuild() } }

        viewModelScope.launch {
            if (session.state.value.isConnected) repo.refreshAll()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            repo.refreshAll()
            // 硬件报告变化极少，跟着页面刷新拉一次即可
            repo.refreshHardwareReport()
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun setEditMode(enabled: Boolean) {
        _editMode.value = enabled
    }

    // ---------- 时间范围 ----------

    fun setRangeMinutes(minutes: Int) {
        rangeMinutes.value = minutes
        prefs.monitorRangeMinutes = minutes
    }

    fun toggleSeriesVisibility(key: String) {
        hiddenSeries.value = hiddenSeries.value.let {
            if (key in it) it - key else it + key
        }
    }

    // ---------- 区块管理 ----------

    fun setSectionOrder(order: List<String>) {
        prefs.monitorSections = order
        rebuild()
    }

    fun removeSection(id: String) {
        prefs.monitorSections = currentSections().filterNot { it == id }
        rebuild()
    }

    fun addSection(id: String) {
        val current = currentSections()
        if (id in current) return
        prefs.monitorSections = current + id
        rebuild()
    }

    fun availableSections(): List<Pair<String, String>> {
        val shown = currentSections().toSet()
        return MonitorSection.all.filterNot { it in shown }.map { it to sectionTitle(it) }
    }

    private fun currentSections(): List<String> {
        val saved = prefs.monitorSections.ifEmpty { return MonitorSection.all }
        val valid = saved.filter { it in MonitorSection.all }
        // 新增的区块（例如从冷却页迁移过来的设备重命名）自动补到末尾，
        // 否则老用户的已保存顺序里永远看不到它
        val missing = MonitorSection.all.filterNot { it in valid }
        return valid + missing
    }

    private fun sectionTitle(id: String): String = when (id) {
        MonitorSection.CHART -> templateApp.getString(R.string.cc_section_chart)
        MonitorSection.ALERTS -> templateApp.getString(R.string.cc_section_alert_rules)
        MonitorSection.RECENT -> templateApp.getString(R.string.cc_section_recent)
        MonitorSection.STATS -> templateApp.getString(R.string.cc_section_stats)
        MonitorSection.DEVICES -> templateApp.getString(R.string.cc_section_devices)
        MonitorSection.HARDWARE -> templateApp.getString(R.string.cc_section_hardware)
        else -> id
    }

    // ---------- 监控通道选择 ----------

    fun selectedChannelKeys(): Set<String> {
        val stored = prefs.monitorChannels
        if (stored.isNotEmpty()) return stored.toSet()
        // 默认监控全部温度通道（最多 4 条，与图表可读性一致）
        return repo.devices.value
            .flatMap { device ->
                val temps = repo.latestStatus.value[device.uid]?.temps.orEmpty()
                temps.map { "${device.uid}|${it.name}" }
            }
            .take(DEFAULT_CHANNEL_LIMIT)
            .toSet()
    }

    fun setSelectedChannels(keys: Set<String>) {
        prefs.monitorChannels = keys.toList()
        rebuild()
    }

    /** 全部可选监控通道（按设备分组），含当前选中状态 */
    fun allChannels(): List<MonitorChannelOption> {
        val selected = selectedChannelKeys()
        return repo.devices.value.flatMap { device ->
            val temps = repo.latestStatus.value[device.uid]?.temps.orEmpty()
            temps.map { temp ->
                val key = "${device.uid}|${temp.name}"
                MonitorChannelOption(
                    key = key,
                    deviceName = device.name,
                    channelLabel = device.channelDisplayName(temp.name),
                    selected = key in selected,
                )
            }
        }
    }

    fun toggleChannel(key: String) {
        val current = selectedChannelKeys()
        setSelectedChannels(if (key in current) current - key else current + key)
    }

    // ---------- 设备详情 ----------

    /**
     * 设备详情：所有设备 + 其下所有通道 + 驱动信息。
     *
     * 顺序取自网页端（[CcRepository.orderedDevices]），与网页端设备列表一致。
     * 通道必须**同时**取自 `info.channels`（风扇等）与 `info.temps`（温度传感器）——
     * 只取前者的话，纯传感器设备（如融合传感器）会只剩一个设备名、没有可改的项。
     */
    private fun buildDeviceDetails(): List<DeviceDetailUi> =
        repo.orderedDevices().map { device ->
            val channels = buildList {
                device.channels.values.forEach { channel ->
                    add(
                        RenameChannelUi(
                            name = channel.name,
                            label = channel.label?.takeIf { it.isNotBlank() } ?: channel.name,
                            isFan = channel.speedOptions != null,
                        )
                    )
                }
                device.temps.forEach { (name, label) ->
                    add(
                        RenameChannelUi(
                            name = name,
                            label = label,
                            isFan = false,
                        )
                    )
                }
            }
            DeviceDetailUi(
                uid = device.uid,
                name = device.name,
                type = device.type,
                driverType = device.driverType,
                driverName = device.driverName,
                driverVersion = device.driverVersion,
                driverLocations = device.driverLocations,
                model = device.model,
                tempRange = if (device.tempMin != null && device.tempMax != null) {
                    "${device.tempMin} ~ ${device.tempMax}℃"
                } else {
                    null
                },
                channels = channels,
            )
        }

    fun renameDevice(deviceUid: String, name: String?) {
        viewModelScope.launch {
            repo.renameDevice(deviceUid, name).onFailure { _message.value = it.message }
        }
    }

    fun renameChannel(deviceUid: String, channel: String, label: String?) {
        viewModelScope.launch {
            repo.renameChannel(deviceUid, channel, label).onFailure { _message.value = it.message }
        }
    }

    // ---------- 报警规则 ----------

    fun setAlertEnabled(alertUid: String, enabled: Boolean) {
        viewModelScope.launch {
            repo.updateAlert(alertUid) { json -> json.put("enabled", enabled) }
                .onFailure { _message.value = it.message }
        }
    }

    fun updateAlertLimits(alertUid: String, min: Double, max: Double) {
        viewModelScope.launch {
            repo.updateAlert(alertUid) { json ->
                json.put("min", min)
                json.put("max", max)
            }.onFailure { _message.value = it.message }
        }
    }

    fun renameAlert(alertUid: String, name: String) {
        viewModelScope.launch {
            repo.updateAlert(alertUid) { json -> json.put("name", name) }
                .onFailure { _message.value = it.message }
        }
    }

    /** 静默：duration 为 null 表示取消静默 */
    fun silenceAlert(alertUid: String, duration: Duration?) {
        viewModelScope.launch {
            repo.updateAlert(alertUid) { json: JSONObject ->
                if (duration == null) {
                    json.put("silenced_until", JSONObject.NULL)
                } else {
                    json.put("silenced_until", Instant.now().plus(duration).toString())
                }
            }.onFailure { _message.value = it.message }
        }
    }

    // ---------- 内部 ----------

    private fun rebuild() {
        val devices = repo.devices.value
        val selected = selectedChannelKeys()
        val windowMs = rangeMinutes.value * 60_000L

        val series = buildList {
            var colorIndex = 0
            devices.forEach { device ->
                selected
                    .filter { it.startsWith("${device.uid}|") }
                    .forEach { key ->
                        val channel = key.substringAfter('|')
                        val points = repo.historyFor(key, windowMs)
                        add(
                            MonitorSeries(
                                key = key,
                                deviceUid = device.uid,
                                channel = channel,
                                deviceName = device.name,
                                deviceType = device.type,
                                channelLabel = device.channelDisplayName(channel),
                                colorIndex = colorIndex % SERIES_COLORS,
                                points = points,
                                current = repo.latestTemp(device.uid, channel),
                            )
                        )
                        colorIndex++
                    }
            }
        }

        // 行顺序跟随网页端主菜单排序，与「设备详情」卡片保持一致。
        // /stats 的返回顺序与菜单排序无关，必须在这里显式排一次；
        // 未出现在菜单里的设备落到末尾，避免新设备插进已有顺序中间。
        val deviceRank = repo.orderedDevices()
            .withIndex()
            .associate { (position, device) -> device.uid to position }

        val stats = buildList {
            repo.stats.value
                .sortedBy { deviceRank[it.uid] ?: Int.MAX_VALUE }
                .forEach { deviceStats ->
                    val device = devices.firstOrNull { it.uid == deviceStats.uid }
                    val deviceName = device?.name ?: deviceStats.uid
                    deviceStats.temps.forEach { (channel, channelStats) ->
                        add(
                            MonitorStatRow(
                                deviceName = deviceName,
                                deviceType = device?.type.orEmpty(),
                                channelLabel = device?.channelDisplayName(channel) ?: channel,
                                min = channelStats.min,
                                avg = channelStats.avg,
                                max = channelStats.max,
                            )
                        )
                    }
                }
        }

        _uiState.value = MonitorUiState(
            sections = currentSections(),
            editMode = _editMode.value,
            rangeMinutes = rangeMinutes.value,
            series = series,
            hiddenSeries = hiddenSeries.value,
            alerts = repo.alerts.value,
            alertLogs = repo.alertLogs.value.take(20),
            stats = stats,
            deviceDetails = buildDeviceDetails(),
            hardwareReport = repo.hardwareReport.value,
            healthIssueCount = repo.health.value.let {
                it.failsafe + it.unreachable + it.missing + it.staleSource
            },
            historyTick = repo.historyTick.value,
        )
    }

    private companion object {
        const val DEFAULT_CHANNEL_LIMIT = 4
        const val SERIES_COLORS = 8
    }
}

/** 监控通道选项（用于通道多选对话框） */
data class MonitorChannelOption(
    val key: String,
    val deviceName: String,
    val channelLabel: String,
    val selected: Boolean,
)
