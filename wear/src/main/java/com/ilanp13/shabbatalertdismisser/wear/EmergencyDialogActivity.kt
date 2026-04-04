package com.ilanp13.shabbatalertdismisser.wear

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.ilanp13.shabbatalertdismisser.shared.HebcalService
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.AlertRed
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatWatchTheme

class EmergencyDialogActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissMs = 10_000L

    private fun startAutoDismissTimer() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ finish() }, autoDismissMs)
    }

    private fun cancelAutoDismissTimer() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val showSos = prefs.getBoolean(WearDataReceiver.PREF_EMERGENCY_SOS, true)
        val showLastAlert = prefs.getBoolean(WearDataReceiver.PREF_EMERGENCY_LAST_ALERT, true)
        val lastAlert = intent.getStringExtra("last_alert")

        autoDismissMs = prefs.getInt(WearDataReceiver.PREF_LONG_PRESS_SECONDS, 10) * 1000L
        startAutoDismissTimer()

        setContent {
            ShabbatWatchTheme {
                var showConfirm by remember { mutableStateOf(false) }

                if (showConfirm) {
                    // Restart timer on confirmation screen
                    LaunchedEffect(Unit) { startAutoDismissTimer() }

                    EndShabbatConfirmation(
                        isAfterHavdalah = isAfterHavdalah(),
                        onConfirm = {
                            cancelAutoDismissTimer()
                            ShabbatModeController(this@EmergencyDialogActivity)
                                .deactivateShabbatMode()
                            finish()
                        },
                        onCancel = {
                            showConfirm = false
                            startAutoDismissTimer()
                        }
                    )
                } else {
                    EmergencyMenu(
                        showSos = showSos,
                        showLastAlert = showLastAlert && lastAlert != null,
                        lastAlert = lastAlert,
                        onSos = {
                            cancelAutoDismissTimer()
                            val number = getString(R.string.emergency_number)
                            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                            startActivity(callIntent)
                        },
                        onEndShabbat = {
                            cancelAutoDismissTimer()
                            showConfirm = true
                        },
                        onCancel = {
                            cancelAutoDismissTimer()
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAutoDismissTimer()
    }

    private fun isAfterHavdalah(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val json = prefs.getString(WearDataReceiver.PREF_SCHEDULE_JSON, "[]") ?: "[]"
        val windows = HebcalService.windowsFromJson(json)
        val now = System.currentTimeMillis()
        val current = windows.find { now in it.candleMs..it.havdalahMs }
        return current == null
    }
}

@Composable
private fun EmergencyMenu(
    showSos: Boolean,
    showLastAlert: Boolean,
    lastAlert: String?,
    onSos: () -> Unit,
    onEndShabbat: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.emergency_title),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (showSos) {
            Button(
                onClick = onSos,
                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Text(stringResource(R.string.emergency_sos), fontSize = 14.sp)
            }
        }

        if (showLastAlert && lastAlert != null) {
            Text(
                text = lastAlert,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = Color.Yellow,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Button(
            onClick = onEndShabbat,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text(stringResource(R.string.emergency_end_shabbat), fontSize = 13.sp)
        }

        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text(stringResource(R.string.emergency_cancel), fontSize = 13.sp)
        }
    }
}

@Composable
private fun EndShabbatConfirmation(
    isAfterHavdalah: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isAfterHavdalah) {
                stringResource(R.string.emergency_confirm_title)
            } else {
                stringResource(R.string.emergency_confirm_early)
            },
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Button(
            onClick = onConfirm,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isAfterHavdalah) Color.DarkGray else AlertRed
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text(stringResource(R.string.emergency_confirm_yes), fontSize = 13.sp)
        }

        Button(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text(stringResource(R.string.emergency_confirm_no), fontSize = 13.sp)
        }
    }
}
