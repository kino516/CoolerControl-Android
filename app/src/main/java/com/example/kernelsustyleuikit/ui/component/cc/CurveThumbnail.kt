package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.data.model.CcCurvePoint

/**
 * 风扇曲线缩略图：横轴温度、纵轴占空比。
 *
 * 官方曲线是「温度 -> 占空比」的控制点集（`speed_profile`），这里只做只读绘制，
 * 不提供控制点编辑（PRD 将其列为未实现项）。
 */
@Composable
fun CurveThumbnail(
    points: List<CcCurvePoint>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val minTemp = points.minOf { it.temp }
        val maxTemp = points.maxOf { it.temp }
        val tempRange = (maxTemp - minTemp).takeIf { it > 0.001 } ?: 1.0

        val stepX = size.width / (points.size - 1)

        val path = Path()
        points.forEachIndexed { index, point ->
            val x = index * stepX
            // 占空比 0..100 映射到画布高度（0% 在底部）
            val fraction = (point.duty.coerceIn(0, 100)) / 100f
            val y = size.height - fraction * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
