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
    hebrewDate: String,
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
    isAmbient: Boolean
) {
    val textColor = if (isAmbient) ShabbatAmbientGray else ShabbatWhite
    val accent = if (isAmbient) ShabbatAmbientGray else accentColor

    val clockWeight = when (clockSize) {
        "small" -> 0.7f
        "large" -> 1.3f
        else -> 1f // medium
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: indicator + battery on same line
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = indicator,
                    fontSize = 14.sp,
                    color = accent,
                    textAlign = TextAlign.Center
                )
                if (showBattery) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$batteryLevel%",
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.5f)
                    )
                }
            }

            if (useAnalog) {
                AnalogClock(
                    modifier = Modifier.weight(clockWeight),
                    color = textColor,
                    accentColor = accent,
                    showSeconds = showSeconds,
                    isAmbient = isAmbient
                )
            } else {
                DigitalClock(
                    modifier = Modifier.weight(clockWeight),
                    color = textColor,
                    showSeconds = showSeconds,
                    isAmbient = isAmbient
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                if (showHebrewDate) {
                    Text(
                        text = hebrewDate,
                        fontSize = 12.sp,
                        color = textColor,
                        textAlign = TextAlign.Center
                    )
                }
                if (showParasha && parasha != null) {
                    Text(
                        text = parasha,
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
                if (showHavdalah && havdalahTime.isNotEmpty()) {
                    Text(
                        text = havdalahTime,
                        fontSize = 12.sp,
                        color = accent,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (alertText != null) {
                AlertBanner(
                    alertText = alertText,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}
