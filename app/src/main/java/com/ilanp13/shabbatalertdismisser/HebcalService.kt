package com.ilanp13.shabbatalertdismisser

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.util.TimeZone

object HebcalService {

    private const val TAG = "HebcalService"

    data class ShabbatWindow(val candleMs: Long, val havdalahMs: Long)

    /**
     * Fetches the next Shabbat's candle-lighting and havdalah times from Hebcal.
     * Returns null on network failure (caller should fall back to local calculation).
     *
     * @param candleMins  Minutes before sunset for candle lighting (Hebcal 'b' param)
     * @param havdalahMins Minutes after sunset for havdalah (Hebcal 'm' param)
     */
    fun fetch(lat: Double, lon: Double, candleMins: Int, havdalahMins: Int): ShabbatWindow? {
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

            parse(body).also { Log.d(TAG, "Result: $it") }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch failed: ${e.message}")
            null
        }
    }

    private fun parse(json: String): ShabbatWindow? {
        val items = JSONObject(json).getJSONArray("items")
        var candleMs = 0L
        var havdalahMs = 0L

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            when (item.optString("category")) {
                "candles"  -> candleMs  = parseDate(item.getString("date"))
                "havdalah" -> havdalahMs = parseDate(item.getString("date"))
            }
        }

        return if (candleMs > 0 && havdalahMs > 0) ShabbatWindow(candleMs, havdalahMs) else null
    }

    private fun parseDate(s: String): Long =
        OffsetDateTime.parse(s).toInstant().toEpochMilli()
}
