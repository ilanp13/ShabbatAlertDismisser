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
        Log.d(TAG, "====== FETCHHISTORY START ======")

        // Try multiple endpoint variations with different patterns
        val endpoints = listOf(
            // Original pattern variations
            "https://www.oref.org.il/WarningMessages/alertsHistoryJson/alerts.json",
            "https://www.oref.org.il/WarningMessages/AlertsHistoryJson/Alerts.json",
            // Alternative paths
            "https://www.oref.org.il/alerts/history",
            "https://www.oref.org.il/api/alerts/history",
            // With query parameters
            "https://www.oref.org.il/WarningMessages/alertsHistoryJson/alerts.json?hours=24",
            "https://www.oref.org.il/WarningMessages/alertsHistoryJson/alerts.json?days=1"
        )

        for ((index, endpoint) in endpoints.withIndex()) {
            Log.d(TAG, "[$index/${endpoints.size}] Attempting: $endpoint")

            try {
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000
                conn.setRequestProperty("Referer", "https://www.oref.org.il/")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")

                val responseCode = conn.responseCode
                Log.d(TAG, "Response code: $responseCode")

                if (responseCode !in 200..299) {
                    Log.w(TAG, "Bad status $responseCode, skipping")
                    conn.disconnect()
                    continue
                }

                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                Log.d(TAG, "Response length: ${body.length} bytes")
                if (body.length < 200) {
                    Log.d(TAG, "Full response: $body")
                } else {
                    Log.d(TAG, "Response start: ${body.take(300)}")
                }

                if (body.isEmpty() || body.trim() == "{}" || body.trim() == "[]") {
                    Log.d(TAG, "Empty/blank response")
                    continue
                }

                // Try parsing as array first
                val result = try {
                    Log.d(TAG, "Trying to parse as JSON array...")
                    parseHistoryArray(body)
                } catch (e: Exception) {
                    Log.w(TAG, "Array parse failed: ${e.message}")
                    try {
                        // If it's a single object, wrap it in array
                        Log.d(TAG, "Trying to wrap as array...")
                        val wrapped = "[$body]"
                        parseHistoryArray(wrapped)
                    } catch (e2: Exception) {
                        Log.w(TAG, "Wrapped parse also failed: ${e2.message}")
                        // Try parsing as object with "data" field
                        try {
                            Log.d(TAG, "Trying to parse as object with data field...")
                            val obj = JSONObject(body)
                            val dataArray = obj.optJSONArray("data")
                            if (dataArray != null) {
                                parseHistoryArray(dataArray.toString())
                            } else {
                                Log.w(TAG, "No data field found")
                                emptyList()
                            }
                        } catch (e3: Exception) {
                            Log.w(TAG, "Data field parse failed: ${e3.message}")
                            emptyList()
                        }
                    }
                }

                if (result.isNotEmpty()) {
                    Log.d(TAG, "✓ SUCCESS: Got ${result.size} alerts from this endpoint")
                    Log.d(TAG, "====== FETCHHISTORY END (SUCCESS) ======")
                    return result
                } else {
                    Log.d(TAG, "No results from this endpoint")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
            }
        }

        Log.w(TAG, "✗ All endpoints exhausted, returning empty")
        Log.d(TAG, "====== FETCHHISTORY END (FAILED) ======")
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
