package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.kernelsustyleuikit.ui.LocalUiMode
import com.example.kernelsustyleuikit.ui.UiMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 当前风格下的主色 */
@Composable
fun ccPrimaryColor(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.primary
    UiMode.Material -> MaterialTheme.colorScheme.primary
}

/** 当前风格下的前景色 */
@Composable
fun ccOnSurfaceColor(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.onSurface
    UiMode.Material -> MaterialTheme.colorScheme.onSurface
}

/** 当前风格下的次要文字色 */
@Composable
fun ccSecondaryTextColor(): Color = when (LocalUiMode.current) {
    UiMode.Miuix -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    UiMode.Material -> MaterialTheme.colorScheme.outline
}

/** 进度条轨道色（两种风格都安全：由前景色加透明度得到） */
@Composable
fun ccTrackColor(): Color = ccOnSurfaceColor().copy(alpha = 0.12f)
