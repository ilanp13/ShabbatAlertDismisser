package com.ilanp13.shabbatalertdismisser

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object RedAlertService {

    private const val TAG = "RedAlertService"

    data class ActiveAlert(
        val title: String,
        val regions: List<String>,
        val description: String,
        val type: String = ""  // e.g., "missiles", "event", etc.
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
            Log.d(TAG, "Fetching alerts from: $url")

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

    private fun parse(json: String): ActiveAlert? {
        // Empty response means no alerts
        if (json.isEmpty() || json.trim() == "{}") {
            return null
        }

        return try {
            val obj = JSONObject(json)
            val title = obj.optString("title", "")
            val description = obj.optString("desc", "")
            val type = obj.optString("type", "")  // e.g., "Missiles", "Event", "Test"
            val dataArray = obj.optJSONArray("data")

            val regions = mutableListOf<String>()
            if (dataArray != null) {
                for (i in 0 until dataArray.length()) {
                    regions.add(dataArray.getString(i))
                }
            }

            if (title.isNotEmpty() && regions.isNotEmpty()) {
                ActiveAlert(title, regions, description, type)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Parse failed: ${e.message}")
            null
        }
    }
}
