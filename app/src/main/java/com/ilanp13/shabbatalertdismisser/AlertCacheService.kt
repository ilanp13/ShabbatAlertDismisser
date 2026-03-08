package com.ilanp13.shabbatalertdismisser

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject

object AlertCacheService {

    private const val CACHE_KEY = "alert_cache"
    private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L  // 24 hours

    data class CachedAlert(
        val timestampMs: Long,
        val title: String,
        val regions: List<String>,
        val description: String,
        val type: String = ""
    )

    /**
     * Save an active alert to the cache.
     * Only saves if it's a different alert (prevents duplicates from polling).
     * Appends to the cache and trims entries older than 24 hours.
     */
    fun save(context: Context, alert: RedAlertService.ActiveAlert) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        try {
            val now = System.currentTimeMillis()
            val cacheJson = prefs.getString(CACHE_KEY, "[]") ?: "[]"
            val cacheArray = JSONArray(cacheJson)

            // Check if the same alert already exists in the last entry
            // If so, don't add a duplicate (this prevents duplication from polling)
            if (cacheArray.length() > 0) {
                val lastAlert = cacheArray.getJSONObject(cacheArray.length() - 1)
                val lastTitle = lastAlert.optString("title", "")
                val lastType = lastAlert.optString("type", "")
                val lastRegions = lastAlert.optJSONArray("regions")

                // Check if current alert matches the last one
                if (lastTitle == alert.title && lastType == alert.type) {
                    // Compare regions
                    val currentRegionsList = alert.regions.sorted()
                    val lastRegionsList = mutableListOf<String>()
                    if (lastRegions != null) {
                        for (i in 0 until lastRegions.length()) {
                            lastRegionsList.add(lastRegions.getString(i))
                        }
                    }
                    if (lastRegionsList.sorted() == currentRegionsList) {
                        // Same alert, don't save duplicate
                        return
                    }
                }
            }

            // Add new alert (only if different from last one)
            val alertObj = JSONObject()
            alertObj.put("timestampMs", now)
            alertObj.put("title", alert.title)
            alertObj.put("description", alert.description)
            alertObj.put("type", alert.type)
            val regionsArray = JSONArray()
            for (region in alert.regions) {
                regionsArray.put(region)
            }
            alertObj.put("regions", regionsArray)
            cacheArray.put(alertObj)

            // Trim entries older than 24 hours
            val trimmedArray = JSONArray()
            for (i in 0 until cacheArray.length()) {
                val obj = cacheArray.getJSONObject(i)
                val timestamp = obj.getLong("timestampMs")
                if (now - timestamp < CACHE_DURATION_MS) {
                    trimmedArray.put(obj)
                }
            }

            prefs.edit().putString(CACHE_KEY, trimmedArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get all cached alerts from the last 24 hours.
     */
    fun getLast24Hours(context: Context): List<CachedAlert> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return try {
            val now = System.currentTimeMillis()
            val cacheJson = prefs.getString(CACHE_KEY, "[]") ?: "[]"
            val cacheArray = JSONArray(cacheJson)
            val result = mutableListOf<CachedAlert>()

            for (i in 0 until cacheArray.length()) {
                val obj = cacheArray.getJSONObject(i)
                val timestamp = obj.getLong("timestampMs")

                // Only include alerts within 24 hours
                if (now - timestamp < CACHE_DURATION_MS) {
                    val title = obj.getString("title")
                    val description = obj.getString("description")
                    val type = obj.optString("type", "")
                    val regionsArray = obj.getJSONArray("regions")
                    val regions = mutableListOf<String>()
                    for (j in 0 until regionsArray.length()) {
                        regions.add(regionsArray.getString(j))
                    }
                    result.add(CachedAlert(timestamp, title, regions, description, type))
                }
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
