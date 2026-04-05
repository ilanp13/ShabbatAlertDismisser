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
 * Safety mechanisms:
 * - 5 rapid button presses = emergency deactivation (always works)
 * - Long press (configurable) = emergency dialog
 * - PREF_SHABBAT_MODE_ACTIVE=false = service stops blocking
 * - 26-hour auto-timeout from activation = automatic deactivation
 * - Phone unlock command sets pref to false = service stops blocking
 */
class ShabbatLockService : AccessibilityService() {

    companion object {
        private const val TAG = "ShabbatLockService"
        private const val RAPID_PRESS_COUNT = 5
        private const val RAPID_PRESS_WINDOW_MS = 3000L // 5 presses within 3 seconds
        private const val MAX_SHABBAT_DURATION_MS = 26L * 60 * 60 * 1000 // 26 hours
    }

    private var keyDownTime = 0L
    private val rapidPressTimestamps = mutableListOf<Long>()

    private fun isShabbatMode(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val active = prefs.getBoolean(ShabbatModeController.PREF_SHABBAT_MODE_ACTIVE, false)
        if (!active) return false

        // Auto-timeout safety: check if Shabbat mode has been active too long
        val activatedAt = prefs.getLong("shabbat_mode_activated_at", 0)
        if (activatedAt > 0 && System.currentTimeMillis() - activatedAt > MAX_SHABBAT_DURATION_MS) {
            Log.w(TAG, "Shabbat mode exceeded 26-hour safety timeout — auto-deactivating")
            ShabbatModeController(this).deactivateShabbatMode()
            return false
        }

        return true
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

                // Check for rapid press escape (5 presses in 3 seconds)
                rapidPressTimestamps.add(now)
                rapidPressTimestamps.removeAll { now - it > RAPID_PRESS_WINDOW_MS }
                if (rapidPressTimestamps.size >= RAPID_PRESS_COUNT) {
                    Log.w(TAG, "Rapid press escape triggered — deactivating Shabbat mode")
                    rapidPressTimestamps.clear()
                    ShabbatModeController(this).deactivateShabbatMode()
                    return true
                }

                // Check for long press → emergency dialog
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val thresholdMs = prefs.getInt(WearDataReceiver.PREF_LONG_PRESS_SECONDS, 10) * 1000L
                if (duration >= thresholdMs) {
                    Log.d(TAG, "Long press detected (${duration}ms) — opening emergency dialog")
                    val intent = Intent(this, EmergencyDialogActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            }
        }

        return true // Consume all key events during Shabbat mode
    }

    override fun onInterrupt() {
        Log.d(TAG, "Shabbat lock service interrupted")
    }
}
