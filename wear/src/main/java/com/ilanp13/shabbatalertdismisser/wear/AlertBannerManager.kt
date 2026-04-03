package com.ilanp13.shabbatalertdismisser.wear

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AlertBannerManager(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val handler = Handler(Looper.getMainLooper())

    private val _alertText = MutableStateFlow<String?>(null)
    val alertText: StateFlow<String?> = _alertText

    fun showAlert(text: String) {
        _alertText.value = text
        val timeoutSec = prefs.getInt(WearDataReceiver.PREF_BANNER_TIMEOUT_SEC, 30)
        if (timeoutSec > 0) {
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ _alertText.value = null }, timeoutSec * 1000L)
        }
    }

    fun dismiss() {
        handler.removeCallbacksAndMessages(null)
        _alertText.value = null
    }

    var lastAlertText: String? = null
        private set

    fun onAlertReceived(text: String) {
        lastAlertText = text
        showAlert(text)
    }
}
