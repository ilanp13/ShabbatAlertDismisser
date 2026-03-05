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
        val description: String
    )

    /**
     * Save an active alert to the cache.
     * Appends to the cache and trims entries older than 24 hours.
     */
    fun save(context: Context, alert: RedAlertService.ActiveAlert) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        try {
            val now = System.currentTimeMillis()
            val cacheJson = prefs.getString(CACHE_KEY, "[]") ?: "[]"
            val cacheArray = JSONArray(cacheJson)

            // Add new alert
            val alertObj = JSONObject()
            alertObj.put("timestampMs", now)
            alertObj.put("title", alert.title)
            alertObj.put("description", alert.description)
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
                    val regionsArray = obj.getJSONArray("regions")
                    val regions = mutableListOf<String>()
                    for (j in 0 until regionsArray.length()) {
                        regions.add(regionsArray.getString(j))
                    }
                    result.add(CachedAlert(timestamp, title, regions, description))
                }
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
