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

            // Overlay text elements
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
            // Digital mode — keep simple layout
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
