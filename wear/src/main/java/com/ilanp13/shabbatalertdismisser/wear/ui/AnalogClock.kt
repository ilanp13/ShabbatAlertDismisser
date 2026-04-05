package com.ilanp13.shabbatalertdismisser.wear.ui

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnalogClock(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    accentColor: Color = ShabbatGold,
    showSeconds: Boolean = false,
    isAmbient: Boolean = false,
    batteryLevel: Int = 100,
    showBattery: Boolean = true,
    hebrewDay: String = "",
    hebrewMonth: String = "",
    showHebrewDate: Boolean = true,
    isShabbatActive: Boolean = false,
    candleLightingCountdown: String? = null,
    scaleFactor: Float = 1f
) {
    var calendar by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(isAmbient, showSeconds) {
        while (true) {
            calendar = Calendar.getInstance()
            delay(if (isAmbient || !showSeconds) 60_000L else 1_000L)
        }
    }

    val ambient = isAmbient
    val textColor = if (ambient) ShabbatAmbientGray else color
    val accent = if (ambient) ShabbatAmbientGray else accentColor
    val dimColor = if (ambient) ShabbatAmbientGray.copy(alpha = 0.5f) else Color(0xFF555555)
    val darkBg = Color(0xFF1A1A1A)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val W = size.width
            val C = Offset(W / 2, W / 2)
            val R = W / 2 * scaleFactor

            // === Outer chapter ring ===
            drawCircle(
                color = Color(0xFF333333),
                radius = R - 8f,
                center = C,
                style = Stroke(width = 1f)
            )

            // === Minute tick marks (60) ===
            for (i in 0 until 60) {
                val angle = Math.toRadians((i * 6 - 90).toDouble())
                val isHour = i % 5 == 0
                val inner = if (isHour) R - 35f else R - 22f
                val outer = R - 10f
                drawLine(
                    color = if (isHour) textColor else dimColor,
                    start = Offset(
                        C.x + (inner * cos(angle)).toFloat(),
                        C.y + (inner * sin(angle)).toFloat()
                    ),
                    end = Offset(
                        C.x + (outer * cos(angle)).toFloat(),
                        C.y + (outer * sin(angle)).toFloat()
                    ),
                    strokeWidth = if (isHour) 3f else 1.5f
                )
            }

            // === Hour numerals (skip 12, 3, 9 where sub-dials are) ===
            val skipPositions = mutableSetOf<Int>()
            if (showBattery) skipPositions.add(12)
            skipPositions.add(9)
            if (showHebrewDate) skipPositions.add(3)

            val textPaint = android.graphics.Paint().apply {
                this.color = textColor.toArgb()
                textSize = 18f * scaleFactor
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            for (i in 1..12) {
                if (i in skipPositions) continue
                val angle = Math.toRadians((i * 30 - 90).toDouble())
                val r = R - 42f
                val x = C.x + (r * cos(angle)).toFloat()
                val y = C.y + (r * sin(angle)).toFloat()
                drawContext.canvas.nativeCanvas.drawText(
                    i.toString(), x, y + 6f, textPaint
                )
            }

            // === Sub-dial: Battery (12 o'clock) ===
            if (showBattery) {
                val bx = C.x
                val by = C.y - 100f * scaleFactor
                val br = 50f * scaleFactor
                drawSubDial(C = Offset(bx, by), radius = br, bg = darkBg, border = dimColor)
                drawBatteryArc(Offset(bx, by), br - 10f, batteryLevel, accent, ambient)
                val battPaint = android.graphics.Paint().apply {
                    this.color = textColor.toArgb()
                    textSize = 14f * scaleFactor
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "$batteryLevel%", bx, by + 5f, battPaint
                )
                val labelPaint = android.graphics.Paint().apply {
                    this.color = (if (ambient) ShabbatAmbientGray else Color(0xFF888888)).toArgb()
                    textSize = 10f * scaleFactor
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "BATTERY", bx, by + 18f, labelPaint
                )
            }

            // === Sub-dial: Shabbat status (9 o'clock) ===
            val sx = C.x - 110f * scaleFactor
            val sy = C.y + 15f * scaleFactor
            val sr = 50f * scaleFactor
            drawSubDial(Offset(sx, sy), sr, darkBg, dimColor)
            drawCandles(Offset(sx, sy), accent, ambient, scaleFactor)
            if (!isShabbatActive && candleLightingCountdown != null) {
                val countPaint = android.graphics.Paint().apply {
                    this.color = (if (ambient) ShabbatAmbientGray else Color(0xFF888888)).toArgb()
                    textSize = 9f * scaleFactor
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    candleLightingCountdown, sx, sy + 24f * scaleFactor, countPaint
                )
                countPaint.textSize = 8f * scaleFactor
                drawContext.canvas.nativeCanvas.drawText(
                    "הדלקת נרות", sx, sy + 34f * scaleFactor, countPaint
                )
            } else {
                val shabPaint = android.graphics.Paint().apply {
                    this.color = (if (ambient) ShabbatAmbientGray else Color(0xFF888888)).toArgb()
                    textSize = 10f * scaleFactor
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "שבת", sx, sy + 28f * scaleFactor, shabPaint
                )
            }

            // === Sub-dial: Hebrew date (3 o'clock) ===
            if (showHebrewDate) {
                val hx = C.x + 110f * scaleFactor
                val hy = C.y + 15f * scaleFactor
                val hr = 50f * scaleFactor
                drawSubDial(Offset(hx, hy), hr, darkBg, dimColor)
                val dayPaint = android.graphics.Paint().apply {
                    this.color = textColor.toArgb()
                    textSize = 16f * scaleFactor
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    hebrewDay, hx, hy - 2f, dayPaint
                )
                val monthPaint = android.graphics.Paint().apply {
                    this.color = accent.toArgb()
                    textSize = 13f * scaleFactor
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    hebrewMonth, hx, hy + 14f, monthPaint
                )
            }

            // === Clock hands ===
            val hour = calendar.get(Calendar.HOUR)
            val minute = calendar.get(Calendar.MINUTE)
            val second = calendar.get(Calendar.SECOND)

            val hAngle = Math.toRadians(((hour + minute / 60.0) * 30 - 90))
            drawHand(C, hAngle, 95f * scaleFactor, textColor, 8f)

            val mAngle = Math.toRadians((minute * 6 - 90).toDouble())
            drawHand(C, mAngle, 135f * scaleFactor, textColor, 5f)

            if (showSeconds && !ambient) {
                val sAngle = Math.toRadians((second * 6 - 90).toDouble())
                drawHand(C, sAngle, 140f * scaleFactor, accent, 1.5f)
            }

            drawCircle(color = accent, radius = 5f, center = C)
            drawCircle(color = Color(0xFF111111), radius = 2f, center = C)
        }
    }
}

private fun DrawScope.drawSubDial(C: Offset, radius: Float, bg: Color, border: Color) {
    drawCircle(color = bg, radius = radius, center = C)
    drawCircle(color = border, radius = radius, center = C, style = Stroke(width = 1f))
}

private fun DrawScope.drawBatteryArc(center: Offset, radius: Float, level: Int, accent: Color, ambient: Boolean) {
    val startAngle = 126f
    val sweepTotal = 288f
    drawArc(
        color = Color(0xFF333333),
        startAngle = startAngle,
        sweepAngle = sweepTotal,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )
    val arcColor = if (ambient) ShabbatAmbientGray else when {
        level > 50 -> accent
        level > 20 -> WarningOrange
        else -> AlertRed
    }
    drawArc(
        color = arcColor,
        startAngle = startAngle,
        sweepAngle = sweepTotal * level / 100f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawCandles(center: Offset, accent: Color, ambient: Boolean, scale: Float) {
    val candleColor = if (ambient) ShabbatAmbientGray else accent
    val flameOuter = if (ambient) ShabbatAmbientGray else Color(0xFFFFD700)
    val flameInner = if (ambient) ShabbatAmbientGray.copy(alpha = 0.7f) else Color(0xFFFFA500)

    for (dx in listOf(-10f, 10f)) {
        val cx = center.x + dx * scale
        val cy = center.y - 4f * scale
        drawRect(
            color = candleColor,
            topLeft = Offset(cx - 2f * scale, cy - 8f * scale),
            size = androidx.compose.ui.geometry.Size(4f * scale, 16f * scale)
        )
        drawOval(
            color = flameOuter,
            topLeft = Offset(cx - 3f * scale, cy - 17f * scale),
            size = androidx.compose.ui.geometry.Size(6f * scale, 10f * scale)
        )
        drawOval(
            color = flameInner,
            topLeft = Offset(cx - 1.5f * scale, cy - 14f * scale),
            size = androidx.compose.ui.geometry.Size(3f * scale, 6f * scale)
        )
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

private fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
