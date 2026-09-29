package com.example.kernelsustyleuikit.ui.screen.monitor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.model.CcAlert
import com.example.kernelsustyleuikit.data.model.CcAlertLog
import com.example.kernelsustyleuikit.ui.component.cc.CC_SERIES_COLORS
import com.example.kernelsustyleuikit.ui.component.cc.CcActionText
import com.example.kernelsustyleuikit.ui.component.cc.CcContentDialog
import com.example.kernelsustyleuikit.ui.component.cc.CcCard
import com.example.kernelsustyleuikit.ui.component.cc.CcText
import com.example.kernelsustyleuikit.ui.component.cc.CcTextStyle
import com.example.kernelsustyleuikit.ui.component.cc.ChartSeries
import com.example.kernelsustyleuikit.ui.component.cc.MultiLineChart
import com.example.kernelsustyleuikit.ui.component.cc.ccOnSurfaceColor
import com.example.kernelsustyleuikit.ui.component.cc.ccPrimaryColor
import com.example.kernelsustyleuikit.ui.screen.home.alertEventLabel
import com.example.kernelsustyleuikit.ui.util.CcFormat
import com.example.kernelsustyleuikit.ui.util.CcTemperatureStyle
import kotlin.math.roundToInt

/** 监控页回调集合 */
@Immutable
data class MonitorCallbacks(
    val onRangeChange: (Int) -> Unit = {},
    val onToggleSeries: (String) -> Unit = {},
    val onSelectChannels: () -> Unit = {},
    val onEditAlert: (CcAlert) -> Unit = {},
    val onSilenceAlert: (CcAlert) -> Unit = {},
    val onToggleAlertEnabled: (uid: String, enabled: Boolean) -> Unit = { _, _ -> },
    val onRenameDevice: (uid: String, current: String) -> Unit = { _, _ -> },
    val onRenameChannel: (deviceUid: String, channel: String, current: String) -> Unit = { _, _, _ -> },
)

/** 按区块 id 渲染监控页内容 */
@Composable
fun MonitorSectionContent(
    sectionId: String,
    state: MonitorUiState,
    callbacks: MonitorCallbacks,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    when (sectionId) {
        MonitorSection.CHART -> ChartSection(state, callbacks, editMode, onRemove)
        MonitorSection.ALERTS -> AlertsSection(state, callbacks, editMode, onRemove)
        MonitorSection.RECENT -> RecentSection(state, editMode, onRemove)
        MonitorSection.STATS -> StatsSection(state, editMode, onRemove)
        MonitorSection.DEVICES -> DeviceDetailSection(state, callbacks, editMode, onRemove)
        MonitorSection.HARDWARE -> HardwareSection(state, editMode, onRemove)
    }
}

// ---------- 硬件信息 ----------

/** 折叠时预览的行数 */
private const val HARDWARE_PREVIEW_LINES = 8

@Composable
private fun HardwareSection(
    state: MonitorUiState,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val report = state.hardwareReport
    val clipboard = LocalClipboardManager.current

    CcCard(editMode = editMode, onRemove = onRemove) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CcText(
                text = stringResource(R.string.cc_section_hardware),
                style = CcTextStyle.Title,
                modifier = Modifier.weight(1f),
            )
            if (!report.isNullOrBlank()) {
                CcActionText(
                    text = stringResource(R.string.cc_copy),
                    onClick = { clipboard.setText(AnnotatedString(report)) },
                )
            }
        }

        if (report.isNullOrBlank()) {
            CcText(
                text = stringResource(R.string.cc_hardware_unavailable),
                style = CcTextStyle.Subtitle,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@CcCard
        }

        val lines = report.lines().filter { it.isNotBlank() }
        val shown = if (expanded) lines else lines.take(HARDWARE_PREVIEW_LINES)

        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            shown.forEach { line ->
                HardwareLine(line)
            }
        }

        if (lines.size > HARDWARE_PREVIEW_LINES) {
            CcActionText(
                text = if (expanded) {
                    stringResource(R.string.cc_collapse)
                } else {
                    stringResource(R.string.cc_hardware_more, lines.size - HARDWARE_PREVIEW_LINES)
                },
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * 报告里 `Board    CWWK Default string` 这类「键 + 连续空格 + 值」的行。
 *
 * 拆成左右两列后，扫一眼就能找到 Kernel / BIOS，不用在长文本里逐字找。
 */
private val HARDWARE_FIELD_REGEX = Regex("""^(\S+)\s{2,}(.+)$""")

/** 键列固定宽度，保证多行的值对齐 */
private val HARDWARE_KEY_WIDTH = 68.dp

@Composable
private fun HardwareLine(line: String) {
    val match = HARDWARE_FIELD_REGEX.matchEntire(line.trim())
    val isIndented = line.startsWith(" ")

    when {
        // 键值行：左键右值
        match != null && !isIndented -> {
            val (key, value) = match.destructured
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CcText(
                    text = key,
                    style = CcTextStyle.Caption,
                    modifier = Modifier.width(HARDWARE_KEY_WIDTH),
                    maxLines = 1,
                )
                CcText(
                    text = value,
                    style = CcTextStyle.Body,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 缩进明细（如具体通道）：小字 + 缩进，与分组标题拉开层次
        isIndented -> CcText(
            text = line.trim(),
            style = CcTextStyle.Caption,
            modifier = Modifier.padding(start = 10.dp, top = 2.dp),
        )

        // 分组标题（HWMon Fan Channels / Liquidctl …）
        else -> CcText(
            text = line,
            style = CcTextStyle.Body,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ---------- 设备详情 ----------

@Composable
private fun DeviceDetailSection(
    state: MonitorUiState,
    callbacks: MonitorCallbacks,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    // 正在查看详情的设备；null 表示未打开弹窗
    var detailDevice by remember { mutableStateOf<DeviceDetailUi?>(null) }
    // 重命名模式：全卡片只有一个入口，放在标题栏最右侧
    var renameMode by remember { mutableStateOf(false) }

    CcCard(editMode = editMode, onRemove = onRemove) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CcText(
                text = stringResource(R.string.cc_section_devices),
                style = CcTextStyle.Title,
                modifier = Modifier.weight(1f),
            )
            CcActionText(
                text = stringResource(
                    if (renameMode) R.string.cc_done else R.string.cc_rename_action
                ),
                onClick = { renameMode = !renameMode },
            )
        }
        CcText(
            text = stringResource(
                if (renameMode) R.string.cc_rename_hint else R.string.cc_device_hint
            ),
            style = CcTextStyle.Caption,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (state.deviceDetails.isEmpty()) {
            CcText(
                text = stringResource(R.string.cc_device_empty),
                style = CcTextStyle.Subtitle,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@CcCard
        }

        state.deviceDetails.forEach { device ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        if (renameMode) {
                            callbacks.onRenameDevice(device.uid, device.name)
                        } else {
                            detailDevice = device
                        }
                    }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    CcText(
                        text = device.name,
                        style = CcTextStyle.Body,
                        maxLines = 1,
                    )
                    // 副标题优先显示驱动名，没有则退回驱动类型
                    val driver = device.driverName?.takeIf { it.isNotBlank() }
                        ?: device.driverType?.takeIf { it.isNotBlank() }
                    CcText(
                        text = listOfNotNull(driver, device.type.takeIf { it.isNotBlank() })
                            .joinToString(" · "),
                        style = CcTextStyle.Caption,
                        maxLines = 1,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (device.fanCount > 0) {
                        CcText(
                            text = stringResource(R.string.cc_device_fan_count, device.fanCount),
                            style = CcTextStyle.Caption,
                        )
                    }
                    if (device.sensorCount > 0) {
                        CcText(
                            text = stringResource(R.string.cc_device_sensor_count, device.sensorCount),
                            style = CcTextStyle.Caption,
                        )
                    }
                }
            }
        }
    }

    // 详情弹窗：驱动信息 + 通道清单（通道可单独改名）
    detailDevice?.let { device ->
        CcContentDialog(
            show = true,
            title = device.name,
            onDismiss = { detailDevice = null },
        ) {
            DeviceInfoRow(stringResource(R.string.cc_device_model), device.model)
            DeviceInfoRow(
                label = stringResource(R.string.cc_device_driver),
                value = listOfNotNull(
                    device.driverName?.takeIf { it.isNotBlank() },
                    device.driverVersion?.takeIf { it.isNotBlank() },
                ).joinToString(" ").takeIf { it.isNotBlank() } ?: device.driverType,
            )
            DeviceInfoRow(stringResource(R.string.cc_device_range), device.tempRange)
            DeviceInfoRow(
                label = stringResource(R.string.cc_rename_device_tag),
                value = device.type.takeIf { it.isNotBlank() },
            )

            if (device.driverLocations.isNotEmpty()) {
                CcText(
                    text = stringResource(R.string.cc_device_paths),
                    style = CcTextStyle.Caption,
                    modifier = Modifier.padding(top = 8.dp),
                )
                device.driverLocations.forEach { path ->
                    CcText(
                        text = path,
                        style = CcTextStyle.Caption,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            CcText(
                text = stringResource(R.string.cc_device_channels),
                style = CcTextStyle.Caption,
                modifier = Modifier.padding(top = 12.dp),
            )
            device.channels.forEach { channel ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CcText(
                        text = channel.label,
                        style = CcTextStyle.Body,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    CcText(
                        text = stringResource(
                            if (channel.isFan) R.string.cc_rename_kind_fan
                            else R.string.cc_rename_kind_sensor
                        ),
                        style = CcTextStyle.Caption,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    CcActionText(
                        text = stringResource(R.string.cc_rename_action),
                        onClick = {
                            callbacks.onRenameChannel(device.uid, channel.name, channel.label)
                        },
                    )
                }
            }
        }
    }
}

/** 详情弹窗里的一行：左标签 + 右值，值为空则整行不渲染 */
@Composable
private fun DeviceInfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        CcText(
            text = label,
            style = CcTextStyle.Caption,
            modifier = Modifier.weight(1f),
        )
        CcText(
            text = value,
            style = CcTextStyle.Body,
            maxLines = 2,
        )
    }
}

// ---------- 时间温度表 ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartSection(
    state: MonitorUiState,
    callbacks: MonitorCallbacks,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    CcCard(editMode = editMode, onRemove = onRemove) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CcText(
                text = stringResource(R.string.cc_section_chart),
                style = CcTextStyle.Title,
                modifier = Modifier.weight(1f),
            )
            CcActionText(
                text = stringResource(R.string.cc_edit),
                onClick = callbacks.onSelectChannels,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RANGE_OPTIONS.forEach { minutes ->
                RangeChip(
                    minutes = minutes,
                    selected = state.rangeMinutes == minutes,
                    onClick = { callbacks.onRangeChange(minutes) },
                )
            }
        }

        val visible = state.visibleSeries
        if (visible.any { it.points.size >= 2 }) {
            MultiLineChart(
                series = visible.map {
                    ChartSeries(
                        points = it.points,
                        color = CC_SERIES_COLORS[it.colorIndex % CC_SERIES_COLORS.size],
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(top = 12.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(top = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                CcText(
                    text = stringResource(R.string.cc_collecting),
                    style = CcTextStyle.Subtitle,
                )
            }
        }

        // 图例：显示通道名与当前值，点击隐藏/显示单个通道
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.series.forEach { series ->
                val visibleNow = series.key !in state.hiddenSeries
                Row(
                    modifier = Modifier.clickable { callbacks.onToggleSeries(series.key) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (visibleNow) {
                                    CC_SERIES_COLORS[series.colorIndex % CC_SERIES_COLORS.size]
                                } else {
                                    ccOnSurfaceColor().copy(alpha = 0.25f)
                                }
                            )
                    )
                    CcText(
                        text = "${series.channelLabel} ${CcFormat.temperature(series.current)}",
                        style = CcTextStyle.Caption,
                        color = CcTemperatureStyle.colorFor(
                            series.current,
                            series.deviceType,
                            series.deviceName,
                        ),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeChip(
    minutes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = ccPrimaryColor()
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) primary.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) primary else ccOnSurfaceColor().copy(alpha = 0.2f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        CcText(
            text = stringResource(rangeLabelRes(minutes)),
            style = CcTextStyle.Caption,
            color = if (selected) primary else null,
        )
    }
}

private fun rangeLabelRes(minutes: Int): Int = when (minutes) {
    1 -> R.string.cc_range_1
    15 -> R.string.cc_range_15
    60 -> R.string.cc_range_60
    else -> R.string.cc_range_5
}

private val RANGE_OPTIONS = listOf(1, 5, 15, 60)

// ---------- 报警规则 ----------

@Composable
private fun AlertsSection(
    state: MonitorUiState,
    callbacks: MonitorCallbacks,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    CcCard(editMode = editMode, onRemove = onRemove) {
        CcText(text = stringResource(R.string.cc_section_alert_rules), style = CcTextStyle.Title)

        state.alerts.forEach { alert ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        CcText(text = alert.name, style = CcTextStyle.Body)
                        CcText(
                            text = "${alert.min.roundToInt()} ~ ${alert.max.roundToInt()} °C",
                            style = CcTextStyle.Caption,
                        )
                        alert.silencedUntil?.let { until ->
                            CcText(
                                text = stringResource(
                                    R.string.cc_silenced_until,
                                    CcFormat.shortDateTime(until),
                                ),
                                style = CcTextStyle.Caption,
                            )
                        }
                    }
                    CcText(
                        text = alertStateLabel(alert),
                        style = CcTextStyle.Caption,
                        color = when {
                            alert.isError -> CcTemperatureStyle.Amber
                            alert.isActive -> CcTemperatureStyle.Red
                            else -> CcTemperatureStyle.Green
                        },
                    )
                    Switch(
                        checked = alert.enabled,
                        onCheckedChange = { callbacks.onToggleAlertEnabled(alert.uid, it) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CcActionText(
                        text = stringResource(R.string.cc_edit),
                        onClick = { callbacks.onEditAlert(alert) },
                    )
                    CcActionText(
                        text = stringResource(R.string.cc_alert_silence),
                        onClick = { callbacks.onSilenceAlert(alert) },
                    )
                }
            }
        }
    }
}

/** 报警状态汉化：报警中 / 已恢复 / 错误 / 预热中 / 冷却中 */
@Composable
fun alertStateLabel(alert: CcAlert): String = when (alert.state) {
    "Active" -> stringResource(R.string.cc_state_active)
    "Inactive" -> stringResource(R.string.cc_state_inactive)
    "Error" -> stringResource(R.string.cc_state_error)
    "WarmUp" -> stringResource(R.string.cc_state_warmup)
    "Cooldown" -> stringResource(R.string.cc_state_cooldown)
    else -> alert.state
}

// ---------- 最近报警记录 ----------

/** 默认只展示最近 N 条 */
private const val RECENT_LIMIT = 10

// 定长列用固定宽度贴紧内容，报警列吃掉剩余空间。
// 若四列都用 weight，定长内容（"11 天前"、事件名）左右会各留一截空白，
// 看起来就是「没对齐、又很空」。
private val RECENT_TIME_WIDTH = 78.dp
private val RECENT_EVENT_WIDTH = 52.dp
private val RECENT_VALUE_WIDTH = 44.dp

@Composable
private fun RecentSection(
    state: MonitorUiState,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    CcCard(editMode = editMode, onRemove = onRemove) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CcText(
                text = stringResource(R.string.cc_section_recent),
                style = CcTextStyle.Title,
                modifier = Modifier.weight(1f),
            )
            if (state.alertLogs.size > RECENT_LIMIT) {
                CcText(
                    text = stringResource(R.string.cc_recent_total, state.alertLogs.size),
                    style = CcTextStyle.Caption,
                )
            }
        }

        if (state.alertLogs.isEmpty()) {
            CcText(
                text = stringResource(R.string.cc_no_alert_logs),
                style = CcTextStyle.Subtitle,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@CcCard
        }

        // 表头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecentHeaderCell(
                text = stringResource(R.string.cc_col_time),
                modifier = Modifier.width(RECENT_TIME_WIDTH),
                align = TextAlign.Start,
            )
            RecentHeaderCell(
                text = stringResource(R.string.cc_col_alert),
                modifier = Modifier.weight(1f),
                align = TextAlign.Start,
            )
            RecentHeaderCell(
                text = stringResource(R.string.cc_col_event),
                modifier = Modifier.width(RECENT_EVENT_WIDTH),
            )
            RecentHeaderCell(
                text = stringResource(R.string.cc_col_value),
                modifier = Modifier.width(RECENT_VALUE_WIDTH),
            )
        }

        // 数据行：只取最近 RECENT_LIMIT 条，全部结构化字段，不展示 daemon 的英文原文
        state.alertLogs.take(RECENT_LIMIT).forEach { log ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RecentCell(
                    text = CcFormat.relativeTime(log.timestamp),
                    modifier = Modifier.width(RECENT_TIME_WIDTH),
                    align = TextAlign.Start,
                )
                // 报警列：设备名为主、规则名为副。
                // daemon 只给英文原文，设备名得从冒号前解析出来，
                // 否则这一行只有规则名（如「硬盘温度」），看不出是哪台设备报的
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                ) {
                    CcText(
                        text = log.deviceLabel() ?: stringResource(R.string.cc_alert_no_device),
                        style = CcTextStyle.Body,
                        maxLines = 1,
                    )
                    CcText(
                        text = log.name,
                        style = CcTextStyle.Caption,
                        maxLines = 1,
                    )
                }
                RecentCell(
                    text = alertEventLabel(log),
                    modifier = Modifier.width(RECENT_EVENT_WIDTH),
                    color = if (log.resolved || log.state == "Inactive") null else CcTemperatureStyle.Red,
                )
                RecentCell(
                    text = log.valueLabel(),
                    modifier = Modifier.width(RECENT_VALUE_WIDTH),
                )
            }
        }
    }
}

@Composable
private fun RowScope.RecentHeaderCell(
    text: String,
    modifier: Modifier,
    align: TextAlign = TextAlign.End,
) {
    CcText(
        text = text,
        style = CcTextStyle.Caption,
        modifier = modifier,
        textAlign = align,
    )
}

@Composable
private fun RowScope.RecentCell(
    text: String,
    modifier: Modifier,
    align: TextAlign = TextAlign.End,
    style: CcTextStyle = CcTextStyle.Caption,
    color: Color? = null,
) {
    CcText(
        text = text,
        style = style,
        modifier = modifier,
        textAlign = align,
        color = color,
        maxLines = 1,
    )
}

/**
 * 从 daemon 的英文 message 中抽出温度数值，仅用于表格的「数值」列。
 *
 * message 形如 `硬盘笼: 45 is again within allowed range: 0 - 45`，
 * 第一个数字即触发/恢复时的温度值。抽不到时用占位符补齐，
 * 否则该列会留空、整张表看起来缺一块。
 */
private val ALERT_VALUE_REGEX = Regex("""(-?\d+(?:\.\d+)?)""")

private fun CcAlertLog.valueLabel(): String =
    ALERT_VALUE_REGEX.find(message)?.groupValues?.get(1)?.let { "$it°" } ?: "—"

/**
 * 从 message 里解析出触发报警的设备 / 通道名。
 *
 * daemon 的 `AlertLog` **没有设备字段**（只有 `uid` / `name` / `state` / `message`），
 * 设备名只存在于英文原文里，形如：
 * - `硬盘笼: 46 is greater than allowed maximum: 45`
 * - `测试12: 47 is greater …; 测试8: 47 is greater …`（一次涉及多台设备）
 *
 * 所以取每段冒号前的部分，多台设备用顿号连接。注意 `allowed range: 0 - 45`
 * 这类片段前面是空格而非行首/分号，不会被误当成设备名。
 *
 * 旧记录里可能仍是内部名（`sensor1`）—— 那是 daemon 写入时就固定的，无法回溯。
 */
private val ALERT_DEVICE_REGEX = Regex("""(?:^|;\s*)([^:;]+):""")

private fun CcAlertLog.deviceLabel(): String? =
    ALERT_DEVICE_REGEX.findAll(message)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotBlank() }
        .toList()
        .takeIf { it.isNotEmpty() }
        ?.joinToString("、")

// ---------- 历史温度统计 ----------

@Composable
private fun StatsSection(
    state: MonitorUiState,
    editMode: Boolean,
    onRemove: () -> Unit,
) {
    CcCard(editMode = editMode, onRemove = onRemove) {
        CcText(text = stringResource(R.string.cc_section_stats), style = CcTextStyle.Title)

        // 表头：设备名（含传感器名）占主列，其余三列数值右对齐
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            CcText(
                text = "",
                style = CcTextStyle.Caption,
                modifier = Modifier.weight(NAME_WEIGHT),
            )
            StatHeaderCell(stringResource(R.string.cc_stats_min))
            StatHeaderCell(stringResource(R.string.cc_stats_avg))
            StatHeaderCell(stringResource(R.string.cc_stats_max))
        }

        state.stats.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(NAME_WEIGHT)) {
                    // 设备名为主标题、传感器名为副标题
                    CcText(text = row.deviceName, style = CcTextStyle.Body)
                    CcText(text = row.channelLabel, style = CcTextStyle.Caption)
                }
                StatValueCell(row.min, row)
                StatValueCell(row.avg, row)
                StatValueCell(row.max, row)
            }
        }
    }
}

@Composable
private fun RowScope.StatHeaderCell(text: String) {
    CcText(
        text = text,
        style = CcTextStyle.Caption,
        modifier = Modifier.weight(VALUE_WEIGHT),
        textAlign = TextAlign.End,
    )
}

/** 统计数值沿用温度配色，方便一眼看出哪一项超出最佳区间 */
@Composable
private fun RowScope.StatValueCell(value: Double?, row: MonitorStatRow) {
    CcText(
        text = CcFormat.temperature(value),
        style = CcTextStyle.Caption,
        color = CcTemperatureStyle.colorFor(value, row.deviceType, row.deviceName),
        modifier = Modifier.weight(VALUE_WEIGHT),
        textAlign = TextAlign.End,
    )
}

/** 名称列占绝对主列，温度三列收窄 */
private const val NAME_WEIGHT = 2.6f
private const val VALUE_WEIGHT = 0.8f
