package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import com.example.kernelsustyleuikit.data.local.CcIconOption

/**
 * 桌面图标预览。
 *
 * adaptive icon 是 XML 描述，不能用 `painterResource` 直接渲染，
 * 所以这里用「背景色 + 前景图」手工拼 —— 与 launcher 的实际构成一致，
 * 用户在对话框里选的就是最终看到的样子。
 */
@Composable
fun CcIconPreview(
    option: CcIconOption,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(option.background),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            // 略小于背景：adaptive icon 的前景本身带安全边距，
            // 这里按相近比例缩放，预览才不会显得图标顶满
            modifier = Modifier.size(size * 0.66f),
        )
    }
}

/**
 * 图标选择网格。
 *
 * **只显示图标、不显示名称** —— 图标本身就是最直观的标签，配上文字反而
 * 要在「看颜色」和「读名字」之间来回切换。当前选中项用主色描边 + 浅底高亮。
 */
@Composable
fun CcIconPickerGrid(
    options: List<CcIconOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    iconSize: Dp = 48.dp,
) {
    val primary = ccPrimaryColor()
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        options.chunked(columns).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowOptions.forEach { option ->
                    val selected = option.id == selectedId
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(
                                if (selected) primary.copy(alpha = 0.12f) else Color.Transparent
                            )
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) primary else Color.Transparent,
                                shape = shape,
                            )
                            .clickable { onSelect(option.id) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CcIconPreview(option, size = iconSize)
                    }
                }
                // 补齐这一行的空位，否则最后一行的图标会被 weight 拉宽
                repeat(columns - rowOptions.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
