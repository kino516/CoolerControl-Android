package com.example.kernelsustyleuikit.ui.component.cc

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.kernelsustyleuikit.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 加载指示：一个原地跑动的小人 + 「正在加载中」。
 *
 * 纯 Canvas 绘制，不引入任何动画库 —— 火柴人的四肢角度由同一个相位驱动：
 * 两腿反相摆动、两臂与对侧腿同相，再加上每步一次的上下起伏，
 * 看起来就是自然的跑姿，而不是机械地左右晃。
 */
@Composable
fun CcRunningMan(
    modifier: Modifier = Modifier,
    color: Color = ccPrimaryColor(),
) {
    val transition = rememberInfiniteTransition(label = "running-man")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(modifier = Modifier.size(width = 58.dp, height = 58.dp)) {
            val w = size.width
            val h = size.height
            val stroke = (w * 0.05f).coerceAtLeast(1.5f)
            // 摆动量：-1..1
            val swing = sin(phase)
            // 每步一次的上下起伏，让跑动有重量感
            val bob = cos(phase * 2f) * h * 0.022f

            val hip = Offset(w * 0.5f, h * 0.62f + bob)
            val shoulder = Offset(w * 0.5f, h * 0.38f + bob)
            val headCenter = Offset(w * 0.5f, h * 0.23f + bob)
            val headRadius = w * 0.085f

            // 头
            drawCircle(
                color = color,
                radius = headRadius,
                center = headCenter,
                style = Stroke(width = stroke),
            )

            // 躯干
            drawLine(color, shoulder, hip, strokeWidth = stroke, cap = StrokeCap.Round)

            // 双臂：与对侧腿同相
            val armLen = h * 0.24f
            drawLine(
                color = color,
                start = shoulder,
                end = Offset(
                    shoulder.x + armLen * 0.8f * swing,
                    shoulder.y + armLen * 0.8f,
                ),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = shoulder,
                end = Offset(
                    shoulder.x - armLen * 0.8f * swing,
                    shoulder.y + armLen * 0.8f,
                ),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )

            // 双腿：彼此反相
            val legLen = h * 0.28f
            drawLine(
                color = color,
                start = hip,
                end = Offset(
                    hip.x + legLen * 0.85f * swing,
                    hip.y + legLen * 0.85f,
                ),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = hip,
                end = Offset(
                    hip.x - legLen * 0.85f * swing,
                    hip.y + legLen * 0.85f,
                ),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }

        CcText(
            text = stringResource(R.string.cc_loading),
            style = CcTextStyle.Caption,
            color = color,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
