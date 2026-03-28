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
    private lateinit var alertsHeaderSection: LinearLayout
    private var tvBlocksUpdatedTime: TextView? = null
    private val blockHeaderViews = mutableListOf<Pair<TextView, List<AlertCacheService.CachedAlert>>>() // header → group
    private lateinit var alertBlocksContainer: LinearLayout
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

    private lateinit var btnShowAllIsrael: Button

    private lateinit var prefs: android.content.SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var alertsPollRunnable: Runnable? = null
    private var timestampUpdateRunnable: Runnable? = null
    private var pollCycleCount = 0
    private var lastAlertUpdateMs = 0L
    private var lastKnownAlertFingerprint = ""
    private var clearedAtMs = 0L  // If > 0, only show alerts newer than this
    private var cachedAlertsList = listOf<AlertCacheService.CachedAlert>()
    private var alertsByMinute = listOf<List<AlertCacheService.CachedAlert>>()  // Grouped by minute
    private var currentMinuteGroupIndex = 0
    private var autoCycleRunnable: Runnable? = null
    private var autoCycleDurationMs = 5000L  // Default 5 seconds
    private var isCyclerPaused = false
    private lateinit var btnPausePlay: Button
    private val previousMiniMapRegions = mutableSetOf<String>()
    private var blinkRunnable: Runnable? = null

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
        alertsHeaderSection = view.findViewById(R.id.alertsHeaderSection)
        alertBlocksContainer = view.findViewById(R.id.alertBlocksContainer)
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
        btnPausePlay = view.findViewById(R.id.btnPausePlay)

        btnPrevAlert.setOnClickListener {
            cyclePreviousAlert()
        }

        btnNextAlert.setOnClickListener {
            cycleNextAlert()
        }

        btnPausePlay.setOnClickListener {
            toggleCyclerPause()
        }

        // Tap on mini map (not buttons area) to navigate to full map
        miniMapView.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(3)
        }

        // Show all Israel button
        btnShowAllIsrael = view.findViewById(R.id.btnShowAllIsrael)
        btnShowAllIsrael.setOnClickListener {
            zoomToAllIsrael()
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
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        Configuration.getInstance().apply {
            load(requireContext(), prefs)
            userAgentValue = requireContext().packageName
            osmdroidBasePath = requireContext().cacheDir
        }

        miniMapView.setMultiTouchControls(true)
        miniMapView.isClickable = true
        miniMapView.isFocusable = true
        miniMapView.setDestroyMode(false)

        // Prevent ViewPager2 from intercepting swipes on the mini map
        miniMapView.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        val controller = miniMapView.controller
        controller.setZoom(8.0)
        controller.setCenter(GeoPoint(31.5, 35.0))

        applyMiniMapStyle()
        miniMapContainer.visibility = View.GONE
    }

    private fun applyMiniMapStyle() {
        val style = prefs.getString("map_style", "minimal") ?: "minimal"
        MapTileHelper.applyStyle(miniMapView, resources, style)
    }


    override fun onResume() {
        super.onResume()
        miniMapView.onResume()
        updateStatus()
        updateShabbatBanner()
        updateShabbatTimes()
        updateSyncStatus()
        updateDismissalCount()
        // Reload cached alerts to pick up filter changes from Map tab
        loadCachedAlerts()
        lastKnownAlertFingerprint = alertsFingerprint()
        feedRecentCachedAlertsToStateMachine()
        updateThreatBanner()
        loadCyclerSettings()
        if (isLiveMode()) {
            // Entering live mode — stop cycler, clear normal blocks, init live
            stopAutoCycle()
            initLiveMode()
        } else {
            // Normal mode — clear any leftover live blocks, force rebuild
            RegionAlertTracker.clear()
            alertBlocksContainer.removeAllViews()
            blockHeaderViews.clear()
            if (cachedAlertsList.isNotEmpty()) {
                displayCachedAlertsGroup(currentMinuteGroupIndex, forceRebuild = true)
                showAlertBlocks(true)
            }
            if (shouldStartCycler()) startAutoCycle()
        }
        startAlertPolling()
        if (prefs.getInt("poll_frequency_seconds", 30) == 0) {
            updateActiveAlerts()
            backgroundHistoryRefresh()
        }
    }

    override fun onPause() {
        super.onPause()
        miniMapView.onPause()
        stopAlertPolling()
        stopAutoCycle()
        blinkRunnable?.let { handler.removeCallbacks(it) }
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
        val windows = HebcalService.windowsFromJson(prefs.getString("hebcal_windows_json", null))
        val activeWindow = windows.find { now in it.candleMs..it.havdalahMs }
        val isAlwaysMode = mode == "always"

        if (activeWindow != null) {
            shabbatBanner.visibility = View.VISIBLE
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            tvShabbatBanner.text = getString(R.string.shabbat_banner, fmt.format(Date(activeWindow.havdalahMs)))
        } else if (isAlwaysMode) {
            shabbatBanner.visibility = View.VISIBLE
            tvShabbatBanner.text = getString(R.string.shabbat_banner_always)
        } else {
            shabbatBanner.visibility = View.GONE
        }
    }

    private fun updateShabbatTimes() {
        val now = System.currentTimeMillis()
        val windows = HebcalService.windowsFromJson(prefs.getString("hebcal_windows_json", null))

        // Find the current or next window
        val activeWindow = windows.find { now in it.candleMs..it.havdalahMs }
        val nextWindow = activeWindow ?: windows.filter { it.candleMs > now }.minByOrNull { it.candleMs }
        val displayWindow = activeWindow ?: nextWindow

        // If all windows are past, skip to fallback (local calculation for next week)
        if (displayWindow != null && (activeWindow != null || nextWindow != null)) {
            val fmt = SimpleDateFormat("EEEE HH:mm", Locale.getDefault())
            val havdalahDisplayMs = ((displayWindow.havdalahMs + 30_000L) / 60_000L) * 60_000L
            tvShabbatTimes.text = getString(R.string.shabbat_times_format,
                fmt.format(Date(displayWindow.candleMs)),
                fmt.format(Date(havdalahDisplayMs)))

            // Determine if this window is Shabbat or Yom Tov by checking candle day
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = displayWindow.candleMs
            val isFridayCandle = cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.FRIDAY
            val label = displayWindow.parasha

            tvShabbatTimesTitle.text = when {
                activeWindow != null && isFridayCandle -> getString(R.string.shabbat_times_current)
                activeWindow != null -> label ?: getString(R.string.shabbat_times_current)
                isFridayCandle -> getString(R.string.shabbat_times_next)
                else -> label ?: getString(R.string.shabbat_times_next)
            }

            // Show holiday name or parasha in the parasha section
            tvShabbatParasha.text = if (label != null && !isFridayCandle) {
                label // Holiday name (e.g., "Erev Pesach")
            } else if (label != null) {
                label // Parasha name
            } else {
                getString(R.string.parasha_unavailable)
            }
            // Always refresh in background to keep labels/times current
            backgroundHebcalRefresh()
            return
        }

        // Fallback to single-pair cache (only if still current or upcoming)
        val candleMs = prefs.getLong("hebcal_candle_ms", 0)
        val havdalahMs = prefs.getLong("hebcal_havdalah_ms", 0)
        if (candleMs > 0 && havdalahMs > now) {
            val fmt = SimpleDateFormat("EEEE HH:mm", Locale.getDefault())
            val havdalahDisplayMs = ((havdalahMs + 30_000L) / 60_000L) * 60_000L
            tvShabbatTimes.text = getString(R.string.shabbat_times_format,
                fmt.format(Date(candleMs)),
                fmt.format(Date(havdalahDisplayMs)))
            tvShabbatTimesTitle.text = if (now in candleMs..havdalahMs)
                getString(R.string.shabbat_times_current) else getString(R.string.shabbat_times_next)
            updateParashaDisplay()
            return
        }

        // All cached data is past — use local calculation for next Shabbat
        val lat = prefs.getFloat("latitude", 31.7683f).toDouble()
        val lon = prefs.getFloat("longitude", 35.2137f).toDouble()
        val candle = prefs.getInt("candle_lighting_minutes", 18)
        val havdala = prefs.getInt("havdalah_minutes", 40)
        val times = ShabbatCalculator(lat, lon).getShabbatTimes(candle, havdala)
        if (times != null) {
            val fmt = SimpleDateFormat("EEEE HH:mm", Locale.getDefault())
            val havdalahMs2 = ((times.second.time.time + 30_000L) / 60_000L) * 60_000L
            tvShabbatTimes.text = getString(R.string.shabbat_times_format,
                fmt.format(times.first.time),
                fmt.format(Date(havdalahMs2)))
        } else {
            tvShabbatTimes.text = getString(R.string.shabbat_times_unavailable)
        }
        tvShabbatTimesTitle.text = getString(R.string.shabbat_times_next)
        // Clear stale parasha — will update when Hebcal refreshes
        tvShabbatParasha.text = getString(R.string.parasha_unavailable)

        backgroundHebcalRefresh()
    }

    private var hebcalRefreshPending = false
    private fun backgroundHebcalRefresh() {
        if (hebcalRefreshPending) return
        hebcalRefreshPending = true
        val lat = prefs.getFloat("latitude", 31.7683f).toDouble()
        val lon = prefs.getFloat("longitude", 35.2137f).toDouble()
        Thread {
            val profile = MinhagProfiles.byKey(prefs.getString("minhag_key", "ashkenaz") ?: "ashkenaz")
            val useRt = prefs.getBoolean("use_rabenu_tam", false)
            val havMins = if (useRt) profile.rtMins else profile.graMins
            val result = HebcalService.fetch(lat, lon, profile.candleMins, havMins)
            if (result != null) {
                val n = System.currentTimeMillis()
                val next = result.nextWindow(n) ?: result.windows.lastOrNull()
                val editor = prefs.edit()
                    .putString("hebcal_windows_json", HebcalService.windowsToJson(result.windows))
                    .putLong("hebcal_cache_timestamp_ms", n)
                if (next != null) {
                    editor.putLong("hebcal_candle_ms", next.candleMs)
                    editor.putLong("hebcal_havdalah_ms", next.havdalahMs)
                }
                if (!result.parasha.isNullOrEmpty()) {
                    editor.putString("hebcal_parasha", result.parasha)
                }
                editor.apply()
                handler.post {
                    hebcalRefreshPending = false
                    if (isAdded) {
                        updateShabbatTimes()
                        updateSyncStatus()
                    }
                }
            } else {
                handler.post { hebcalRefreshPending = false }
            }
        }.start()
    }

    private fun updateParashaDisplay() {
        // Show parasha/holiday name from the next upcoming window
        val now = System.currentTimeMillis()
        val windows = HebcalService.windowsFromJson(prefs.getString("hebcal_windows_json", null))
        val activeWindow = windows.find { now in it.candleMs..it.havdalahMs }
        val nextWindow = activeWindow ?: windows.filter { it.candleMs > now }.minByOrNull { it.candleMs }
        val windowLabel = (activeWindow ?: nextWindow)?.parasha

        val parasha = windowLabel ?: prefs.getString("hebcal_parasha", null)
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
        pollCycleCount = 0
        val pollSeconds = prefs.getInt("poll_frequency_seconds", 30)
        if (pollSeconds == 0) {
            // Polling disabled — do a single fetch on resume only
            startTimestampUpdate()
            return
        }
        val pollMs = pollSeconds * 1000L
        // Background history refresh on every poll cycle (matches settings frequency)
        val historyInterval = 1
        alertsPollRunnable = object : Runnable {
            override fun run() {
                updateActiveAlerts()
                // Periodically fetch history to catch short-lived alerts
                pollCycleCount++
                if (pollCycleCount % historyInterval == 0) {
                    backgroundHistoryRefresh()
                }
                handler.postDelayed(this, pollMs)
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
            val showOtherRegions = prefs.getBoolean("show_other_regions", true)

            // Feed into state machine for selected regions
            val ctx = context ?: return@Thread
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
                        } else if (!showOtherRegions && selectedRegions.isNotEmpty()) {
                            // Only show selected regions
                            val filtered = alert.regions.filter { it in selectedRegions }
                            if (filtered.isNotEmpty()) alert.copy(regions = filtered) else null
                        } else {
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
                if (!isAdded) return@post
                // Hide loading indicator and re-enable button
                pbAlertsLoading.visibility = View.GONE
                btnRefreshAlerts.isEnabled = true

                // Capture threat state before update to detect transitions
                val prevThreatLevel = AlertStateMachine.getState(requireContext()).level

                // Update timestamp
                lastAlertUpdateMs = System.currentTimeMillis()
                updateAlertTimestamp()
                updateThreatBanner()

                when {
                    displayAlert != null -> {
                        // Active alert — save to cache so blocks update too
                        AlertCacheService.save(requireContext(), RedAlertService.ActiveAlert(
                            title = displayAlert.title,
                            regions = displayAlert.regions,
                            description = displayAlert.description,
                            type = displayAlert.type,
                            category = displayAlert.category,
                            timestampMs = displayAlert.timestampMs
                        ))
                        loadCachedAlerts()
                        updateThreatBanner()

                        if (isLiveMode()) {
                            // Live mode: feed to region tracker
                            RegionAlertTracker.processAlert(displayAlert)
                            updateLiveModeDisplay()
                        } else {
                            tvActiveAlerts.text = displayAlert.title
                            displayCachedAlertsGroup(0, forceRebuild = true)
                            showAlertBlocks(true)
                            updateMiniMap(displayAlert)
                            miniMapContainer.visibility = View.VISIBLE
                            if (shouldStartCycler()) startAutoCycle()
                        }
                    }
                    isUnavailable -> {
                        // Service unavailable — still show cached/live data
                        loadCachedAlerts()
                        if (isLiveMode()) {
                            updateLiveModeDisplay()
                        } else if (cachedAlertsList.isNotEmpty()) {
                            tvActiveAlerts.text = ""
                            displayCachedAlertsGroup(currentMinuteGroupIndex)
                            showAlertBlocks(true)
                            miniMapContainer.visibility = View.VISIBLE
                        } else {
                            tvActiveAlerts.text = getString(R.string.active_alerts_unavailable)
                            showAlertBlocks(false)
                            miniMapContainer.visibility = View.GONE
                        }
                    }
                    else -> {
                        loadCachedAlerts()
                        feedRecentCachedAlertsToStateMachine()
                        val curThreatLevel = AlertStateMachine.getState(requireContext()).level
                        val threatChanged = curThreatLevel != prevThreatLevel
                        updateThreatBanner()
                        miniMapContainer.visibility = View.VISIBLE

                        if (isLiveMode()) {
                            // Live mode: feed new alerts to tracker, update display
                            val newFp = alertsFingerprint()
                            if (newFp != lastKnownAlertFingerprint) {
                                lastKnownAlertFingerprint = newFp
                                // Feed only alerts from last 30 min (not full 24h cache)
                                val now = System.currentTimeMillis()
                                val recentAlerts = cachedAlertsList.filter {
                                    (now - it.timestampMs) < 30 * 60 * 1000L
                                }
                                if (recentAlerts.isNotEmpty()) {
                                    RegionAlertTracker.processAlerts(recentAlerts)
                                }
                            }
                            updateLiveModeDisplay()
                        } else {
                            // Normal mode: history blocks
                            val newFingerprint = alertsFingerprint()
                            val hasNewAlerts = newFingerprint != lastKnownAlertFingerprint && lastKnownAlertFingerprint.isNotEmpty()
                            lastKnownAlertFingerprint = newFingerprint

                            if (cachedAlertsList.isNotEmpty()) {
                                tvActiveAlerts.text = ""
                                if (hasNewAlerts || threatChanged) {
                                    displayCachedAlertsGroup(0, forceRebuild = true)
                                    if (shouldStartCycler()) startAutoCycle()
                                } else if (alertBlocksContainer.childCount <= 1) {
                                    displayCachedAlertsGroup(currentMinuteGroupIndex, forceRebuild = true)
                                    if (shouldStartCycler()) startAutoCycle()
                                }
                                showAlertBlocks(true)
                            } else {
                                stopAutoCycle()
                                tvActiveAlerts.text = getString(R.string.active_alerts_none)
                                showAlertBlocks(false)
                                miniMapView.overlays.clear()
                                miniMapView.invalidate()
                            }
                        }
                    }
                }
            }
        }.start()
    }

    /**
     * Lightweight background history fetch — merges new alerts into cache
     * and re-feeds the state machine so the threat banner updates without
     * the user having to press "Refetch".
     */
    private fun backgroundHistoryRefresh() {
        Thread {
            val ctx = context ?: return@Thread
            val historyAlerts = RedAlertService.fetchHistory()
            if (historyAlerts.isNotEmpty()) {
                AlertCacheService.saveBatch(ctx, historyAlerts)
                handler.post {
                    if (!isAdded) return@post
                    loadCachedAlerts()
                    feedRecentCachedAlertsToStateMachine()
                    updateThreatBanner()
                    val newFp = alertsFingerprint()
                    if (newFp != lastKnownAlertFingerprint) {
                        lastKnownAlertFingerprint = newFp
                        if (isLiveMode()) {
                            // Only feed recent alerts (last 30 min) — not full 24h cache
                            val now = System.currentTimeMillis()
                            val recentAlerts = cachedAlertsList.filter {
                                (now - it.timestampMs) < 30 * 60 * 1000L
                            }
                            RegionAlertTracker.processAlerts(recentAlerts)
                            updateLiveModeDisplay()
                        } else {
                            displayCachedAlertsGroup(0, forceRebuild = true)
                        }
                    }
                }
            }
        }.start()
    }

    private fun clearAlerts() {
        // Don't delete cache — just mark "cleared at" so only newer alerts show
        clearedAtMs = System.currentTimeMillis()

        loadCachedAlerts() // Will filter out alerts older than clearedAtMs
        lastKnownAlertFingerprint = alertsFingerprint()
        stopAutoCycle()

        tvActiveAlerts.text = getString(R.string.active_alerts_none)
        showAlertBlocks(false)
        miniMapView.overlays.clear()
        miniMapView.invalidate()
    }

    private fun refetch24h() {
        pbAlertsLoading.visibility = View.VISIBLE
        btnRefreshAlerts.isEnabled = false
        btnClearAlerts.isEnabled = false
        btnRefetch24h.isEnabled = false
        clearedAtMs = 0  // Reset clear filter — show all alerts again

        Thread {
            android.util.Log.d("StatusFragment", "Refetch 24h: Fetching history...")
            val historyAlerts = RedAlertService.fetchHistory()
            android.util.Log.d("StatusFragment", "Refetch 24h: Got ${historyAlerts.size} history alerts")

            // Batch save all at once (clears old cache and writes single time)
            val ctx = context ?: return@Thread
            AlertCacheService.saveBatch(ctx, historyAlerts)

            handler.post {
                if (!isAdded) return@post
                pbAlertsLoading.visibility = View.GONE
                btnRefreshAlerts.isEnabled = true
                btnClearAlerts.isEnabled = true
                btnRefetch24h.isEnabled = true

                lastAlertUpdateMs = System.currentTimeMillis()
                updateAlertTimestamp()

                loadCachedAlerts()
                updateThreatBanner()

                val count = historyAlerts.size
                android.widget.Toast.makeText(
                    requireContext(),
                    if (count > 0) getString(R.string.fetched_alerts_format, count) else getString(R.string.no_alerts_last_24h),
                    android.widget.Toast.LENGTH_SHORT
                ).show()

                if (alertsByMinute.isNotEmpty()) {
                    displayCachedAlertsGroup(currentMinuteGroupIndex)
                    miniMapContainer.visibility = View.VISIBLE
                    showAlertBlocks(true)
                    if (shouldStartCycler()) {
                        startAutoCycle()
                    }
                } else {
                    tvActiveAlerts.text = getString(R.string.active_alerts_none)
                    showAlertBlocks(false)
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
            updateBlocksTimestamp()
            refreshBlockHeaderTimes()
            // Refresh mini-map overlay so its header "time ago" updates too
            miniMapView.invalidate()
        }
    }

    private fun updateMiniMap(alert: RedAlertService.ActiveAlert) {
        val ctx = context ?: return
        val selectedRegions = getSelectedRegions().toSet()
        val showOther = prefs.getBoolean("show_other_regions", true)
        val regions = if (!showOther && selectedRegions.isNotEmpty())
            alert.regions.filter { it in selectedRegions } else alert.regions
        val color = getAlertTypeColor(alert.type)
        val regionData = regions.map { MapRegionData(it, color, 0.35f, it in selectedRegions) }
        val header = getString(R.string.map_now_header, alert.title)
        drawMiniMapPolygons(regionData, true, header)
    }

    private fun updateMiniMapFromCached(alert: AlertCacheService.CachedAlert) {
        val ctx = context ?: return
        val selectedRegions = getSelectedRegions().toSet()
        val showOther = prefs.getBoolean("show_other_regions", true)
        val regions = if (!showOther && selectedRegions.isNotEmpty())
            alert.regions.filter { it in selectedRegions } else alert.regions
        val color = getAlertTypeColor(alert.type)
        val regionData = regions.map { MapRegionData(it, color, 0.25f, it in selectedRegions) }
        val ago = AlertCacheService.formatTimeAgo(ctx, alert.timestampMs)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val header = "${alert.title} ${timeFmt.format(Date(alert.timestampMs))} ($ago)"
        drawMiniMapPolygons(regionData, false, header)
    }

    private data class MapRegionData(
        val region: String,
        val color: Int,
        val fillAlpha: Float,
        val isSelected: Boolean
    )

    private fun updateThreatBanner() {
        val ctx = context ?: return
        val state = AlertStateMachine.getState(ctx)
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
            AlertStateMachine.ThreatLevel.EVENT_ENDED -> {
                threatBanner.visibility = View.VISIBLE
                threatBanner.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50")) // Green
                tvThreatLevel.text = getString(R.string.threat_event_ended)
                tvThreatLevel.setTextColor(android.graphics.Color.WHITE)
                tvThreatRegions.text = getString(R.string.threat_regions, state.regions.joinToString(", "))
                tvThreatRegions.setTextColor(android.graphics.Color.WHITE)
                tvThreatSince.setTextColor(android.graphics.Color.parseColor("#C8E6C9"))
                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                tvThreatSince.text = getString(R.string.threat_since, fmt.format(Date(state.since)))
            }
        }
    }

    private fun getAlertTypeColor(type: String): Int {
        return when (type.lowercase()) {
            "alarm" -> android.graphics.Color.RED
            "warning" -> android.graphics.Color.parseColor("#FFC107")  // Yellow
            "event_ended" -> android.graphics.Color.parseColor("#4CAF50") // Green
            // Legacy type names (old cached data)
            "missile", "aircraft" -> android.graphics.Color.RED
            "event" -> android.graphics.Color.parseColor("#FFC107")    // Legacy warning
            else -> android.graphics.Color.RED
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
        val newIndex = if (currentMinuteGroupIndex > 0) currentMinuteGroupIndex - 1 else alertsByMinute.size - 1
        displayCachedAlertsGroup(newIndex)
    }

    private fun cycleNextAlert() {
        if (alertsByMinute.isEmpty()) return
        val newIndex = (currentMinuteGroupIndex + 1) % alertsByMinute.size
        android.util.Log.d("Cycler", "next: current=$currentMinuteGroupIndex new=$newIndex total=${alertsByMinute.size} blocks=${alertBlocksContainer.childCount}")
        displayCachedAlertsGroup(newIndex)
    }

    private fun displayCachedAlert(index: Int) {
        // Legacy method - show first group
        if (alertsByMinute.isEmpty()) return
        currentMinuteGroupIndex = 0
        displayCachedAlertsGroup(0)
    }

    private fun displayCachedAlertsGroup(groupIndex: Int, isUserClick: Boolean = false, forceRebuild: Boolean = false) {
        if (groupIndex < 0 || groupIndex >= alertsByMinute.size) return
        currentMinuteGroupIndex = groupIndex

        if (forceRebuild || alertBlocksContainer.childCount <= 1) {
            displayAllAlertBlocks()
        } else {
            // Fast path: just update highlights without rebuilding
            refreshBlockHighlights()
        }
        showAlertBlocks(true)
        scrollToHighlightedBlock()

        // Update mini map with current group
        val group = alertsByMinute[groupIndex]
        updateMiniMapFromCachedGroup(group)

        // Zoom to fit alert regions based on setting
        val zoomMode = prefs.getString("map_zoom_mode", "off") ?: "off"
        val shouldZoom = when (zoomMode) {
            "click" -> isUserClick
            "auto" -> true
            else -> false
        }
        if (shouldZoom) zoomMiniMapToFitGroup(group)
    }

    private fun showAlertBlocks(visible: Boolean) {
        if (visible) {
            alertBlocksContainer.visibility = View.VISIBLE
            alertsHeaderSection.visibility = View.GONE
            tvActiveAlerts.visibility = View.GONE
        } else {
            alertBlocksContainer.visibility = View.GONE
            alertsHeaderSection.visibility = View.VISIBLE
            tvActiveAlerts.visibility = View.VISIBLE
        }
    }

    /** Fingerprint of the latest few alerts — changes only when genuinely new alerts arrive */
    private fun alertsFingerprint(): String {
        val top = cachedAlertsList.take(3)
        return top.joinToString("|") { "${it.timestampMs}:${it.title}:${it.regions.size}" }
    }

    /** Refresh the "X min ago" text in all block headers */
    private fun refreshBlockHeaderTimes() {
        val ctx = context ?: return
        val groupingMode = prefs.getString("history_grouping", "tiered") ?: "tiered"
        for ((headerView, group) in blockHeaderViews) {
            if (group.isEmpty()) continue
            val headerText = if (groupingMode == "all") {
                val candleMs = prefs.getLong("hebcal_candle_ms", 0)
                val havdalahMs = prefs.getLong("hebcal_havdalah_ms", 0)
                val now = System.currentTimeMillis()
                val isInShabbat = candleMs > 0 && havdalahMs > 0 && now in candleMs..havdalahMs
                val label = if (isInShabbat) getString(R.string.history_since_shabbat) else getString(R.string.history_all_24h)
                "$label (${group.size})"
            } else {
                val header = AlertCacheService.formatGroupHeader(ctx, group)
                val countStr = AlertCacheService.formatGroupCount(ctx, group)
                val titles = group.map { it.title }.distinct().joinToString(", ")
                "$header$countStr — $titles"
            }
            headerView.text = headerText
        }
    }

    private fun updateBlocksTimestamp() {
        val pollSec = prefs.getInt("poll_frequency_seconds", 30)
        tvBlocksUpdatedTime?.text = if (pollSec > 0) "⟳ ${pollSec}s" else ""
    }


    /** Update block highlight styling without rebuilding the view tree */
    private fun refreshBlockHighlights() {
        val dp2 = (2 * resources.displayMetrics.density).toInt()
        for (i in 0 until alertBlocksContainer.childCount) {
            val row = alertBlocksContainer.getChildAt(i)
            val idx = row.tag as? Int ?: continue
            val highlighted = idx == currentMinuteGroupIndex
            // Content block is the last child of the row
            val content = (row as? LinearLayout)?.getChildAt(row.childCount - 1) as? LinearLayout ?: continue
            val bg = content.background as? android.graphics.drawable.GradientDrawable ?: continue
            val blockColor = if (idx < alertsByMinute.size) getAlertTypeColor(alertsByMinute[idx].first().type)
                else android.graphics.Color.RED
            if (highlighted) bg.setStroke(dp2 + 1, android.graphics.Color.WHITE)
            else bg.setStroke(1, blockColor)
            // Bold header
            (content.getChildAt(0) as? TextView)?.setTypeface(null,
                if (highlighted) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    private fun scrollToHighlightedBlock() {
        val legendOffset = if (alertBlocksContainer.childCount > alertsByMinute.size) 1 else 0
        val childIndex = currentMinuteGroupIndex + legendOffset
        if (childIndex < 0 || childIndex >= alertBlocksContainer.childCount) return
        val block = alertBlocksContainer.getChildAt(childIndex) ?: return
        val scrollView = alertBlocksContainer.parent?.parent as? android.widget.ScrollView ?: return

        val doScroll = {
            val blockTop = block.top + alertBlocksContainer.top
            val blockBottom = blockTop + block.height
            val scrollY = scrollView.scrollY
            val scrollHeight = scrollView.height
            if (blockBottom > scrollY + scrollHeight || blockTop < scrollY) {
                scrollView.smoothScrollTo(0, blockTop)
            }
        }

        // If block has been laid out (height > 0), scroll immediately via post
        // Otherwise wait for layout (happens after full rebuild)
        if (block.height > 0) {
            block.post { doScroll() }
        } else {
            alertBlocksContainer.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    alertBlocksContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    doScroll()
                }
            })
        }
    }

    private fun buildColorLegend(ctx: android.content.Context): View {
        val density = resources.displayMetrics.density
        val dp2 = (2 * density).toInt()
        val dp3 = (3 * density).toInt()
        val dotSize = (8 * density).toInt()

        val items = listOf(
            getString(R.string.legend_missile) to android.graphics.Color.RED,
            getString(R.string.legend_warning) to android.graphics.Color.parseColor("#FFC107"),
            getString(R.string.legend_ended) to android.graphics.Color.parseColor("#4CAF50"),
        )

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp3) }

            for ((i, pair) in items.withIndex()) {
                val (label, color) = pair
                addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                        setMargins(if (i == 0) 0 else dp3, 0, dp2, 0)
                    }
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(color)
                    }
                })
                addView(TextView(ctx).apply {
                    text = label
                    textSize = 7f
                    setSingleLine(true)
                })
            }
        }
    }

    /** Build all alert group blocks in the container, highlighting the current cycling one */
    private fun displayAllAlertBlocks() {
        val ctx = context ?: return
        alertBlocksContainer.removeAllViews()
        blockHeaderViews.clear()

        if (alertsByMinute.isEmpty()) return

        // Legend + Updated timestamp on one row
        val legendRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, (3 * resources.displayMetrics.density).toInt()) }
        }
        legendRow.addView(buildColorLegend(ctx))
        // Spacer
        legendRow.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })
        tvBlocksUpdatedTime = TextView(ctx).apply {
            textSize = 7f
            alpha = 0.5f
        }
        updateBlocksTimestamp()
        legendRow.addView(tvBlocksUpdatedTime)
        alertBlocksContainer.addView(legendRow)

        val showOther = prefs.getBoolean("show_other_regions", true)
        val selectedRegions = getSelectedRegions().toSet()
        val userLat = prefs.getFloat("latitude", 31.7683f).toDouble()
        val userLon = prefs.getFloat("longitude", 35.2137f).toDouble()
        val userRegion = OrefPolygons.findRegionContaining(ctx, userLat, userLon)
        val groupingMode = prefs.getString("history_grouping", "tiered") ?: "tiered"
        val maxRegionsShown = 6 // Truncate region list after this many

        for ((idx, group) in alertsByMinute.withIndex()) {
            if (group.isEmpty()) continue
            val isHighlighted = idx == currentMinuteGroupIndex

            // Block is single-type (split by type in loadCachedAlerts)
            val blockType = group.first().type
            val blockColor = getAlertTypeColor(blockType)
            android.util.Log.d("StatusBlocks", "Block $idx: type=$blockType color=${Integer.toHexString(blockColor)} count=${group.size} title=${group.first().title}")

            // Build header text
            val headerText = if (groupingMode == "all") {
                val candleMs = prefs.getLong("hebcal_candle_ms", 0)
                val havdalahMs = prefs.getLong("hebcal_havdalah_ms", 0)
                val now = System.currentTimeMillis()
                val isInShabbat = candleMs > 0 && havdalahMs > 0 && now in candleMs..havdalahMs
                val label = if (isInShabbat) getString(R.string.history_since_shabbat) else getString(R.string.history_all_24h)
                "$label (${group.size})"
            } else {
                val header = AlertCacheService.formatGroupHeader(ctx, group)
                val countStr = AlertCacheService.formatGroupCount(ctx, group)
                val titles = group.map { it.title }.distinct().joinToString(", ")
                "$header$countStr — $titles"
            }

            // Get regions (filtered, selected first)
            val allRegions = group.flatMap { it.regions }.distinct().let { regions ->
                if (!showOther && selectedRegions.isNotEmpty()) regions.filter { it in selectedRegions }
                else regions
            }.sortedByDescending { it in selectedRegions }

            // Build region text with selected regions bold
            val displayRegions = if (allRegions.size > maxRegionsShown)
                allRegions.take(maxRegionsShown) else allRegions
            val suffix = if (allRegions.size > maxRegionsShown) "… (+${allRegions.size - maxRegionsShown})" else ""

            val regionSpannable = android.text.SpannableStringBuilder()
            for ((ri, region) in displayRegions.withIndex()) {
                if (ri > 0) regionSpannable.append(", ")
                val start = regionSpannable.length
                regionSpannable.append(region)
                if (region in selectedRegions || region == userRegion) {
                    regionSpannable.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        start, regionSpannable.length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            if (suffix.isNotEmpty()) regionSpannable.append(suffix)

            // Check if block contains selected / current-location regions
            val hasSelectedRegion = allRegions.any { it in selectedRegions }
            val hasUserRegion = userRegion != null && userRegion in allRegions

            val density = resources.displayMetrics.density
            val dp4 = (4 * density).toInt()
            val dp6 = (6 * density).toInt()
            val dp2 = (2 * density).toInt()
            val dp3 = (3 * density).toInt()

            // Outer container: horizontal — optional left strip + content
            val row = LinearLayout(ctx).apply {
                tag = idx  // Used by updateAllBlockHighlights to identify block rows
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, dp4) }

                setOnClickListener {
                    currentMinuteGroupIndex = idx
                    displayCachedAlertsGroup(idx, isUserClick = true)
                }
            }

            // Left colored strip for selected/current-location blocks
            if (hasUserRegion || hasSelectedRegion) {
                val stripColor = if (hasUserRegion) android.graphics.Color.parseColor("#2196F3")
                    else android.graphics.Color.parseColor("#9C27B0")
                val strip = View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp3, LinearLayout.LayoutParams.MATCH_PARENT)
                    val stripBg = android.graphics.drawable.GradientDrawable()
                    stripBg.setColor(stripColor)
                    stripBg.cornerRadii = floatArrayOf(dp4.toFloat(), dp4.toFloat(), 0f, 0f, 0f, 0f, dp4.toFloat(), dp4.toFloat())
                    background = stripBg
                }
                row.addView(strip)
            }

            // Content block
            val block = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp6, dp4, dp6, dp4)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                val bg = android.graphics.drawable.GradientDrawable()
                val cornerRadii = if (hasUserRegion || hasSelectedRegion)
                    floatArrayOf(0f, 0f, dp4.toFloat(), dp4.toFloat(), dp4.toFloat(), dp4.toFloat(), 0f, 0f)
                else
                    floatArrayOf(dp4.toFloat(), dp4.toFloat(), dp4.toFloat(), dp4.toFloat(), dp4.toFloat(), dp4.toFloat(), dp4.toFloat(), dp4.toFloat())
                bg.cornerRadii = cornerRadii
                val fadedColor = android.graphics.Color.argb(40,
                    android.graphics.Color.red(blockColor),
                    android.graphics.Color.green(blockColor),
                    android.graphics.Color.blue(blockColor))
                bg.setColor(fadedColor)
                if (isHighlighted) {
                    bg.setStroke(dp2 + 1, android.graphics.Color.WHITE)
                } else {
                    bg.setStroke(1, blockColor)
                }
                background = bg
            }

            // Header
            val headerView = TextView(ctx).apply {
                text = headerText
                textSize = 10f
                setTypeface(null, if (isHighlighted) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            block.addView(headerView)
            blockHeaderViews.add(headerView to group)

            // Region text with selected regions bold
            block.addView(TextView(ctx).apply {
                text = regionSpannable
                textSize = 9f
                alpha = 0.8f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            row.addView(block)
            alertBlocksContainer.addView(row)
        }
    }

    private fun updateMiniMapFromCachedGroup(alerts: List<AlertCacheService.CachedAlert>) {
        val ctx = context ?: return
        val selectedRegions = getSelectedRegions().toSet()
        val showOther = prefs.getBoolean("show_other_regions", true)

        // Collect region→color: process oldest-first so latest alert wins
        // (but yellow doesn't cancel red/orange)
        val regionMap = mutableMapOf<String, MapRegionData>()
        for (alert in alerts.asReversed()) {
            val regions = if (!showOther && selectedRegions.isNotEmpty())
                alert.regions.filter { it in selectedRegions } else alert.regions
            val color = getAlertTypeColor(alert.type)
            for (region in regions) {
                val existing = regionMap[region]
                if (existing == null || shouldOverwriteColor(existing.color, color)) {
                    regionMap[region] = MapRegionData(region, color, 0.25f, region in selectedRegions)
                }
            }
        }

        val groupingMode = prefs.getString("history_grouping", "tiered") ?: "tiered"
        val header = if (groupingMode == "all") {
            val candleMs = prefs.getLong("hebcal_candle_ms", 0)
            val havdalahMs = prefs.getLong("hebcal_havdalah_ms", 0)
            val now = System.currentTimeMillis()
            val isInShabbat = candleMs > 0 && havdalahMs > 0 && now in candleMs..havdalahMs
            val label = if (isInShabbat) getString(R.string.history_since_shabbat) else getString(R.string.history_all_24h)
            "$label (${alerts.size})"
        } else {
            val h = AlertCacheService.formatGroupHeader(ctx, alerts)
            val c = AlertCacheService.formatGroupCount(ctx, alerts)
            val titles = alerts.map { it.title }.distinct().joinToString(", ")
            "$titles $h$c"
        }
        drawMiniMapPolygons(regionMap.values.toList(), false, header)
    }

    /** Unified polygon drawing for the mini map */
    private fun drawMiniMapPolygons(
        regionData: List<MapRegionData>,
        isActive: Boolean,
        headerText: String?
    ) {
        val ctx = context ?: return
        miniMapView.overlays.removeAll { it !is org.osmdroid.views.overlay.compass.CompassOverlay }

        val userLat = prefs.getFloat("latitude", 31.7683f).toDouble()
        val userLon = prefs.getFloat("longitude", 35.2137f).toDouble()

        // Draw selected region borders (outline only, no fill) as base layer
        val selectedRegions = getSelectedRegions().toSet()
        for (region in selectedRegions) {
            val polyPoints = OrefPolygons.getPolygon(ctx, region) ?: continue
            if (polyPoints.size < 3) continue
            val border = org.osmdroid.views.overlay.Polygon(miniMapView)
            border.points = polyPoints
            border.fillPaint.color = android.graphics.Color.TRANSPARENT
            border.outlinePaint.color = android.graphics.Color.parseColor("#9C27B0") // Purple
            border.outlinePaint.strokeWidth = 2f
            border.outlinePaint.alpha = 180
            miniMapView.overlays.add(border)
        }

        // Draw current location region border (blue, thicker)
        val userRegion = OrefPolygons.findRegionContaining(ctx, userLat, userLon)
        if (userRegion != null) {
            val polyPoints = OrefPolygons.getPolygon(ctx, userRegion)
            if (polyPoints != null && polyPoints.size >= 3) {
                val border = org.osmdroid.views.overlay.Polygon(miniMapView)
                border.points = polyPoints
                border.fillPaint.color = android.graphics.Color.TRANSPARENT
                border.outlinePaint.color = android.graphics.Color.parseColor("#2196F3") // Blue
                border.outlinePaint.strokeWidth = 4f
                border.outlinePaint.alpha = 220
                miniMapView.overlays.add(border)
            }
        }

        // Deduplicate: latest alert per region wins, but yellow doesn't cancel red/orange
        val regionMap = mutableMapOf<String, MapRegionData>()
        for (rd in regionData) {
            val existing = regionMap[rd.region]
            if (existing == null || shouldOverwriteColor(existing.color, rd.color)) {
                regionMap[rd.region] = rd
            }
        }

        // Identify newly added regions for blink animation
        val currentRegionNames = regionMap.keys.toSet()
        val newRegionNames = currentRegionNames - previousMiniMapRegions
        previousMiniMapRegions.clear()
        previousMiniMapRegions.addAll(currentRegionNames)

        // Draw alert polygon overlays, collecting new ones for blink
        val newPolygons = mutableListOf<org.osmdroid.views.overlay.Polygon>()
        for ((_, rd) in regionMap) {
            val polyPoints = OrefPolygons.getPolygon(ctx, rd.region)
            if (polyPoints != null && polyPoints.size >= 3) {
                val polygon = org.osmdroid.views.overlay.Polygon(miniMapView)
                polygon.points = polyPoints
                polygon.fillPaint.color = rd.color
                polygon.fillPaint.alpha = (rd.fillAlpha * 255).toInt()
                polygon.outlinePaint.color = if (rd.isSelected) android.graphics.Color.parseColor("#9C27B0") else rd.color
                polygon.outlinePaint.strokeWidth = if (rd.isSelected) 3f else 1.5f
                polygon.outlinePaint.alpha = 200
                miniMapView.overlays.add(polygon)
                if (rd.region in newRegionNames) newPolygons.add(polygon)
            } else {
                // Fallback dot
                val coords = OrefRegionCoords.coords[rd.region] ?: continue
                val dotOverlay = object : org.osmdroid.views.overlay.Overlay() {
                    override fun draw(canvas: android.graphics.Canvas?, mapView: MapView?, shadow: Boolean) {
                        if (shadow || canvas == null || mapView == null) return
                        val pj = mapView.projection ?: return
                        val paint = android.graphics.Paint().apply { isAntiAlias = true }
                        val pos = pj.toPixels(GeoPoint(coords.first, coords.second), null)
                        val radius = if (rd.isSelected) 10f else 8f
                        paint.color = rd.color; paint.style = android.graphics.Paint.Style.FILL
                        paint.alpha = (rd.fillAlpha * 255).toInt()
                        canvas.drawCircle(pos.x.toFloat(), pos.y.toFloat(), radius, paint)
                        if (rd.isSelected) {
                            paint.color = android.graphics.Color.parseColor("#9C27B0"); paint.style = android.graphics.Paint.Style.STROKE
                            paint.strokeWidth = 2f; paint.alpha = 255
                            canvas.drawCircle(pos.x.toFloat(), pos.y.toFloat(), radius + 1f, paint)
                        }
                    }
                }
                miniMapView.overlays.add(dotOverlay)
            }
        }

        // Header overlay — computes "time ago" dynamically on each draw
        val headerAlerts = regionData.map { it.region } // just for reference
        val headerOverlay = object : org.osmdroid.views.overlay.Overlay() {
            override fun draw(canvas: android.graphics.Canvas?, mapView: MapView?, shadow: Boolean) {
                if (shadow || canvas == null || mapView == null) return
                val paint = android.graphics.Paint().apply { isAntiAlias = true }

                // Recompute header text with fresh "time ago"
                val currentHeader = if (isActive) headerText else {
                    val c = context ?: return
                    if (currentMinuteGroupIndex in alertsByMinute.indices) {
                        val grp = alertsByMinute[currentMinuteGroupIndex]
                        val gm = prefs.getString("history_grouping", "tiered") ?: "tiered"
                        if (gm == "all") headerText else {
                            val titles = grp.map { it.title }.distinct().joinToString(", ")
                            val h = AlertCacheService.formatGroupHeader(c, grp)
                            val cnt = AlertCacheService.formatGroupCount(c, grp)
                            "$titles $h$cnt"
                        }
                    } else headerText
                }

                if (currentHeader != null) {
                    paint.color = if (isActive) android.graphics.Color.parseColor("#CC000000")
                        else android.graphics.Color.parseColor("#CC333333")
                    paint.style = android.graphics.Paint.Style.FILL
                    canvas.drawRect(0f, 0f, canvas.width.toFloat(), 40f, paint)
                    paint.color = if (isActive) android.graphics.Color.RED else android.graphics.Color.WHITE
                    paint.textSize = 22f
                    paint.typeface = if (isActive) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                    canvas.drawText(currentHeader, 8f, 30f, paint)
                    paint.typeface = android.graphics.Typeface.DEFAULT
                }
            }
        }
        miniMapView.overlays.add(headerOverlay)
        miniMapView.invalidate()

        // Blink newly added regions to draw attention
        if (newPolygons.isNotEmpty()) {
            blinkPolygons(newPolygons, regionMap.filter { it.key in newRegionNames }.values.map { it.fillAlpha })
        }
    }

    /** Flash new polygon overlays: bright → dim → bright → settle at normal alpha */
    private fun blinkPolygons(polygons: List<org.osmdroid.views.overlay.Polygon>, normalAlphas: List<Float>) {
        blinkRunnable?.let { handler.removeCallbacks(it) }
        val normalAlpha = if (normalAlphas.isNotEmpty()) (normalAlphas.first() * 255).toInt() else 89
        var step = 0
        val alphaSequence = intArrayOf(255, 30, 220, 30, normalAlpha) // bright-dim-bright-dim-settle
        blinkRunnable = object : Runnable {
            override fun run() {
                if (step >= alphaSequence.size) return
                val alpha = alphaSequence[step]
                for (p in polygons) {
                    p.fillPaint.alpha = alpha
                    p.outlinePaint.alpha = if (alpha < 50) 50 else 200
                }
                miniMapView.invalidate()
                step++
                if (step < alphaSequence.size) {
                    handler.postDelayed(this, 250)
                }
            }
        }
        handler.post(blinkRunnable!!)
    }

    /**
     * Whether a new (later) alert color should overwrite the existing one for a region.
     * Rule: yellow (warning) does NOT cancel red/orange (active missile/aircraft).
     * Only green (event_ended) or another red/orange can overwrite red/orange.
     */
    private fun shouldOverwriteColor(existingColor: Int, newColor: Int): Boolean {
        val isExistingActive = existingColor == android.graphics.Color.RED
        val isNewWarning = newColor == android.graphics.Color.parseColor("#FFC107")
        // Yellow doesn't cancel red
        return !(isExistingActive && isNewWarning)
    }

    private fun isLiveMode(): Boolean {
        return prefs.getString("cycler_mode", "off") == "live"
    }

    /** Initialize live mode — rebuild region states from recent cache */
    private fun initLiveMode() {
        val ctx = context ?: return
        RegionAlertTracker.clear()
        // Feed only recent alerts (last 30 min) — older ones are stale
        val now = System.currentTimeMillis()
        val recentAlerts = AlertCacheService.getLast24Hours(ctx)
            .filter { (now - it.timestampMs) < 30 * 60 * 1000L }
        RegionAlertTracker.processAlerts(recentAlerts)
        RegionAlertTracker.processTick()
        updateLiveModeDisplay()
    }

    /** Update the live mode map and block display */
    private fun updateLiveModeDisplay() {
        val ctx = context ?: return
        RegionAlertTracker.processTick()

        val activeRegions = RegionAlertTracker.getActiveRegions()
        val selectedRegions = getSelectedRegions().toSet()
        val showOther = prefs.getBoolean("show_other_regions", true)

        // Build polygon data for all active regions
        val regionData = activeRegions.mapNotNull { (region, state) ->
            // Respect filters: show selected regions always, others only if showOther
            if (!showOther && selectedRegions.isNotEmpty() && region !in selectedRegions) return@mapNotNull null
            val color = RegionAlertTracker.getLevelColor(state.level)
            if (color == android.graphics.Color.TRANSPARENT) return@mapNotNull null
            MapRegionData(region, color, 0.35f, region in selectedRegions)
        }

        // Draw on mini map
        val header = if (activeRegions.isEmpty()) getString(R.string.active_alerts_none)
            else {
                val alarmCount = activeRegions.count { it.value.level == RegionAlertTracker.RegionLevel.ALARM }
                val warningCount = activeRegions.count { it.value.level == RegionAlertTracker.RegionLevel.WARNING }
                val endedCount = activeRegions.count { it.value.level == RegionAlertTracker.RegionLevel.EVENT_ENDED }
                buildString {
                    append("LIVE")
                    if (alarmCount > 0) append(" 🔴$alarmCount")
                    if (warningCount > 0) append(" 🟡$warningCount")
                    if (endedCount > 0) append(" 🟢$endedCount")
                }
            }
        drawMiniMapPolygons(regionData, activeRegions.any { it.value.level == RegionAlertTracker.RegionLevel.ALARM }, header)

        // Update blocks area with live summary
        updateLiveModeBlocks(activeRegions, selectedRegions)

        miniMapContainer.visibility = View.VISIBLE
        showAlertBlocks(if (activeRegions.isNotEmpty()) true else false)
        tvActiveAlerts.text = ""
    }

    /** Build live mode blocks — grouped by alert level */
    private fun updateLiveModeBlocks(
        activeRegions: Map<String, RegionAlertTracker.RegionState>,
        selectedRegions: Set<String>
    ) {
        val ctx = context ?: return
        alertBlocksContainer.removeAllViews()
        blockHeaderViews.clear()

        if (activeRegions.isEmpty()) return

        // Legend
        val legendRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, (3 * resources.displayMetrics.density).toInt()) }
        }
        legendRow.addView(buildColorLegend(ctx))
        legendRow.addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        })
        tvBlocksUpdatedTime = TextView(ctx).apply { textSize = 7f; alpha = 0.5f }
        updateBlocksTimestamp()
        legendRow.addView(tvBlocksUpdatedTime)
        alertBlocksContainer.addView(legendRow)

        val density = resources.displayMetrics.density
        val dp4 = (4 * density).toInt()
        val dp6 = (6 * density).toInt()
        val dp3 = (3 * density).toInt()
        val dp2 = (2 * density).toInt()

        // Group active regions by level
        val byLevel = activeRegions.entries.groupBy { it.value.level }
        val levelOrder = listOf(
            RegionAlertTracker.RegionLevel.ALARM,
            RegionAlertTracker.RegionLevel.WARNING,
            RegionAlertTracker.RegionLevel.EVENT_ENDED
        )

        for (level in levelOrder) {
            val regions = byLevel[level] ?: continue
            val color = RegionAlertTracker.getLevelColor(level)
            val levelName = when (level) {
                RegionAlertTracker.RegionLevel.ALARM -> getString(R.string.legend_missile)
                RegionAlertTracker.RegionLevel.WARNING -> getString(R.string.legend_warning)
                RegionAlertTracker.RegionLevel.EVENT_ENDED -> getString(R.string.legend_ended)
                else -> ""
            }
            val regionNames = regions.map { it.key }
                .sortedByDescending { it in selectedRegions }

            val userLat = prefs.getFloat("latitude", 31.7683f).toDouble()
            val userLon = prefs.getFloat("longitude", 35.2137f).toDouble()
            val userRegion = OrefPolygons.findRegionContaining(ctx, userLat, userLon)
            val hasSelected = regionNames.any { it in selectedRegions }
            val hasUser = userRegion != null && userRegion in regionNames

            // Row with optional left strip
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, dp4) }
            }

            if (hasUser || hasSelected) {
                val stripColor = if (hasUser) android.graphics.Color.parseColor("#2196F3")
                    else android.graphics.Color.parseColor("#9C27B0")
                row.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp3, LinearLayout.LayoutParams.MATCH_PARENT)
                    val bg = android.graphics.drawable.GradientDrawable()
                    bg.setColor(stripColor)
                    bg.cornerRadii = floatArrayOf(dp4.toFloat(), dp4.toFloat(), 0f, 0f, 0f, 0f, dp4.toFloat(), dp4.toFloat())
                    background = bg
                })
            }

            val block = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp6, dp4, dp6, dp4)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                val bg = android.graphics.drawable.GradientDrawable()
                val cornerRadii = if (hasUser || hasSelected)
                    floatArrayOf(0f, 0f, dp4.toFloat(), dp4.toFloat(), dp4.toFloat(), dp4.toFloat(), 0f, 0f)
                else floatArrayOf(dp4.toFloat(), dp4.toFloat(), dp4.toFloat(), dp4.toFloat(), dp4.toFloat(), dp4.toFloat(), dp4.toFloat(), dp4.toFloat())
                bg.cornerRadii = cornerRadii
                bg.setColor(android.graphics.Color.argb(40,
                    android.graphics.Color.red(color),
                    android.graphics.Color.green(color),
                    android.graphics.Color.blue(color)))
                bg.setStroke(1, color)
                background = bg
            }

            block.addView(TextView(ctx).apply {
                text = "$levelName (${regionNames.size})"
                textSize = 10f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })

            // Region text with bold for selected
            val regionSpan = android.text.SpannableStringBuilder()
            val maxShow = 8
            val displayRegions = if (regionNames.size > maxShow) regionNames.take(maxShow) else regionNames
            for ((i, r) in displayRegions.withIndex()) {
                if (i > 0) regionSpan.append(", ")
                val start = regionSpan.length
                regionSpan.append(r)
                if (r in selectedRegions || r == userRegion) {
                    regionSpan.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        start, regionSpan.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            if (regionNames.size > maxShow) regionSpan.append("… (+${regionNames.size - maxShow})")

            block.addView(TextView(ctx).apply {
                text = regionSpan
                textSize = 9f
                alpha = 0.8f
                maxLines = 3
            })

            row.addView(block)
            alertBlocksContainer.addView(row)
        }
    }

    /** Zoom the mini map to fit all regions in the given alert group */
    private fun zoomMiniMapToFitGroup(group: List<AlertCacheService.CachedAlert>) {
        if (group.isEmpty()) return
        val showOther = prefs.getBoolean("show_other_regions", true)
        val selectedRegions = getSelectedRegions().toSet()

        val allRegions = group.flatMap { it.regions }.distinct().let { regions ->
            if (!showOther && selectedRegions.isNotEmpty()) regions.filter { it in selectedRegions }
            else regions
        }
        if (allRegions.isEmpty()) return

        // Collect all polygon points for the bounding box
        val ctx = context ?: return
        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
        var hasPoints = false

        for (region in allRegions) {
            val polyPoints = OrefPolygons.getPolygon(ctx, region)
            if (polyPoints != null) {
                for (p in polyPoints) {
                    minLat = minOf(minLat, p.latitude); maxLat = maxOf(maxLat, p.latitude)
                    minLon = minOf(minLon, p.longitude); maxLon = maxOf(maxLon, p.longitude)
                    hasPoints = true
                }
            } else {
                val coords = OrefRegionCoords.coords[region] ?: continue
                minLat = minOf(minLat, coords.first); maxLat = maxOf(maxLat, coords.first)
                minLon = minOf(minLon, coords.second); maxLon = maxOf(maxLon, coords.second)
                hasPoints = true
            }
        }
        if (!hasPoints) return

        // Add padding
        val latPad = (maxLat - minLat) * 0.15 + 0.01
        val lonPad = (maxLon - minLon) * 0.15 + 0.01
        val boundingBox = org.osmdroid.util.BoundingBox(
            maxLat + latPad, maxLon + lonPad, minLat - latPad, minLon - lonPad
        )
        miniMapView.post {
            miniMapView.zoomToBoundingBox(boundingBox, true)
        }
    }

    /** Zoom mini map to show all of Israel */
    private fun zoomToAllIsrael() {
        val israelBounds = org.osmdroid.util.BoundingBox(
            33.35, 35.90, 29.45, 34.20
        )
        miniMapView.post {
            miniMapView.zoomToBoundingBox(israelBounds, true)
        }
    }

    private fun loadCyclerSettings() {
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

    private fun toggleCyclerPause() {
        if (isCyclerPaused) {
            isCyclerPaused = false
            btnPausePlay.text = "⏸"
            if (shouldStartCycler()) startAutoCycle()
        } else {
            isCyclerPaused = true
            btnPausePlay.text = "▶"
            stopAutoCycle()
        }
    }

    private fun startAutoCycle() {
        if (isCyclerPaused) return
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
        val currentState = AlertStateMachine.getState(ctx)
        val recentThresholdMs = 60 * 60 * 1000L // 1 hour

        // Only process alerts NEWER than the current state's last update.
        // Even when CLEAR, use lastUpdate if set — prevents replay loop where
        // processTick clears EVENT_ENDED, then old cached alerts re-trigger it.
        val sinceMs = if (currentState.lastUpdate > 0)
            currentState.lastUpdate else (now - recentThresholdMs)

        val allCached = AlertCacheService.getLast24Hours(ctx)
        val recentMatching = allCached
            .filter { alert ->
                if (alert.timestampMs <= sinceMs) return@filter false
                if ((now - alert.timestampMs) >= recentThresholdMs) return@filter false
                // Only process alerts that match selected regions (all categories)
                alert.regions.any { it in selectedRegions }
            }
            .sortedBy { it.timestampMs }

        if (recentMatching.isEmpty()) return

        for (cached in recentMatching) {
            val category = if (cached.category > 0) cached.category
                else RedAlertService.inferCategoryFromTitle(cached.title)
            val activeAlert = RedAlertService.ActiveAlert(
                title = cached.title,
                regions = cached.regions,
                description = cached.description,
                type = cached.type,
                category = category,
                timestampMs = cached.timestampMs
            )
            AlertStateMachine.processAlert(ctx, activeAlert, selectedRegions)
        }
    }


    private fun loadCachedAlerts() {
        // Load and filter alerts by type and region
        val showOtherRegions = prefs.getBoolean("show_other_regions", true)
        val selectedRegions = getSelectedRegions().toSet()
        var allFiltered = AlertCacheService.getLast24Hours(requireContext())
            .filter { AlertTypeFilter.shouldShow(requireContext(), it.type) }
            .filter { alert ->
                showOtherRegions || selectedRegions.isEmpty() || alert.regions.any { it in selectedRegions }
            }
            .filter { clearedAtMs <= 0 || it.timestampMs > clearedAtMs } // Hide alerts before "clear" press
            .sortedByDescending { it.timestampMs }

        // If "all" grouping mode and in Shabbat/holiday, filter to since candle lighting
        val groupingMode = prefs.getString("history_grouping", "tiered") ?: "tiered"
        if (groupingMode == "all") {
            val candleMs = prefs.getLong("hebcal_candle_ms", 0)
            val havdalahMs = prefs.getLong("hebcal_havdalah_ms", 0)
            val now = System.currentTimeMillis()
            val isInShabbat = candleMs > 0 && havdalahMs > 0 && now in candleMs..havdalahMs
            if (isInShabbat) {
                allFiltered = allFiltered.filter { it.timestampMs >= candleMs }
            }
        }

        cachedAlertsList = allFiltered

        // Group based on setting, then split each time bucket by alert type
        val timeBuckets = if (groupingMode == "all" && cachedAlertsList.isNotEmpty()) {
            listOf(cachedAlertsList)
        } else {
            AlertCacheService.groupByTimeBucket(cachedAlertsList)
        }
        alertsByMinute = timeBuckets.flatMap { bucket ->
            bucket.groupBy { it.type }.values.toList()
                .sortedByDescending { it.first().timestampMs }
        }

        // Clamp index to valid range (don't reset to 0 unnecessarily)
        currentMinuteGroupIndex = currentMinuteGroupIndex.coerceIn(0, (alertsByMinute.size - 1).coerceAtLeast(0))
        updateCycleButtonStates()
    }

    private fun updateCycleButtonStates() {
        val hasMultipleGroups = alertsByMinute.size > 1
        btnPrevAlert.isEnabled = hasMultipleGroups
        btnNextAlert.isEnabled = hasMultipleGroups
        btnPausePlay.visibility = if (hasMultipleGroups && shouldStartCycler()) View.VISIBLE else View.GONE
    }
}
