package com.ilanp13.shabbatalertdismisser.wear

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.preference.PreferenceManager

/**
 * Accessibility service that enforces the Shabbat lock.
 *
 * Safety escape mechanisms:
 * - 5 rapid button presses within 3 seconds = immediate deactivation
 * - Long press (configurable, default 10s) = emergency dialog
 * - Phone "End Watch Shabbat Mode" button = remote deactivation
 * - PREF_SHABBAT_MODE_ACTIVE=false = service stops blocking instantly
 */
class ShabbatLockService : AccessibilityService() {

    companion object {
        private const val TAG = "ShabbatLockService"
        private const val RAPID_PRESS_COUNT = 5
        private const val RAPID_PRESS_WINDOW_MS = 3000L
    }

    private var keyDownTime = 0L
    private val rapidPressTimestamps = mutableListOf<Long>()

    private fun isShabbatMode(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(ShabbatModeController.PREF_SHABBAT_MODE_ACTIVE, false)
    }

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 50
        }
        serviceInfo = info
        Log.d(TAG, "Shabbat lock service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!isShabbatMode()) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                Log.d(TAG, "Focus lost to $pkg — re-launching Shabbat watch face")
                val intent = Intent(this, ShabbatWatchFaceActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        if (!isShabbatMode()) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                keyDownTime = System.currentTimeMillis()
            }
            KeyEvent.ACTION_UP -> {
                val now = System.currentTimeMillis()
                val duration = now - keyDownTime

                // Safety: 5 rapid presses within 3 seconds = emergency exit
                rapidPressTimestamps.add(now)
                rapidPressTimestamps.removeAll { now - it > RAPID_PRESS_WINDOW_MS }
                if (rapidPressTimestamps.size >= RAPID_PRESS_COUNT) {
                    Log.w(TAG, "Rapid press escape — deactivating Shabbat mode")
                    rapidPressTimestamps.clear()
                    ShabbatModeController(this).deactivateShabbatMode()
                    return true
                }

                // Long press → emergency dialog
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val thresholdMs = prefs.getInt(WearDataReceiver.PREF_LONG_PRESS_SECONDS, 10) * 1000L
                if (duration >= thresholdMs) {
                    Log.d(TAG, "Long press (${duration}ms) — opening emergency dialog")
                    val intent = Intent(this, EmergencyDialogActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            }
        }

        return true
    }

    override fun onInterrupt() {
        Log.d(TAG, "Shabbat lock service interrupted")
    }
}
