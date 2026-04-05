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
import com.ilanp13.shabbatalertdismisser.shared.HolidayCalculator
import com.ilanp13.shabbatalertdismisser.wear.ui.ShabbatFace
import com.ilanp13.shabbatalertdismisser.wear.ui.theme.ShabbatWatchTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = ShabbatModeController(this)
        bannerManager = AlertBannerManager(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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

        setContent {
            ShabbatWatchTheme {
                val alertText by bannerManager.alertText.collectAsState()

                var windowInfo by remember { mutableStateOf(controller.getCurrentWindowInfo()) }
                var hebrewDate by remember { mutableStateOf(formatHebrewDate()) }
                var batteryLevel by remember { mutableStateOf(getBatteryLevel()) }

                LaunchedEffect(Unit) {
                    while (true) {
                        delay(60_000L)
                        windowInfo = controller.getCurrentWindowInfo()
                        hebrewDate = formatHebrewDate()
                        batteryLevel = getBatteryLevel()
                    }
                }

                val havdalahMs = windowInfo?.second ?: 0L
                val parasha = windowInfo?.first

                val havdalahFormatted = if (havdalahMs > 0) {
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    getString(R.string.havdalah_motzash, sdf.format(Date(havdalahMs)))
                } else ""

                val indicator = if (parasha != null && parasha != getString(R.string.shabbat_shalom)) {
                    parasha
                } else {
                    getString(R.string.shabbat_shalom)
                }

                ShabbatFace(
                    indicator = indicator,
                    hebrewDate = hebrewDate,
                    parasha = parasha,
                    havdalahTime = havdalahFormatted,
                    alertText = alertText,
                    batteryLevel = batteryLevel,
                    useAnalog = useAnalog,
                    isAmbient = false
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
            Log.d(TAG, "Lock Task Mode entered")
        } else {
            Log.w(TAG, "Not device owner — Lock Task Mode unavailable")
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

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        // Block all touch events — watch face has no touch interaction.
        // Only physical button long-press is used (handled via onKeyDown/onKeyUp).
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

    private fun formatHebrewDate(): String {
        val hd = HolidayCalculator.gregorianToHebrew(Calendar.getInstance())
        val dayStr = hebrewNumeral(hd.day)
        val monthStr = hebrewMonthName(hd.month)
        val yearStr = hebrewNumeral(hd.year % 1000)
        return "$dayStr $monthStr $yearStr"
    }

    private fun hebrewMonthName(month: Int): String {
        return when (month) {
            1 -> "ניסן"; 2 -> "אייר"; 3 -> "סיוון"; 4 -> "תמוז"
            5 -> "אב"; 6 -> "אלול"; 7 -> "תשרי"; 8 -> "חשוון"
            9 -> "כסלו"; 10 -> "טבת"; 11 -> "שבט"; 12 -> "אדר"
            13 -> "אדר ב׳"; else -> ""
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
