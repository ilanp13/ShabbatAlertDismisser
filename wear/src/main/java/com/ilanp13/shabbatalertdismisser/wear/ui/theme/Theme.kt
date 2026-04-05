package com.ilanp13.shabbatalertdismisser.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ColorScheme

val ShabbatGold = Color(0xFFD4AF37)
val ShabbatWhite = Color(0xFFEEEEEE)
val ShabbatDarkBg = Color(0xFF111111)
val ShabbatAmbientGray = Color(0xFF888888)
val AlertRed = Color(0xFFFF4444)
val WarningOrange = Color(0xFFFF8800)

fun accentColorFromName(name: String): Color = when (name) {
    "blue" -> Color(0xFF4488FF)
    "green" -> Color(0xFF44BB66)
    "white" -> Color(0xFFDDDDDD)
    "red" -> Color(0xFFFF4444)
    "purple" -> Color(0xFFAA66FF)
    else -> ShabbatGold // "gold" default
}

@Composable
fun ShabbatWatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme(
            primary = ShabbatGold,
            onPrimary = ShabbatDarkBg,
            background = ShabbatDarkBg,
            onBackground = ShabbatWhite,
            surfaceContainer = ShabbatDarkBg,
            onSurface = ShabbatWhite,
        ),
        content = content
    )
}
