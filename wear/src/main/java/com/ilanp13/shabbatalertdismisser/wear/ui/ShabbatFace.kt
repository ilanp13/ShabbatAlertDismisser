package com.ilanp13.shabbatalertdismisser.wear.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatGold
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatWhite
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatAmbientGray

@Composable
fun ShabbatFace(
    indicator: String,
    hebrewDate: String,
    parasha: String?,
    havdalahTime: String,
    alertText: String?,
    useAnalog: Boolean,
    isAmbient: Boolean
) {
    val textColor = if (isAmbient) ShabbatAmbientGray else ShabbatWhite
    val accentColor = if (isAmbient) ShabbatAmbientGray else ShabbatGold

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
            Text(
                text = indicator,
                fontSize = 14.sp,
                color = accentColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (useAnalog) {
                AnalogClock(
                    modifier = Modifier.weight(1f),
                    color = textColor,
                    accentColor = accentColor,
                    isAmbient = isAmbient
                )
            } else {
                DigitalClock(
                    modifier = Modifier.weight(1f),
                    color = textColor,
                    isAmbient = isAmbient
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = hebrewDate,
                    fontSize = 12.sp,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
                if (parasha != null) {
                    Text(
                        text = parasha,
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text = havdalahTime,
                    fontSize = 12.sp,
                    color = accentColor,
                    textAlign = TextAlign.Center
                )
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
