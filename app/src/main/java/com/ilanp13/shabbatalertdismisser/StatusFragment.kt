package com.ilanp13.shabbatalertdismisser

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
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
    private lateinit var btnClearAlerts: Button
    private lateinit var btnRefetch24h: Button
    private lateinit var btnPrevAlert: Button
    private lateinit var btnNextAlert: Button
    private lateinit var shabbatBanner: LinearLayout
    private lateinit var tvShabbatBanner: TextView
    private lateinit var threatBanner: LinearLayout
    private lateinit var tvThreatLevel: TextView
    private lateinit var tvThreatRegions: TextView
    private lateinit var tvThreatSince: TextView
    private lateinit var btnDismissThreat: Button

    private lateinit var prefs: android.content.SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var alertsPollRunnable: Runnable? = null
    private var timestampUpdateRunnable: Runnable? = null
    private var lastAlertUpdateMs = 0L
    private var cachedAlertsList = listOf<AlertCacheService.CachedAlert>()
    private var alertsByMinute = listOf<List<AlertCacheService.CachedAlert>>()  // Grouped by minute
    private var currentMinuteGroupIndex = 0
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
        btnClearAlerts = view.findViewById(R.id.btnClearAlerts)
        btnRefetch24h = view.findViewById(R.id.btnRefetch24h)
        shabbatBanner = view.findViewById(R.id.shabbatBanner)
        tvShabbatBanner = view.findViewById(R.id.tvShabbatBanner)
        threatBanner = view.findViewById(R.id.threatBanner)
        tvThreatLevel = view.findViewById(R.id.tvThreatLevel)
        tvThreatRegions = view.findViewById(R.id.tvThreatRegions)
        tvThreatSince = view.findViewById(R.id.tvThreatSince)
        btnDismissThreat = view.findViewById(R.id.btnDismissThreat)

        // Setup mini map
        setupMiniMap()

        btnRefreshAlerts.setOnClickListener {
            updateActiveAlerts()
        }

        btnClearAlerts.setOnClickListener {
            clearAlerts()
        }

        btnRefetch24h.setOnClickListener {
            refetch24h()
        }

        // Previous/Next buttons for cycling through cached alerts
        btnPrevAlert = view.findViewById(R.id.btnPrevAlert)
        btnNextAlert = view.findViewById(R.id.btnNextAlert)

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

        // Dismiss threat button with confirmation
        btnDismissThreat.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.threat_dismiss_title)
                .setMessage(R.string.threat_dismiss_message)
                .setPositiveButton(R.string.threat_dismiss_confirm) { _, _ ->
                    AlertStateMachine.clearState(requireContext())
                    updateThreatBanner()
                }
                .setNegativeButton(R.string.threat_dismiss_cancel, null)
                .show()
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

        // Setup mini map (interactive - allow pinch zoom and pan)
        miniMapView.setTileSource(TileSourceFactory.MAPNIK)
        miniMapView.setMultiTouchControls(true)  // Enable pinch zoom
        miniMapView.isClickable = true
        miniMapView.isFocusable = true
        miniMapView.setDestroyMode(false)  // Prevent destroying on detach

        // Center on Israel with optimal zoom level to show entire country
        // Zoom 8 shows entire Israel including Eilat at bottom
        val controller = miniMapView.controller
        controller.setZoom(8.0)
        controller.setCenter(GeoPoint(31.5, 35.0))

        // Start hidden
        miniMapContainer.visibility = View.GONE
    }


    override fun onResume() {
        super.onResume()
        miniMapView.onResume()
        updateStatus()
        updateShabbatBanner()
        updateShabbatTimes()
        updateSyncStatus()
        updateDismissalCount()
        updateThreatBanner()
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

    private fun updateShabbatBanner() {
        val mode = prefs.getString("mode", "shabbat_only")
        val now = System.currentTimeMillis()
        val candleMs = prefs.getLong("hebcal_candle_ms", 0)
        val havdalahMs = prefs.getLong("hebcal_havdalah_ms", 0)
        val isShabbatNow = candleMs > 0 && havdalahMs > 0 && now in candleMs..havdalahMs
        val isAlwaysMode = mode == "always"

        if (isShabbatNow) {
            shabbatBanner.visibility = View.VISIBLE
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            tvShabbatBanner.text = getString(R.string.shabbat_banner, fmt.format(Date(havdalahMs)))
        } else if (isAlwaysMode) {
            // Preview: show banner when in "always" mode so user can see how it looks
            shabbatBanner.visibility = View.VISIBLE
            tvShabbatBanner.text = getString(R.string.shabbat_banner_always)
        } else {
            shabbatBanner.visibility = View.GONE
        }
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

            val regionMode = prefs.getString("region_display_mode", "all") ?: "all"

            // Feed into state machine for selected regions
            val ctx = requireContext()
            if (result is RedAlertService.FetchResult.Success && result.alert != null) {
                AlertStateMachine.processAlert(ctx, result.alert, selectedRegions.toSet())
            }
            // Always run tick to check timeouts
            AlertStateMachine.processTick(ctx)

            val displayAlert = when (result) {
                is RedAlertService.FetchResult.Success -> {
                    val alert = result.alert
                    if (alert != null) {
                        // Save to cache
                        AlertCacheService.save(ctx, alert)

                        // Filter by alert type
                        if (!AlertTypeFilter.shouldShow(ctx, alert.type)) {
                            null
                        } else if (regionMode == "selected_only" && selectedRegions.isNotEmpty()) {
                            // Filter to matching regions only
                            val filtered = alert.regions.filter { it in selectedRegions }
                            if (filtered.isNotEmpty()) alert.copy(regions = filtered) else null
                        } else {
                            // Show all regions (selected ones get highlighted on map)
                            alert
                        }
                    } else {
                        null
                    }
                }
                is RedAlertService.FetchResult.Unavailable -> null
            }

            val isUnavailable = (result is RedAlertService.FetchResult.Unavailable)

            handler.post {
                // Hide loading indicator and re-enable button
                pbAlertsLoading.visibility = View.GONE
                btnRefreshAlerts.isEnabled = true

                // Update timestamp
                lastAlertUpdateMs = System.currentTimeMillis()
                updateAlertTimestamp()
                updateThreatBanner()

                when {
                    displayAlert != null -> {
                        // Active alerts found - stop cycling and show them
                        stopAutoCycle()
                        tvActiveAlerts.text = displayAlert.title
                        tvActiveAlertsRegions.text = formatRegionsHighlighted(displayAlert.regions)
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
                        feedRecentCachedAlertsToStateMachine()
                        updateThreatBanner()
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

    private fun clearAlerts() {
        // Clear cache immediately on UI thread
        prefs.edit().putString("alert_cache", "[]").apply()

        // Reload and update UI
        loadCachedAlerts()
        stopAutoCycle()

        if (cachedAlertsList.isEmpty()) {
            tvActiveAlerts.text = getString(R.string.active_alerts_none)
            activeAlertsScrollView.visibility = View.GONE
            miniMapView.overlays.clear()
            miniMapView.invalidate()
        }
    }

    private fun refetch24h() {
        pbAlertsLoading.visibility = View.VISIBLE
        btnRefreshAlerts.isEnabled = false
        btnClearAlerts.isEnabled = false
        btnRefetch24h.isEnabled = false

        Thread {
            android.util.Log.d("StatusFragment", "Refetch 24h: Fetching history...")
            val historyAlerts = RedAlertService.fetchHistory()
            android.util.Log.d("StatusFragment", "Refetch 24h: Got ${historyAlerts.size} history alerts")

            // Batch save all at once (clears old cache and writes single time)
            val ctx = requireContext()
            AlertCacheService.saveBatch(ctx, historyAlerts)

            handler.post {
                pbAlertsLoading.visibility = View.GONE
                btnRefreshAlerts.isEnabled = true
                btnClearAlerts.isEnabled = true
                btnRefetch24h.isEnabled = true

                lastAlertUpdateMs = System.currentTimeMillis()
                updateAlertTimestamp()

                loadCachedAlerts()
                feedRecentCachedAlertsToStateMachine()
                updateThreatBanner()

                val count = historyAlerts.size
                android.widget.Toast.makeText(
                    requireContext(),
                    if (count > 0) "Fetched $count alerts" else "No alerts in last 24h",
                    android.widget.Toast.LENGTH_SHORT
                ).show()

                if (alertsByMinute.isNotEmpty()) {
                    displayCachedAlertsGroup(0)
                    miniMapContainer.visibility = View.VISIBLE
                    activeAlertsScrollView.visibility = View.VISIBLE
                    if (shouldStartCycler()) {
                        startAutoCycle()
                    }
                } else {
                    tvActiveAlerts.text = getString(R.string.active_alerts_none)
                    activeAlertsScrollView.visibility = View.GONE
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
        miniMapView.overlays.removeAll { it !is org.osmdroid.views.overlay.compass.CompassOverlay }

        val userLat = prefs.getFloat("latitude", 31.7683f).toDouble()
        val userLon = prefs.getFloat("longitude", 35.2137f).toDouble()
        val selectedRegions = getSelectedRegions().toSet()

        val overlay = object : org.osmdroid.views.overlay.Overlay() {
            override fun draw(canvas: android.graphics.Canvas?, mapView: MapView?, shadow: Boolean) {
                if (shadow || canvas == null || mapView == null) return
                val pj = mapView.projection ?: return
                val paint = android.graphics.Paint().apply { isAntiAlias = true }

                // Draw user location (blue dot)
                val userScreen = pj.toPixels(GeoPoint(userLat, userLon), null)
                paint.color = android.graphics.Color.BLUE
                paint.style = android.graphics.Paint.Style.FILL
                canvas.drawCircle(userScreen.x.toFloat(), userScreen.y.toFloat(), 6f, paint)
                paint.color = android.graphics.Color.WHITE
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawCircle(userScreen.x.toFloat(), userScreen.y.toFloat(), 6f, paint)

                // Draw alert markers — non-selected first, selected on top
                val color = getAlertTypeColor(alert.type)
                val sortedRegions = alert.regions.sortedBy { it in selectedRegions }
                for (region in sortedRegions) {
                    val coords = OrefRegionCoords.coords[region] ?: continue
                    val screenPos = pj.toPixels(GeoPoint(coords.first, coords.second), null)
                    val isSelected = region in selectedRegions
                    val radius = if (isSelected) 14f else 12f

                    paint.color = color
                    paint.style = android.graphics.Paint.Style.FILL
                    paint.alpha = 255
                    canvas.drawCircle(screenPos.x.toFloat(), screenPos.y.toFloat(), radius, paint)

                    if (isSelected) {
                        // Yellow highlight border for selected regions
                        paint.color = android.graphics.Color.YELLOW
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.strokeWidth = 3f
                        paint.alpha = 255
                        canvas.drawCircle(screenPos.x.toFloat(), screenPos.y.toFloat(), radius + 2f, paint)
                    }

                    paint.color = android.graphics.Color.WHITE
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    canvas.drawCircle(screenPos.x.toFloat(), screenPos.y.toFloat(), radius, paint)
                }

                // Header: "NOW" in red + title
                paint.color = android.graphics.Color.parseColor("#CC000000")
                paint.style = android.graphics.Paint.Style.FILL
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), 44f, paint)
                paint.color = android.graphics.Color.RED
                paint.textSize = 24f
                paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                canvas.drawText("NOW  ${alert.title}", 8f, 32f, paint)
                paint.typeface = android.graphics.Typeface.DEFAULT
            }
        }

        miniMapView.overlays.add(overlay)
        miniMapView.invalidate()
    }

    private fun updateMiniMapFromCached(alert: AlertCacheService.CachedAlert) {
        miniMapView.overlays.removeAll { it !is org.osmdroid.views.overlay.compass.CompassOverlay }

        val userLat = prefs.getFloat("latitude", 31.7683f).toDouble()
        val userLon = prefs.getFloat("longitude", 35.2137f).toDouble()
        val selectedRegions = getSelectedRegions().toSet()

        val overlay = object : org.osmdroid.views.overlay.Overlay() {
            override fun draw(canvas: android.graphics.Canvas?, mapView: MapView?, shadow: Boolean) {
                if (shadow || canvas == null || mapView == null) return
                val pj = mapView.projection ?: return
                val paint = android.graphics.Paint().apply { isAntiAlias = true }

                // Draw user location
                val userScreen = pj.toPixels(GeoPoint(userLat, userLon), null)
                paint.color = android.graphics.Color.BLUE
                paint.style = android.graphics.Paint.Style.FILL
                canvas.drawCircle(userScreen.x.toFloat(), userScreen.y.toFloat(), 6f, paint)
                paint.color = android.graphics.Color.WHITE
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawCircle(userScreen.x.toFloat(), userScreen.y.toFloat(), 6f, paint)

                val color = getAlertTypeColor(alert.type)
                val sortedRegions = alert.regions.sortedBy { it in selectedRegions }
                for (region in sortedRegions) {
                    val coords = OrefRegionCoords.coords[region] ?: continue
                    val screenPos = pj.toPixels(GeoPoint(coords.first, coords.second), null)
                    val isSelected = region in selectedRegions
                    val radius = if (isSelected) 12f else 10f

                    paint.color = color
                    paint.style = android.graphics.Paint.Style.FILL
                    paint.alpha = if (isSelected) 255 else 200
                    canvas.drawCircle(screenPos.x.toFloat(), screenPos.y.toFloat(), radius, paint)

                    if (isSelected) {
                        paint.color = android.graphics.Color.YELLOW
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.strokeWidth = 3f
                        paint.alpha = 255
                        canvas.drawCircle(screenPos.x.toFloat(), screenPos.y.toFloat(), radius + 2f, paint)
                    }

                    paint.color = android.graphics.Color.WHITE
                    paint.style = android.graphics.Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    canvas.drawCircle(screenPos.x.toFloat(), screenPos.y.toFloat(), radius, paint)
                }

                // Header
                val dateFmt = SimpleDateFormat("dd.MM", Locale.getDefault())
                val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                val headerText = "History ${dateFmt.format(Date(alert.timestampMs))} ${timeFmt.format(Date(alert.timestampMs))} ${alert.title}"
                paint.color = android.graphics.Color.parseColor("#CC333333")
                paint.style = android.graphics.Paint.Style.FILL
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), 40f, paint)
                paint.color = android.graphics.Color.WHITE
                paint.textSize = 22f
                canvas.drawText(headerText, 8f, 30f, paint)
            }
        }

        miniMapView.overlays.add(overlay)
        miniMapView.invalidate()
    }

    private fun updateThreatBanner() {
        val state = AlertStateMachine.getState(requireContext())
        when (state.level) {
            AlertStateMachine.ThreatLevel.CLEAR -> {
                threatBanner.visibility = View.GONE
            }
            AlertStateMachine.ThreatLevel.WARNING -> {
                threatBanner.visibility = View.VISIBLE
                threatBanner.setBackgroundColor(android.graphics.Color.parseColor("#FFC107")) // Amber
                tvThreatLevel.text = getString(R.string.threat_warning)
                tvThreatLevel.setTextColor(android.graphics.Color.BLACK)
                tvThreatRegions.text = getString(R.string.threat_regions, state.regions.joinToString(", "))
                tvThreatRegions.setTextColor(android.graphics.Color.BLACK)
                tvThreatSince.setTextColor(android.graphics.Color.BLACK)
                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                tvThreatSince.text = getString(R.string.threat_since, fmt.format(Date(state.since)))
            }
            AlertStateMachine.ThreatLevel.ALARM -> {
                threatBanner.visibility = View.VISIBLE
                threatBanner.setBackgroundColor(android.graphics.Color.parseColor("#D32F2F")) // Red
                tvThreatLevel.text = getString(R.string.threat_alarm)
                tvThreatLevel.setTextColor(android.graphics.Color.WHITE)
                tvThreatRegions.text = getString(R.string.threat_regions, state.regions.joinToString(", "))
                tvThreatRegions.setTextColor(android.graphics.Color.WHITE)
                tvThreatSince.setTextColor(android.graphics.Color.parseColor("#FFCDD2"))
                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                tvThreatSince.text = getString(R.string.threat_since, fmt.format(Date(state.since)))
            }
        }
    }

    private fun getAlertTypeColor(type: String): Int {
        return when (type.lowercase()) {
            "missile" -> android.graphics.Color.RED
            "aircraft" -> android.graphics.Color.parseColor("#FF9500") // Orange
            "event" -> android.graphics.Color.GREEN
            "earthquake" -> android.graphics.Color.parseColor("#9C27B0") // Purple
            "tsunami" -> android.graphics.Color.BLUE
            else -> android.graphics.Color.parseColor("#FF9800") // Default orange
        }
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
        if (alertsByMinute.isEmpty()) return
        currentMinuteGroupIndex = if (currentMinuteGroupIndex > 0) {
            currentMinuteGroupIndex - 1
        } else {
            alertsByMinute.size - 1
        }
        displayCachedAlertsGroup(currentMinuteGroupIndex)
    }

    private fun cycleNextAlert() {
        if (alertsByMinute.isEmpty()) return
        currentMinuteGroupIndex = (currentMinuteGroupIndex + 1) % alertsByMinute.size
        displayCachedAlertsGroup(currentMinuteGroupIndex)
    }

    private fun displayCachedAlert(index: Int) {
        // Legacy method - show first group
        if (alertsByMinute.isEmpty()) return
        currentMinuteGroupIndex = 0
        displayCachedAlertsGroup(0)
    }

    private fun displayCachedAlertsGroup(groupIndex: Int) {
        if (groupIndex < 0 || groupIndex >= alertsByMinute.size) return
        val group = alertsByMinute[groupIndex]
        if (group.isEmpty()) return

        val header = AlertCacheService.formatGroupHeader(group)
        val posStr = "${groupIndex + 1}/${alertsByMinute.size}"
        val countStr = AlertCacheService.formatGroupCount(group)
        tvActiveAlerts.text = "$header$countStr ($posStr)${if (group.size == 1) " ${group[0].title}" else ""}"

        // Show all regions from all alerts in this group (selected regions bold + first)
        val allRegions = group.flatMap { it.regions }.distinct()
        tvActiveAlertsRegions.text = formatRegionsHighlighted(allRegions)
        activeAlertsScrollView.visibility = View.VISIBLE

        // Update mini map with all alerts from this minute
        updateMiniMapFromCachedGroup(group)
    }

    private fun updateMiniMapFromCachedGroup(alerts: List<AlertCacheService.CachedAlert>) {
        miniMapView.overlays.removeAll { it !is org.osmdroid.views.overlay.compass.CompassOverlay }

        val userLat = prefs.getFloat("latitude", 31.7683f).toDouble()
        val userLon = prefs.getFloat("longitude", 35.2137f).toDouble()
        val selectedRegions = getSelectedRegions().toSet()

        val overlay = object : org.osmdroid.views.overlay.Overlay() {
            override fun draw(canvas: android.graphics.Canvas?, mapView: MapView?, shadow: Boolean) {
                if (shadow || canvas == null || mapView == null) return
                val pj = mapView.projection ?: return
                val paint = android.graphics.Paint().apply { isAntiAlias = true }

                // Draw user location
                val userScreen = pj.toPixels(GeoPoint(userLat, userLon), null)
                paint.color = android.graphics.Color.BLUE
                paint.style = android.graphics.Paint.Style.FILL
                canvas.drawCircle(userScreen.x.toFloat(), userScreen.y.toFloat(), 6f, paint)
                paint.color = android.graphics.Color.WHITE
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawCircle(userScreen.x.toFloat(), userScreen.y.toFloat(), 6f, paint)

                // Group alerts by region for stacking — draw selected regions last (on top)
                val alertsByRegion = alerts.flatMap { alert ->
                    alert.regions.map { region -> region to alert }
                }.groupBy { it.first }

                val sortedRegionKeys = alertsByRegion.keys.sortedBy { it in selectedRegions }

                for (region in sortedRegionKeys) {
                    val regionAlerts = alertsByRegion[region] ?: continue
                    val coords = OrefRegionCoords.coords[region] ?: continue
                    val screenPos = pj.toPixels(GeoPoint(coords.first, coords.second), null)
                    val bx = screenPos.x.toFloat()
                    val by = screenPos.y.toFloat()
                    val isSelected = region in selectedRegions

                    for ((index, pair) in regionAlerts.withIndex()) {
                        val alert = pair.second
                        val ox = (index % 3) * 6f - 6f
                        val oy = (index / 3) * 6f
                        val color = getAlertTypeColor(alert.type)
                        val radius = if (isSelected) 12f else 10f

                        paint.color = color
                        paint.style = android.graphics.Paint.Style.FILL
                        paint.alpha = if (isSelected) 255 else 200
                        canvas.drawCircle(bx + ox, by + oy, radius, paint)

                        if (isSelected) {
                            paint.color = android.graphics.Color.YELLOW
                            paint.style = android.graphics.Paint.Style.STROKE
                            paint.strokeWidth = 3f
                            paint.alpha = 255
                            canvas.drawCircle(bx + ox, by + oy, radius + 2f, paint)
                        }

                        paint.color = android.graphics.Color.WHITE
                        paint.style = android.graphics.Paint.Style.STROKE
                        paint.strokeWidth = 2f
                        canvas.drawCircle(bx + ox, by + oy, radius, paint)
                    }

                    if (regionAlerts.size > 1) {
                        paint.color = android.graphics.Color.BLACK
                        paint.textSize = 12f
                        paint.style = android.graphics.Paint.Style.FILL
                        canvas.drawText(regionAlerts.size.toString(), bx + 8f, by - 8f, paint)
                    }
                }

                // Header
                if (alerts.isNotEmpty()) {
                    val header = AlertCacheService.formatGroupHeader(alerts)
                    val countStr = AlertCacheService.formatGroupCount(alerts)
                    paint.color = android.graphics.Color.parseColor("#CC333333")
                    paint.style = android.graphics.Paint.Style.FILL
                    canvas.drawRect(0f, 0f, canvas.width.toFloat(), 40f, paint)
                    paint.color = android.graphics.Color.WHITE
                    paint.textSize = 22f
                    canvas.drawText("$header$countStr", 8f, 30f, paint)
                }
            }
        }

        miniMapView.overlays.add(overlay)
        miniMapView.invalidate()
    }

    private fun loadCyclerSettings() {
        // Load cycler duration from preferences (3-10 seconds, default 5)
        val durationSeconds = prefs.getInt("cycler_duration_seconds", 5)
        autoCycleDurationMs = (durationSeconds * 1000).toLong()
    }

    private fun shouldStartCycler(): Boolean {
        // Only cycle if there's more than 1 minute-group
        if (alertsByMinute.size <= 1) return false

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
        if (alertsByMinute.size <= 1) return  // No point cycling with 0-1 minute groups

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

    /**
     * Check recent cached alerts for matches with selected regions and feed them
     * to the state machine. This ensures the threat banner shows even if the
     * 30-second polling missed the brief live alert window.
     * Uses UNFILTERED cache so category 13 (event ended) isn't excluded by type filter.
     */
    private fun feedRecentCachedAlertsToStateMachine() {
        val ctx = context ?: return
        val selectedRegions = getSelectedRegions().toSet()
        if (selectedRegions.isEmpty()) return

        val now = System.currentTimeMillis()
        val recentThresholdMs = 60 * 60 * 1000L // 1 hour

        // Use unfiltered cache so cat 13 "event over" still triggers CLEAR
        val allCached = AlertCacheService.getLast24Hours(ctx)

        // Feed ALL recent matching alerts in chronological order so the full
        // sequence (warning → alarm → event_over) replays correctly and a
        // category 13 "event over" properly clears the state.
        val recentMatching = allCached
            .filter { alert ->
                (now - alert.timestampMs) < recentThresholdMs &&
                    alert.regions.any { it in selectedRegions }
            }
            .sortedBy { it.timestampMs }

        if (recentMatching.isEmpty()) return

        for (cached in recentMatching) {
            val activeAlert = RedAlertService.ActiveAlert(
                title = cached.title,
                regions = cached.regions,
                description = cached.description,
                type = cached.type,
                category = cached.category,
                timestampMs = cached.timestampMs
            )
            AlertStateMachine.processAlert(ctx, activeAlert, selectedRegions)
        }
    }

    /** Format regions with selected ones bold and listed first */
    private fun formatRegionsHighlighted(regions: List<String>): CharSequence {
        val selectedRegions = getSelectedRegions().toSet()
        val sorted = regions.sortedByDescending { it in selectedRegions }
        val builder = android.text.SpannableStringBuilder()
        for ((i, region) in sorted.withIndex()) {
            if (i > 0) builder.append(", ")
            val start = builder.length
            builder.append(region)
            if (region in selectedRegions) {
                builder.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    start, builder.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return builder
    }

    private fun loadCachedAlerts() {
        // Load and filter alerts
        cachedAlertsList = AlertCacheService.getLast24Hours(requireContext())
            .filter { AlertTypeFilter.shouldShow(requireContext(), it.type) }
            .sortedByDescending { it.timestampMs }

        // Tiered grouping: 1min (recent) → 10min → 30min (old)
        alertsByMinute = AlertCacheService.groupByTimeBucket(cachedAlertsList)

        currentMinuteGroupIndex = 0
        updateCycleButtonStates()
    }

    private fun updateCycleButtonStates() {
        val hasMultipleGroups = alertsByMinute.size > 1
        btnPrevAlert.isEnabled = hasMultipleGroups
        btnNextAlert.isEnabled = hasMultipleGroups
    }
}
