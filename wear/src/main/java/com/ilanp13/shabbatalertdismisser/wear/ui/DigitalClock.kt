package com.ilanp13.shabbatalertdismisser.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DigitalClock(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    showSeconds: Boolean = true,
    isAmbient: Boolean = false
) {
    val format = if (showSeconds) "HH:mm:ss" else "HH:mm"
    var time by remember(format) { mutableStateOf(formatTime(format)) }

    LaunchedEffect(isAmbient, showSeconds) {
        while (true) {
            time = formatTime(format)
            delay(if (isAmbient || !showSeconds) 60_000L else 1_000L)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = time,
            fontSize = if (showSeconds) 40.sp else 48.sp,
            fontWeight = FontWeight.Light,
            color = color
        )
    }
}

private fun formatTime(format: String): String {
    return SimpleDateFormat(format, Locale.getDefault()).format(Date())
}
