package com.ilanp13.shabbatalertdismisser

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager

class AlertDismissService : AccessibilityService() {

    companion object {
        private const val TAG = "ShabbatAlertDismiss"
        private val CELL_BROADCAST_PACKAGES = setOf(
            "com.android.cellbroadcastreceiver",
            "com.google.android.cellbroadcastreceiver",
            "com.samsung.android.cellbroadcastreceiver",
            "com.android.cellbroadcastservice"
        )
        private val DISMISS_BUTTON_TEXTS = listOf(
            "OK", "Ok", "ok",
            "אישור",
            "סגור",
            "Close",
            "Dismiss"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Check if this is a cell broadcast alert
        if (!isCellBroadcastPackage(packageName)) return

        Log.d(TAG, "Cell broadcast alert detected from: $packageName")

        // Check mode
        val mode = prefs.getString("mode", "shabbat_only")
        if (mode == "disabled") return

        if (mode == "always") {
            // Skip time checks, always dismiss
        } else {
            // Check if it's currently Shabbat (or holiday)
            val lat = prefs.getFloat("latitude", 31.7683f).toDouble()
            val lon = prefs.getFloat("longitude", 35.2137f).toDouble()
            val candleMinutes = prefs.getInt("candle_lighting_minutes", 18)
            val havdalahMinutes = prefs.getInt("havdalah_minutes", 40)

            val calculator = ShabbatCalculator(lat, lon)
            val isShabbat = calculator.isShabbatNow(candleMinutes, havdalahMinutes)
            val isHoliday = if (mode == "shabbat_holidays") {
                HolidayCalculator.isYomTovToday(candleMinutes, havdalahMinutes, lat, lon)
            } else false

            if (!isShabbat && !isHoliday) {
                Log.d(TAG, "Not Shabbat/holiday right now, ignoring")
                return
            }
        }

        // Wait before dismissing (so siren can be heard)
        val delaySeconds = prefs.getInt("delay_seconds", 10)
        Log.d(TAG, "Shabbat mode active. Will dismiss in $delaySeconds seconds")

        handler.postDelayed({
            dismissAlert()
        }, delaySeconds * 1000L)
    }

    private fun dismissAlert() {
        val rootNode = rootInActiveWindow ?: run {
            Log.w(TAG, "Could not get root window")
            return
        }

        try {
            for (buttonText in DISMISS_BUTTON_TEXTS) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(buttonText)
                for (node in nodes) {
                    if (tryClick(node)) {
                        Log.d(TAG, "Successfully dismissed alert via button: $buttonText")
                        return
                    }
                }
            }

            // Fallback: try to find any clickable button
            if (findAndClickButton(rootNode)) {
                Log.d(TAG, "Dismissed alert via fallback button search")
                return
            }

            // Last resort: press Back
            Log.d(TAG, "No button found, trying BACK action")
            performGlobalAction(GLOBAL_ACTION_BACK)

        } finally {
            rootNode.recycle()
        }
    }

    private fun tryClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        // Walk up to find clickable parent
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
            parent = parent.parent
            depth++
        }
        return false
    }

    private fun findAndClickButton(node: AccessibilityNodeInfo): Boolean {
        if (node.className?.toString() == "android.widget.Button" && node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findAndClickButton(child)) return true
        }
        return false
    }

    private fun isCellBroadcastPackage(pkg: String): Boolean {
        return CELL_BROADCAST_PACKAGES.any { pkg.contains(it) } ||
               pkg.contains("cellbroadcast") ||
               pkg.contains("emergencyalert")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
}
