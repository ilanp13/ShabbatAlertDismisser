package com.ilanp13.shabbatalertdismisser.wear

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.preference.PreferenceManager

/**
 * Accessibility service that enforces the Shabbat lock by:
 * 1. Instantly re-launching ShabbatWatchFaceActivity when another app/UI takes focus
 * 2. Intercepting physical button key events (with long-press → emergency dialog)
 *
 * Requires user to enable in Settings > Accessibility (one-time setup).
 */
class ShabbatLockService : AccessibilityService() {

    companion object {
        private const val TAG = "ShabbatLockService"
    }

    private var keyDownTime = 0L

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            // Listen for window changes AND notification shade
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 50
        }
        serviceInfo = info
        Log.d(TAG, "Shabbat lock service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isShabbatMode = prefs.getBoolean(ShabbatModeController.PREF_SHABBAT_MODE_ACTIVE, false)
        if (!isShabbatMode) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // Already our app

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                // Another app or system UI took focus — re-launch immediately
                Log.d(TAG, "Focus lost to $pkg (${event.eventType}) — re-launching")
                val intent = Intent(this, ShabbatWatchFaceActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isShabbatMode = prefs.getBoolean(ShabbatModeController.PREF_SHABBAT_MODE_ACTIVE, false)
        if (!isShabbatMode) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                keyDownTime = System.currentTimeMillis()
            }
            KeyEvent.ACTION_UP -> {
                val duration = System.currentTimeMillis() - keyDownTime
                val thresholdMs = prefs.getInt(WearDataReceiver.PREF_LONG_PRESS_SECONDS, 10) * 1000L
                if (duration >= thresholdMs) {
                    // Long press — open emergency dialog
                    Log.d(TAG, "Long press detected (${duration}ms) — opening emergency dialog")
                    val intent = Intent(this, EmergencyDialogActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                }
            }
        }

        // Consume all key events during Shabbat mode
        return true
    }

    override fun onInterrupt() {
        Log.d(TAG, "Shabbat lock service interrupted")
    }
}
