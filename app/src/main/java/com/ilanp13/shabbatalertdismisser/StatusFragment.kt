package com.ilanp13.shabbatalertdismisser

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatusFragment : Fragment() {

    private lateinit var tvStatus: TextView
    private lateinit var tvShabbatTimes: TextView
    private lateinit var tvSyncStatus: TextView
    private lateinit var tvDismissalCount: TextView
    private lateinit var tvActiveAlerts: TextView
    private lateinit var tvActiveAlertsRegions: TextView
    private lateinit var activeAlertsScrollView: android.widget.ScrollView
    private lateinit var btnRefreshAlerts: Button
    private lateinit var pbAlertsLoading: android.widget.ProgressBar
    private lateinit var tvAlertsUpdatedTime: TextView
    private lateinit var miniMapView: MapView
    private lateinit var miniMapContainer: FrameLayout

    private lateinit var prefs: android.content.SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var alertsPollRunnable: Runnable? = null
    private var timestampUpdateRunnable: Runnable? = null
    private var lastAlertUpdateMs = 0L

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
        tvActiveAlerts = view.findViewById(R.id.tvActiveAlerts)
        tvActiveAlertsRegions = view.findViewById(R.id.tvActiveAlertsRegions)
        activeAlertsScrollView = view.findViewById(R.id.activeAlertsScrollView)
        btnRefreshAlerts = view.findViewById(R.id.btnRefreshAlerts)
        pbAlertsLoading = view.findViewById(R.id.pbAlertsLoading)
        tvAlertsUpdatedTime = view.findViewById(R.id.tvAlertsUpdatedTime)
        miniMapContainer = view.findViewById(R.id.miniMapContainer)
        miniMapView = view.findViewById(R.id.miniMapView)

        // Setup mini map
        setupMiniMap()

        btnRefreshAlerts.setOnClickListener {
            updateActiveAlerts()
        }

        // Tap on mini map container to navigate to full map
        miniMapContainer.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(3)
        }
    }

    private fun setupMiniMap() {
        // Configure osmdroid
        Configuration.getInstance().apply {
            load(requireContext(), PreferenceManager.getDefaultSharedPreferences(requireContext()))
            userAgentValue = requireContext().packageName
        }

        // Setup mini map (non-interactive)
        miniMapView.setTileSource(TileSourceFactory.MAPNIK)
        miniMapView.isEnabled = false
        miniMapView.setMultiTouchControls(false)
        miniMapView.isClickable = false
        miniMapView.isFocusable = false

        // Center on Israel, zoom 7
        val controller = miniMapView.controller
        controller.setZoom(7.0)
        controller.setCenter(GeoPoint(31.5, 35.0))

        // Start hidden
        miniMapContainer.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        miniMapView.onResume()
        updateStatus()
        updateShabbatTimes()
        updateSyncStatus()
        updateDismissalCount()
        startAlertPolling()
    }

    override fun onPause() {
        super.onPause()
        miniMapView.onPause()
        stopAlertPolling()
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

    private fun startAlertPolling() {
        stopAlertPolling()
        alertsPollRunnable = object : Runnable {
            override fun run() {
                updateActiveAlerts()
                handler.postDelayed(this, 30_000) // Poll every 30 seconds
            }
        }
        alertsPollRunnable?.let { handler.post(it) }
        startTimestampUpdate()
    }

    private fun stopAlertPolling() {
        alertsPollRunnable?.let { handler.removeCallbacks(it) }
        alertsPollRunnable = null
        timestampUpdateRunnable?.let { handler.removeCallbacks(it) }
        timestampUpdateRunnable = null
    }

    private fun startTimestampUpdate() {
        timestampUpdateRunnable?.let { handler.removeCallbacks(it) }
        timestampUpdateRunnable = object : Runnable {
            override fun run() {
                if (lastAlertUpdateMs > 0) {
                    updateAlertTimestamp()
                    handler.postDelayed(this, 1000)  // Update every second
                }
            }
        }
        timestampUpdateRunnable?.let { handler.post(it) }
    }

    private fun updateActiveAlerts() {
        // Show loading indicator and disable button
        pbAlertsLoading.visibility = View.VISIBLE
        btnRefreshAlerts.isEnabled = false

        Thread {
            val result = RedAlertService.fetch()
            val selectedRegions = getSelectedRegions()

            val displayAlert = when (result) {
                is RedAlertService.FetchResult.Success -> {
                    val alert = result.alert
                    if (alert != null) {
                        // Save to cache
                        AlertCacheService.save(requireContext(), alert)

                        // Filter alert regions by selected regions
                        if (selectedRegions.isEmpty()) {
                            // All regions selected - show alert
                            alert
                        } else {
                            // Filter to matching regions
                            val filtered = alert.regions.filter { it in selectedRegions }
                            if (filtered.isNotEmpty()) {
                                alert.copy(regions = filtered)
                            } else {
                                null
                            }
                        }
                    } else {
                        null  // No active alerts from API
                    }
                }
                is RedAlertService.FetchResult.Unavailable -> null  // API error
            }

            val isUnavailable = (result is RedAlertService.FetchResult.Unavailable)

            handler.post {
                // Hide loading indicator and re-enable button
                pbAlertsLoading.visibility = View.GONE
                btnRefreshAlerts.isEnabled = true

                // Update timestamp
                lastAlertUpdateMs = System.currentTimeMillis()
                updateAlertTimestamp()

                when {
                    displayAlert != null -> {
                        tvActiveAlerts.text = displayAlert.title
                        tvActiveAlertsRegions.text = displayAlert.regions.joinToString(", ")
                        activeAlertsScrollView.visibility = View.VISIBLE
                        updateMiniMap(displayAlert)
                        miniMapContainer.visibility = View.VISIBLE
                    }
                    isUnavailable -> {
                        // Service unavailable (network error)
                        tvActiveAlerts.text = getString(R.string.active_alerts_unavailable)
                        activeAlertsScrollView.visibility = View.GONE
                        miniMapContainer.visibility = View.GONE
                    }
                    else -> {
                        // No alerts in selected regions (API working, but no active alerts)
                        tvActiveAlerts.text = getString(R.string.active_alerts_none)
                        activeAlertsScrollView.visibility = View.GONE
                        miniMapContainer.visibility = View.GONE
                    }
                }
            }
        }.start()
    }

    private fun updateAlertTimestamp() {
        if (lastAlertUpdateMs > 0) {
            val now = System.currentTimeMillis()
            val diffSec = (now - lastAlertUpdateMs) / 1000
            tvAlertsUpdatedTime.text = "Updated ${if (diffSec == 0L) "just now" else "$diffSec sec ago"}"
            tvAlertsUpdatedTime.visibility = View.VISIBLE
        }
    }

    private fun updateMiniMap(alert: RedAlertService.ActiveAlert) {
        // Clear existing overlays
        miniMapView.overlays.removeAll { it is org.osmdroid.views.overlay.ItemizedIconOverlay<*> }

        // Add red markers for active alert regions
        val items = mutableListOf<org.osmdroid.views.overlay.OverlayItem>()
        for (region in alert.regions) {
            val coords = OrefRegionCoords.coords[region]
            if (coords != null) {
                val point = GeoPoint(coords.first, coords.second)
                val item = org.osmdroid.views.overlay.OverlayItem(region, alert.title, point)
                items.add(item)
            }
        }

        if (items.isNotEmpty()) {
            val overlay = org.osmdroid.views.overlay.ItemizedIconOverlay(
                items,
                object : org.osmdroid.views.overlay.ItemizedIconOverlay.OnItemGestureListener<org.osmdroid.views.overlay.OverlayItem> {
                    override fun onItemSingleTapUp(index: Int, item: org.osmdroid.views.overlay.OverlayItem): Boolean = true
                    override fun onItemLongPress(index: Int, item: org.osmdroid.views.overlay.OverlayItem): Boolean = false
                },
                requireContext()
            )
            miniMapView.overlays.add(overlay)
        }

        miniMapView.invalidate()
    }

    private fun getSelectedRegions(): List<String> {
        val json = prefs.getString("alert_regions_selected", "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            val regions = mutableListOf<String>()
            for (i in 0 until array.length()) {
                regions.add(array.getString(i))
            }
            regions
        } catch (e: Exception) {
            emptyList()
        }
    }
}
