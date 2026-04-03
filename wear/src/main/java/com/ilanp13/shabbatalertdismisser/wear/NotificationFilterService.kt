package com.ilanp13.shabbatalertdismisser.wear

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONArray

class NotificationFilterService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifFilter"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val isShabbatMode = prefs.getBoolean(ShabbatModeController.PREF_SHABBAT_MODE_ACTIVE, false)
        if (!isShabbatMode) return

        val packageName = sbn.packageName
        val whitelistJson = prefs.getString(WearDataReceiver.PREF_WHITELISTED_PACKAGES, "[]") ?: "[]"
        val whitelist = try {
            val arr = JSONArray(whitelistJson)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }

        if (packageName in whitelist) {
            val text = sbn.notification.extras.getString("android.text")
                ?: sbn.notification.extras.getString("android.title")
                ?: "Alert"
            Log.d(TAG, "Whitelisted notification from $packageName: $text")

            val intent = android.content.Intent(
                "com.ilanp13.shabbatalertdismisser.wear.ALERT_NOTIFICATION"
            ).setPackage(getPackageName()).putExtra("alert_text", text)
            sendBroadcast(intent)
        } else {
            cancelNotification(sbn.key)
        }
    }
}
