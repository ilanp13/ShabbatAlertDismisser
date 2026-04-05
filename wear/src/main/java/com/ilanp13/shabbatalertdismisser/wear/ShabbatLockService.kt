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
 * 1. Instantly re-launching ShabbatWatchFaceActivity when another app takes focus
 * 2. Intercepting physical button key events
 *
 * Much faster than the onPause() approach (~milliseconds vs ~seconds).
 * Requires user to enable in Settings > Accessibility (one-time setup).
 */
class ShabbatLockService : AccessibilityService() {

    companion object {
        private const val TAG = "ShabbatLockService"
    }

    override fun onServiceConnected() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100
        }
        serviceInfo = info
        Log.d(TAG, "Shabbat lock service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isShabbatMode = prefs.getBoolean(ShabbatModeController.PREF_SHABBAT_MODE_ACTIVE, false)
        if (!isShabbatMode) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return // Already our app

        // Another app took focus during Shabbat mode — re-launch immediately
        Log.d(TAG, "Focus lost to $pkg — re-launching Shabbat watch face")
        val intent = Intent(this, ShabbatWatchFaceActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isShabbatMode = prefs.getBoolean(ShabbatModeController.PREF_SHABBAT_MODE_ACTIVE, false)
        if (!isShabbatMode) return false // Don't intercept outside Shabbat mode

        // Consume all key events during Shabbat mode
        // This catches STEM_PRIMARY (Samsung home button) and other hardware keys
        Log.d(TAG, "Key event consumed: ${event.keyCode}")
        return true
    }

    override fun onInterrupt() {
        Log.d(TAG, "Shabbat lock service interrupted")
    }
}
