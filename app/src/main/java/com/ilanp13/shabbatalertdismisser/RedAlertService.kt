package com.ilanp13.shabbatalertdismisser

import android.util.Log
import org.json.JSONArray
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
        // Try multiple endpoint variations
        val endpoints = listOf(
            "https://www.oref.org.il/WarningMessages/alertsHistoryJson/alerts.json",
            "https://www.oref.org.il/WarningMessages/AlertsHistoryJson/Alerts.json",
            "https://www.oref.org.il/alerts/history",
            "https://www.oref.org.il/api/alerts/history"
        )

        for (endpoint in endpoints) {
            try {
                val url = URL(endpoint)
                Log.d(TAG, "Attempting to fetch history from: $url")

                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("Referer", "https://www.oref.org.il/")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")

                val responseCode = conn.responseCode
                Log.d(TAG, "History response code for $endpoint: $responseCode")

                if (responseCode != 200) {
                    Log.w(TAG, "Endpoint $endpoint returned status $responseCode, trying next...")
                    conn.disconnect()
                    continue
                }

                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                Log.d(TAG, "History response length: ${body.length}")
                Log.d(TAG, "History response (first 500 chars): ${body.take(500)}")

                if (body.isEmpty() || body.trim() == "{}" || body.trim() == "[]") {
                    Log.d(TAG, "Empty history response from $endpoint, trying next...")
                    continue
                }

                // Try parsing as array first
                val result = try {
                    parseHistoryArray(body)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse as array: ${e.message}")
                    try {
                        // If it's a single object, wrap it in array and parse
                        val wrapped = "[$body]"
                        parseHistoryArray(wrapped)
                    } catch (e2: Exception) {
                        Log.w(TAG, "Also failed to parse as wrapped object: ${e2.message}")
                        emptyList()
                    }
                }

                if (result.isNotEmpty()) {
                    Log.d(TAG, "SUCCESS: Parsed ${result.size} historical alerts from $endpoint")
                    return result
                } else {
                    Log.d(TAG, "Endpoint $endpoint returned no results, trying next...")
                    continue
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fetch history from $endpoint failed: ${e.message}")
                continue
            }
        }

        Log.w(TAG, "All history fetch endpoints failed")
        return emptyList()
    }

    private fun parseHistoryArray(json: String): List<ActiveAlert> {
        return try {
            val alerts = mutableListOf<ActiveAlert>()
            val array = JSONArray(json)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val alert = parseAlertObject(obj)
                if (alert != null) {
                    alerts.add(alert)
                }
            }
            Log.d(TAG, "Parsed ${alerts.size} historical alerts")
            alerts
        } catch (e: Exception) {
            Log.w(TAG, "Parse history array failed: ${e.message}")
            emptyList()
        }
    }

    private fun parseAlertObject(obj: JSONObject): ActiveAlert? {
        return try {
            val title = obj.optString("title", "")
            val description = obj.optString("desc", "")
            val type = obj.optString("type", "")
            val citiesArray = obj.optJSONArray("cities")

            val regions = mutableListOf<String>()
            if (citiesArray != null) {
                for (i in 0 until citiesArray.length()) {
                    regions.add(citiesArray.getString(i))
                }
            }

            if (title.isNotEmpty() && regions.isNotEmpty()) {
                ActiveAlert(title, regions, description, type)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Parse alert object failed: ${e.message}")
            null
        }
    }

    private fun parse(json: String): ActiveAlert? {
        // Empty response means no alerts
        if (json.isEmpty() || json.trim() == "{}") {
            return null
        }

        return try {
            val obj = JSONObject(json)
            // For active alerts, regions come in "data" field instead of "cities"
            val title = obj.optString("title", "")
            val description = obj.optString("desc", "")
            val type = obj.optString("type", "")
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
