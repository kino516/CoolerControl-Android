package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs

/**
 * 可拖动排序的卡片列表（编辑模式下长按拖动）。
 *
 * 实现要点（这三条决定了「跟不跟手」）：
 *
 * 1. **拖动过程中绝不改动列表顺序** —— 只累加 [dragOffset]。真正的重排等到 `onDragEnd`
 *    才提交一次。早期版本在拖动中实时交换列表项，每交换一次卡片就跳一次位，手感必然断裂。
 * 2. **让位靠被动项的位移动画** —— 先由 [targetIndex] 按各项**实测高度**累加算出落点索引，
 *    再把被跨过的项反向平移一个「被拖动项高度 + 间距」，用 [animateFloatAsState] 平滑过渡。
 * 3. **容器用 Column 而非 LazyColumn** —— 卡片数量有限，非懒加载才能稳定拿到每项高度，
 *    也避免复用时高度丢失导致落点判定错乱。
 *
 * 手势只在 [editMode] 为真时挂载，且只消费垂直分量，水平滑动留给父级 Pager。
 */
@Composable
fun <T> CcDraggableCardList(
    items: List<T>,
    itemKey: (T) -> String,
    editMode: Boolean,
    onOrderChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    bottomPadding: Dp = 0.dp,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    itemContent: @Composable (item: T, editMode: Boolean) -> Unit,
) {
    val heights = remember { mutableStateMapOf<Int, Int>() }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val spacingPx = with(LocalDensity.current) { CARD_SPACING.toPx() }

    val currentItems by rememberUpdatedState(items)
    val currentItemKey by rememberUpdatedState(itemKey)
    val currentOnOrderChange by rememberUpdatedState(onOrderChange)

    // 手指落点对应的目标位；未拖动时为 -1
    val targetIdx = if (draggingIndex >= 0) {
        targetIndex(draggingIndex, dragOffset, heights, items.size, spacingPx)
    } else {
        -1
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(CARD_SPACING),
    ) {
        header?.let { headerContent ->
            Box(modifier = Modifier.fillMaxWidth()) { headerContent() }
        }

        items.forEachIndexed { index, item ->
            val isDragging = index == draggingIndex

            // 让位位移：被拖动项跨过的那些项反方向平移，腾出空间
            val shift = if (!isDragging && draggingIndex >= 0 && targetIdx >= 0) {
                val draggedSpan = (heights[draggingIndex] ?: 0).toFloat() + spacingPx
                when {
                    draggingIndex < targetIdx && index in (draggingIndex + 1)..targetIdx -> -draggedSpan
                    draggingIndex > targetIdx && index in targetIdx until draggingIndex -> draggedSpan
                    else -> 0f
                }
            } else {
                0f
            }
            val animatedShift by animateFloatAsState(
                targetValue = shift,
                animationSpec = tween(durationMillis = 220),
                label = "cardShift",
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffset else animatedShift
                        if (isDragging) {
                            scaleX = 1.02f
                            scaleY = 1.02f
                            shadowElevation = 16f
                        }
                    }
                    .onGloballyPositioned { heights[index] = it.size.height }
                    .then(
                        if (!editMode) {
                            Modifier
                        } else {
                            // key 用整个 id 序列：重排后必须重建手势，否则闭包里捕获的
                            // index 会是旧值（拖动已经结束，重建不会打断手势）
                            Modifier.pointerInput(items.map(itemKey)) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingIndex = index
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        // 只消费垂直分量：水平滑动要留给父级 Pager，
                                        // 否则编辑模式下就无法左右切页了
                                        if (abs(amount.y) >= abs(amount.x)) {
                                            change.consume()
                                            dragOffset += amount.y
                                        }
                                    },
                                    onDragEnd = {
                                        // 到这里才真正提交一次顺序变更
                                        val target = targetIndex(
                                            draggingIndex, dragOffset, heights, currentItems.size, spacingPx,
                                        )
                                        val from = draggingIndex
                                        if (from >= 0 && target != from && target in currentItems.indices) {
                                            val reordered = currentItems.map(currentItemKey).toMutableList()
                                            reordered.add(target, reordered.removeAt(from))
                                            currentOnOrderChange(reordered)
                                        }
                                        draggingIndex = -1
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingIndex = -1
                                        dragOffset = 0f
                                    },
                                )
                            }
                        }
                    )
            ) {
                // 必须是 Column：卡片内部常为「标题 + 内容」多段，用 Box 会相互堆叠
                Column(modifier = Modifier.fillMaxWidth()) {
                    itemContent(item, editMode)
                }
            }
        }

        footer?.let { footerContent ->
            Box(modifier = Modifier.fillMaxWidth()) { footerContent() }
        }

        if (bottomPadding > 0.dp) {
            Spacer(Modifier.height(bottomPadding))
        }
    }
}

/**
 * 根据拖动位移与各项实测高度累加，算出松手后应落入的索引。
 *
 * 累加时把项间距一并计入，否则跨过多个项时落点会偏前。
 */
private fun targetIndex(
    from: Int,
    offset: Float,
    heights: Map<Int, Int>,
    count: Int,
    spacingPx: Float,
): Int {
    if (offset > 0f) {
        var acc = 0f
        for (i in from + 1 until count) {
            acc += (heights[i] ?: 0).toFloat() + spacingPx
            if (offset < acc) return i
        }
        return count - 1
    }
    if (offset < 0f) {
        var acc = 0f
        for (i in from - 1 downTo 0) {
            acc += (heights[i] ?: 0).toFloat() + spacingPx
            if (-offset < acc) return i
        }
        return 0
    }
    return from
}

private val CARD_SPACING = 12.dp
