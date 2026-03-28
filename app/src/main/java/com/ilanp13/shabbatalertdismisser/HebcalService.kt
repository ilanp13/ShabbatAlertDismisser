package com.ilanp13.shabbatalertdismisser

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.util.TimeZone

object HebcalService {

    private const val TAG = "HebcalService"

    data class ShabbatWindow(val candleMs: Long, val havdalahMs: Long, val parasha: String? = null)

    /**
     * Result of a Hebcal fetch: all time windows for the upcoming week,
     * merged so adjacent/overlapping Yom Tov + Shabbat become one window.
     *
     * Examples:
     *   - Normal week: [Fri 18:40 → Sat 20:11]
     *   - Pesach Wed + gap + Shabbat: [Wed 18:40 → Thu 20:10], [Fri 18:41 → Sat 20:11]
     *   - Shavuot Thu → Shabbat (continuous): [Thu 18:40 → Sat 20:11]
     */
    data class FetchResult(
        val windows: List<ShabbatWindow>,
        val parasha: String? = null
    ) {
        /** The next upcoming window (or current if active) — for display/backward compat */
        fun nextWindow(now: Long = System.currentTimeMillis()): ShabbatWindow? {
            // Currently active window
            val active = windows.find { now in it.candleMs..it.havdalahMs }
            if (active != null) return active
            // Next upcoming
            return windows.filter { it.havdalahMs > now }.minByOrNull { it.candleMs }
        }

        /** Check if a timestamp falls within any window */
        fun isActiveAt(now: Long = System.currentTimeMillis()): Boolean {
            return windows.any { now in it.candleMs..it.havdalahMs }
        }
    }

    /**
     * Fetches Shabbat/holiday times from Hebcal for the upcoming week.
     * Returns all candle→havdalah windows, merged when adjacent.
     */
    fun fetch(lat: Double, lon: Double, candleMins: Int, havdalahMins: Int): FetchResult? {
        return try {
            val tzId = TimeZone.getDefault().id
            val url = URL(
                "https://www.hebcal.com/shabbat?cfg=json" +
                        "&latitude=$lat&longitude=$lon&tzid=$tzId" +
                        "&b=$candleMins&m=$havdalahMins"
            )
            Log.d(TAG, "Fetching: $url")

            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "ShabbatAlertDismisser/1.0")

            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            parse(body).also { Log.d(TAG, "Result: ${it?.windows?.size} windows") }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch failed: ${e.message}")
            null
        }
    }

    private fun parse(json: String): FetchResult? {
        val items = JSONObject(json).getJSONArray("items")
        var parasha: String? = null

        val candleTimes = mutableListOf<Long>()
        val havdalahTimes = mutableListOf<Long>()
        // Collect holiday names with their dates for association with windows
        val holidayNames = mutableListOf<Pair<Long, String>>() // (dateMs, hebrewName)

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            when (item.optString("category")) {
                "candles" -> candleTimes.add(parseDate(item.getString("date")))
                "havdalah" -> havdalahTimes.add(parseDate(item.getString("date")))
                "parashat" -> {
                    val hebrewText = item.optString("hebrew")
                    if (hebrewText.isNotEmpty()) parasha = hebrewText
                }
                "holiday" -> {
                    val hebrewText = item.optString("hebrew")
                    val dateStr = item.optString("date")
                    if (hebrewText.isNotEmpty() && dateStr.isNotEmpty()) {
                        try { holidayNames.add(parseDate(dateStr) to hebrewText) } catch (_: Exception) {}
                    }
                }
            }
        }

        if (candleTimes.isEmpty() || havdalahTimes.isEmpty()) return null

        // Sort both lists chronologically
        candleTimes.sort()
        havdalahTimes.sort()

        // Pair candles with havdalahs: each candle matches with the next havdalah after it
        val windows = mutableListOf<ShabbatWindow>()
        var hIdx = 0
        for (candle in candleTimes) {
            while (hIdx < havdalahTimes.size && havdalahTimes[hIdx] <= candle) hIdx++
            if (hIdx < havdalahTimes.size) {
                // Find the best label: holiday name near this candle, or parasha
                val nearbyHoliday = holidayNames.find { (dateMs, _) ->
                    kotlin.math.abs(dateMs - candle) < 48 * 3600 * 1000L // within 48h
                }?.second
                val label = nearbyHoliday ?: parasha
                windows.add(ShabbatWindow(candle, havdalahTimes[hIdx], label))
                hIdx++
            }
        }

        if (windows.isEmpty()) return null

        // Merge overlapping/adjacent windows
        val merged = mergeWindows(windows)
        Log.d(TAG, "Parsed ${candleTimes.size} candles, ${havdalahTimes.size} havdalahs -> " +
                "${windows.size} pairs -> ${merged.size} merged windows")

        return FetchResult(merged, parasha)
    }

    /**
     * Merge windows where one's havdalah >= next's candle (adjacent or overlapping).
     * This handles Shavuot Thu → Shabbat where Yom Tov havdalah = Shabbat candle.
     */
    private fun mergeWindows(windows: List<ShabbatWindow>): List<ShabbatWindow> {
        if (windows.size <= 1) return windows
        val sorted = windows.sortedBy { it.candleMs }
        val result = mutableListOf(sorted.first())

        for (i in 1 until sorted.size) {
            val last = result.last()
            val next = sorted[i]
            if (next.candleMs <= last.havdalahMs) {
                // Merge: extend the end, keep parasha from whichever has it
                result[result.lastIndex] = ShabbatWindow(
                    last.candleMs,
                    maxOf(last.havdalahMs, next.havdalahMs),
                    last.parasha ?: next.parasha
                )
            } else {
                result.add(next)
            }
        }
        return result
    }

    /** Serialize windows to JSON for SharedPreferences storage */
    fun windowsToJson(windows: List<ShabbatWindow>): String {
        val arr = JSONArray()
        for (w in windows) {
            val obj = JSONObject()
            obj.put("candle", w.candleMs)
            obj.put("havdalah", w.havdalahMs)
            if (w.parasha != null) obj.put("parasha", w.parasha)
            arr.put(obj)
        }
        return arr.toString()
    }

    /** Deserialize windows from JSON */
    fun windowsFromJson(json: String?): List<ShabbatWindow> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ShabbatWindow(
                    obj.getLong("candle"),
                    obj.getLong("havdalah"),
                    obj.optString("parasha", null)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse windows JSON: ${e.message}")
            emptyList()
        }
    }

    /** Check if a timestamp falls within any of the stored windows */
    fun isInAnyWindow(windows: List<ShabbatWindow>, now: Long = System.currentTimeMillis()): Boolean {
        return windows.any { now in it.candleMs..it.havdalahMs }
    }

    private fun parseDate(s: String): Long =
        OffsetDateTime.parse(s).toInstant().toEpochMilli()
}
