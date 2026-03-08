package com.ilanp13.shabbatalertdismisser

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
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
    private lateinit var cbEarthquake: CheckBox
    private lateinit var cbTsunami: CheckBox
    private var selectedAlertTypes = setOf("missiles", "aircraft", "event", "earthquake", "tsunami")

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
        cbEarthquake = view.findViewById(R.id.cbEarthquake)
        cbTsunami = view.findViewById(R.id.cbTsunami)

        // Load filter preferences
        loadFilterPreferences()

        // Setup checkbox listeners
        setupFilterListeners()

        setupMap()
        loadAlerts()
    }

    private fun setupFilterListeners() {
        val listener = View.OnClickListener {
            updateSelectedTypes()
            saveFilterPreferences()
            loadAlerts()  // Reload alerts with new filter
        }
        cbMissiles.setOnClickListener(listener)
        cbAircraft.setOnClickListener(listener)
        cbEvent.setOnClickListener(listener)
        cbEarthquake.setOnClickListener(listener)
        cbTsunami.setOnClickListener(listener)
    }

    private fun updateSelectedTypes() {
        val types = mutableSetOf<String>()
        if (cbMissiles.isChecked) types.add("missiles")
        if (cbAircraft.isChecked) types.add("aircraft")
        if (cbEvent.isChecked) types.add("event")
        if (cbEarthquake.isChecked) types.add("earthquake")
        if (cbTsunami.isChecked) types.add("tsunami")
        selectedAlertTypes = types
    }

    private fun loadFilterPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val savedTypes = prefs.getStringSet("map_alert_type_filter", null)
        if (savedTypes != null) {
            selectedAlertTypes = savedTypes
        }

        // Update checkbox states
        cbMissiles.isChecked = selectedAlertTypes.contains("missiles")
        cbAircraft.isChecked = selectedAlertTypes.contains("aircraft")
        cbEvent.isChecked = selectedAlertTypes.contains("event")
        cbEarthquake.isChecked = selectedAlertTypes.contains("earthquake")
        cbTsunami.isChecked = selectedAlertTypes.contains("tsunami")
    }

    private fun saveFilterPreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        prefs.edit().putStringSet("map_alert_type_filter", selectedAlertTypes).apply()
    }

    private fun setupMap() {
        // Configure osmdroid
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        Configuration.getInstance().apply {
            load(requireContext(), prefs)
            userAgentValue = requireContext().packageName
            // Ensure cache directory exists
            osmdroidBasePath = requireContext().cacheDir
        }

        // Setup map tiles
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.setDestroyMode(false)  // Prevent destroying on detach

        // Center on Israel (lat 31.5, lon 35.0, zoom 9.5)
        val controller = mapView.controller
        controller.setZoom(9.5)
        controller.setCenter(GeoPoint(31.5, 35.0))

        // Add compass overlay
        val compass = CompassOverlay(requireContext(), mapView)
        compass.enableCompass()
        mapView.overlays.add(compass)
    }

    private fun loadAlerts() {
        pbMapLoading.visibility = View.VISIBLE

        Thread {
            try {
                // Fetch active alerts
                val activeResult = RedAlertService.fetch()
                val activeAlert = when (activeResult) {
                    is RedAlertService.FetchResult.Success -> activeResult.alert
                    is RedAlertService.FetchResult.Unavailable -> null
                }

                // Get cached history (last 24 hours)
                val historyAlerts = AlertCacheService.getLast24Hours(requireContext())

                // Post to UI thread
                requireActivity().runOnUiThread {
                    placeMarkers(activeAlert, historyAlerts)
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

    private fun placeMarkers(
        activeAlert: RedAlertService.ActiveAlert?,
        historyAlerts: List<AlertCacheService.CachedAlert>
    ) {
        // Build list of markers: (point, region, isActive, alpha, type, color)
        val markers = mutableListOf<Tuple6<GeoPoint, String, Boolean, Float, String, Int>>()
        var mostRecentAlert: String? = null

        // Add active alert markers (colored based on type)
        if (activeAlert != null && shouldShowAlertType(activeAlert.type)) {
            mostRecentAlert = "${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())} - ${activeAlert.title}"
            val color = getAlertTypeColor(activeAlert.type)
            for (region in activeAlert.regions) {
                val coords = OrefRegionCoords.coords[region]
                if (coords != null) {
                    val point = GeoPoint(coords.first, coords.second)
                    markers.add(Tuple6(point, region, true, 1.0f, activeAlert.type, color))
                }
            }
        }

        // Add history markers (colored based on type) with fading opacity
        for (cached in historyAlerts) {
            if (!shouldShowAlertType(cached.type)) continue

            val now = System.currentTimeMillis()
            val ageMs = now - cached.timestampMs
            val agePercent = (ageMs.toDouble() / (24 * 60 * 60 * 1000L)).coerceIn(0.0, 1.0)
            // Fade from 1.0 (new) to 0.3 (24h old)
            val alpha = (1.0 - agePercent * 0.7).toFloat()

            // Use first history alert as display if no active alert
            if (mostRecentAlert == null) {
                mostRecentAlert = "${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(cached.timestampMs))} - ${cached.title}"
            }

            val color = getAlertTypeColor(cached.type)
            for (region in cached.regions) {
                val coords = OrefRegionCoords.coords[region]
                if (coords != null) {
                    val point = GeoPoint(coords.first, coords.second)
                    markers.add(Tuple6(point, region, false, alpha, cached.type, color))
                }
            }
        }

        // Create overlay with custom drawer
        val overlay = object : org.osmdroid.views.overlay.Overlay() {
            override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
                if (shadow) return

                val pj = mapView?.projection ?: return
                val paint = Paint().apply { isAntiAlias = true }

                // Draw all markers
                for ((point, region, isActive, alpha, type, color) in markers) {
                    val screenPos = pj.toPixels(point, null)

                    if (isActive) {
                        // Colored circle for active alert
                        paint.color = color
                        paint.style = Paint.Style.FILL
                        paint.alpha = 255
                        canvas?.drawCircle(screenPos.x.toFloat(), screenPos.y.toFloat(), 12f, paint)
                        // White border
                        paint.color = Color.WHITE
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 2f
                        canvas?.drawCircle(screenPos.x.toFloat(), screenPos.y.toFloat(), 12f, paint)
                    } else {
                        // Colored circle for history with fade
                        paint.color = color
                        paint.style = Paint.Style.FILL
                        paint.alpha = (alpha * 255).toInt()
                        canvas?.drawCircle(screenPos.x.toFloat(), screenPos.y.toFloat(), 10f, paint)
                    }
                }

                // Draw alert info text at top
                if (mostRecentAlert != null) {
                    paint.color = Color.BLACK
                    paint.textSize = 16f
                    paint.style = Paint.Style.FILL
                    paint.alpha = 255
                    val yPos = 40f
                    canvas?.drawText(mostRecentAlert, 20f, yPos, paint)
                }
            }
        }

        mapView.overlays.removeAll { it !is CompassOverlay }
        mapView.overlays.add(overlay)
        mapView.invalidate()
    }

    private fun shouldShowAlertType(type: String): Boolean {
        val normalized = type.lowercase()
        return when {
            normalized.contains("missile") || normalized.contains("rocket") -> selectedAlertTypes.contains("missiles")
            normalized.contains("aircraft") -> selectedAlertTypes.contains("aircraft")
            normalized.contains("event") -> selectedAlertTypes.contains("event")
            normalized.contains("earthquake") -> selectedAlertTypes.contains("earthquake")
            normalized.contains("tsunami") -> selectedAlertTypes.contains("tsunami")
            else -> true  // Show unknown types by default
        }
    }

    private fun getAlertTypeColor(type: String): Int {
        return when {
            type.contains("Missiles", ignoreCase = true) || type.contains("Rocket", ignoreCase = true) -> Color.RED
            type.contains("Aircraft", ignoreCase = true) -> Color.parseColor("#FF9500")
            type.contains("Event-Over", ignoreCase = true) || type.contains("Event", ignoreCase = true) -> Color.GREEN
            type.contains("Earthquake", ignoreCase = true) -> Color.parseColor("#9C27B0")
            type.contains("Tsunami", ignoreCase = true) -> Color.BLUE
            else -> Color.parseColor("#FF9800")  // Orange default
        }
    }

    // Helper data classes for tuples
    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private data class Tuple6<A, B, C, D, E, F>(val a: A, val b: B, val c: C, val d: D, val e: E, val f: F)

    override fun onResume() {
        super.onResume()
        mapView.onResume()
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
