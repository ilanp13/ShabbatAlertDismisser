package com.ilanp13.shabbatalertdismisser

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONObject

object AlertStateMachine {

    private const val TAG = "AlertStateMachine"
    private const val PREF_KEY = "threat_state"

    enum class ThreatLevel { CLEAR, WARNING, ALARM }

    data class ThreatState(
        val level: ThreatLevel,
        val title: String,
        val regions: List<String>,
        val since: Long,       // timestamp when this state started
        val lastUpdate: Long   // timestamp of last alert that touched this state
    )

    private const val ALARM_TIMEOUT_MS = 30 * 60 * 1000L   // 30 minutes
    private const val WARNING_TIMEOUT_MS = 60 * 60 * 1000L  // 60 minutes

    /**
     * Process an incoming alert and update the threat state for selected regions.
     * Returns the new threat state.
     *
     * Category mapping:
     *   12 -> WARNING (potential threat incoming)
     *   1, 2 -> ALARM (active missiles/aircraft)
     *   13 -> CLEAR (event ended)
     */
    fun processAlert(
        context: Context,
        alert: RedAlertService.ActiveAlert,
        selectedRegions: Set<String>
    ): ThreatState {
        val now = System.currentTimeMillis()
        val current = getState(context)
        val category = alert.category

        // Event ended (cat 13): clear if we're in an elevated state, regardless
        // of region matching. The "event over" broadcast may cover different
        // regions than the original missiles/aircraft alerts.
        if (category == 13 && current.level != ThreatLevel.CLEAR) {
            Log.d(TAG, "Event ended (cat 13) while in ${current.level} -> CLEAR")
            val cleared = ThreatState(ThreatLevel.CLEAR, "", emptyList(), now, now)
            saveState(context, cleared)
            return cleared
        }

        // For all other categories, only process alerts that affect selected regions
        val matchingRegions = alert.regions.filter { it in selectedRegions }
        if (matchingRegions.isEmpty()) return current

        val newState = when {
            // Event ended -> CLEAR (also handles cat 13 with matching regions when already CLEAR)
            category == 13 -> {
                Log.d(TAG, "Event ended (cat 13) -> CLEAR")
                ThreatState(ThreatLevel.CLEAR, "", emptyList(), now, now)
            }

            // Active alarm (missiles/aircraft) -> ALARM
            // ALARM never downgrades to WARNING
            category == 1 || category == 2 -> {
                Log.d(TAG, "Active alarm (cat $category) -> ALARM")
                ThreatState(
                    ThreatLevel.ALARM,
                    alert.title,
                    matchingRegions,
                    if (current.level == ThreatLevel.ALARM) current.since else now,
                    now
                )
            }

            // Warning -> WARNING (only if not already in ALARM)
            category == 12 -> {
                if (current.level == ThreatLevel.ALARM) {
                    Log.d(TAG, "Warning received but already in ALARM, staying in ALARM")
                    current.copy(lastUpdate = now)
                } else {
                    Log.d(TAG, "Warning (cat 12) -> WARNING")
                    ThreatState(
                        ThreatLevel.WARNING,
                        alert.title,
                        matchingRegions,
                        if (current.level == ThreatLevel.WARNING) current.since else now,
                        now
                    )
                }
            }

            else -> {
                Log.d(TAG, "Unknown category $category, no state change")
                current
            }
        }

        saveState(context, newState)
        return newState
    }

    /**
     * Check for timeout — call this periodically (e.g., every poll cycle).
     * If the state has been active longer than the timeout, auto-clear.
     */
    fun processTick(context: Context): ThreatState {
        val current = getState(context)
        if (current.level == ThreatLevel.CLEAR) return current

        val now = System.currentTimeMillis()
        val elapsed = now - current.lastUpdate

        val timeout = when (current.level) {
            ThreatLevel.ALARM -> ALARM_TIMEOUT_MS
            ThreatLevel.WARNING -> WARNING_TIMEOUT_MS
            ThreatLevel.CLEAR -> return current
        }

        if (elapsed > timeout) {
            Log.d(TAG, "${current.level} timed out after ${elapsed / 60000}min -> CLEAR")
            val cleared = ThreatState(ThreatLevel.CLEAR, "", emptyList(), now, now)
            saveState(context, cleared)
            return cleared
        }

        return current
    }

    fun getState(context: Context): ThreatState {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val json = prefs.getString(PREF_KEY, null) ?: return defaultState()
        return try {
            val obj = JSONObject(json)
            val level = ThreatLevel.valueOf(obj.getString("level"))
            val title = obj.optString("title", "")
            val regionsArr = obj.optJSONArray("regions")
            val regions = mutableListOf<String>()
            if (regionsArr != null) {
                for (i in 0 until regionsArr.length()) {
                    regions.add(regionsArr.getString(i))
                }
            }
            val since = obj.optLong("since", 0L)
            val lastUpdate = obj.optLong("lastUpdate", 0L)
            ThreatState(level, title, regions, since, lastUpdate)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read threat state: ${e.message}")
            defaultState()
        }
    }

    fun clearState(context: Context) {
        saveState(context, defaultState())
    }

    private fun defaultState(): ThreatState {
        return ThreatState(ThreatLevel.CLEAR, "", emptyList(), 0L, 0L)
    }

    private fun saveState(context: Context, state: ThreatState) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        try {
            val obj = JSONObject()
            obj.put("level", state.level.name)
            obj.put("title", state.title)
            obj.put("since", state.since)
            obj.put("lastUpdate", state.lastUpdate)
            val regionsArr = org.json.JSONArray()
            for (r in state.regions) regionsArr.put(r)
            obj.put("regions", regionsArr)
            prefs.edit().putString(PREF_KEY, obj.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save threat state: ${e.message}")
        }
    }
}
