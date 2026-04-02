package com.ilanp13.shabbatalertdismisser.wear

import android.content.Context
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * Applies and restores battery-saving settings during Shabbat mode.
 * Stores previous state so it can be restored on deactivation.
 */
class BatteryOptimizer(private val context: Context) {

    companion object {
        private const val TAG = "BatteryOptimizer"
        private const val PREF_PREV_WIFI = "prev_wifi_enabled"
        private const val PREF_PREV_TILT_WAKE = "prev_tilt_to_wake"
        private const val PREF_PREV_TOUCH_WAKE = "prev_touch_to_wake"
    }

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    fun applyShabbatSettings() {
        val editor = prefs.edit()

        // Wi-Fi
        if (prefs.getBoolean(WearDataReceiver.PREF_DISABLE_WIFI, true)) {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            editor.putBoolean(PREF_PREV_WIFI, wifiManager.isWifiEnabled)
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = false
            Log.d(TAG, "Wi-Fi disabled")
        }

        // Tilt-to-wake
        if (prefs.getBoolean(WearDataReceiver.PREF_DISABLE_TILT_WAKE, true)) {
            try {
                val prev = Settings.Global.getInt(
                    context.contentResolver, "tilt_to_wake", 1
                )
                editor.putInt(PREF_PREV_TILT_WAKE, prev)
                Settings.Global.putInt(context.contentResolver, "tilt_to_wake", 0)
                Log.d(TAG, "Tilt-to-wake disabled")
            } catch (e: Exception) {
                Log.w(TAG, "Could not disable tilt-to-wake: ${e.message}")
            }
        }

        // Touch-to-wake
        if (prefs.getBoolean(WearDataReceiver.PREF_DISABLE_TOUCH_WAKE, false)) {
            try {
                val prev = Settings.Global.getInt(
                    context.contentResolver, "touch_to_wake", 1
                )
                editor.putInt(PREF_PREV_TOUCH_WAKE, prev)
                Settings.Global.putInt(context.contentResolver, "touch_to_wake", 0)
                Log.d(TAG, "Touch-to-wake disabled")
            } catch (e: Exception) {
                Log.w(TAG, "Could not disable touch-to-wake: ${e.message}")
            }
        }

        editor.apply()
    }

    fun restoreSettings() {
        // Wi-Fi
        if (prefs.getBoolean(WearDataReceiver.PREF_DISABLE_WIFI, true)) {
            val prev = prefs.getBoolean(PREF_PREV_WIFI, true)
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = prev
            Log.d(TAG, "Wi-Fi restored to $prev")
        }

        // Tilt-to-wake
        if (prefs.getBoolean(WearDataReceiver.PREF_DISABLE_TILT_WAKE, true)) {
            try {
                val prev = prefs.getInt(PREF_PREV_TILT_WAKE, 1)
                Settings.Global.putInt(context.contentResolver, "tilt_to_wake", prev)
                Log.d(TAG, "Tilt-to-wake restored to $prev")
            } catch (e: Exception) {
                Log.w(TAG, "Could not restore tilt-to-wake: ${e.message}")
            }
        }

        // Touch-to-wake
        if (prefs.getBoolean(WearDataReceiver.PREF_DISABLE_TOUCH_WAKE, false)) {
            try {
                val prev = prefs.getInt(PREF_PREV_TOUCH_WAKE, 1)
                Settings.Global.putInt(context.contentResolver, "touch_to_wake", prev)
                Log.d(TAG, "Touch-to-wake restored to $prev")
            } catch (e: Exception) {
                Log.w(TAG, "Could not restore touch-to-wake: ${e.message}")
            }
        }
    }
}
