package com.example.kernelsustyleuikit.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.model.CcAlertLog
import com.example.kernelsustyleuikit.ui.component.cc.CcCard
import com.example.kernelsustyleuikit.ui.component.cc.CcCardHeader
import com.example.kernelsustyleuikit.ui.component.cc.CcSegmentedControl
import com.example.kernelsustyleuikit.ui.component.cc.CcText
import com.example.kernelsustyleuikit.ui.component.cc.CcTextStyle
import com.example.kernelsustyleuikit.ui.component.cc.DutyBar
import com.example.kernelsustyleuikit.ui.component.cc.Sparkline
import com.example.kernelsustyleuikit.ui.component.cc.ccPrimaryColor
import com.example.kernelsustyleuikit.ui.component.cc.ccTrackColor
import com.example.kernelsustyleuikit.ui.util.CcFormat
import com.example.kernelsustyleuikit.ui.util.CcTemperatureStyle

/** 主页卡片分发：五种卡片类型共用同一套容器与文字体系 */
@Composable
fun HomeCardItem(
    card: HomeCardUi,
    deviceType: String,
    editMode: Boolean,
    actions: HomeActions,
    onRemove: () -> Unit,
) {
    when (card) {
        is HomeCardUi.Temperature -> TemperatureCard(card, deviceType, editMode, actions, onRemove)
        is HomeCardUi.Fan -> FanCard(card, editMode, actions, onRemove)
        is HomeCardUi.AlertSummary -> AlertSummaryCard(card, editMode, actions, onRemove)
        is HomeCardUi.ModeSummary -> ModeSummaryCard(card, editMode, actions, onRemove)
        is HomeCardUi.Overview -> OverviewCard(card, editMode, actions, onRemove)
    }
}

@Composable
private fun TemperatureCard(
    card: HomeCardUi.Temperature,
    deviceType: String,
    editMode: Boolean,
    actions: HomeActions,
    onRemove: () -> Unit,
) {
    val color = CcTemperatureStyle.colorFor(card.value, deviceType, card.subtitle.orEmpty())
    CcCard(
        onClick = { actions.onTemperatureClick(card) },
        editMode = editMode,
        onRemove = onRemove,
    ) {
        CcCardHeader(
            title = card.title,
            subtitle = card.subtitle,
            value = CcFormat.temperature(card.value),
            valueColor = color,
        )
        if (card.trend.size >= 2) {
            Sparkline(
                values = card.trend,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun FanCard(
    card: HomeCardUi.Fan,
    editMode: Boolean,
    actions: HomeActions,
    onRemove: () -> Unit,
) {
    CcCard(
        onClick = { actions.onFanClick(card) },
        editMode = editMode,
        onRemove = onRemove,
    ) {
        CcCardHeader(
            title = card.title,
            subtitle = card.subtitle,
            value = card.rpm?.let { "$it RPM" } ?: "--",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DutyBar(
                duty = card.duty,
                trackColor = ccTrackColor(),
                fillColor = ccPrimaryColor(),
                modifier = Modifier.weight(1f),
            )
            CcText(
                text = CcFormat.duty(card.duty),
                style = CcTextStyle.Caption,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun AlertSummaryCard(
    card: HomeCardUi.AlertSummary,
    editMode: Boolean,
    actions: HomeActions,
    onRemove: () -> Unit,
) {
    val allNormal = card.active == 0
    CcCard(
        // 整卡点击仍进报警列表；单条记录的详情走行内独立的「详情」入口
        onClick = actions.onAlertClick,
        editMode = editMode,
        onRemove = onRemove,
    ) {
        CcCardHeader(
            title = card.title,
            subtitle = if (allNormal) {
                stringResource(R.string.cc_all_normal)
            } else {
                stringResource(R.string.cc_alerts_active, card.active)
            },
        )
        card.recent.forEach { log ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CcText(
                    text = log.name,
                    style = CcTextStyle.Body,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                CcText(
                    text = alertEventLabel(log),
                    style = CcTextStyle.Caption,
                    color = if (log.resolved || log.state == "Inactive") {
                        null
                    } else {
                        CcTemperatureStyle.Red
                    },
                )
                CcText(
                    text = CcFormat.relativeTime(log.timestamp),
                    style = CcTextStyle.Caption,
                )
            }
        }
    }
}

@Composable
private fun ModeSummaryCard(
    card: HomeCardUi.ModeSummary,
    editMode: Boolean,
    actions: HomeActions,
    onRemove: () -> Unit,
) {
    // 与冷却页保持一致：直接用分段控件切换，而不是只显示当前模式、再弹对话框选
    CcCard(
        editMode = editMode,
        onRemove = onRemove,
    ) {
        CcCardHeader(
            title = card.title,
            subtitle = stringResource(R.string.cc_modes_count, card.modeCount),
        )

        if (card.modes.isEmpty()) {
            // 没有可切换的模式时退回纯展示，避免出现一个空控件
            CcText(
                text = card.currentMode ?: stringResource(R.string.cc_unknown),
                style = CcTextStyle.Value,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 1,
            )
            return@CcCard
        }

        CcSegmentedControl(
            options = card.modes,
            selectedId = card.currentModeUid,
            onSelect = actions.onSwitchMode,
            fillEqually = true,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun OverviewCard(
    card: HomeCardUi.Overview,
    editMode: Boolean,
    actions: HomeActions,
    onRemove: () -> Unit,
) {
    CcCard(
        onClick = actions.onOverviewClick,
        editMode = editMode,
        onRemove = onRemove,
    ) {
        CcCardHeader(title = card.title, subtitle = null)

        // 端点直接作为主体大字：这是「网络状态」最该一眼看到的信息，
        // 原先拆成几行标签-值反而把重点淹没了
        CcText(
            text = card.endpointLabel ?: stringResource(R.string.cc_not_connected),
            style = CcTextStyle.Value,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 2,
        )

        // 其余信息压成一行小字：连接状态 · 实时通道 · 设备数 · daemon 版本
        val facts = mutableListOf<String>()
        facts += stringResource(
            if (card.connected) R.string.cc_connected else R.string.cc_disconnected
        )
        facts += stringResource(
            if (card.sseConnected) R.string.cc_sse_live else R.string.cc_sse_offline
        )
        facts += stringResource(R.string.cc_devices_count, card.deviceCount)
        card.daemonVersion?.let { facts += "daemon $it" }

        CcText(
            text = facts.joinToString(" · "),
            style = CcTextStyle.Caption,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 报警事件类型汉化（触发 / 恢复 / 持续报警 / 错误） */
@Composable
fun alertEventLabel(log: CcAlertLog): String = when (log.kind) {
    "triggered" -> stringResource(R.string.cc_event_triggered)
    "stillActive" -> stringResource(R.string.cc_event_still_active)
    "resolved" -> stringResource(R.string.cc_event_resolved)
    "error" -> stringResource(R.string.cc_event_error)
    "errorResolved" -> stringResource(R.string.cc_event_error_resolved)
    else -> when (log.state) {
        "Active" -> stringResource(R.string.cc_state_active)
        "Inactive" -> stringResource(R.string.cc_state_inactive)
        "Error" -> stringResource(R.string.cc_state_error)
        else -> stringResource(R.string.cc_event_unknown)
    }
}
