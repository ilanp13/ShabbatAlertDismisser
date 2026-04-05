package com.ilanp13.shabbatalertdismisser.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.preference.PreferenceManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatGold
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatWatchTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IdleStatusActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(newBase)
        val lang = prefs.getString(WearDataReceiver.PREF_LANGUAGE, null)
        if (lang != null && lang != "system") {
            val locale = java.util.Locale(lang)
            val config = newBase.resources.configuration
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val controller = ShabbatModeController(this)

        if (controller.isShabbatModeActive()) {
            startActivity(
                Intent(this, ShabbatWatchFaceActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
            return
        }

        // If "always" mode, redirect to Shabbat watch face
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getString(WearDataReceiver.PREF_ACTIVATION_MODE, "auto") == "always") {
            controller.activateShabbatMode()
            finish()
            return
        }

        controller.scheduleFromSyncedWindows()

        val nextWindow = controller.getNextWindowInfo()
        val sdf = SimpleDateFormat("EEE dd/MM HH:mm", Locale.getDefault())

        // Check if AOD needs manual enable
        val batteryOptimizer = BatteryOptimizer(this)
        val needsAodWarning = !batteryOptimizer.isAodEnabled()

        setContent {
            ShabbatWatchTheme {
                IdleScreen(
                    nextCandleLighting = nextWindow?.let { sdf.format(Date(it.first)) },
                    nextHavdalah = nextWindow?.let { sdf.format(Date(it.second)) },
                    parasha = nextWindow?.third,
                    aodWarning = needsAodWarning,
                    onActivateEarly = {
                        controller.activateShabbatMode()
                        startActivity(
                            Intent(this@IdleStatusActivity, ShabbatWatchFaceActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        )
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun IdleScreen(
    nextCandleLighting: String?,
    nextHavdalah: String?,
    parasha: String?,
    aodWarning: Boolean = false,
    onActivateEarly: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 16.sp,
            color = ShabbatGold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (nextCandleLighting != null) {
            Text(
                text = stringResource(R.string.next_candle_lighting, nextCandleLighting),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (nextHavdalah != null) {
                Text(
                    text = stringResource(R.string.havdalah_label, nextHavdalah),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (parasha != null) {
                Text(
                    text = parasha,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Button(
                onClick = onActivateEarly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.activate_early), fontSize = 13.sp)
            }
        } else {
            Text(
                text = stringResource(R.string.no_schedule),
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = stringResource(R.string.sync_from_phone),
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }

        if (aodWarning) {
            Text(
                text = stringResource(R.string.aod_warning),
                fontSize = 9.sp,
                color = Color(0xFFFF8800),
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
            )
        }
    }
}
