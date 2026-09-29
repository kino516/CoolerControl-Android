package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.model.CcProfile
import com.example.kernelsustyleuikit.data.model.CcSpeedOptions
import kotlin.math.roundToInt

/**
 * 风扇控制对话框（主页风扇卡与冷却页共用）。
 *
 * 关键规则（PRD FR-2.4）：
 * - 拖动滑块**只改变待应用值**，不会立即下发
 * - 必须点「应用手动」才真正生效
 * - 提供「选择曲线」与「恢复曲线控制」
 * - 不支持手动调速的通道，滑块置灰并说明原因
 */
@Composable
fun FanControlDialog(
    show: Boolean,
    title: String,
    subtitle: String?,
    rpm: Int?,
    duty: Double?,
    speedOptions: CcSpeedOptions?,
    currentProfileName: String?,
    profiles: List<CcProfile>,
    onDismiss: () -> Unit,
    onApplyManual: (Int) -> Unit,
    onSelectProfile: (String) -> Unit,
    onRestoreCurve: () -> Unit,
) {
    val minDuty = (speedOptions?.minDuty ?: 0).toFloat()
    val maxDuty = (speedOptions?.maxDuty ?: 100).toFloat()
    val adjustable = speedOptions != null && speedOptions.fixedEnabled

    var pending by remember { mutableFloatStateOf(50f) }
    var showCurvePicker by remember { mutableStateOf(false) }

    // 每次打开时把待应用值对齐到当前占空比（拖动期间不被 SSE 更新打断）
    LaunchedEffect(show) {
        if (show) {
            pending = ((duty ?: minDuty.toDouble()).toFloat()).coerceIn(minDuty, maxDuty)
        }
    }

    CcContentDialog(
        show = show,
        title = title,
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.cc_apply_manual),
        confirmEnabled = adjustable,
        onConfirm = { onApplyManual(pending.roundToInt()) },
    ) {
        if (subtitle != null) {
            CcText(text = subtitle, style = CcTextStyle.Subtitle)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            rpm?.let {
                CcText(
                    text = stringResource(R.string.cc_current_rpm, it),
                    style = CcTextStyle.Body,
                )
            }
            duty?.let {
                CcText(
                    text = stringResource(R.string.cc_current_duty, it.roundToInt()),
                    style = CcTextStyle.Body,
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            CcText(
                text = stringResource(R.string.cc_manual_value, pending.roundToInt()),
                style = CcTextStyle.Title,
            )
            CcSlider(
                value = pending,
                onValueChange = { pending = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                enabled = adjustable,
                valueRange = minDuty..maxDuty,
            )
        }

        if (!adjustable) {
            CcText(
                text = stringResource(
                    if (speedOptions == null) R.string.cc_not_adjustable else R.string.cc_fixed_disabled
                ),
                style = CcTextStyle.Caption,
            )
        } else {
            CcText(
                text = stringResource(R.string.cc_manual_warning),
                style = CcTextStyle.Caption,
            )
        }

        // 控制配置：显示当前绑定的曲线名，点击可换曲线
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CcText(text = stringResource(R.string.cc_control_config), style = CcTextStyle.Caption)
                CcText(
                    text = currentProfileName ?: stringResource(R.string.cc_curve_none),
                    style = CcTextStyle.Body,
                )
            }
            CcActionText(
                text = stringResource(R.string.cc_select_curve),
                onClick = { showCurvePicker = true },
            )
        }

        CcActionText(
            text = stringResource(R.string.cc_restore_curve),
            onClick = onRestoreCurve,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    CurveSelectDialog(
        show = showCurvePicker,
        profiles = profiles,
        onDismiss = { showCurvePicker = false },
        onSelect = { uid ->
            showCurvePicker = false
            onSelectProfile(uid)
        },
    )
}
