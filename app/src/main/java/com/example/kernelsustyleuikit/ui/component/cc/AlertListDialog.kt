package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.model.CcAlert
import com.example.kernelsustyleuikit.ui.screen.monitor.alertStateLabel
import com.example.kernelsustyleuikit.ui.util.CcTemperatureStyle
import kotlin.math.roundToInt

/**
 * 主页报警卡点击后的对话框：列出全部报警（名称 / 阈值 / 状态），
 * 并可跳到监控页做进一步处理（编辑阈值、静默）。
 */
@Composable
fun AlertListDialog(
    show: Boolean,
    alerts: List<CcAlert>,
    onDismiss: () -> Unit,
    onViewAll: () -> Unit,
) {
    CcContentDialog(
        show = show,
        title = stringResource(R.string.cc_card_alerts),
        onDismiss = onDismiss,
    ) {
        if (alerts.isEmpty()) {
            CcText(
                text = stringResource(R.string.cc_no_alert_logs),
                style = CcTextStyle.Subtitle,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                alerts.forEach { alert ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CcText(
                                text = alert.name,
                                style = CcTextStyle.Body,
                                modifier = Modifier.weight(1f),
                            )
                            CcText(
                                text = alertStateLabel(alert),
                                style = CcTextStyle.Caption,
                                color = when {
                                    alert.isError -> CcTemperatureStyle.Amber
                                    alert.isActive -> CcTemperatureStyle.Red
                                    else -> CcTemperatureStyle.Green
                                },
                            )
                        }
                        CcText(
                            text = "${alert.min.roundToInt()} ~ ${alert.max.roundToInt()} °C",
                            style = CcTextStyle.Caption,
                        )
                    }
                }
            }
        }

        CcActionText(
            text = stringResource(R.string.cc_view_in_monitor),
            onClick = onViewAll,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
