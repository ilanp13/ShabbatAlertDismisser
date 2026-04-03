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
    isAmbient: Boolean = false
) {
    var time by remember { mutableStateOf(formatTime()) }

    LaunchedEffect(isAmbient) {
        while (true) {
            time = formatTime()
            delay(if (isAmbient) 60_000L else 1_000L)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = time,
            fontSize = 48.sp,
            fontWeight = FontWeight.Light,
            color = color
        )
    }
}

private fun formatTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}
