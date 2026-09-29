package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kernelsustyleuikit.ui.LocalUiMode
import com.example.kernelsustyleuikit.ui.UiMode

/**
 * 跨风格滑块。
 *
 * 拖动只改「待应用值」，是否下发由调用方决定 —— 这是 PRD 的硬规则：
 * 滑块不自动生效，必须点「应用手动」。
 */
@Composable
fun CcSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..100f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> top.yukonga.miuix.kmp.basic.Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )

        UiMode.Material -> androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
    }
}
