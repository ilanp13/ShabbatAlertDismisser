package com.ilanp13.shabbatalertdismisser

import com.ilanp13.shabbatalertdismisser.shared.HebcalService
import com.ilanp13.shabbatalertdismisser.shared.MinhagProfiles
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager

/**
 * Triggers a background Hebcal re-sync after device reboot so that
 * Shabbat times are up-to-date before the accessibility service needs them.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d("BootReceiver", "Boot completed — refreshing Hebcal times")

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val lat   = prefs.getFloat("latitude",  31.7683f).toDouble()
        val lon   = prefs.getFloat("longitude", 35.2137f).toDouble()
        val profile = MinhagProfiles.byKey(prefs.getString("minhag_key", "ashkenaz") ?: "ashkenaz")
        val useRt   = prefs.getBoolean("use_rabenu_tam", false)
        val havMins = if (useRt) profile.rtMins else profile.graMins

        Thread {
            val result = HebcalService.fetch(lat, lon, profile.candleMins, havMins)
            if (result != null) {
                val now = System.currentTimeMillis()
                val next = result.nextWindow(now) ?: result.windows.lastOrNull()
                val editor = prefs.edit()
                    .putString("hebcal_windows_json", HebcalService.windowsToJson(result.windows))
                if (next != null) {
                    editor.putLong("hebcal_candle_ms", next.candleMs)
                    editor.putLong("hebcal_havdalah_ms", next.havdalahMs)
                }
                editor
                    .putLong("hebcal_cache_timestamp_ms", now)
                if (!result.parasha.isNullOrEmpty()) {
                    editor.putString("hebcal_parasha", result.parasha)
                }
                editor.apply()
                WatchSyncService.syncSchedule(context)
                Log.d("BootReceiver", "Hebcal times refreshed: ${result.windows.size} windows")
            }
        }.start()
    }
}
