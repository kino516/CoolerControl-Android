package com.example.kernelsustyleuikit.ui.screen.monitor

import androidx.compose.runtime.Immutable
import com.example.kernelsustyleuikit.data.model.CcAlert
import com.example.kernelsustyleuikit.data.model.CcAlertLog
import com.example.kernelsustyleuikit.data.repository.CcSample

/** 监控页区块 id */
object MonitorSection {
    const val CHART = "chart"
    const val ALERTS = "alerts"
    const val RECENT = "recent"
    const val STATS = "stats"
    /** 设备详情（重命名入口在卡片标题栏），已取代原先独立的「设备重命名」卡片 */
    const val DEVICES = "devices"
    const val HARDWARE = "hardware"

    val all = listOf(CHART, ALERTS, RECENT, STATS, DEVICES, HARDWARE)
}

@Immutable
data class MonitorUiState(
    val sections: List<String> = MonitorSection.all,
    val editMode: Boolean = false,
    /** 时间窗：1 / 5 / 15 / 60 分钟 */
    val rangeMinutes: Int = 5,
    val series: List<MonitorSeries> = emptyList(),
    val hiddenSeries: Set<String> = emptySet(),
    val alerts: List<CcAlert> = emptyList(),
    val alertLogs: List<CcAlertLog> = emptyList(),
    val stats: List<MonitorStatRow> = emptyList(),
    /** 设备详情列表，顺序与网页端一致 */
    val deviceDetails: List<DeviceDetailUi> = emptyList(),
    /** daemon 提供的硬件支持报告（纯文本） */
    val hardwareReport: String? = null,
    val healthIssueCount: Int = 0,
    val historyTick: Long = 0L,
) {
    val visibleSeries: List<MonitorSeries>
        get() = series.filter { it.key !in hiddenSeries }
}

/**
 * 设备详情：一台设备 + 它的驱动信息 + 它下面所有可改名的通道。
 *
 * daemon 侧有两级命名（`PUT /settings/devices/{uid}/overrides` 改设备名，
 * `PUT /settings/devices/{uid}/channels/{channel}/overrides` 改通道名），
 * 详情弹窗里把两级都摊平展示，避免用户漏掉传感器。
 */
@Immutable
data class DeviceDetailUi(
    val uid: String,
    val name: String,
    /** 设备类型：CPU / GPU / Hwmon / CustomSensors ... */
    val type: String,
    /** 驱动来源：Kernel / CoolerControl */
    val driverType: String? = null,
    /** 具体驱动名，如 it87 / sd / spd5118 */
    val driverName: String? = null,
    val driverVersion: String? = null,
    /** sysfs 路径 */
    val driverLocations: List<String> = emptyList(),
    /** 硬件型号 */
    val model: String? = null,
    /** 温度量程，形如 "0 ~ 150℃" */
    val tempRange: String? = null,
    val channels: List<RenameChannelUi> = emptyList(),
) {
    /** 风扇通道数 */
    val fanCount: Int get() = channels.count { it.isFan }
    /** 传感器通道数 */
    val sensorCount: Int get() = channels.count { !it.isFan }
}

@Immutable
data class RenameChannelUi(
    /** 通道内部名，调用 API 时用 */
    val name: String,
    /** 当前显示名（label 优先，否则内部名） */
    val label: String,
    /** 是否可调速（风扇） */
    val isFan: Boolean,
)

/** 时间温度表里的一条曲线 */
@Immutable
data class MonitorSeries(
    val key: String,
    val deviceUid: String,
    val channel: String,
    val deviceName: String,
    /** 设备类型，用于温度配色的区间判定（CPU / Hwmon / ...） */
    val deviceType: String,
    val channelLabel: String,
    val colorIndex: Int,
    val points: List<CcSample>,
    val current: Double?,
) {
    val label: String get() = "$deviceName · $channelLabel"
}

/** 历史温度统计的一行：设备名为主标题、传感器名为副标题 */
@Immutable
data class MonitorStatRow(
    val deviceName: String,
    /** 设备类型，用于温度配色的区间判定 */
    val deviceType: String,
    val channelLabel: String,
    val min: Double?,
    val avg: Double?,
    val max: Double?,
)
