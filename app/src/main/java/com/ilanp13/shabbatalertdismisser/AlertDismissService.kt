package com.ilanp13.shabbatalertdismisser

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertDismissService : AccessibilityService() {

    companion object {
        private const val TAG = "ShabbatAlertDismiss"
        private const val NOTIF_CHANNEL_ID = "shabbat_status"
        private const val NOTIF_ID = 1
        private const val NOTIF_UPDATE_MS = 60_000L

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

    // Update notification whenever mode or Hebcal times change
    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in listOf("mode", "hebcal_candle_ms", "hebcal_havdalah_ms")) {
            postStatusNotification()
        }
    }

    private val notifUpdateRunnable = object : Runnable {
        override fun run() {
            postStatusNotification()
            handler.postDelayed(this, NOTIF_UPDATE_MS)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        createNotificationChannel()
        postStatusNotification()
        handler.postDelayed(notifUpdateRunnable, NOTIF_UPDATE_MS)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        Log.d(TAG, "Service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        handler.removeCallbacks(notifUpdateRunnable)
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        Log.d(TAG, "Service destroyed")
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.notif_channel_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun postStatusNotification() {
        val mode      = prefs.getString("mode", "shabbat_only")
        val candleMs  = prefs.getLong("hebcal_candle_ms",   0)
        val havMs     = prefs.getLong("hebcal_havdalah_ms", 0)
        val now       = System.currentTimeMillis()
        val fmt       = SimpleDateFormat("EEE HH:mm", Locale.getDefault())

        fun fmtHav(ms: Long) = fmt.format(Date((ms + 30_000L) / 60_000L * 60_000L))

        val body = when (mode) {
            "disabled" -> getString(R.string.notif_body_disabled)
            "always"   -> getString(R.string.notif_body_always)
            else -> {
                val isShabbat = candleMs > 0 && havMs > 0 && now in candleMs..havMs
                if (isShabbat) {
                    getString(R.string.notif_body_active, if (havMs > 0) fmtHav(havMs) else "?")
                } else {
                    val starts = if (candleMs > now) fmt.format(Date(candleMs)) else "?"
                    val ends   = if (havMs   > 0)   fmtHav(havMs)              else "?"
                    getString(R.string.notif_body_standby, starts, ends)
                }
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif)
    }

    // ── Accessibility event ───────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        if (!isCellBroadcastPackage(packageName)) return

        Log.d(TAG, "Cell broadcast alert detected from: $packageName")

        val mode = prefs.getString("mode", "shabbat_only")
        if (mode == "disabled") return

        if (mode != "always") {
            val lat = prefs.getFloat("latitude", 31.7683f).toDouble()
            val lon = prefs.getFloat("longitude", 35.2137f).toDouble()
            val candleMinutes = prefs.getInt("candle_lighting_minutes", 18)
            val havdalahMinutes = prefs.getInt("havdalah_minutes", 40)

            val isShabbat = isShabbatNow(lat, lon, candleMinutes, havdalahMinutes)
            val isHoliday = if (mode == "shabbat_holidays") {
                HolidayCalculator.isYomTovToday(candleMinutes, havdalahMinutes, lat, lon)
            } else false

            if (!isShabbat && !isHoliday) {
                Log.d(TAG, "Not Shabbat/holiday right now, ignoring")
                return
            }
        }

        val delaySeconds = prefs.getInt("delay_seconds", 10)
        Log.d(TAG, "Shabbat mode active. Will dismiss in $delaySeconds seconds")

        handler.postDelayed({ dismissAlert() }, delaySeconds * 1000L)
    }

    /**
     * Checks if it is currently Shabbat.
     * Uses Hebcal-synced absolute timestamps when available (accurate),
     * falls back to local NOAA sunset calculation otherwise.
     */
    private fun isShabbatNow(lat: Double, lon: Double, candleMins: Int, havdalahMins: Int): Boolean {
        val candleMs  = prefs.getLong("hebcal_candle_ms",   0)
        val havdalahMs = prefs.getLong("hebcal_havdalah_ms", 0)
        val now = System.currentTimeMillis()

        if (candleMs > 0 && havdalahMs > 0 && havdalahMs > now - 30 * 60_000L) {
            val result = now in candleMs..havdalahMs
            Log.d(TAG, "Hebcal cache hit — isShabbat=$result")
            return result
        }

        Log.d(TAG, "No valid Hebcal cache, using local calculation")
        return ShabbatCalculator(lat, lon).isShabbatNow(candleMins, havdalahMins)
    }

    // ── Alert dismissal ───────────────────────────────────────────────────────

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
                        Log.d(TAG, "Dismissed via button: $buttonText")
                        return
                    }
                }
            }

            if (findAndClickButton(rootNode)) {
                Log.d(TAG, "Dismissed via fallback button search")
                return
            }

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
