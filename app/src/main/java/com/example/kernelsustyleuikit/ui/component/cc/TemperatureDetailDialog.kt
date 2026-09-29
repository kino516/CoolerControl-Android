package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.model.CcChannelStats
import kotlin.math.roundToInt

/**
 * 温度详情对话框（PRD 3.6.5）：
 * 大号数值 + 近期曲线 + 累计统计（来自 `/stats`，是 daemon 自启动以来的累计值）。
 */
@Composable
fun TemperatureDetailDialog(
    show: Boolean,
    title: String,
    subtitle: String?,
    value: Double?,
    stats: CcChannelStats?,
    trend: List<Double>,
    trendColor: Color,
    onDismiss: () -> Unit,
    onViewInMonitor: () -> Unit,
) {
    CcContentDialog(
        show = show,
        title = title,
        onDismiss = onDismiss,
    ) {
        if (subtitle != null) {
            CcText(text = subtitle, style = CcTextStyle.Subtitle)
        }

        CcText(
            text = value?.let { "${it.roundToInt()} °C" } ?: "--",
            style = CcTextStyle.LargeValue,
            color = trendColor,
        )

        if (trend.size >= 2) {
            Sparkline(
                values = trend,
                color = trendColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(vertical = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            StatItem(stringResource(R.string.cc_stats_min), stats?.min)
            StatItem(stringResource(R.string.cc_stats_max), stats?.max)
            StatItem(stringResource(R.string.cc_stats_avg), stats?.avg)
        }

        CcActionText(
            text = stringResource(R.string.cc_view_in_monitor),
            onClick = onViewInMonitor,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun StatItem(label: String, value: Double?) {
    Column {
        CcText(text = label, style = CcTextStyle.Caption)
        CcText(
            text = value?.let { "${it.roundToInt()}°" } ?: "--",
            style = CcTextStyle.Body,
        )
    }
}
