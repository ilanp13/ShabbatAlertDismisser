package com.ilanp13.shabbatalertdismisser

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object RedAlertService {

    private const val TAG = "RedAlertService"

    data class ActiveAlert(
        val title: String,
        val regions: List<String>,
        val description: String,
        val type: String = "",  // e.g., "missile", "event", etc.
        val category: Int = 0,  // raw category: 1=missile, 2=aircraft, 12=warning, 13=event_ended
        val timestampMs: Long = 0L  // 0 = use current time when saving to cache
    )

    sealed class FetchResult {
        data class Success(val alert: ActiveAlert?) : FetchResult()  // null = no active alerts
        object Unavailable : FetchResult()  // API unreachable or error
    }

    /**
     * Fetches active alerts from Pikud HaOref (Israeli Home Front Command).
     *
     * @return FetchResult.Success with ActiveAlert if alerts are active,
     *         FetchResult.Success with null if no active alerts,
     *         FetchResult.Unavailable if endpoint is unreachable/failed
     */
    fun fetch(): FetchResult {
        return try {
            val url = URL("https://www.oref.org.il/WarningMessages/alert/alerts.json")
            Log.d(TAG, "Fetching active alerts from: $url")

            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Referer", "https://www.oref.org.il/")
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")

            val responseCode = conn.responseCode
            Log.d(TAG, "Response code: $responseCode")

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            if (body.isEmpty() || body.trim() == "{}") {
                Log.d(TAG, "No active alerts (empty response)")
                FetchResult.Success(null)  // No alerts (successful API call)
            } else {
                val alert = parse(body)
                Log.d(TAG, "Result: $alert")
                FetchResult.Success(alert)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch failed: ${e.message}")
            FetchResult.Unavailable  // API error or unreachable
        }
    }

    /**
     * Fetches historical alerts from the last 24 hours.
     * Uses the alertsHistoryJson endpoint which can return multiple historical alerts.
     */
    fun fetchHistory(): List<ActiveAlert> {
        Log.d(TAG, "====== FETCHHISTORY START ======")

        // Fetch in day-sized chunks to work around API's 3000-entry limit
        val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale.US)
        val allAlerts = mutableListOf<ActiveAlert>()

        // Fetch today and yesterday separately for better coverage
        val cal = Calendar.getInstance()
        val today = dateFmt.format(cal.time)
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = dateFmt.format(cal.time)

        // Chunk 1: yesterday only
        allAlerts.addAll(fetchHistoryChunk(yesterday, yesterday))
        // Chunk 2: today only
        allAlerts.addAll(fetchHistoryChunk(today, today))

        Log.d(TAG, "✓ Total: ${allAlerts.size} alerts from 2 chunks")
        Log.d(TAG, "====== FETCHHISTORY END ======")
        return allAlerts
    }

    private fun fetchHistoryChunk(fromDate: String, toDate: String): List<ActiveAlert> {
        val endpoint = "https://alerts-history.oref.org.il/Shared/Ajax/GetAlarmsHistory.aspx?lang=he&fromDate=$fromDate&toDate=$toDate&mode=0"
        Log.d(TAG, "Fetching chunk: $fromDate → $toDate")

        try {
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")

            val responseCode = conn.responseCode
            Log.d(TAG, "Response code: $responseCode")

            if (responseCode !in 200..299) {
                Log.w(TAG, "Bad status $responseCode")
                conn.disconnect()
                return emptyList()
            }

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            Log.d(TAG, "Response length: ${body.length} bytes")

            val trimmed = body.trim().replace("\uFEFF", "")
            if (trimmed.length < 5) {
                Log.d(TAG, "Empty response for $fromDate")
                return emptyList()
            }

            val result = parseHistoryArray(trimmed)
            Log.d(TAG, "Got ${result.size} alerts for $fromDate → $toDate")
            return result
        } catch (e: Exception) {
            Log.w(TAG, "Exception for $fromDate: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
        return emptyList()
    }

    private fun parseHistoryArray(json: String): List<ActiveAlert> {
        return try {
            val array = JSONArray(json)
            Log.d(TAG, "Parsing ${array.length()} raw entries, grouping by timestamp+category...")

            // Date formats to try for alertDate parsing
            val dateFormats = listOf(
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
                SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.US),
                SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            )

            // First pass: group raw JSON objects by alertDate + category
            data class RawEntry(val title: String, val type: String, val region: String, val desc: String, val alertDate: String, val category: Int)
            val groups = mutableMapOf<String, MutableList<RawEntry>>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val alertDate = obj.optString("alertDate", "")
                val categoryStr = obj.optString("category", "")
                val rawCategory = categoryStr.toIntOrNull() ?: obj.optInt("category", 0)
                val key = "$alertDate|$categoryStr"

                var title = obj.optString("title", "")
                if (title.isEmpty()) title = obj.optString("category_desc", "")

                // Infer category from title if API didn't provide one
                val categoryInt = if (rawCategory > 0) rawCategory else inferCategoryFromTitle(title)

                var type = obj.optString("type", "")
                if (type.isEmpty()) {
                    type = mapCategoryToType(categoryInt)
                }
                if (type.isEmpty()) type = "missile"

                val region = obj.optString("data", "").trim()
                if (title.isNotEmpty() && region.isNotEmpty()) {
                    groups.getOrPut(key) { mutableListOf() }
                        .add(RawEntry(title, type, region, obj.optString("desc", ""), alertDate, categoryInt))
                }
            }

            // Second pass: merge each group into a single ActiveAlert with original timestamp
            val merged = groups.map { (_, entries) ->
                val first = entries[0]
                val allRegions = entries.map { it.region }.distinct()
                val timestampMs = parseAlertDate(first.alertDate, dateFormats)

                ActiveAlert(first.title, allRegions, first.desc, first.type, first.category, timestampMs)
            }

            Log.d(TAG, "Merged into ${merged.size} grouped alerts")
            merged
        } catch (e: Exception) {
            Log.w(TAG, "Parse history array failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseAlertDate(alertDate: String, formats: List<SimpleDateFormat>): Long {
        if (alertDate.isEmpty()) return 0L
        for (fmt in formats) {
            try {
                val date = fmt.parse(alertDate)
                if (date != null) return date.time
            } catch (_: Exception) { }
        }
        Log.w(TAG, "Could not parse alertDate: $alertDate")
        return 0L
    }

    private fun parseAlertObject(obj: JSONObject): ActiveAlert? {
        return try {
            // Title: try "title", then "category_desc" (from history endpoint)
            var title = obj.optString("title", "")
            if (title.isEmpty()) {
                title = obj.optString("category_desc", "")
            }
            val description = obj.optString("desc", "")
            val alertDate = obj.optString("alertDate", "")

            // Type can come from "type" field or "category" field (numeric or string)
            var type = obj.optString("type", "")
            if (type.isEmpty()) {
                val categoryValue = obj.opt("category")
                type = when (categoryValue) {
                    is Int -> mapCategoryToType(categoryValue)
                    is String -> mapCategoryToType(categoryValue.toIntOrNull() ?: -1)
                    else -> ""
                }
                if (type.isEmpty()) {
                    type = obj.optString("category", "")
                }
            }

            // Regions can come from "cities" array or "data" field
            val citiesArray = obj.optJSONArray("cities")
            val dataArray = obj.optJSONArray("data")

            val regions = mutableListOf<String>()

            // Try cities array first
            if (citiesArray != null) {
                Log.d(TAG, "Parsing cities array with ${citiesArray.length()} items")
                for (i in 0 until citiesArray.length()) {
                    regions.add(citiesArray.getString(i))
                }
            }
            // If no cities, try data array
            else if (dataArray != null) {
                Log.d(TAG, "Parsing data array with ${dataArray.length()} items")
                for (i in 0 until dataArray.length()) {
                    regions.add(dataArray.getString(i))
                }
            }
            // If data is a string — treat as single region or comma-separated list
            else {
                val dataStr = obj.optString("data", "")
                Log.d(TAG, "RAW data string: '$dataStr'")
                if (dataStr.isNotEmpty()) {
                    // Only split by comma (region names can contain spaces!)
                    if (dataStr.contains(",")) {
                        Log.d(TAG, "Splitting by comma")
                        regions.addAll(dataStr.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                    } else {
                        // Single region name (may contain spaces)
                        Log.d(TAG, "Single region: $dataStr")
                        regions.add(dataStr.trim())
                    }
                }
            }

            Log.d(TAG, "Alert: title=$title, date=$alertDate, type=$type, regions=$regions")

            if (title.isNotEmpty() && regions.isNotEmpty()) {
                // Log which regions have coordinates vs not
                val validRegions = regions.filter { OrefRegionCoords.coords.containsKey(it) }
                val unknownRegions = regions.filter { !OrefRegionCoords.coords.containsKey(it) }
                if (unknownRegions.isNotEmpty()) {
                    Log.w(TAG, "Regions missing from OrefRegionCoords: $unknownRegions")
                }
                Log.d(TAG, "Valid regions: $validRegions out of $regions")

                // Keep ALL regions (even without coordinates) so alerts show in list
                ActiveAlert(title, regions, description, type)
            } else {
                Log.d(TAG, "Alert missing title or regions: title=$title, regions=$regions")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Parse alert object failed: ${e.message}", e)
            null
        }
    }

    internal fun inferCategoryFromTitle(title: String): Int {
        val t = title.lowercase()
        return when {
            t.contains("רקטות") || t.contains("טילים") -> 1  // missiles
            t.contains("כלי טיס") || t.contains("חדירת") -> 2  // aircraft
            t.contains("הסתיים") || t.contains("all clear") -> 13  // event ended
            t.contains("אירוע") || t.contains("בדקות הקרובות") || t.contains("התרעות") -> 12  // general event/warning
            else -> 0
        }
    }

    private fun mapCategoryToType(category: Int): String {
        return when (category) {
            1 -> "missile"
            2 -> "aircraft"
            3 -> "earthquake"
            4 -> "tsunami"
            12 -> "event"
            13 -> "event"  // Event ended / all clear
            else -> ""
        }
    }

    private fun parse(json: String): ActiveAlert? {
        // Empty response means no alerts
        if (json.isEmpty() || json.trim() == "{}") {
            return null
        }

        return try {
            val obj = JSONObject(json)
            val title = obj.optString("title", "")
            val description = obj.optString("desc", "")
            val type = obj.optString("type", "")
            val cat = obj.optInt("cat", 0)
            val dataArray = obj.optJSONArray("data")

            val regions = mutableListOf<String>()
            if (dataArray != null) {
                for (i in 0 until dataArray.length()) {
                    regions.add(dataArray.getString(i))
                }
            }

            // Infer category from title if cat field is missing
            val category = if (cat > 0) cat else inferCategoryFromTitle(title)

            if (title.isNotEmpty() && regions.isNotEmpty()) {
                ActiveAlert(title, regions, description, type, category)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Parse failed: ${e.message}")
            null
        }
    }
}
