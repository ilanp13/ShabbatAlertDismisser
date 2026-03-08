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
    private lateinit var tvShabbatTimesTitle: TextView
    private lateinit var tvShabbatTimes: TextView
    private lateinit var tvSyncStatus: TextView
    private lateinit var tvShabbatParasha: TextView
    private lateinit var tvDismissalCount: TextView
    private lateinit var tvActiveAlerts: TextView
    private lateinit var tvActiveAlertsRegions: TextView
    private lateinit var activeAlertsScrollView: android.widget.ScrollView
    private lateinit var btnRefreshAlerts: Button
    private lateinit var pbAlertsLoading: android.widget.ProgressBar
    private lateinit var tvAlertsUpdatedTime: TextView
    private lateinit var miniMapView: MapView
    private lateinit var miniMapContainer: android.widget.LinearLayout
    private lateinit var btnClearRefreshAlerts: Button

    private lateinit var prefs: android.content.SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var alertsPollRunnable: Runnable? = null
    private var timestampUpdateRunnable: Runnable? = null
    private var lastAlertUpdateMs = 0L
    private var cachedAlertsList = listOf<AlertCacheService.CachedAlert>()
    private var currentCachedAlertIndex = 0
    private var autoCycleRunnable: Runnable? = null
    private var autoCycleDurationMs = 5000L  // Default 5 seconds

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
        tvShabbatTimesTitle = view.findViewById(R.id.tvShabbatTimesTitle)
        tvShabbatTimes = view.findViewById(R.id.tvShabbatTimes)
        tvSyncStatus = view.findViewById(R.id.tvSyncStatus)
        tvShabbatParasha = view.findViewById(R.id.tvShabbatParasha)
        tvDismissalCount = view.findViewById(R.id.tvDismissalCount)
        tvActiveAlerts = view.findViewById(R.id.tvActiveAlerts)
        tvActiveAlertsRegions = view.findViewById(R.id.tvActiveAlertsRegions)
        activeAlertsScrollView = view.findViewById(R.id.activeAlertsScrollView)
        btnRefreshAlerts = view.findViewById(R.id.btnRefreshAlerts)
        pbAlertsLoading = view.findViewById(R.id.pbAlertsLoading)
        tvAlertsUpdatedTime = view.findViewById(R.id.tvAlertsUpdatedTime)
        miniMapContainer = view.findViewById(R.id.miniMapContainer)
        miniMapView = view.findViewById(R.id.miniMapView)
        btnClearRefreshAlerts = view.findViewById(R.id.btnClearRefreshAlerts)

        // Initialize demo alerts if cache is empty (for development/testing)
        initializeDemoAlerts()

        // Setup mini map
        setupMiniMap()

        btnRefreshAlerts.setOnClickListener {
            updateActiveAlerts()
        }

        btnClearRefreshAlerts.setOnClickListener {
            clearAndRefreshAlerts()
        }

        // Previous/Next buttons for cycling through cached alerts
        val btnPrevAlert = view.findViewById<Button>(R.id.btnPrevAlert)
        val btnNextAlert = view.findViewById<Button>(R.id.btnNextAlert)

        btnPrevAlert.setOnClickListener {
            cyclePreviousAlert()
        }

        btnNextAlert.setOnClickListener {
            cycleNextAlert()
        }

        // Tap on mini map container to navigate to full map
        miniMapContainer.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(3)
        }
    }

    private fun setupMiniMap() {
        // Configure osmdroid
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        Configuration.getInstance().apply {
            load(requireContext(), prefs)
            userAgentValue = requireContext().packageName
            // Ensure cache directory exists
            osmdroidBasePath = requireContext().cacheDir
        }

        // Setup mini map (non-interactive, focused on Israel)
        miniMapView.setTileSource(TileSourceFactory.MAPNIK)
        miniMapView.isEnabled = false
        miniMapView.setMultiTouchControls(false)
        miniMapView.isClickable = false
        miniMapView.isFocusable = false
        miniMapView.setDestroyMode(false)  // Prevent destroying on detach

        // Center on Israel with optimal zoom level
        // Zoom 9 shows entire Israel with good detail
        val controller = miniMapView.controller
        controller.setZoom(9.0)
        controller.setCenter(GeoPoint(31.25, 35.05))

        // Add current location marker (blue dot)
        addCurrentLocationMarker()

        // Start hidden
        miniMapContainer.visibility = View.GONE
    }

    private fun addCurrentLocationMarker() {
        try {
            val lat = prefs.getFloat("latitude", 31.7683f).toDouble()
            val lon = prefs.getFloat("longitude", 35.2137f).toDouble()

            // Clear existing location markers
            miniMapView.overlays.removeAll { it is org.osmdroid.views.overlay.Marker }

            // Add blue dot for current location
            val locationMarker = org.osmdroid.views.overlay.Marker(miniMapView)
            locationMarker.position = GeoPoint(lat, lon)
            locationMarker.title = "Current Location"
            locationMarker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)

            // Create a blue circle drawable for current location
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLUE
                style = android.graphics.Paint.Style.FILL
                isAntiAlias = true
            }
            val bitmap = android.graphics.Bitmap.createBitmap(20, 20, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawCircle(10f, 10f, 8f, paint)

            locationMarker.icon = android.graphics.drawable.BitmapDrawable(resources, bitmap)
            miniMapView.overlays.add(locationMarker)
            miniMapView.invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        miniMapView.onResume()
        updateStatus()
        updateShabbatTimes()
        updateSyncStatus()
        updateDismissalCount()
        loadCyclerSettings()
        startAlertPolling()
        if (cachedAlertsList.isNotEmpty() && shouldStartCycler()) {
            startAutoCycle()
        }
    }

    override fun onPause() {
        super.onPause()
        miniMapView.onPause()
        stopAlertPolling()
        stopAutoCycle()
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
            // Set title based on whether we're in Shabbat
            val isInShabbat = now in candleMs..havdalahMs
            tvShabbatTimesTitle.text = if (isInShabbat) {
                getString(R.string.shabbat_times_current)
            } else {
                getString(R.string.shabbat_times_next)
            }
            updateParashaDisplay()
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
        tvShabbatTimesTitle.text = getString(R.string.shabbat_times_next)
        updateParashaDisplay()
    }

    private fun updateParashaDisplay() {
        val parasha = prefs.getString("hebcal_parasha", null)
        tvShabbatParasha.text = if (parasha.isNullOrEmpty()) {
            getString(R.string.parasha_unavailable)
        } else {
            parasha
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
                        // Active alerts found - stop cycling and show them
                        stopAutoCycle()
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
                        // Show map with cached alerts if available
                        val cachedAlerts = AlertCacheService.getLast24Hours(requireContext())
                        if (cachedAlerts.isNotEmpty()) {
                            miniMapContainer.visibility = View.VISIBLE
                        } else {
                            miniMapContainer.visibility = View.GONE
                        }
                    }
                    else -> {
                        // No active alerts (API working, but no current alerts)
                        stopAutoCycle()
                        loadCachedAlerts()
                        miniMapContainer.visibility = View.VISIBLE  // Always show map

                        if (cachedAlertsList.isNotEmpty()) {
                            // Show most recent cached alert
                            displayCachedAlert(0)
                            if (shouldStartCycler()) {
                                startAutoCycle()  // Start cycling through alerts if enabled
                            }
                        } else {
                            // No cached alerts at all
                            tvActiveAlerts.text = getString(R.string.active_alerts_none)
                            activeAlertsScrollView.visibility = View.GONE
                            miniMapView.overlays.clear()
                            miniMapView.invalidate()
                        }
                    }
                }
            }
        }.start()
    }

    private fun clearAndRefreshAlerts() {
        pbAlertsLoading.visibility = View.VISIBLE
        btnRefreshAlerts.isEnabled = false
        btnClearRefreshAlerts.isEnabled = false

        Thread {
            // Clear the cache completely
            prefs.edit().putString("alert_cache", "[]").apply()

            // Fetch latest alert
            val result = RedAlertService.fetch()

            when (result) {
                is RedAlertService.FetchResult.Success -> {
                    val alert = result.alert
                    if (alert != null) {
                        // Save to cache without filtering
                        AlertCacheService.save(requireContext(), alert)
                    }
                }
                is RedAlertService.FetchResult.Unavailable -> {
                    // API error
                }
            }

            handler.post {
                pbAlertsLoading.visibility = View.GONE
                btnRefreshAlerts.isEnabled = true
                btnClearRefreshAlerts.isEnabled = true

                lastAlertUpdateMs = System.currentTimeMillis()
                updateAlertTimestamp()

                // After clearing and fetching, reload and display cached alerts
                // This shows everything without region filtering
                loadCachedAlerts()

                if (cachedAlertsList.isNotEmpty()) {
                    // Show the most recent alert
                    displayCachedAlert(0)
                    miniMapContainer.visibility = View.VISIBLE
                    activeAlertsScrollView.visibility = View.VISIBLE

                    // Don't start cycler here - will be started automatically if conditions are met
                } else {
                    // No alerts after clear & refresh
                    tvActiveAlerts.text = getString(R.string.active_alerts_none)
                    activeAlertsScrollView.visibility = View.GONE
                    miniMapView.overlays.clear()
                    miniMapView.invalidate()
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

    private fun updateMiniMapFromCached(alert: AlertCacheService.CachedAlert) {
        // Clear existing overlays
        miniMapView.overlays.removeAll { it is org.osmdroid.views.overlay.ItemizedIconOverlay<*> }

        // Add orange markers for cached alert regions
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

    private fun cyclePreviousAlert() {
        if (cachedAlertsList.isEmpty()) return
        currentCachedAlertIndex = if (currentCachedAlertIndex > 0) {
            currentCachedAlertIndex - 1
        } else {
            cachedAlertsList.size - 1
        }
        displayCachedAlert(currentCachedAlertIndex)
    }

    private fun cycleNextAlert() {
        if (cachedAlertsList.isEmpty()) return
        currentCachedAlertIndex = (currentCachedAlertIndex + 1) % cachedAlertsList.size
        displayCachedAlert(currentCachedAlertIndex)
    }

    private fun displayCachedAlert(index: Int) {
        if (index < 0 || index >= cachedAlertsList.size) return
        val alert = cachedAlertsList[index]
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val timeStr = fmt.format(Date(alert.timestampMs))
        val indexStr = if (cachedAlertsList.size > 1) " (${index + 1}/${cachedAlertsList.size})" else ""
        tvActiveAlerts.text = "[$timeStr] ${alert.title}$indexStr"
        tvActiveAlertsRegions.text = alert.regions.joinToString(", ")
        activeAlertsScrollView.visibility = View.VISIBLE
        updateMiniMapFromCached(alert)
    }

    private fun initializeDemoAlerts() {
        // Initialize with demo alerts if cache is empty (for development/testing)
        val existingAlerts = AlertCacheService.getLast24Hours(requireContext())
        if (existingAlerts.isEmpty()) {
            val testAlert1 = RedAlertService.ActiveAlert(
                "Test Alert - North",
                listOf("חיפה", "תל אביב", "נתניה"),
                "This is a test alert for development"
            )
            val testAlert2 = RedAlertService.ActiveAlert(
                "Test Alert - South",
                listOf("באר שבע", "אשקלון"),
                "This is another test alert for development"
            )
            AlertCacheService.save(requireContext(), testAlert1)
            AlertCacheService.save(requireContext(), testAlert2)
        }
    }

    private fun loadCyclerSettings() {
        // Load cycler duration from preferences (3-10 seconds, default 5)
        val durationSeconds = prefs.getInt("cycler_duration_seconds", 5)
        autoCycleDurationMs = (durationSeconds * 1000).toLong()
    }

    private fun shouldStartCycler(): Boolean {
        // Only cycle if there's more than 1 alert
        if (cachedAlertsList.size <= 1) return false

        val cyclerMode = prefs.getString("cycler_mode", "off") ?: "off"
        if (cyclerMode == "off") return false
        if (cyclerMode == "always") return true
        // For "shabbat" mode, check if we're during Shabbat
        if (cyclerMode == "shabbat") {
            val now = System.currentTimeMillis()
            val candleMs = prefs.getLong("hebcal_candle_ms", 0)
            val havdalahMs = prefs.getLong("hebcal_havdalah_ms", 0)
            return now in candleMs..havdalahMs
        }
        return false
    }

    private fun startAutoCycle() {
        stopAutoCycle()
        if (cachedAlertsList.size <= 1) return  // No point cycling with 0-1 alerts

        autoCycleRunnable = object : Runnable {
            override fun run() {
                cycleNextAlert()
                handler.postDelayed(this, autoCycleDurationMs)
            }
        }
        autoCycleRunnable?.let { handler.postDelayed(it, autoCycleDurationMs) }
    }

    private fun stopAutoCycle() {
        autoCycleRunnable?.let { handler.removeCallbacks(it) }
        autoCycleRunnable = null
    }

    private fun loadCachedAlerts() {
        cachedAlertsList = AlertCacheService.getLast24Hours(requireContext())
            .sortedByDescending { it.timestampMs }
        currentCachedAlertIndex = 0
    }
}
