package com.ilanp13.shabbatalertdismisser.wear

import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.*
import org.json.JSONArray

class WearDataReceiver : WearableListenerService() {

    companion object {
        private const val TAG = "WearDataReceiver"
        const val PATH_SCHEDULE = "/shabbat-schedule"
        const val PATH_SETTINGS = "/watch-settings"
        const val PATH_UNLOCK = "/unlock-shabbat-mode"

        const val PREF_SCHEDULE_JSON = "watch_schedule_json"
        const val PREF_FACE_STYLE = "watch_face_style"
        const val PREF_ACTIVATION_MODE = "watch_activation_mode"
        const val PREF_OFFSET_BEFORE = "watch_offset_before_min"
        const val PREF_OFFSET_AFTER = "watch_offset_after_min"
        const val PREF_DISABLE_WIFI = "watch_disable_wifi"
        const val PREF_DISABLE_GPS = "watch_disable_gps"
        const val PREF_DISABLE_TILT_WAKE = "watch_disable_tilt_wake"
        const val PREF_DISABLE_TOUCH_WAKE = "watch_disable_touch_wake"
        const val PREF_DISABLE_LTE = "watch_disable_lte"
        const val PREF_WHITELISTED_PACKAGES = "watch_whitelisted_packages"
        const val PREF_BANNER_TIMEOUT_SEC = "watch_banner_timeout_sec"
        const val PREF_EMERGENCY_SOS = "watch_emergency_sos"
        const val PREF_EMERGENCY_LAST_ALERT = "watch_emergency_last_alert"
        const val PREF_LANGUAGE = "watch_language"
        const val PREF_LONG_PRESS_SECONDS = "watch_long_press_seconds"
        const val PREF_SHOW_SECONDS = "watch_show_seconds"
        const val PREF_DISABLE_HEART_RATE = "watch_disable_heart_rate"
        const val PREF_DISABLE_SPO2 = "watch_disable_spo2"
        const val PREF_DISABLE_STEP_COUNTER = "watch_disable_step_counter"
        const val PREF_DISABLE_BODY_SENSORS = "watch_disable_body_sensors"
        const val PREF_LAST_SYNC_MS = "watch_last_sync_ms"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val path = event.dataItem.uri.path ?: continue
            val data = DataMapItem.fromDataItem(event.dataItem).dataMap

            when (path) {
                PATH_SCHEDULE -> {
                    val json = data.getString("schedule_json", "[]")
                    prefs.edit()
                        .putString(PREF_SCHEDULE_JSON, json)
                        .putLong(PREF_LAST_SYNC_MS, System.currentTimeMillis())
                        .apply()
                    Log.d(TAG, "Schedule synced: ${JSONArray(json).length()} windows")
                    sendBroadcast(Intent("com.ilanp13.shabbatalertdismisser.wear.SCHEDULE_UPDATED").setPackage(packageName))
                }

                PATH_SETTINGS -> {
                    val editor = prefs.edit()
                    editor.putString(PREF_FACE_STYLE, data.getString("face_style", "digital"))
                    editor.putString(PREF_ACTIVATION_MODE, data.getString("activation_mode", "auto"))
                    editor.putInt(PREF_OFFSET_BEFORE, data.getInt("offset_before_min", 0))
                    editor.putInt(PREF_OFFSET_AFTER, data.getInt("offset_after_min", 0))
                    editor.putBoolean(PREF_DISABLE_WIFI, data.getBoolean("disable_wifi", true))
                    editor.putBoolean(PREF_DISABLE_GPS, data.getBoolean("disable_gps", true))
                    editor.putBoolean(PREF_DISABLE_TILT_WAKE, data.getBoolean("disable_tilt_wake", true))
                    editor.putBoolean(PREF_DISABLE_TOUCH_WAKE, data.getBoolean("disable_touch_wake", false))
                    editor.putBoolean(PREF_DISABLE_LTE, data.getBoolean("disable_lte", false))
                    editor.putBoolean(PREF_DISABLE_HEART_RATE, data.getBoolean("disable_heart_rate", true))
                    editor.putBoolean(PREF_DISABLE_SPO2, data.getBoolean("disable_spo2", true))
                    editor.putBoolean(PREF_DISABLE_STEP_COUNTER, data.getBoolean("disable_step_counter", true))
                    editor.putBoolean(PREF_DISABLE_BODY_SENSORS, data.getBoolean("disable_body_sensors", true))
                    editor.putString(PREF_WHITELISTED_PACKAGES, data.getString("whitelisted_packages", "[]"))
                    editor.putInt(PREF_BANNER_TIMEOUT_SEC, data.getInt("banner_timeout_sec", 30))
                    editor.putBoolean(PREF_EMERGENCY_SOS, data.getBoolean("emergency_sos", true))
                    editor.putBoolean(PREF_EMERGENCY_LAST_ALERT, data.getBoolean("emergency_last_alert", true))
                    editor.putString(PREF_LANGUAGE, data.getString("language", "iw"))
                    editor.putInt(PREF_LONG_PRESS_SECONDS, data.getInt("long_press_seconds", 10))
                    editor.putBoolean(PREF_SHOW_SECONDS, data.getBoolean("show_seconds", true))
                    editor.apply()
                    Log.d(TAG, "Settings synced")

                    // If "always" mode, immediately activate Shabbat mode
                    val mode = data.getString("activation_mode", "auto")
                    if (mode == "always") {
                        val controller = ShabbatModeController(this)
                        if (!controller.isShabbatModeActive()) {
                            controller.activateShabbatMode()
                            Log.d(TAG, "Always-on mode: activated Shabbat mode on sync")
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            PATH_UNLOCK -> {
                Log.d(TAG, "Unlock command received from phone")
                sendBroadcast(Intent("com.ilanp13.shabbatalertdismisser.wear.UNLOCK_SHABBAT").setPackage(packageName))
            }
        }
    }
}
