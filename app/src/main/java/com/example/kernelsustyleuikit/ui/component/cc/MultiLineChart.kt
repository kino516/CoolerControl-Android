package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.data.repository.CcSample
import com.example.kernelsustyleuikit.ui.util.CcFormat

/** 多通道曲线图的调色板 */
val CC_SERIES_COLORS = listOf(
    Color(0xFF2196F3),
    Color(0xFF36D167),
    Color(0xFFFFC107),
    Color(0xFFF72727),
    Color(0xFF9C27B0),
    Color(0xFF00BCD4),
    Color(0xFFFF9800),
    Color(0xFF795548),
)

/** 一条待绘制的曲线 */
data class ChartSeries(
    val points: List<CcSample>,
    val color: Color,
)

/** 单条曲线最多保留的采样点数：等间隔抽样，屏幕上分辨不出差异 */
private const val MAX_POINTS_PER_SERIES = 240

/**
 * 多通道实时曲线（自绘，不引入图表库）。
 *
 * 横轴为时间、纵轴为温度；所有曲线共用同一坐标系，按屏幕宽度直接连线
 * （采样密度约 1 点/秒，窗口最长 60 分钟）。
 *
 * 几何计算全部放在 [remember] 里，**不写在 Canvas 的绘制块中** ——
 * 绘制块每帧都会执行（滚动、切页、任何动画都会重绘），把换算与建 Path
 * 放进去会导致 60 分钟窗口下每帧重复解析上万个采样点。
 */
@Composable
fun MultiLineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
) {
    // 线宽跟随网页端的 chartLineScale（1 档 ≈ 0.6dp）。
    // 必须在 Composable 作用域读取，否则值变化不会触发重组。
    val lineScale = CcFormat.chartLineScale.coerceIn(1, 5)
    val strokeWidthPx = with(LocalDensity.current) { (lineScale * 0.6f).dp.toPx() }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // 只有「曲线数据变化」或「画布尺寸变化」才重算 Path；
    // 线宽变化只影响 Stroke，不需要重建几何
    val paths = remember(series, canvasSize) {
        buildChartPaths(series, canvasSize.width.toFloat(), canvasSize.height.toFloat())
    }

    Canvas(
        modifier = modifier.onSizeChanged {
            if (it != canvasSize) canvasSize = it
        },
    ) {
        paths.forEach { (path, color) ->
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

/**
 * 把各条曲线换算成画布坐标并构建 Path。
 *
 * 只在数据或尺寸变化时调用一次（见 [MultiLineChart] 的 remember）。
 */
private fun buildChartPaths(
    series: List<ChartSeries>,
    width: Float,
    height: Float,
): List<Pair<Path, Color>> {
    if (width <= 0f || height <= 0f) return emptyList()

    val allPoints = series.flatMap { it.points }
    if (allPoints.size < 2) return emptyList()

    // Y 轴上下各留 12% 余量。若让纵轴紧贴数据范围，1–2℃ 的正常波动
    // 会被拉伸成满屏的陡坡，看起来像剧烈震荡，其实并没有。
    val rawMin = allPoints.minOf { it.value }
    val rawMax = allPoints.maxOf { it.value }
    val rawRange = (rawMax - rawMin).takeIf { it > 0.5 } ?: 1.0
    val margin = rawRange * 0.12
    val minValue = rawMin - margin
    val valueRange = rawRange + margin * 2

    val minTime = allPoints.minOf { it.timestampMs }
    val maxTime = allPoints.maxOf { it.timestampMs }
    val timeRange = (maxTime - minTime).takeIf { it > 0L } ?: 1L

    return series.mapNotNull { chartSeries ->
        val samples = downsample(chartSeries.points)
        if (samples.size < 2) return@mapNotNull null

        // 先把采样点换算成画布坐标
        val points = samples.map { sample ->
            Offset(
                x = ((sample.timestampMs - minTime).toFloat() / timeRange) * width,
                y = height - (((sample.value - minValue) / valueRange).toFloat() * height),
            )
        }

        // Catmull-Rom 转三次贝塞尔：用前后相邻点估算切线作为控制点，
        // 把折线变平滑，避免采样点稀疏时出现生硬的尖角
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 0 until points.size - 1) {
            val p0 = points[if (i > 0) i - 1 else i]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[if (i + 2 < points.size) i + 2 else i + 1]

            path.cubicTo(
                p1.x + (p2.x - p0.x) / 6f,
                p1.y + (p2.y - p0.y) / 6f,
                p2.x - (p3.x - p1.x) / 6f,
                p2.y - (p3.y - p1.y) / 6f,
                p2.x,
                p2.y,
            )
        }

        path to chartSeries.color
    }
}

/**
 * 等间隔抽样到 [MAX_POINTS_PER_SERIES] 以内。
 *
 * 末点必须保留 —— 曲线的「当前值」在最右端，丢掉会让图看起来停在过去。
 */
private fun downsample(samples: List<CcSample>): List<CcSample> {
    if (samples.size <= MAX_POINTS_PER_SERIES) return samples

    val step = samples.size / MAX_POINTS_PER_SERIES
    val result = ArrayList<CcSample>(MAX_POINTS_PER_SERIES + 1)
    var i = 0
    while (i < samples.size) {
        result.add(samples[i])
        i += step
    }
    if (result.last() !== samples.last()) result.add(samples.last())
    return result
}
