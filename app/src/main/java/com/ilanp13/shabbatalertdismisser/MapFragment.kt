package com.ilanp13.shabbatalertdismisser

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.compass.CompassOverlay
import androidx.preference.PreferenceManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MapFragment : Fragment() {

    private lateinit var mapView: MapView
    private lateinit var pbMapLoading: ProgressBar
    private lateinit var cbMissiles: CheckBox
    private lateinit var cbAircraft: CheckBox
    private lateinit var cbEvent: CheckBox
    private lateinit var btnMapPrev: Button
    private lateinit var btnMapNext: Button
    private lateinit var tvMapCounter: TextView

    // Cycling state
    private var activeAlert: RedAlertService.ActiveAlert? = null
    private var alertsByMinute = listOf<List<AlertCacheService.CachedAlert>>()
    private var currentGroupIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapView = view.findViewById(R.id.mapView)
        pbMapLoading = view.findViewById(R.id.pbMapLoading)
        cbMissiles = view.findViewById(R.id.cbMissiles)
        cbAircraft = view.findViewById(R.id.cbAircraft)
        cbEvent = view.findViewById(R.id.cbEvent)
        btnMapPrev = view.findViewById(R.id.btnMapPrev)
        btnMapNext = view.findViewById(R.id.btnMapNext)
        tvMapCounter = view.findViewById(R.id.tvMapCounter)

        loadFilterPreferences()
        setupFilterListeners()
        setupNavigationButtons()
        setupMap()
        loadAlerts()
    }

    private fun setupNavigationButtons() {
        btnMapPrev.setOnClickListener {
            if (alertsByMinute.isEmpty()) return@setOnClickListener
            currentGroupIndex = if (currentGroupIndex > 0) currentGroupIndex - 1 else alertsByMinute.size - 1
            displayCurrentGroup()
        }
        btnMapNext.setOnClickListener {
            if (alertsByMinute.isEmpty()) return@setOnClickListener
            currentGroupIndex = (currentGroupIndex + 1) % alertsByMinute.size
            displayCurrentGroup()
        }
    }

    private fun setupFilterListeners() {
        val listener = View.OnClickListener {
            updateSelectedTypes()
            saveFilterPreferences()
            loadAlerts()
        }
        cbMissiles.setOnClickListener(listener)
        cbAircraft.setOnClickListener(listener)
        cbEvent.setOnClickListener(listener)
    }

    private fun updateSelectedTypes() {
        val types = mutableSetOf<String>()
        if (cbMissiles.isChecked) types.add("missile")
        if (cbAircraft.isChecked) types.add("aircraft")
        if (cbEvent.isChecked) types.add("event")
        AlertTypeFilter.setSelectedTypes(requireContext(), types)
    }

    private fun loadFilterPreferences() {
        val selectedTypes = AlertTypeFilter.getSelectedTypes(requireContext())
        cbMissiles.isChecked = selectedTypes.contains("missile")
        cbAircraft.isChecked = selectedTypes.contains("aircraft")
        cbEvent.isChecked = selectedTypes.contains("event")
    }

    private fun saveFilterPreferences() {
        val types = mutableSetOf<String>()
        if (cbMissiles.isChecked) types.add("missile")
        if (cbAircraft.isChecked) types.add("aircraft")
        if (cbEvent.isChecked) types.add("event")
        AlertTypeFilter.setSelectedTypes(requireContext(), types)
    }

    private fun setupMap() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        Configuration.getInstance().apply {
            load(requireContext(), prefs)
            userAgentValue = requireContext().packageName
            osmdroidBasePath = requireContext().cacheDir
        }

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setDestroyMode(false)

        val controller = mapView.controller
        controller.setZoom(9.5)
        controller.setCenter(GeoPoint(31.5, 35.0))

        val compass = CompassOverlay(requireContext(), mapView)
        compass.enableCompass()
        mapView.overlays.add(compass)
    }

    private fun loadAlerts() {
        pbMapLoading.visibility = View.VISIBLE

        Thread {
            try {
                val activeResult = RedAlertService.fetch()
                val fetchedActive = when (activeResult) {
                    is RedAlertService.FetchResult.Success -> activeResult.alert
                    is RedAlertService.FetchResult.Unavailable -> null
                }

                val historyAlerts = AlertCacheService.getLast24Hours(requireContext())
                    .filter { AlertTypeFilter.shouldShow(requireContext(), it.type) }
                    .sortedByDescending { it.timestampMs }

                // Tiered grouping: 1min (recent) → 10min → 30min (old)
                val minuteGroups = AlertCacheService.groupByTimeBucket(historyAlerts)

                requireActivity().runOnUiThread {
                    activeAlert = if (fetchedActive != null && shouldShowAlertType(fetchedActive.type)) fetchedActive else null
                    alertsByMinute = minuteGroups
                    currentGroupIndex = 0
                    updateNavigationState()
                    displayCurrentGroup()
                    pbMapLoading.visibility = View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                requireActivity().runOnUiThread {
                    pbMapLoading.visibility = View.GONE
                }
            }
        }.start()
    }

    private fun updateNavigationState() {
        val totalGroups = alertsByMinute.size + (if (activeAlert != null) 1 else 0)
        val hasMultiple = totalGroups > 1
        btnMapPrev.isEnabled = hasMultiple
        btnMapNext.isEnabled = hasMultiple
    }

    private fun displayCurrentGroup() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val hasActive = activeAlert != null
        val totalGroups = alertsByMinute.size + (if (hasActive) 1 else 0)

        if (totalGroups == 0) {
            tvMapCounter.text = "No alerts"
            drawMarkersOnMap(emptyList(), false, null)
            return
        }

        // Index 0 = active alert (if exists), then history groups
        val displayIndex = currentGroupIndex % totalGroups
        val isShowingActive = hasActive && displayIndex == 0
        val historyIndex = if (hasActive) displayIndex - 1 else displayIndex

        if (isShowingActive) {
            val alert = activeAlert!!
            tvMapCounter.text = "NOW - ${alert.title} (1/$totalGroups)"

            // Convert active alert regions to display data
            val color = getAlertTypeColor(alert.type)
            val selectedRegions = getSelectedRegions(prefs)
            val markers = alert.regions.mapNotNull { region ->
                val coords = OrefRegionCoords.coords[region] ?: return@mapNotNull null
                MarkerData(GeoPoint(coords.first, coords.second), region, color, 1.0f, selectedRegions.contains(region))
            }
            drawMarkersOnMap(markers, true, "NOW  ${alert.title}")
        } else if (historyIndex in alertsByMinute.indices) {
            val group = alertsByMinute[historyIndex]
            val header = AlertCacheService.formatGroupHeader(group)
            val countStr = AlertCacheService.formatGroupCount(group)
            tvMapCounter.text = "$header$countStr (${displayIndex + 1}/$totalGroups)"

            val selectedRegions = getSelectedRegions(prefs)
            val markers = group.flatMap { alert ->
                val color = getAlertTypeColor(alert.type)
                alert.regions.mapNotNull { region ->
                    val coords = OrefRegionCoords.coords[region] ?: return@mapNotNull null
                    MarkerData(GeoPoint(coords.first, coords.second), region, color, 0.85f, selectedRegions.contains(region))
                }
            }
            drawMarkersOnMap(markers, false, "$header$countStr")
        }
    }

    private fun getSelectedRegions(prefs: android.content.SharedPreferences): Set<String> {
        val json = prefs.getString("alert_regions_selected", "[]") ?: "[]"
        return try {
            val array = org.json.JSONArray(json)
            val set = mutableSetOf<String>()
            for (i in 0 until array.length()) set.add(array.getString(i))
            set
        } catch (_: Exception) { emptySet() }
    }

    private data class MarkerData(
        val point: GeoPoint,
        val region: String,
        val color: Int,
        val alpha: Float,
        val isSelectedRegion: Boolean
    )

    private fun drawMarkersOnMap(markers: List<MarkerData>, isActive: Boolean, headerText: String?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val userLat = prefs.getFloat("latitude", 31.7683f).toDouble()
        val userLon = prefs.getFloat("longitude", 35.2137f).toDouble()

        // Group by location for stacking
        val byLocation = markers.groupBy { "${it.point.latitude},${it.point.longitude}" }

        val overlay = object : org.osmdroid.views.overlay.Overlay() {
            override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
                if (shadow || canvas == null || mapView == null) return
                val pj = mapView.projection ?: return
                val paint = Paint().apply { isAntiAlias = true }

                // Draw current location marker (blue dot) - always visible
                val userPoint = GeoPoint(userLat, userLon)
                val userScreen = pj.toPixels(userPoint, null)
                paint.color = Color.BLUE
                paint.style = Paint.Style.FILL
                paint.alpha = 255
                canvas.drawCircle(userScreen.x.toFloat(), userScreen.y.toFloat(), 8f, paint)
                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f
                canvas.drawCircle(userScreen.x.toFloat(), userScreen.y.toFloat(), 8f, paint)

                // Draw alert markers — non-selected first, selected on top
                val sortedLocations = byLocation.entries.sortedBy { entry ->
                    entry.value.any { it.isSelectedRegion }
                }
                for ((_, locationMarkers) in sortedLocations) {
                    if (locationMarkers.isEmpty()) continue
                    val screenPos = pj.toPixels(locationMarkers[0].point, null)
                    val bx = screenPos.x.toFloat()
                    val by = screenPos.y.toFloat()

                    for ((index, m) in locationMarkers.withIndex()) {
                        val ox = (index % 3) * 6f - 6f
                        val oy = (index / 3) * 6f
                        val radius = if (isActive) 12f else 10f

                        // Draw marker
                        paint.color = m.color
                        paint.style = Paint.Style.FILL
                        paint.alpha = (m.alpha * 255).toInt()
                        canvas.drawCircle(bx + ox, by + oy, radius, paint)

                        // Selected region highlight: thicker yellow border
                        if (m.isSelectedRegion) {
                            paint.color = Color.YELLOW
                            paint.style = Paint.Style.STROKE
                            paint.strokeWidth = 3f
                            paint.alpha = 255
                            canvas.drawCircle(bx + ox, by + oy, radius + 2f, paint)
                        }

                        // White border
                        paint.color = Color.WHITE
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 2f
                        paint.alpha = 255
                        canvas.drawCircle(bx + ox, by + oy, radius, paint)
                    }

                    // Count badge
                    if (locationMarkers.size > 1) {
                        paint.color = Color.BLACK
                        paint.textSize = 14f
                        paint.style = Paint.Style.FILL
                        paint.alpha = 255
                        canvas.drawText(locationMarkers.size.toString(), bx + 10f, by - 10f, paint)
                    }
                }

                // Draw header text (bigger, bold, colored for active)
                if (headerText != null) {
                    // Background strip
                    paint.color = if (isActive) Color.parseColor("#CC000000") else Color.parseColor("#CC333333")
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(0f, 0f, canvas.width.toFloat(), 56f, paint)

                    paint.color = if (isActive) Color.RED else Color.WHITE
                    paint.textSize = 30f
                    paint.style = Paint.Style.FILL
                    paint.alpha = 255
                    paint.typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    canvas.drawText(headerText, 16f, 42f, paint)
                    paint.typeface = Typeface.DEFAULT
                }
            }
        }

        mapView.overlays.removeAll { it !is CompassOverlay }
        mapView.overlays.add(overlay)
        mapView.invalidate()
    }

    private fun shouldShowAlertType(type: String): Boolean {
        return AlertTypeFilter.shouldShow(requireContext(), type)
    }

    private fun getAlertTypeColor(type: String): Int {
        return when (type.lowercase()) {
            "missile" -> Color.RED
            "aircraft" -> Color.parseColor("#FF9500")
            "event" -> Color.GREEN
            else -> Color.parseColor("#FF9800")
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        loadAlerts()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDetach()
    }
}
