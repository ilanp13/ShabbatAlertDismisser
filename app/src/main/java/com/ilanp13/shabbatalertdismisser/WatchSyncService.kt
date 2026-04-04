package com.ilanp13.shabbatalertdismisser

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.*

/**
 * Syncs Shabbat schedule and watch settings to the connected Wear OS watch.
 */
object WatchSyncService {

    private const val TAG = "WatchSyncService"
    private const val PATH_SCHEDULE = "/shabbat-schedule"
    private const val PATH_SETTINGS = "/watch-settings"
    private const val PATH_UNLOCK = "/unlock-shabbat-mode"
    private const val CAPABILITY_WATCH_APP = "shabbat_watch_app"

    /**
     * Check if a watch with the Shabbat app is connected.
     */
    fun checkWatchConnected(context: Context, callback: (Boolean) -> Unit) {
        Wearable.getCapabilityClient(context)
            .getCapability(CAPABILITY_WATCH_APP, CapabilityClient.FILTER_REACHABLE)
            .addOnSuccessListener { capabilityInfo ->
                callback(capabilityInfo.nodes.isNotEmpty())
            }
            .addOnFailureListener {
                Log.w(TAG, "Failed to check watch capability: ${it.message}")
                callback(false)
            }
    }

    /**
     * Sync the Shabbat schedule to the watch.
     * Call after Hebcal refresh or settings change.
     */
    fun syncSchedule(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val windowsJson = prefs.getString("hebcal_windows_json", "[]") ?: "[]"

        val dataMap = PutDataMapRequest.create(PATH_SCHEDULE).apply {
            dataMap.putString("schedule_json", windowsJson)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(dataMap)
            .addOnSuccessListener { Log.d(TAG, "Schedule synced to watch") }
            .addOnFailureListener { Log.w(TAG, "Schedule sync failed: ${it.message}") }
    }

    /**
     * Sync all watch-specific settings to the watch.
     * Call when any watch setting changes.
     */
    fun syncSettings(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val dataMap = PutDataMapRequest.create(PATH_SETTINGS).apply {
            dataMap.putString("face_style", prefs.getString("watch_face_style", "digital") ?: "digital")
            dataMap.putString("activation_mode", prefs.getString("watch_activation_mode", "auto") ?: "auto")
            dataMap.putInt("offset_before_min", prefs.getInt("watch_offset_before_min", 0))
            dataMap.putInt("offset_after_min", prefs.getInt("watch_offset_after_min", 0))
            dataMap.putBoolean("disable_wifi", prefs.getBoolean("watch_disable_wifi", true))
            dataMap.putBoolean("disable_gps", prefs.getBoolean("watch_disable_gps", true))
            dataMap.putBoolean("disable_tilt_wake", prefs.getBoolean("watch_disable_tilt_wake", true))
            dataMap.putBoolean("disable_touch_wake", prefs.getBoolean("watch_disable_touch_wake", false))
            dataMap.putBoolean("disable_lte", prefs.getBoolean("watch_disable_lte", false))
            dataMap.putBoolean("disable_heart_rate", prefs.getBoolean("watch_disable_heart_rate", true))
            dataMap.putBoolean("disable_spo2", prefs.getBoolean("watch_disable_spo2", true))
            dataMap.putBoolean("disable_step_counter", prefs.getBoolean("watch_disable_step_counter", true))
            dataMap.putBoolean("disable_body_sensors", prefs.getBoolean("watch_disable_body_sensors", true))
            dataMap.putString("whitelisted_packages", prefs.getString("watch_whitelisted_packages", "[]") ?: "[]")
            dataMap.putInt("banner_timeout_sec", prefs.getInt("watch_banner_timeout_sec", 30))
            dataMap.putInt("long_press_seconds", prefs.getInt("watch_long_press_seconds", 10))
            dataMap.putBoolean("emergency_sos", prefs.getBoolean("watch_emergency_sos", true))
            dataMap.putBoolean("emergency_last_alert", prefs.getBoolean("watch_emergency_last_alert", true))
            val lang = prefs.getString("app_language", "iw") ?: "iw"
            dataMap.putString("language", if (lang == "he") "iw" else lang)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        Wearable.getDataClient(context).putDataItem(dataMap)
            .addOnSuccessListener { Log.d(TAG, "Settings synced to watch") }
            .addOnFailureListener { Log.w(TAG, "Settings sync failed: ${it.message}") }
    }

    /**
     * Send manual unlock command to the watch.
     */
    fun sendUnlockCommand(context: Context) {
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes ->
                for (node in nodes) {
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, PATH_UNLOCK, byteArrayOf())
                        .addOnSuccessListener { Log.d(TAG, "Unlock sent to ${node.displayName}") }
                }
            }
    }

    /**
     * Sync both schedule and settings. Call on app launch or after Hebcal refresh.
     */
    fun syncAll(context: Context) {
        syncSchedule(context)
        syncSettings(context)
    }
}
