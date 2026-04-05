# Chronograph Watch Face Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the simple analog clock with a chronograph-style watch face featuring 3 sub-dials (battery, Shabbat status, Hebrew date), refined tick marks, bold hands, and dynamic accent colors.

**Architecture:** Complete rewrite of `AnalogClock.kt` as a self-contained Canvas composable that draws everything (ticks, hands, sub-dials, text). `ShabbatFace.kt` simplified to pass data down. Activity computes candle lighting countdown.

**Tech Stack:** Jetpack Compose Canvas API, `android.icu.util.HebrewCalendar`, `android.graphics.Paint` for text rendering in Canvas

**Spec:** `docs/superpowers/specs/2026-04-05-chronograph-watch-face-design.md`

---

## File Structure

### Files to modify
```
wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/
├── ui/AnalogClock.kt              # COMPLETE REWRITE — chronograph Canvas
├── ui/ShabbatFace.kt              # Simplify for analog mode (clock draws everything)
├── ShabbatWatchFaceActivity.kt    # Add countdown computation, split Hebrew date
└── ui/theme/Theme.kt              # Add warning/orange color constant
```

---

## Task 1: Rewrite AnalogClock.kt as chronograph

**Files:**
- Rewrite: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/AnalogClock.kt`
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/theme/Theme.kt`

- [ ] **Step 1: Add WarningOrange color to Theme.kt**

Add after `val AlertRed`:

```kotlin
val WarningOrange = Color(0xFFFF8800)
```

- [ ] **Step 2: Rewrite AnalogClock.kt**

Replace the entire file with:

```kotlin
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
            skipPositions.add(9) // Shabbat status always present
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
                val by = C.y - 85f * scaleFactor
                val br = 42f * scaleFactor
                drawSubDial(C = Offset(bx, by), radius = br, bg = darkBg, border = dimColor)
                drawBatteryArc(Offset(bx, by), br - 10f, batteryLevel, accent, ambient)
                // Percentage text
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
            val sx = C.x - 95f * scaleFactor
            val sy = C.y + 15f * scaleFactor
            val sr = 42f * scaleFactor
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
                val hx = C.x + 95f * scaleFactor
                val hy = C.y + 15f * scaleFactor
                val hr = 42f * scaleFactor
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

            // Hour hand
            val hAngle = Math.toRadians(((hour + minute / 60.0) * 30 - 90))
            drawHand(C, hAngle, 80f * scaleFactor, textColor, 7f)

            // Minute hand
            val mAngle = Math.toRadians((minute * 6 - 90).toDouble())
            drawHand(C, mAngle, 115f * scaleFactor, textColor, 4.5f)

            // Second hand (optional)
            if (showSeconds && !ambient) {
                val sAngle = Math.toRadians((second * 6 - 90).toDouble())
                drawHand(C, sAngle, 120f * scaleFactor, accent, 1f)
            }

            // Center dot
            drawCircle(color = accent, radius = 5f, center = C)
            drawCircle(color = Color(0xFF111111), radius = 2f, center = C)
        }
    }
}

// === Drawing helpers ===

private fun DrawScope.drawSubDial(C: Offset, radius: Float, bg: Color, border: Color) {
    drawCircle(color = bg, radius = radius, center = C)
    drawCircle(color = border, radius = radius, center = C, style = Stroke(width = 1f))
}

private fun DrawScope.drawBatteryArc(center: Offset, radius: Float, level: Int, accent: Color, ambient: Boolean) {
    val startAngle = 126f // ~7 o'clock position
    val sweepTotal = 288f // arc span
    // Background arc
    drawArc(
        color = Color(0xFF333333),
        startAngle = startAngle,
        sweepAngle = sweepTotal,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )
    // Level arc
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
        // Candle body
        drawRect(
            color = candleColor,
            topLeft = Offset(cx - 2f * scale, cy - 8f * scale),
            size = androidx.compose.ui.geometry.Size(4f * scale, 16f * scale)
        )
        // Flame outer
        drawOval(
            color = flameOuter,
            topLeft = Offset(cx - 3f * scale, cy - 17f * scale),
            size = androidx.compose.ui.geometry.Size(6f * scale, 10f * scale)
        )
        // Flame inner
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
```

- [ ] **Step 3: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL (with warnings about unused parameters in ShabbatFace — we fix those in Task 2)

- [ ] **Step 4: Commit**

```bash
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/AnalogClock.kt \
    wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/theme/Theme.kt
git commit -m "feat(wear): rewrite AnalogClock as chronograph with sub-dials"
```

---

## Task 2: Update ShabbatFace to pass data to chronograph

**Files:**
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/ShabbatFace.kt`

- [ ] **Step 1: Update ShabbatFace signature and AnalogClock call**

Add new parameters and pass them to AnalogClock. Replace the entire file:

```kotlin
package com.ilanp13.shabbatalertdismisser.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatWhite
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatAmbientGray

@Composable
fun ShabbatFace(
    indicator: String,
    hebrewDay: String,
    hebrewMonth: String,
    parasha: String?,
    havdalahTime: String,
    alertText: String?,
    batteryLevel: Int,
    useAnalog: Boolean,
    showSeconds: Boolean,
    accentColor: Color,
    clockSize: String,
    showBattery: Boolean,
    showHebrewDate: Boolean,
    showParasha: Boolean,
    showHavdalah: Boolean,
    isShabbatActive: Boolean,
    candleLightingCountdown: String?,
    isAmbient: Boolean
) {
    val textColor = if (isAmbient) ShabbatAmbientGray else ShabbatWhite
    val accent = if (isAmbient) ShabbatAmbientGray else accentColor

    val scaleFactor = when (clockSize) {
        "small" -> 0.85f
        "large" -> 1.15f
        else -> 1f
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (useAnalog) {
            // Chronograph draws everything — indicator, sub-dials, hands
            AnalogClock(
                modifier = Modifier.fillMaxSize(),
                color = textColor,
                accentColor = accent,
                showSeconds = showSeconds,
                isAmbient = isAmbient,
                batteryLevel = batteryLevel,
                showBattery = showBattery,
                hebrewDay = hebrewDay,
                hebrewMonth = hebrewMonth,
                showHebrewDate = showHebrewDate,
                isShabbatActive = isShabbatActive,
                candleLightingCountdown = candleLightingCountdown,
                scaleFactor = scaleFactor
            )

            // Overlay text elements that Canvas doesn't handle well
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top: indicator text
                Text(
                    text = indicator,
                    fontSize = 15.sp,
                    color = accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                // Bottom: havdalah + parasha
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 28.dp)
                ) {
                    if (showHavdalah && havdalahTime.isNotEmpty()) {
                        Text(
                            text = havdalahTime,
                            fontSize = 12.sp,
                            color = accent,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (showParasha && parasha != null) {
                        Text(
                            text = parasha,
                            fontSize = 11.sp,
                            color = textColor.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Alert banner overlay
            if (alertText != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    AlertBanner(
                        alertText = alertText,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        } else {
            // Digital mode — keep existing layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = indicator, fontSize = 14.sp, color = accent, textAlign = TextAlign.Center)
                    if (showBattery) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "$batteryLevel%", fontSize = 10.sp, color = textColor.copy(alpha = 0.5f))
                    }
                }
                DigitalClock(
                    modifier = Modifier.weight(1f),
                    color = textColor,
                    showSeconds = showSeconds,
                    isAmbient = isAmbient
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 4.dp)) {
                    if (showHebrewDate) {
                        Text(text = "$hebrewDay $hebrewMonth", fontSize = 12.sp, color = textColor, textAlign = TextAlign.Center)
                    }
                    if (showParasha && parasha != null) {
                        Text(text = parasha, fontSize = 11.sp, color = textColor.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                    }
                    if (showHavdalah && havdalahTime.isNotEmpty()) {
                        Text(text = havdalahTime, fontSize = 12.sp, color = accent, textAlign = TextAlign.Center)
                    }
                }
                if (alertText != null) {
                    AlertBanner(alertText = alertText, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD FAILED — ShabbatWatchFaceActivity still passes old `hebrewDate: String` parameter. Fixed in Task 3.

- [ ] **Step 3: Commit**

```bash
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ui/ShabbatFace.kt
git commit -m "feat(wear): update ShabbatFace for chronograph with sub-dial data"
```

---

## Task 3: Update ShabbatWatchFaceActivity to provide chronograph data

**Files:**
- Modify: `wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatWatchFaceActivity.kt`

- [ ] **Step 1: Split Hebrew date and add countdown computation**

In `ShabbatWatchFaceActivity.kt`, replace the `formatHebrewDate()` call and `ShabbatFace(...)` call in `setContent`.

Replace:
```kotlin
                var hebrewDate by remember { mutableStateOf(formatHebrewDate()) }
```
with:
```kotlin
                var hebrewDay by remember { mutableStateOf(formatHebrewDay()) }
                var hebrewMonth by remember { mutableStateOf(formatHebrewMonth()) }
```

In the `LaunchedEffect`, replace:
```kotlin
                        hebrewDate = formatHebrewDate()
```
with:
```kotlin
                        hebrewDay = formatHebrewDay()
                        hebrewMonth = formatHebrewMonth()
```

Add countdown computation before the `ShabbatFace` call:
```kotlin
                val isShabbatActive = windowInfo != null
                val candleLightingCountdown = if (!isShabbatActive) {
                    controller.getNextWindowInfo()?.let { next ->
                        val diffMs = next.first - System.currentTimeMillis()
                        if (diffMs > 0) {
                            val hours = (diffMs / 3_600_000).toInt()
                            val mins = ((diffMs % 3_600_000) / 60_000).toInt()
                            "${hours}ש ${mins}ד"
                        } else null
                    }
                } else null
```

Update the `ShabbatFace(...)` call — replace `hebrewDate = hebrewDate,` with:
```kotlin
                    hebrewDay = hebrewDay,
                    hebrewMonth = hebrewMonth,
```

And add after `showHavdalah`:
```kotlin
                    isShabbatActive = isShabbatActive,
                    candleLightingCountdown = candleLightingCountdown,
```

- [ ] **Step 2: Replace `formatHebrewDate()` with two separate functions**

Replace the existing `formatHebrewDate()` method with:

```kotlin
    private fun formatHebrewDay(): String {
        val hcal = android.icu.util.HebrewCalendar()
        val day = hcal.get(android.icu.util.HebrewCalendar.DAY_OF_MONTH)
        return hebrewNumeral(day)
    }

    private fun formatHebrewMonth(): String {
        val hcal = android.icu.util.HebrewCalendar()
        val month = hcal.get(android.icu.util.HebrewCalendar.MONTH)
        return hebrewMonthName(month)
    }
```

Remove the old `formatHebrewDate()` method.

- [ ] **Step 3: Build**

```bash
./gradlew :wear:assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add wear/src/main/java/com/ilanp13/shabbatalertdismisser/wear/ShabbatWatchFaceActivity.kt
git commit -m "feat(wear): split Hebrew date and add candle lighting countdown"
```

---

## Task 4: Build verification and version bump

- [ ] **Step 1: Clean build**

```bash
./gradlew clean :wear:assembleDebug :app:assembleDebug
```

Expected: BUILD SUCCESSFUL for both modules

- [ ] **Step 2: Bump wear version and build AAB**

In `wear/build.gradle.kts`, increment `versionCode` by 1 from current value.

```bash
./gradlew :wear:bundleRelease
```

- [ ] **Step 3: Commit and push**

```bash
git add wear/build.gradle.kts
git commit -m "chore: bump wear version for chronograph release"
git push
```
