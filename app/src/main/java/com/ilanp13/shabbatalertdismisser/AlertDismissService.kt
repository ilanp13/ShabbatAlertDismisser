package com.ilanp13.shabbatalertdismisser

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
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

    // Update notification / screen state whenever relevant prefs change
    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in listOf("mode", "hebcal_candle_ms", "hebcal_havdalah_ms", "show_notification")) {
            postStatusNotification()
        }
        if (key in listOf("mode", "screen_on_mode", "hebcal_candle_ms", "hebcal_havdalah_ms")) {
            updateScreenOn()
        }
    }

    private val notifUpdateRunnable = object : Runnable {
        override fun run() {
            postStatusNotification()
            updateScreenOn()
            handler.postDelayed(this, NOTIF_UPDATE_MS)
        }
    }

    private var screenOnView: View? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        createNotificationChannel()
        postStatusNotification()
        updateScreenOn()
        handler.postDelayed(notifUpdateRunnable, NOTIF_UPDATE_MS)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        Log.d(TAG, "Service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        handler.removeCallbacks(notifUpdateRunnable)
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        removeScreenOnOverlay()
        Log.d(TAG, "Service destroyed")
    }

    // ── Screen-on overlay ─────────────────────────────────────────────────────

    /**
     * Keeps the screen awake using a zero-size TYPE_ACCESSIBILITY_OVERLAY window
     * with FLAG_KEEP_SCREEN_ON — no extra permissions required for accessibility services.
     */
    private fun updateScreenOn() {
        val modeDisabled = prefs.getString("mode", "shabbat_only") == "disabled"
        val wantScreenOn = !modeDisabled && when (prefs.getString("screen_on_mode", "off")) {
            "always"  -> true
            "shabbat" -> isShabbatOrHolidayNow()
            else      -> false
        }
        if (wantScreenOn && screenOnView == null) {
            try {
                val params = WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                )
                val view = View(this)
                getSystemService(WindowManager::class.java).addView(view, params)
                screenOnView = view
                Log.d(TAG, "Screen-on overlay added")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add screen-on overlay: ${e.message}")
            }
        } else if (!wantScreenOn) {
            removeScreenOnOverlay()
        }
    }

    private fun removeScreenOnOverlay() {
        screenOnView?.let {
            try {
                getSystemService(WindowManager::class.java).removeView(it)
                Log.d(TAG, "Screen-on overlay removed")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to remove screen-on overlay: ${e.message}")
            }
            screenOnView = null
        }
    }

    /** Returns true if auto-dismiss is currently active (respects mode setting). */
    private fun isActiveNow(): Boolean {
        val mode = prefs.getString("mode", "shabbat_only")
        if (mode == "disabled") return false
        if (mode == "always")   return true
        return isShabbatOrHolidayNow()
    }

    /**
     * Returns true if it is currently Shabbat or a holiday — always based on
     * actual halachic times, regardless of the dismiss mode setting.
     * Used for screen-on so it never stays on outside Shabbat.
     */
    private fun isShabbatOrHolidayNow(): Boolean {
        val lat          = prefs.getFloat("latitude",  31.7683f).toDouble()
        val lon          = prefs.getFloat("longitude", 35.2137f).toDouble()
        val candleMins   = prefs.getInt("candle_lighting_minutes", 18)
        val havdalahMins = prefs.getInt("havdalah_minutes", 40)
        return isShabbatNow(lat, lon, candleMins, havdalahMins) ||
               HolidayCalculator.isYomTovToday(candleMins, havdalahMins, lat, lon)
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
        if (!prefs.getBoolean("show_notification", true)) {
            getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
            return
        }

        val mode      = prefs.getString("mode", "shabbat_only")
        var candleMs  = prefs.getLong("hebcal_candle_ms",   0)
        var havMs     = prefs.getLong("hebcal_havdalah_ms", 0)
        val now       = System.currentTimeMillis()
        val fmt       = SimpleDateFormat("EEE HH:mm", Locale.getDefault())

        fun fmtHav(ms: Long) = fmt.format(Date((ms + 30_000L) / 60_000L * 60_000L))

        // Detect stale cache: if all windows are past or cache is older than 7 days
        val cacheTimestampMs = prefs.getLong("hebcal_cache_timestamp_ms", 0)
        val windows = HebcalService.windowsFromJson(prefs.getString("hebcal_windows_json", null))
        val allWindowsPast = if (windows.isNotEmpty())
            windows.all { it.havdalahMs < now } else (havMs > 0 && now > havMs)
        val isCacheStale = allWindowsPast ||
                          (cacheTimestampMs > 0 && now - cacheTimestampMs > 7 * 86_400_000L)

        if (isCacheStale) {
            // Refetch from Hebcal to get next Shabbat's times
            val lat = prefs.getFloat("latitude", 31.7683f).toDouble()
            val lon = prefs.getFloat("longitude", 35.2137f).toDouble()
            val candleMins = prefs.getInt("candle_lighting_minutes", 18)
            val havdalahMins = prefs.getInt("havdalah_minutes", 40)

            val result = HebcalService.fetch(lat, lon, candleMins, havdalahMins)
            if (result != null) {
                val next = result.nextWindow(now)
                candleMs = next?.candleMs ?: 0L
                havMs = next?.havdalahMs ?: 0L
                prefs.edit()
                    .putString("hebcal_windows_json", HebcalService.windowsToJson(result.windows))
                    .putLong("hebcal_candle_ms", candleMs)
                    .putLong("hebcal_havdalah_ms", havMs)
                    .putLong("hebcal_cache_timestamp_ms", now)
                    .apply()
            }
        }

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
        val now = System.currentTimeMillis()

        // Check multi-window cache first (handles holidays + Shabbat correctly)
        val windowsJson = prefs.getString("hebcal_windows_json", null)
        val windows = HebcalService.windowsFromJson(windowsJson)
        if (windows.isNotEmpty()) {
            val result = HebcalService.isInAnyWindow(windows, now)
            Log.d(TAG, "Hebcal windows (${windows.size}) — isShabbat=$result")
            return result
        }

        // Fallback to single-window cache (backward compat)
        val candleMs = prefs.getLong("hebcal_candle_ms", 0)
        val havdalahMs = prefs.getLong("hebcal_havdalah_ms", 0)
        if (candleMs > 0 && havdalahMs > 0 && havdalahMs > now - 30 * 60_000L) {
            val result = now in candleMs..havdalahMs
            Log.d(TAG, "Hebcal single window — isShabbat=$result")
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

        val packageName = rootNode.packageName?.toString() ?: "unknown"

        // Re-check that the current window is still a cell broadcast alert.
        // If the user already dismissed it manually, the window may have changed
        // and we should not click on whatever is now on screen.
        if (!isCellBroadcastPackage(packageName)) {
            Log.d(TAG, "Window changed to $packageName (not cell broadcast), skipping dismiss")
            rootNode.recycle()
            return
        }

        val windowText = captureWindowText(rootNode)

        try {
            for (buttonText in DISMISS_BUTTON_TEXTS) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(buttonText)
                for (node in nodes) {
                    if (tryClick(node)) {
                        Log.d(TAG, "Dismissed via button: $buttonText")
                        saveHistoryRecord(packageName, buttonText, windowText)
                        return
                    }
                }
            }

            if (findAndClickButton(rootNode)) {
                Log.d(TAG, "Dismissed via fallback button search")
                saveHistoryRecord(packageName, "fallback", windowText)
                return
            }

            Log.d(TAG, "No button found, trying BACK action")
            performGlobalAction(GLOBAL_ACTION_BACK)
            saveHistoryRecord(packageName, "back", windowText)

        } finally {
            rootNode.recycle()
        }
    }

    private fun captureWindowText(node: AccessibilityNodeInfo): String {
        val texts = mutableListOf<String>()
        collectText(node, texts)
        return texts.joinToString(" ").trim()
    }

    private fun collectText(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.let { if (it.isNotBlank()) texts.add(it.toString()) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, texts)
        }
    }

    private fun saveHistoryRecord(packageName: String, buttonText: String, windowText: String) {
        try {
            val historyJson = prefs.getString("dismiss_history", "[]") ?: "[]"
            val array = JSONArray(historyJson)

            val record = JSONObject().apply {
                put("timestampMs", System.currentTimeMillis())
                put("packageName", packageName)
                put("buttonText", buttonText)
                put("windowText", windowText)
            }

            array.put(record)

            // Keep only last 200 records
            while (array.length() > 200) {
                array.remove(0)
            }

            prefs.edit().putString("dismiss_history", array.toString()).apply()
            Log.d(TAG, "History record saved, total: ${array.length()}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save history record: ${e.message}")
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
