package com.ilanp13.shabbatalertdismisser.shared

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
                buildString {
                    // Use the calendar API with 2-week range for full holiday coverage
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    val startDate = sdf.format(java.util.Date())
                    val cal = java.util.Calendar.getInstance()
                    cal.add(java.util.Calendar.DAY_OF_YEAR, 14)
                    val endDate = sdf.format(cal.time)
                    append("https://www.hebcal.com/hebcal?v=1&cfg=json&i=on")
                    append("&latitude=$lat&longitude=$lon&tzid=$tzId")
                    append("&b=$candleMins&m=$havdalahMins")
                    append("&start=$startDate&end=$endDate")
                    append("&c=on&s=on&maj=on&ss=on") // candles, havdalah, major holidays, parasha
                }
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
        // Holiday items with Hebrew names and dates (for labeling windows)
        data class HolidayInfo(val dateMs: Long, val hebrew: String, val subcat: String)
        val holidays = mutableListOf<HolidayInfo>()

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            when (item.optString("category")) {
                "candles" -> candleTimes.add(parseDate(item.getString("date")))
                "havdalah" -> havdalahTimes.add(parseDate(item.getString("date")))
                "parashat" -> {
                    val hebrewText = item.optString("hebrew")
                    if (hebrewText.isNotEmpty()) parasha = hebrewText
                    // Also store as a holiday-like item for window labeling
                    val dateStr = item.optString("date")
                    if (dateStr.isNotEmpty()) {
                        try { holidays.add(HolidayInfo(parseDateLoose(dateStr), hebrewText, "parashat")) }
                        catch (_: Exception) {}
                    }
                }
                "holiday" -> {
                    val hebrewText = item.optString("hebrew")
                    val dateStr = item.optString("date")
                    val subcat = item.optString("subcat")
                    if (hebrewText.isNotEmpty() && dateStr.isNotEmpty()) {
                        try { holidays.add(HolidayInfo(parseDateLoose(dateStr), hebrewText, subcat)) }
                        catch (_: Exception) {}
                    }
                }
            }
        }

        if (candleTimes.isEmpty() || havdalahTimes.isEmpty()) return null

        candleTimes.sort()
        havdalahTimes.sort()

        // Pair candles with havdalahs
        val windows = mutableListOf<ShabbatWindow>()
        var hIdx = 0
        for (candle in candleTimes) {
            while (hIdx < havdalahTimes.size && havdalahTimes[hIdx] <= candle) hIdx++
            if (hIdx < havdalahTimes.size) {
                val havdalah = havdalahTimes[hIdx]
                // Find the best Hebrew label for this window:
                // Look for major holidays within the window, then parasha
                // Find the actual holiday (not "erev") that falls within candle→havdalah
                // Holiday dates are midnight, candle is evening — so allow a small backward buffer
                val majorHoliday = holidays
                    .filter { h ->
                        h.subcat == "major" && !h.hebrew.startsWith("ערב") &&
                        h.dateMs >= candle - 6 * 3600_000L && h.dateMs <= havdalah
                    }
                    .minByOrNull { it.dateMs } // prefer the earliest matching holiday
                val parashat = holidays.find { h ->
                    h.subcat == "parashat" &&
                    h.dateMs >= candle - 6 * 3600_000L && h.dateMs <= havdalah
                }
                // Prefer major holiday name; fall back to parasha
                val label = majorHoliday?.hebrew ?: parashat?.hebrew
                windows.add(ShabbatWindow(candle, havdalah, label))
                hIdx++
            }
        }

        if (windows.isEmpty()) return null

        val merged = mergeWindows(windows)
        Log.d(TAG, "Parsed ${candleTimes.size} candles, ${havdalahTimes.size} havdalahs, " +
                "${holidays.size} holidays -> ${windows.size} pairs -> ${merged.size} merged")

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
                    obj.optString("parasha", "").ifEmpty { null }
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

    /** Parse both ISO datetime ("2026-04-01T18:42:00+03:00") and date-only ("2026-04-01") */
    private fun parseDateLoose(s: String): Long {
        return if (s.contains("T")) {
            parseDate(s)
        } else {
            // Date-only: treat as noon on that day in default timezone
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.timeZone = TimeZone.getDefault()
            sdf.parse(s)?.time ?: throw IllegalArgumentException("Invalid date: $s")
        }
    }
}
