package com.ilanp13.shabbatalertdismisser.wear

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.os.BatteryManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.preference.PreferenceManager
import androidx.wear.ambient.AmbientLifecycleObserver
import com.ilanp13.shabbatalertdismisser.shared.HolidayCalculator
import com.ilanp13.shabbatalertdismisser.wear.ui.ShabbatFace
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatWatchTheme
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.accentColorFromName
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

class ShabbatWatchFaceActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ShabbatWatchFace"
    }

    private lateinit var controller: ShabbatModeController
    private lateinit var bannerManager: AlertBannerManager
    private var buttonDownTime = 0L

    private val stopLockTaskReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            exitLockTaskAndFinish()
        }
    }

    private val alertNotificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val text = intent.getStringExtra("alert_text") ?: return
            bannerManager.onAlertReceived(text)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        // Apply synced language preference before inflating any resources
        val prefs = PreferenceManager.getDefaultSharedPreferences(newBase)
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
        controller = ShabbatModeController(this)
        bannerManager = AlertBannerManager(this)

        // Use ambient mode — AOD keeps our app visible when screen dims
        val ambientObserver = AmbientLifecycleObserver(this, object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {}
            override fun onExitAmbient() {}
            override fun onUpdateAmbient() {}
        })
        lifecycle.addObserver(ambientObserver)

        enterLockTask()

        val filter = IntentFilter("com.ilanp13.shabbatalertdismisser.wear.STOP_LOCK_TASK")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopLockTaskReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopLockTaskReceiver, filter)
        }

        val alertFilter = IntentFilter("com.ilanp13.shabbatalertdismisser.wear.ALERT_NOTIFICATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(alertNotificationReceiver, alertFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(alertNotificationReceiver, alertFilter)
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useAnalog = prefs.getString(WearDataReceiver.PREF_FACE_STYLE, "digital") == "analog"
        val showSeconds = prefs.getBoolean(WearDataReceiver.PREF_SHOW_SECONDS, true)
        val accentColorName = prefs.getString(WearDataReceiver.PREF_ACCENT_COLOR, "gold") ?: "gold"
        val clockSize = prefs.getString(WearDataReceiver.PREF_CLOCK_SIZE, "medium") ?: "medium"
        val showBattery = prefs.getBoolean(WearDataReceiver.PREF_SHOW_BATTERY, true)
        val showHebrewDate = prefs.getBoolean(WearDataReceiver.PREF_SHOW_HEBREW_DATE, true)
        val showParasha = prefs.getBoolean(WearDataReceiver.PREF_SHOW_PARASHA, true)
        val showHavdalah = prefs.getBoolean(WearDataReceiver.PREF_SHOW_HAVDALAH, true)

        setContent {
            ShabbatWatchTheme {
                val alertText by bannerManager.alertText.collectAsState()

                var windowInfo by remember { mutableStateOf(controller.getCurrentWindowInfo()) }
                var hebrewDay by remember { mutableStateOf(formatHebrewDay()) }
                var hebrewMonth by remember { mutableStateOf(formatHebrewMonth()) }
                var batteryLevel by remember { mutableStateOf(getBatteryLevel()) }

                LaunchedEffect(Unit) {
                    while (true) {
                        delay(60_000L)
                        windowInfo = controller.getCurrentWindowInfo()
                        hebrewDay = formatHebrewDay()
                        hebrewMonth = formatHebrewMonth()
                        batteryLevel = getBatteryLevel()
                    }
                }

                val havdalahMs = windowInfo?.second ?: 0L
                val parasha = windowInfo?.first

                val havdalahFormatted = if (havdalahMs > 0) {
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    getString(R.string.havdalah_motzash, sdf.format(Date(havdalahMs)))
                } else ""

                val indicator = when {
                    // If we have a parasha/holiday name from the schedule, show it
                    parasha != null && parasha != getString(R.string.shabbat_shalom) -> parasha
                    // If inside a Shabbat window, show "Shabbat Shalom"
                    windowInfo != null -> getString(R.string.shabbat_shalom)
                    // "Always" mode outside Shabbat — show mode indicator
                    else -> getString(R.string.shabbat_mode_active)
                }

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

                ShabbatFace(
                    indicator = indicator,
                    hebrewDay = hebrewDay,
                    hebrewMonth = hebrewMonth,
                    parasha = parasha,
                    havdalahTime = havdalahFormatted,
                    alertText = alertText,
                    batteryLevel = batteryLevel,
                    useAnalog = useAnalog,
                    showSeconds = showSeconds,
                    accentColor = accentColorFromName(accentColorName),
                    clockSize = clockSize,
                    showBattery = showBattery,
                    showHebrewDate = showHebrewDate,
                    showParasha = showParasha,
                    showHavdalah = showHavdalah,
                    isShabbatActive = isShabbatActive,
                    candleLightingCountdown = candleLightingCountdown,
                    isAmbient = true
                )
            }
        }
    }

    private fun enterLockTask() {
        if (AdminReceiver.isDeviceOwner(this)) {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = AdminReceiver.getComponentName(this)
            dpm.setLockTaskPackages(admin, arrayOf(packageName))
            startLockTask()
            Log.d(TAG, "Lock Task Mode entered (device owner)")
        } else {
            // Fallback: screen pinning — shows a one-time confirmation dialog
            // Once pinned, HOME, notifications, and quick settings are blocked by the system
            try {
                startLockTask()
                Log.d(TAG, "Screen pinning requested (no device owner)")
            } catch (e: Exception) {
                Log.w(TAG, "Screen pinning failed: ${e.message}")
            }
        }
    }

    private fun exitLockTaskAndFinish() {
        try {
            stopLockTask()
        } catch (e: Exception) {
            Log.w(TAG, "Could not stop lock task: ${e.message}")
        }
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        buttonDownTime = System.currentTimeMillis()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val thresholdMs = prefs.getInt(WearDataReceiver.PREF_LONG_PRESS_SECONDS, 10) * 1000L
        val pressDuration = System.currentTimeMillis() - buttonDownTime
        if (pressDuration >= thresholdMs) {
            val intent = Intent(this, EmergencyDialogActivity::class.java)
                .putExtra("last_alert", bannerManager.lastAlertText)
            startActivity(intent)
        }
        return true
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Block back button
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        // Block rotary bezel/crown input (Samsung, Pixel Watch)
        if (event?.source?.and(InputDevice.SOURCE_ROTARY_ENCODER) != 0) {
            return true // Consume rotary events
        }
        return true // Consume all generic motion events during lock
    }

    private val tapTimestamps = mutableListOf<Long>()

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        // Safety escape: 7 rapid taps within 4 seconds opens emergency dialog
        // (NOT 5 — Samsung SOS triggers on 5 rapid home button presses)
        if (event?.action == MotionEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            tapTimestamps.add(now)
            tapTimestamps.removeAll { now - it > 4000L }
            if (tapTimestamps.size >= 7) {
                tapTimestamps.clear()
                Log.d(TAG, "5-tap escape triggered — opening emergency dialog")
                val intent = Intent(this, EmergencyDialogActivity::class.java)
                    .putExtra("last_alert", bannerManager.lastAlertText)
                startActivity(intent)
                return true
            }
        }
        // Block all other touch events
        return true
    }

    override fun onPause() {
        super.onPause()
        // If Shabbat mode is still active and we lost focus (e.g., HOME press without
        // Lock Task Mode), re-launch immediately to maintain the lock
        if (controller.isShabbatModeActive()) {
            val relaunch = Intent(this, ShabbatWatchFaceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(relaunch)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(stopLockTaskReceiver)
        } catch (e: Exception) { /* ignore */ }
        try {
            unregisterReceiver(alertNotificationReceiver)
        } catch (e: Exception) { /* ignore */ }
    }

    private fun getBatteryLevel(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

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

    private fun hebrewMonthName(month: Int): String {
        // android.icu.util.HebrewCalendar months are 0-based:
        // 0=Tishrei, 1=Heshvan, ..., 5=Adar, 6=Adar II, 7=Nisan, ..., 12=Elul
        return when (month) {
            0 -> "תשרי"; 1 -> "חשוון"; 2 -> "כסלו"; 3 -> "טבת"
            4 -> "שבט"; 5 -> "אדר"; 6 -> "אדר ב׳"; 7 -> "ניסן"
            8 -> "אייר"; 9 -> "סיוון"; 10 -> "תמוז"; 11 -> "אב"
            12 -> "אלול"; else -> ""
        }
    }

    private fun hebrewNumeral(n: Int): String {
        if (n <= 0) return ""
        val ones = arrayOf("", "א", "ב", "ג", "ד", "ה", "ו", "ז", "ח", "ט")
        val tens = arrayOf("", "י", "כ", "ל", "מ", "נ", "ס", "ע", "פ", "צ")
        val hundreds = arrayOf("", "ק", "ר", "ש", "ת", "תק", "תר", "תש", "תת", "תתק")

        val h = (n / 100).coerceAtMost(9)
        val t = (n % 100) / 10
        val o = n % 10

        var result = hundreds[h] + tens[t] + ones[o]
        result = result.replace("יה", "טו").replace("יו", "טז")

        return when {
            result.length == 1 -> "$result׳"
            result.length > 1 -> result.dropLast(1) + "״" + result.last()
            else -> result
        }
    }
}
