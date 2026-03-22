package com.ilanp13.shabbatalertdismisser

import android.content.Context
import androidx.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlertCacheService {

    private const val CACHE_KEY = "alert_cache"
    private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L  // 24 hours

    data class CachedAlert(
        val timestampMs: Long,
        val title: String,
        val regions: List<String>,
        val description: String,
        val type: String = "",
        val category: Int = 0
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
            val timestamp = if (alert.timestampMs > 0) alert.timestampMs else now
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
            val alertObj = alertToJson(alert, timestamp)
            cacheArray.put(alertObj)

            // Trim entries older than 24 hours
            val trimmedArray = JSONArray()
            for (i in 0 until cacheArray.length()) {
                val obj = cacheArray.getJSONObject(i)
                val ts = obj.getLong("timestampMs")
                if (now - ts < CACHE_DURATION_MS) {
                    trimmedArray.put(obj)
                }
            }

            prefs.edit().putString(CACHE_KEY, trimmedArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Save a batch of alerts at once (single write to SharedPreferences).
     * Replaces the entire cache. Used for history refetch.
     * No time filtering - saves all provided alerts (filtering happens on read).
     */
    fun saveBatch(context: Context, alerts: List<RedAlertService.ActiveAlert>) {
        if (alerts.isEmpty()) return  // Don't clear cache on empty fetch
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        try {
            val now = System.currentTimeMillis()
            val cacheArray = JSONArray()

            // Deduplicate by title+type+sorted regions+timestamp (rounded to minute)
            val seen = mutableSetOf<String>()
            for (alert in alerts) {
                val timestamp = if (alert.timestampMs > 0) alert.timestampMs else now
                val minuteKey = timestamp / 60_000
                val dedupeKey = "${alert.title}|${alert.type}|${alert.regions.sorted()}|$minuteKey"
                if (!seen.add(dedupeKey)) continue

                cacheArray.put(alertToJson(alert, timestamp))
            }

            prefs.edit().putString(CACHE_KEY, cacheArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun alertToJson(alert: RedAlertService.ActiveAlert, timestamp: Long): JSONObject {
        val alertObj = JSONObject()
        alertObj.put("timestampMs", timestamp)
        alertObj.put("title", alert.title)
        alertObj.put("description", alert.description)
        alertObj.put("type", alert.type)
        alertObj.put("category", alert.category)
        val regionsArray = JSONArray()
        for (region in alert.regions) {
            regionsArray.put(region)
        }
        alertObj.put("regions", regionsArray)
        return alertObj
    }

    /**
     * Group alerts into time buckets with tiered granularity:
     *  - Last 30 min: group by exact minute
     *  - 30 min – 3 hours: group by 10-minute window
     *  - 3+ hours: group by 30-minute window
     * Returns groups sorted newest-first.
     */
    fun groupByTimeBucket(alerts: List<CachedAlert>): List<List<CachedAlert>> {
        val now = System.currentTimeMillis()
        val thirtyMin = 30 * 60 * 1000L
        val threeHours = 3 * 60 * 60 * 1000L

        val grouped = alerts.groupBy { alert ->
            val age = now - alert.timestampMs
            val bucketSize = when {
                age < thirtyMin -> 60_000L          // 1 minute
                age < threeHours -> 10 * 60_000L    // 10 minutes
                else -> 30 * 60_000L                 // 30 minutes
            }
            alert.timestampMs / bucketSize
        }

        return grouped.entries
            .sortedByDescending { group -> group.value.maxOf { it.timestampMs } }
            .map { it.value }
    }

    /**
     * Build a display string for a group of alerts showing date + time range + time ago.
     * Examples: "היסטוריה 09.03 12:30 (35 דק׳ לפני)", "09.03 12:30-12:47 (2 שע׳ לפני)"
     */
    fun formatGroupHeader(context: Context, alerts: List<CachedAlert>): String {
        if (alerts.isEmpty()) return ""
        val dateFmt = SimpleDateFormat("dd.MM", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        val earliest = alerts.minOf { it.timestampMs }
        val latest = alerts.maxOf { it.timestampMs }

        val dateStr = dateFmt.format(Date(earliest))
        val fromTime = timeFmt.format(Date(earliest))
        val toTime = timeFmt.format(Date(latest))

        val timeStr = if (fromTime == toTime) fromTime else "$fromTime-$toTime"
        val prefix = context.getString(R.string.history_prefix)
        val ago = formatTimeAgo(context, earliest)
        return "$prefix $dateStr $timeStr ($ago)"
    }

    /**
     * Format a count string that includes distinct alert titles.
     * Returns empty string for single-alert groups.
     */
    fun formatGroupCount(context: Context, alerts: List<CachedAlert>): String {
        if (alerts.size <= 1) return ""
        val titles = alerts.map { it.title }.distinct()
        return context.getString(R.string.alerts_count_format, alerts.size, titles.joinToString(", "))
    }

    /**
     * Format a relative time string like "5 min ago" or "2 hr ago".
     */
    fun formatTimeAgo(context: Context, timestampMs: Long): String {
        val diffMs = System.currentTimeMillis() - timestampMs
        val diffMin = (diffMs / 60_000).toInt()
        return when {
            diffMin < 1 -> context.getString(R.string.time_ago_just_now)
            diffMin < 60 -> context.getString(R.string.time_ago_minutes, diffMin)
            else -> context.getString(R.string.time_ago_hours, diffMin / 60)
        }
    }

    /**
     * Get all cached alerts (up to 48h to cover history refetches).
     */
    fun getLast24Hours(context: Context): List<CachedAlert> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return try {
            val now = System.currentTimeMillis()
            val maxAge = 48 * 60 * 60 * 1000L  // 48 hours to cover full history refetch
            val cacheJson = prefs.getString(CACHE_KEY, "[]") ?: "[]"
            val cacheArray = JSONArray(cacheJson)
            val result = mutableListOf<CachedAlert>()

            for (i in 0 until cacheArray.length()) {
                val obj = cacheArray.getJSONObject(i)
                val timestamp = obj.getLong("timestampMs")

                if (now - timestamp < maxAge) {
                    val title = obj.getString("title")
                    val description = obj.getString("description")
                    val rawType = obj.optString("type", "")
                    val category = obj.optInt("category", 0)
                    // Normalize types: title-based inference overrides unknown categories
                    val inferredCat = RedAlertService.inferCategoryFromTitle(title)
                    val resolvedCat = if (inferredCat > 0) inferredCat else if (category > 0) category else 0
                    val type = if (resolvedCat > 0) {
                        when (resolvedCat) {
                            1, 2, 3, 4 -> "alarm"
                            12, 14 -> "warning"
                            13 -> "event_ended"
                            else -> rawType
                        }
                    } else rawType
                    if (resolvedCat == 13 || title.contains("הסתיים")) {
                        android.util.Log.d("AlertCache", "Event ended: rawType=$rawType cat=$category resolvedCat=$resolvedCat type=$type title=$title")
                    }
                    val regionsArray = obj.getJSONArray("regions")
                    val regions = mutableListOf<String>()
                    for (j in 0 until regionsArray.length()) {
                        regions.add(regionsArray.getString(j))
                    }
                    result.add(CachedAlert(timestamp, title, regions, description, type, resolvedCat))
                }
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
