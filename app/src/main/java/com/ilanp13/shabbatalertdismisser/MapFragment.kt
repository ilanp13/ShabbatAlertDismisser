package com.ilanp13.shabbatalertdismisser

import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
import org.osmdroid.config.Configuration as OsmConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
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
    private lateinit var cbShowOtherRegions: CheckBox
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
        cbShowOtherRegions = view.findViewById(R.id.cbShowOtherRegions)
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
        val typeListener = View.OnClickListener {
            updateSelectedTypes()
            saveFilterPreferences()
            loadAlerts()
        }
        cbMissiles.setOnClickListener(typeListener)
        cbAircraft.setOnClickListener(typeListener)
        cbEvent.setOnClickListener(typeListener)

        cbShowOtherRegions.setOnClickListener {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            prefs.edit().putBoolean("show_other_regions", cbShowOtherRegions.isChecked).apply()
            loadAlerts()
        }
    }

    private fun updateSelectedTypes() {
        val types = mutableSetOf<String>()
        if (cbMissiles.isChecked) types.add("alarm")
        if (cbAircraft.isChecked) types.add("warning")
        if (cbEvent.isChecked) types.add("event_ended")
        AlertTypeFilter.setSelectedTypes(requireContext(), types)
    }

    private fun loadFilterPreferences() {
        val selectedTypes = AlertTypeFilter.getSelectedTypes(requireContext())
        cbMissiles.isChecked = selectedTypes.contains("alarm")
        cbAircraft.isChecked = selectedTypes.contains("warning")
        cbEvent.isChecked = selectedTypes.contains("event_ended")

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (prefs.contains("region_display_mode")) {
            val oldMode = prefs.getString("region_display_mode", "all")
            prefs.edit()
                .putBoolean("show_other_regions", oldMode == "all")
                .remove("region_display_mode")
                .apply()
        }
        cbShowOtherRegions.isChecked = prefs.getBoolean("show_other_regions", true)
    }

    private fun saveFilterPreferences() {
        val types = mutableSetOf<String>()
        if (cbMissiles.isChecked) types.add("alarm")
        if (cbAircraft.isChecked) types.add("warning")
        if (cbEvent.isChecked) types.add("event_ended")
        AlertTypeFilter.setSelectedTypes(requireContext(), types)
    }

    private fun setupMap() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        OsmConfig.getInstance().apply {
            load(requireContext(), prefs)
            userAgentValue = requireContext().packageName
            osmdroidBasePath = requireContext().cacheDir
        }

        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setDestroyMode(false)

        // Prevent ViewPager2 from intercepting horizontal swipes on the map
        mapView.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN,
                android.view.MotionEvent.ACTION_MOVE -> v.parent?.requestDisallowInterceptTouchEvent(true)
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false // Let map handle the event normally
        }

        val controller = mapView.controller
        controller.setZoom(9.5)
        controller.setCenter(GeoPoint(31.5, 35.0))

        applyDarkModeIfNeeded()

        val compass = CompassOverlay(requireContext(), mapView)
        compass.enableCompass()
        mapView.overlays.add(compass)
    }

    private fun applyDarkModeIfNeeded() {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
            val invert = ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            ))
            val combined = ColorMatrix()
            combined.setConcat(invert, grayscale)
            mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(combined))
        } else {
            mapView.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(grayscale))
        }
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

                val ctx = requireContext()
                val mapPrefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                val showOther = mapPrefs.getBoolean("show_other_regions", true)
                val selRegions = getSelectedRegions(mapPrefs)

                val historyAlerts = AlertCacheService.getLast24Hours(ctx)
                    .filter { AlertTypeFilter.shouldShow(ctx, it.type) }
                    .filter { alert ->
                        showOther || selRegions.isEmpty() || alert.regions.any { it in selRegions }
                    }
                    .sortedByDescending { it.timestampMs }

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
            tvMapCounter.text = getString(R.string.map_no_alerts)
            drawOnMap(emptyList(), false, null)
            return
        }

        val selectedRegions = getSelectedRegions(prefs)
        val showOther = prefs.getBoolean("show_other_regions", true)

        val displayIndex = currentGroupIndex % totalGroups
        val isShowingActive = hasActive && displayIndex == 0
        val historyIndex = if (hasActive) displayIndex - 1 else displayIndex

        if (isShowingActive) {
            val alert = activeAlert!!
            tvMapCounter.text = getString(R.string.map_now_counter, alert.title, 1, totalGroups)

            val color = getAlertTypeColor(alert.type)
            val regions = if (!showOther && selectedRegions.isNotEmpty())
                alert.regions.filter { it in selectedRegions } else alert.regions
            val regionData = regions.map { RegionColorData(it, color, 0.35f, it in selectedRegions) }
            drawOnMap(regionData, true, getString(R.string.map_now_header, alert.title))
            zoomToFitRegions(regionData)
        } else if (historyIndex in alertsByMinute.indices) {
            val group = alertsByMinute[historyIndex]
            val ctx = requireContext()
            val header = AlertCacheService.formatGroupHeader(ctx, group)
            val countStr = AlertCacheService.formatGroupCount(ctx, group)
            tvMapCounter.text = "$header$countStr (${displayIndex + 1}/$totalGroups)"

            // Process oldest-first so latest alert color wins per region
            val regionData = group.asReversed().flatMap { alert ->
                val color = getAlertTypeColor(alert.type)
                val regions = if (!showOther && selectedRegions.isNotEmpty())
                    alert.regions.filter { it in selectedRegions } else alert.regions
                regions.map { RegionColorData(it, color, 0.25f, it in selectedRegions) }
            }
            drawOnMap(regionData, false, "$header$countStr")

            val zoomMode = prefs.getString("map_zoom_mode", "off") ?: "off"
            if (zoomMode == "auto") zoomToFitRegions(regionData)
        }
    }

    private fun zoomToFitRegions(regionData: List<RegionColorData>) {
        val ctx = context ?: return
        if (regionData.isEmpty()) return

        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
        var hasPoints = false

        for (rd in regionData) {
            val polyPoints = OrefPolygons.getPolygon(ctx, rd.region)
            if (polyPoints != null) {
                for (p in polyPoints) {
                    minLat = minOf(minLat, p.latitude); maxLat = maxOf(maxLat, p.latitude)
                    minLon = minOf(minLon, p.longitude); maxLon = maxOf(maxLon, p.longitude)
                    hasPoints = true
                }
            } else {
                val coords = OrefRegionCoords.coords[rd.region] ?: continue
                minLat = minOf(minLat, coords.first); maxLat = maxOf(maxLat, coords.first)
                minLon = minOf(minLon, coords.second); maxLon = maxOf(maxLon, coords.second)
                hasPoints = true
            }
        }
        if (!hasPoints) return

        val latPad = (maxLat - minLat) * 0.15 + 0.01
        val lonPad = (maxLon - minLon) * 0.15 + 0.01
        val bbox = org.osmdroid.util.BoundingBox(
            maxLat + latPad, maxLon + lonPad, minLat - latPad, minLon - lonPad
        )
        mapView.post { mapView.zoomToBoundingBox(bbox, true) }
    }

    private data class RegionColorData(
        val region: String,
        val color: Int,
        val fillAlpha: Float,
        val isSelectedRegion: Boolean
    )

    private fun drawOnMap(
        regionData: List<RegionColorData>,
        isActive: Boolean,
        headerText: String?
    ) {
        val ctx = context ?: return
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        val userLat = prefs.getFloat("latitude", 31.7683f).toDouble()
        val userLon = prefs.getFloat("longitude", 35.2137f).toDouble()

        // Remove previous alert overlays (keep compass)
        mapView.overlays.removeAll { it !is CompassOverlay }
        applyDarkModeIfNeeded()

        // Draw selected region borders (outline only, no fill) as base layer
        val selectedRegions = getSelectedRegions(prefs)
        for (region in selectedRegions) {
            val polyPoints = OrefPolygons.getPolygon(ctx, region) ?: continue
            if (polyPoints.size < 3) continue
            val border = Polygon(mapView)
            border.points = polyPoints
            border.fillPaint.color = Color.TRANSPARENT
            border.outlinePaint.color = Color.parseColor("#9C27B0") // Purple
            border.outlinePaint.strokeWidth = 3f
            border.outlinePaint.alpha = 180
            mapView.overlays.add(border)
        }

        // Draw current location region border (blue, thicker)
        val userRegion = OrefPolygons.findRegionContaining(ctx, userLat, userLon)
        if (userRegion != null) {
            val polyPoints = OrefPolygons.getPolygon(ctx, userRegion)
            if (polyPoints != null && polyPoints.size >= 3) {
                val border = Polygon(mapView)
                border.points = polyPoints
                border.fillPaint.color = Color.TRANSPARENT
                border.outlinePaint.color = Color.parseColor("#2196F3") // Blue
                border.outlinePaint.strokeWidth = 5f
                border.outlinePaint.alpha = 220
                mapView.overlays.add(border)
            }
        }

        // Deduplicate: if a region appears multiple times, use the highest-priority color
        // Latest alert per region wins, but yellow doesn't cancel red/orange
        val regionMap = mutableMapOf<String, RegionColorData>()
        for (rd in regionData) {
            val existing = regionMap[rd.region]
            if (existing == null || shouldOverwriteColor(existing.color, rd.color)) {
                regionMap[rd.region] = rd
            }
        }

        // Draw alert polygons for each region
        for ((_, rd) in regionMap) {
            val polyPoints = OrefPolygons.getPolygon(ctx, rd.region)
            if (polyPoints != null && polyPoints.size >= 3) {
                val polygon = Polygon(mapView)
                polygon.points = polyPoints
                polygon.fillPaint.color = rd.color
                polygon.fillPaint.alpha = (rd.fillAlpha * 255).toInt()
                polygon.outlinePaint.color = if (rd.isSelectedRegion) Color.parseColor("#9C27B0") else rd.color
                polygon.outlinePaint.strokeWidth = if (rd.isSelectedRegion) 4f else 2f
                polygon.outlinePaint.alpha = 200
                polygon.title = rd.region
                mapView.overlays.add(polygon)
            } else {
                // Fallback to dot for regions without polygon data
                val coords = OrefRegionCoords.coords[rd.region] ?: continue
                val dotOverlay = createDotOverlay(
                    GeoPoint(coords.first, coords.second),
                    rd.color, rd.isSelectedRegion, isActive
                )
                mapView.overlays.add(dotOverlay)
            }
        }

        // Header overlay
        val headerOverlay = object : org.osmdroid.views.overlay.Overlay() {
            override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
                if (shadow || canvas == null || mapView == null) return
                val paint = Paint().apply { isAntiAlias = true }

                // Header strip
                if (headerText != null) {
                    paint.color = if (isActive) Color.parseColor("#CC000000") else Color.parseColor("#CC333333")
                    paint.style = Paint.Style.FILL
                    canvas.drawRect(0f, 0f, canvas.width.toFloat(), 56f, paint)

                    paint.color = if (isActive) Color.RED else Color.WHITE
                    paint.textSize = 30f
                    paint.typeface = if (isActive) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    canvas.drawText(headerText, 16f, 42f, paint)
                    paint.typeface = Typeface.DEFAULT
                }
            }
        }
        mapView.overlays.add(headerOverlay)
        mapView.invalidate()
    }

    private fun createDotOverlay(
        point: GeoPoint,
        color: Int,
        isSelected: Boolean,
        isActive: Boolean
    ): org.osmdroid.views.overlay.Overlay {
        return object : org.osmdroid.views.overlay.Overlay() {
            override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
                if (shadow || canvas == null || mapView == null) return
                val pj = mapView.projection ?: return
                val paint = Paint().apply { isAntiAlias = true }
                val screenPos = pj.toPixels(point, null)
                val bx = screenPos.x.toFloat()
                val by = screenPos.y.toFloat()
                val radius = if (isActive) 12f else 10f

                paint.color = color
                paint.style = Paint.Style.FILL
                paint.alpha = if (isActive) 255 else 200
                canvas.drawCircle(bx, by, radius, paint)

                if (isSelected) {
                    paint.color = Color.parseColor("#9C27B0")
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    paint.alpha = 255
                    canvas.drawCircle(bx, by, radius + 2f, paint)
                }

                paint.color = Color.WHITE
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.alpha = 255
                canvas.drawCircle(bx, by, radius, paint)
            }
        }
    }

    /**
     * Whether a new (later) alert color should overwrite the existing one for a region.
     * Yellow (warning) does NOT cancel red/orange (active missile/aircraft).
     * Only green (event_ended) or another red/orange can overwrite red/orange.
     */
    private fun shouldOverwriteColor(existingColor: Int, newColor: Int): Boolean {
        val isExistingActive = existingColor == Color.RED
        val isNewWarning = newColor == Color.parseColor("#FFC107")
        return !(isExistingActive && isNewWarning)
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

    private fun shouldShowAlertType(type: String): Boolean {
        return AlertTypeFilter.shouldShow(requireContext(), type)
    }

    private fun getAlertTypeColor(type: String): Int {
        return when (type.lowercase()) {
            "alarm" -> Color.RED
            "warning" -> Color.parseColor("#FFC107")     // Yellow
            "event_ended" -> Color.parseColor("#4CAF50") // Green
            // Legacy type names (old cached data)
            "missile", "aircraft" -> Color.RED
            "event" -> Color.parseColor("#FFC107")       // Legacy warning
            else -> Color.RED
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
