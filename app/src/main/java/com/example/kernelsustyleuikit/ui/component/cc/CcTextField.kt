package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * 自封装的文本输入框。
 *
 * **刻意不使用 UI Kit 移植过来的 `EditText`** —— 它靠 `FocusInteraction` 转发焦点，
 * 真机上点击唤不起输入法（实测致命缺陷）。这里用标准 `BasicTextField`，
 * 两种界面风格共用同一实现。
 *
 * @param prefix 输入框内左侧的固定前缀（例如端口的 `:`）
 * @param fillMaxWidth 置于 `Row` 中时传 false，由调用方决定宽度
 */
@Composable
fun CcTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefix: String? = null,
    singleLine: Boolean = true,
    fillMaxWidth: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val shape = RoundedCornerShape(12.dp)
    val textColor = ccOnSurfaceColor()

    Box(
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(shape)
            .background(ccOnSurfaceColor().copy(alpha = 0.06f))
            .border(1.dp, ccOnSurfaceColor().copy(alpha = 0.15f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            textStyle = LocalTextStyle.current.copy(color = textColor),
            cursorBrush = SolidColor(ccPrimaryColor()),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (prefix != null) {
                        CcText(
                            text = prefix,
                            style = CcTextStyle.Body,
                            color = ccSecondaryTextColor(),
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty() && placeholder != null) {
                            CcText(text = placeholder, style = CcTextStyle.Subtitle)
                        }
                        innerTextField()
                    }
                }
            },
        )
    }
}
