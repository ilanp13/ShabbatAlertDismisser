package com.ilanp13.shabbatalertdismisser.wear

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
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
        private const val PREF_PREV_BODY_SENSORS = "prev_body_sensors_enabled"
        private const val PREF_PREV_BRIGHTNESS = "prev_screen_brightness"
        private const val PREF_PREV_BRIGHTNESS_MODE = "prev_screen_brightness_mode"
        private const val PREF_PREV_AOD = "prev_aod_mode"
        private const val SHABBAT_BRIGHTNESS = 10 // Very low (0-255 scale)
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

        // Health sensors — best-effort disable via permission revocation
        val anyHealthDisabled = prefs.getBoolean(WearDataReceiver.PREF_DISABLE_HEART_RATE, true) ||
            prefs.getBoolean(WearDataReceiver.PREF_DISABLE_SPO2, true) ||
            prefs.getBoolean(WearDataReceiver.PREF_DISABLE_STEP_COUNTER, true) ||
            prefs.getBoolean(WearDataReceiver.PREF_DISABLE_BODY_SENSORS, true)
        if (anyHealthDisabled) {
            editor.putBoolean(PREF_PREV_BODY_SENSORS, true)
            setSensorPermission(false)
            Log.d(TAG, "Health sensors disabled")
        }

        // Ensure Always-On Display is enabled (keeps screen on in ambient mode)
        try {
            val prevAod = Settings.Global.getInt(context.contentResolver, "aod_mode", 0)
            editor.putInt(PREF_PREV_AOD, prevAod)
            Settings.Global.putInt(context.contentResolver, "aod_mode", 1)
            Log.d(TAG, "AOD enabled for Shabbat mode")
        } catch (e: Exception) {
            Log.w(TAG, "Could not enable AOD: ${e.message}")
        }

        editor.apply()
    }

    private fun setSensorPermission(granted: Boolean) {
        if (!AdminReceiver.isDeviceOwner(context)) {
            Log.w(TAG, "Not device owner — cannot manage sensor permissions")
            return
        }
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = AdminReceiver.getComponentName(context)
        val grantState = if (granted)
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
        else
            DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
        try {
            dpm.setPermissionGrantState(
                admin, context.packageName,
                android.Manifest.permission.BODY_SENSORS, grantState
            )
            Log.d(TAG, "BODY_SENSORS permission ${if (granted) "granted" else "denied"}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set BODY_SENSORS permission: ${e.message}")
        }
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

        // Health sensors
        if (prefs.getBoolean(PREF_PREV_BODY_SENSORS, false)) {
            setSensorPermission(true)
            Log.d(TAG, "Health sensors restored")
        }

        // AOD
        try {
            val prevAod = prefs.getInt(PREF_PREV_AOD, 1)
            Settings.Global.putInt(context.contentResolver, "aod_mode", prevAod)
            Log.d(TAG, "AOD restored to $prevAod")
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore AOD: ${e.message}")
        }
    }
}
