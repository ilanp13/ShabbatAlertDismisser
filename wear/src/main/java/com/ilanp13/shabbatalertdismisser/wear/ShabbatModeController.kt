package com.ilanp13.shabbatalertdismisser.wear

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.preference.PreferenceManager
import com.ilanp13.shabbatalertdismisser.shared.HebcalService

/**
 * Manages the Shabbat mode lifecycle:
 * - Schedules activation/deactivation based on synced windows
 * - Enters/exits Lock Task Mode
 * - Activates DND and battery optimizations
 */
class ShabbatModeController(private val context: Context) {

    companion object {
        private const val TAG = "ShabbatModeCtrl"
        const val ACTION_ACTIVATE = "com.ilanp13.shabbatalertdismisser.wear.ACTIVATE_SHABBAT"
        const val ACTION_DEACTIVATE = "com.ilanp13.shabbatalertdismisser.wear.DEACTIVATE_SHABBAT"
        const val PREF_SHABBAT_MODE_ACTIVE = "shabbat_mode_active"
    }

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val batteryOptimizer = BatteryOptimizer(context)

    fun scheduleFromSyncedWindows() {
        val json = prefs.getString(WearDataReceiver.PREF_SCHEDULE_JSON, "[]") ?: "[]"
        val windows = HebcalService.windowsFromJson(json)
        if (windows.isEmpty()) {
            Log.d(TAG, "No windows to schedule")
            return
        }

        val now = System.currentTimeMillis()
        val offsetBefore = prefs.getInt(WearDataReceiver.PREF_OFFSET_BEFORE, 0) * 60_000L
        val offsetAfter = prefs.getInt(WearDataReceiver.PREF_OFFSET_AFTER, 0) * 60_000L

        val nextWindow = windows.find { it.havdalahMs + offsetAfter > now }
        if (nextWindow == null) {
            Log.d(TAG, "All windows are in the past")
            return
        }

        val activateTime = nextWindow.candleMs - offsetBefore
        val deactivateTime = nextWindow.havdalahMs + offsetAfter

        if (now >= activateTime && now < deactivateTime) {
            if (!isShabbatModeActive()) {
                activateShabbatMode()
            }
            scheduleDeactivation(deactivateTime)
        } else if (now < activateTime) {
            scheduleActivation(activateTime)
            scheduleDeactivation(deactivateTime)
        }

        Log.d(TAG, "Scheduled: activate=$activateTime, deactivate=$deactivateTime")
    }

    private fun scheduleActivation(timeMs: Long) {
        val intent = Intent(ACTION_ACTIVATE).setPackage(context.packageName)
        val pi = PendingIntent.getBroadcast(
            context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi)
    }

    private fun scheduleDeactivation(timeMs: Long) {
        val mode = prefs.getString(WearDataReceiver.PREF_ACTIVATION_MODE, "auto")
        if (mode == "auto") {
            val intent = Intent(ACTION_DEACTIVATE).setPackage(context.packageName)
            val pi = PendingIntent.getBroadcast(
                context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMs, pi)
        }
    }

    fun activateShabbatMode() {
        Log.d(TAG, "Activating Shabbat mode")
        prefs.edit().putBoolean(PREF_SHABBAT_MODE_ACTIVE, true).apply()

        batteryOptimizer.applyShabbatSettings()

        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not activate DND: ${e.message}")
        }

        val intent = Intent(context, ShabbatWatchFaceActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
    }

    fun deactivateShabbatMode() {
        Log.d(TAG, "Deactivating Shabbat mode")
        prefs.edit().putBoolean(PREF_SHABBAT_MODE_ACTIVE, false).apply()

        batteryOptimizer.restoreSettings()

        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore DND: ${e.message}")
        }

        context.sendBroadcast(Intent("com.ilanp13.shabbatalertdismisser.wear.STOP_LOCK_TASK").setPackage(context.packageName))

        scheduleFromSyncedWindows()
    }

    fun isShabbatModeActive(): Boolean {
        return prefs.getBoolean(PREF_SHABBAT_MODE_ACTIVE, false)
    }

    fun getCurrentWindowInfo(): Pair<String?, Long>? {
        val json = prefs.getString(WearDataReceiver.PREF_SCHEDULE_JSON, "[]") ?: "[]"
        val windows = HebcalService.windowsFromJson(json)
        val now = System.currentTimeMillis()
        val offsetBefore = prefs.getInt(WearDataReceiver.PREF_OFFSET_BEFORE, 0) * 60_000L
        val offsetAfter = prefs.getInt(WearDataReceiver.PREF_OFFSET_AFTER, 0) * 60_000L

        return windows.find { now in (it.candleMs - offsetBefore)..(it.havdalahMs + offsetAfter) }
            ?.let { Pair(it.parasha, it.havdalahMs) }
    }

    fun getNextWindowInfo(): Triple<Long, Long, String?>? {
        val json = prefs.getString(WearDataReceiver.PREF_SCHEDULE_JSON, "[]") ?: "[]"
        val windows = HebcalService.windowsFromJson(json)
        val now = System.currentTimeMillis()
        val next = windows.find { it.havdalahMs > now } ?: return null
        return Triple(next.candleMs, next.havdalahMs, next.parasha)
    }
}

/**
 * BroadcastReceiver that handles activation/deactivation alarms.
 */
class ShabbatModeAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val controller = ShabbatModeController(context)
        when (intent.action) {
            ShabbatModeController.ACTION_ACTIVATE -> controller.activateShabbatMode()
            ShabbatModeController.ACTION_DEACTIVATE -> controller.deactivateShabbatMode()
            "com.ilanp13.shabbatalertdismisser.wear.SCHEDULE_UPDATED" -> controller.scheduleFromSyncedWindows()
            "com.ilanp13.shabbatalertdismisser.wear.UNLOCK_SHABBAT" -> controller.deactivateShabbatMode()
        }
    }
}
