package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.kernelsustyleuikit.ui.LocalUiMode
import com.example.kernelsustyleuikit.ui.UiMode
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 跨风格文本层级 */
enum class CcTextStyle {
    /** 卡片标题 */
    Title,

    /** 副标题 / 说明 */
    Subtitle,

    /** 正文 */
    Body,

    /** 次要说明 */
    Caption,

    /** 主数值（右上角） */
    Value,

    /** 大号数值（对话框主数值） */
    LargeValue,
}

/**
 * 跨风格文本：Miuix 与 Material 各取自己的排版体系，调用方只关心层级。
 */
@Composable
fun CcText(
    text: String,
    modifier: Modifier = Modifier,
    style: CcTextStyle = CcTextStyle.Body,
    color: Color? = null,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> {
            val scheme = MiuixTheme.colorScheme
            val fontSize: TextUnit
            val fontWeight: FontWeight
            val defaultColor: Color
            when (style) {
                CcTextStyle.Title -> {
                    fontSize = 16.sp; fontWeight = FontWeight.SemiBold; defaultColor = scheme.onSurface
                }

                CcTextStyle.Subtitle -> {
                    fontSize = 14.sp; fontWeight = FontWeight.Medium; defaultColor = scheme.onSurfaceVariantSummary
                }

                CcTextStyle.Body -> {
                    fontSize = 14.sp; fontWeight = FontWeight.Normal; defaultColor = scheme.onSurface
                }

                CcTextStyle.Caption -> {
                    fontSize = 12.sp; fontWeight = FontWeight.Normal; defaultColor = scheme.onSurfaceVariantSummary
                }

                CcTextStyle.Value -> {
                    fontSize = 18.sp; fontWeight = FontWeight.SemiBold; defaultColor = scheme.onSurface
                }

                CcTextStyle.LargeValue -> {
                    fontSize = 34.sp; fontWeight = FontWeight.SemiBold; defaultColor = scheme.onSurface
                }
            }
            MiuixText(
                text = text,
                modifier = modifier,
                color = color ?: defaultColor,
                fontSize = fontSize,
                fontWeight = fontWeight,
                maxLines = maxLines,
                textAlign = textAlign ?: TextAlign.Unspecified,
            )
        }

        UiMode.Material -> {
            val typography = MaterialTheme.typography
            val textStyle = when (style) {
                CcTextStyle.Title -> typography.titleMedium
                CcTextStyle.Subtitle -> typography.bodySmall
                CcTextStyle.Body -> typography.bodyMedium
                CcTextStyle.Caption -> typography.labelSmall
                CcTextStyle.Value -> typography.titleLarge
                CcTextStyle.LargeValue -> typography.displaySmall
            }
            Text(
                text = text,
                modifier = modifier,
                style = textStyle,
                color = color ?: MaterialTheme.colorScheme.onSurface,
                maxLines = maxLines,
                textAlign = textAlign ?: TextAlign.Unspecified,
            )
        }
    }
}
