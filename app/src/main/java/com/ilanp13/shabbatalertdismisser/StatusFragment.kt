package com.ilanp13.shabbatalertdismisser

import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatusFragment : Fragment() {

    private lateinit var tvStatus: TextView
    private lateinit var tvShabbatTimes: TextView
    private lateinit var tvSyncStatus: TextView
    private lateinit var tvDismissalCount: TextView

    private lateinit var prefs: android.content.SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_status, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        tvStatus = view.findViewById(R.id.tvStatus)
        tvShabbatTimes = view.findViewById(R.id.tvShabbatTimes)
        tvSyncStatus = view.findViewById(R.id.tvSyncStatus)
        tvDismissalCount = view.findViewById(R.id.tvDismissalCount)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateShabbatTimes()
        updateSyncStatus()
        updateDismissalCount()
    }

    private fun updateStatus() {
        val mode = prefs.getString("mode", "shabbat_only")
        val base = when {
            !isAccessibilityServiceEnabled() -> getString(R.string.status_no_accessibility)
            mode == "disabled" -> getString(R.string.status_disabled)
            mode == "always" -> getString(R.string.status_always)
            mode == "shabbat_holidays" -> getString(R.string.status_shabbat_holidays)
            else -> getString(R.string.status_shabbat_only)
        }
        val screenOnMode = prefs.getString("screen_on_mode", "off")
        val screenOnLine = when {
            !isAccessibilityServiceEnabled() || mode == "disabled" -> null
            screenOnMode == "shabbat" -> getString(R.string.status_screen_on_shabbat)
            screenOnMode == "always" -> getString(R.string.status_screen_on_always)
            else -> null
        }
        tvStatus.text = if (screenOnLine != null) "$base\n$screenOnLine" else base
    }

    private fun updateShabbatTimes() {
        val candleMs = prefs.getLong("hebcal_candle_ms", 0)
        val havdalahMs = prefs.getLong("hebcal_havdalah_ms", 0)
        val now = System.currentTimeMillis()

        if (candleMs > 0 && havdalahMs > now - 7 * 86_400_000L) {
            val fmt = SimpleDateFormat("EEEE HH:mm", Locale.getDefault())
            val havdalahDisplayMs = ((havdalahMs + 30_000L) / 60_000L) * 60_000L
            tvShabbatTimes.text = getString(R.string.shabbat_times_format,
                fmt.format(Date(candleMs)),
                fmt.format(Date(havdalahDisplayMs)))
            return
        }

        val lat = prefs.getFloat("latitude", 31.7683f).toDouble()
        val lon = prefs.getFloat("longitude", 35.2137f).toDouble()
        val candle = prefs.getInt("candle_lighting_minutes", 18)
        val havdala = prefs.getInt("havdalah_minutes", 40)
        val times = ShabbatCalculator(lat, lon).getShabbatTimes(candle, havdala)
        if (times != null) {
            val fmt = SimpleDateFormat("EEEE HH:mm", Locale.getDefault())
            val havdalahMs = ((times.second.time.time + 30_000L) / 60_000L) * 60_000L
            tvShabbatTimes.text = getString(R.string.shabbat_times_format,
                fmt.format(times.first.time),
                fmt.format(Date(havdalahMs)))
        } else {
            tvShabbatTimes.text = getString(R.string.shabbat_times_unavailable)
        }
    }

    private fun updateSyncStatus() {
        val havdalahMs = prefs.getLong("hebcal_havdalah_ms", 0)
        tvSyncStatus.text = if (havdalahMs > System.currentTimeMillis() - 7 * 86_400_000L) {
            getString(R.string.sync_status_synced)
        } else {
            getString(R.string.sync_status_offline)
        }
    }

    private fun updateDismissalCount() {
        try {
            val historyJson = prefs.getString("dismiss_history", "[]") ?: "[]"
            val array = JSONArray(historyJson)
            val count = array.length()
            tvDismissalCount.text = if (count == 0) {
                getString(R.string.history_no_dismissals)
            } else {
                getString(R.string.history_summary_format, count)
            }
        } catch (e: Exception) {
            tvDismissalCount.text = getString(R.string.history_no_dismissals)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val cn = android.content.ComponentName(requireContext(), AlertDismissService::class.java)
        val enabled = Settings.Secure.getString(
            requireContext().contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(cn.flattenToString())
    }
}
