package com.ilanp13.shabbatalertdismisser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint

/**
 * Loads and caches polygon boundary data for Pikud HaOref alert regions.
 * Data sourced from the RedAlert Android project (Tzofar app polygon data).
 *
 * Files used:
 *  - res/raw/region_polygon_map.json  → Hebrew region name → polygon ID
 *  - res/raw/polygons.json            → polygon ID → array of [lat, lng] pairs
 */
object OrefPolygons {

    private var nameToId: Map<String, String>? = null
    private var polygonsById: Map<String, List<GeoPoint>>? = null

    /** Get polygon boundary for a region name. Returns null if no polygon data exists. */
    fun getPolygon(context: Context, regionName: String): List<GeoPoint>? {
        ensureLoaded(context)
        val id = nameToId?.get(regionName) ?: return null
        return polygonsById?.get(id)
    }

    /** Check if polygon data is available for a region. */
    fun hasPolygon(context: Context, regionName: String): Boolean {
        ensureLoaded(context)
        return nameToId?.get(regionName)?.let { polygonsById?.containsKey(it) } ?: false
    }

    /** Find the region whose polygon contains the given lat/lng. Returns null if not inside any. */
    fun findRegionContaining(context: Context, lat: Double, lng: Double): String? {
        ensureLoaded(context)
        val map = nameToId ?: return null
        val polys = polygonsById ?: return null
        for ((name, id) in map) {
            val points = polys[id] ?: continue
            if (pointInPolygon(lat, lng, points)) return name
        }
        return null
    }

    /** Ray-casting point-in-polygon test */
    private fun pointInPolygon(lat: Double, lng: Double, polygon: List<GeoPoint>): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val yi = polygon[i].latitude; val xi = polygon[i].longitude
            val yj = polygon[j].latitude; val xj = polygon[j].longitude
            if (((yi > lat) != (yj > lat)) &&
                (lng < (xj - xi) * (lat - yi) / (yj - yi) + xi)) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun ensureLoaded(context: Context) {
        if (nameToId != null && polygonsById != null) return

        // Load name → ID mapping
        val mapJson = context.resources.openRawResource(R.raw.region_polygon_map)
            .bufferedReader().use { it.readText() }
        val mapObj = JSONObject(mapJson)
        val idMap = mutableMapOf<String, String>()
        for (key in mapObj.keys()) {
            idMap[key] = mapObj.getString(key)
        }
        nameToId = idMap

        // Load polygon coordinates
        val polyJson = context.resources.openRawResource(R.raw.polygons)
            .bufferedReader().use { it.readText() }
        val polyObj = JSONObject(polyJson)
        val polyMap = mutableMapOf<String, List<GeoPoint>>()
        for (id in polyObj.keys()) {
            val arr = polyObj.getJSONArray(id)
            val points = mutableListOf<GeoPoint>()
            for (i in 0 until arr.length()) {
                val coord = arr.getJSONArray(i)
                points.add(GeoPoint(coord.getDouble(0), coord.getDouble(1)))
            }
            if (points.isNotEmpty()) {
                polyMap[id] = points
            }
        }
        polygonsById = polyMap
    }
}
