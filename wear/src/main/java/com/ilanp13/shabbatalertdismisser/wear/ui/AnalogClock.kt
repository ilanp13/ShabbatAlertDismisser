package com.ilanp13.shabbatalertdismisser.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatGold
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnalogClock(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    accentColor: Color = ShabbatGold,
    showSeconds: Boolean = true,
    isAmbient: Boolean = false
) {
    var calendar by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(isAmbient, showSeconds) {
        while (true) {
            calendar = Calendar.getInstance()
            delay(if (isAmbient || !showSeconds) 60_000L else 1_000L)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2

            for (i in 0 until 12) {
                val angle = Math.toRadians((i * 30 - 90).toDouble())
                val innerR = radius * 0.85f
                val outerR = radius * 0.95f
                drawLine(
                    color = color.copy(alpha = 0.5f),
                    start = Offset(
                        center.x + (innerR * cos(angle)).toFloat(),
                        center.y + (innerR * sin(angle)).toFloat()
                    ),
                    end = Offset(
                        center.x + (outerR * cos(angle)).toFloat(),
                        center.y + (outerR * sin(angle)).toFloat()
                    ),
                    strokeWidth = if (i % 3 == 0) 3f else 1.5f
                )
            }

            val hour = calendar.get(Calendar.HOUR)
            val minute = calendar.get(Calendar.MINUTE)
            val second = calendar.get(Calendar.SECOND)

            val hourAngle = Math.toRadians(((hour + minute / 60.0) * 30 - 90))
            drawHand(center, hourAngle, radius * 0.5f, color, 4f)

            val minuteAngle = Math.toRadians((minute * 6 - 90).toDouble())
            drawHand(center, minuteAngle, radius * 0.7f, color, 2.5f)

            if (showSeconds && !isAmbient) {
                val secondAngle = Math.toRadians((second * 6 - 90).toDouble())
                drawHand(center, secondAngle, radius * 0.75f, accentColor, 1f)
            }

            drawCircle(color = accentColor, radius = 4f, center = center)
        }
    }
}

private fun DrawScope.drawHand(
    center: Offset,
    angle: Double,
    length: Float,
    color: Color,
    width: Float
) {
    drawLine(
        color = color,
        start = center,
        end = Offset(
            center.x + (length * cos(angle)).toFloat(),
            center.y + (length * sin(angle)).toFloat()
        ),
        strokeWidth = width,
        cap = StrokeCap.Round
    )
}
