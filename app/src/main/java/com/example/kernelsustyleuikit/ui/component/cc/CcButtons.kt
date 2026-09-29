package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 胶囊按钮：冷却页的动作入口统一用它。
 *
 * - `filled = true` 为实心主色按钮（如「应用手动」）
 * - `filled = false` 为描边按钮（如显示当前曲线名的「控制配置」）
 * - `large = true` 放大内边距，用于「开始测试」这类主操作
 *
 * 内容始终居中，因此配合 `Modifier.fillMaxWidth()` 可做整行大按钮。
 */
@Composable
fun CcPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tone: Color? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    large: Boolean = false,
) {
    val accent = tone ?: ccPrimaryColor()
    val shape = RoundedCornerShape(50)
    val contentColor = when {
        !enabled -> ccOnSurfaceColor().copy(alpha = 0.4f)
        filled -> Color.White
        else -> accent
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    !enabled -> ccOnSurfaceColor().copy(alpha = 0.06f)
                    filled -> accent
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (filled || !enabled) 0.dp else 1.dp,
                color = if (filled || !enabled) Color.Transparent else accent.copy(alpha = 0.55f),
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (large) 24.dp else 16.dp,
                vertical = if (large) 13.dp else 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(if (large) 18.dp else 16.dp),
            )
        }
        CcText(
            text = text,
            style = if (large) CcTextStyle.Title else CcTextStyle.Body,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/**
 * 分段控件的一个选项。
 *
 * 用字符串 id 而不是泛型，是因为调用方（散热模式、压力测试类型）本来就是
 * uid / 固定字符串，省掉一层泛型包装。
 */
data class CcSegment(val id: String, val label: String)

/**
 * 分段控件：互斥单选且选项不多时，比分立芯片整洁得多。
 *
 * 整条共用一个圆角容器与底色，选中段用主色实心填充 —— 相比每个选项各自
 * 带边框、勾选图标和徽标，视觉噪音小很多，也更像「一组里选一个」。
 *
 * @param fillEqually 等分整行宽度。选项不多（≤4）时用它，
 *   每个选项都能占满可用空间，比固定宽度的小按钮好点得多
 * @param minSegmentWidth 每段最小宽度；总宽超出时由调用方套 `horizontalScroll`
 */
@Composable
fun CcSegmentedControl(
    options: List<CcSegment>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    fillEqually: Boolean = false,
    minSegmentWidth: Dp = 0.dp,
) {
    if (options.isEmpty()) return

    val primary = ccPrimaryColor()
    val onSurface = ccOnSurfaceColor()
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .clip(shape)
            .background(onSurface.copy(alpha = 0.06f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val selected = option.id == selectedId
            Box(
                modifier = Modifier
                    .then(
                        when {
                            fillEqually -> Modifier.weight(1f)
                            minSegmentWidth > 0.dp -> Modifier.widthIn(min = minSegmentWidth)
                            else -> Modifier
                        }
                    )
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) primary else Color.Transparent)
                    .clickable { onSelect(option.id) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                CcText(
                    text = option.label,
                    style = CcTextStyle.Body,
                    color = if (selected) Color.White else onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}
@Composable
fun CcChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    leadingIcon: ImageVector? = null,
) {
    val primary = ccPrimaryColor()
    val onSurface = ccOnSurfaceColor()
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) primary.copy(alpha = 0.12f) else onSurface.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = if (selected) primary else onSurface.copy(alpha = 0.15f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            selected -> Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(16.dp),
            )

            leadingIcon != null -> Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
        CcText(
            text = text,
            style = CcTextStyle.Body,
            color = if (selected) primary else onSurface,
            maxLines = 1,
        )
        badge?.let {
            CcText(
                text = it,
                style = CcTextStyle.Caption,
                color = primary,
            )
        }
    }
}
