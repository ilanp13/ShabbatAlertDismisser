package com.ilanp13.shabbatalertdismisser

import android.util.Log

/**
 * Tracks per-region alert state for Live Mode.
 * Each region independently tracks: CLEAR → WARNING → ALARM → EVENT_ENDED → CLEAR
 *
 * Rules:
 *   - Warning ("in the next minutes"): region turns yellow
 *   - Alarm (missiles/aircraft): region turns red, yellow doesn't downgrade red
 *   - Event ended: clears the region (brief green, then clear)
 *   - Timeout: if no event_ended received, auto-clear after TIMEOUT_MS
 */
object RegionAlertTracker {

    private const val TAG = "RegionTracker"

    enum class RegionLevel { CLEAR, WARNING, ALARM, EVENT_ENDED }

    data class RegionState(
        val level: RegionLevel,
        val since: Long,       // when this state started
        val lastUpdate: Long   // last alert that touched this region
    )

    private const val ALARM_TIMEOUT_MS = 20 * 60 * 1000L      // 20 min — no event_ended fallback
    private const val WARNING_TIMEOUT_MS = 15 * 60 * 1000L    // 15 min
    private const val EVENT_ENDED_SHOW_MS = 3 * 60 * 1000L    // Show green for 3 min then clear

    private val regionStates = mutableMapOf<String, RegionState>()

    /** Process an alert and update affected regions */
    fun processAlert(alert: RedAlertService.ActiveAlert) {
        val now = System.currentTimeMillis()
        val alertTime = if (alert.timestampMs > 0) alert.timestampMs else now
        val category = if (alert.category > 0) alert.category
            else RedAlertService.inferCategoryFromTitle(alert.title)

        when {
            // Event ended (cat 13) — clear affected regions
            category == 13 -> {
                for (region in alert.regions) {
                    val current = regionStates[region]
                    if (current != null && current.level != RegionLevel.CLEAR) {
                        regionStates[region] = RegionState(RegionLevel.EVENT_ENDED, alertTime, now)
                    }
                }
            }
            // Alarm (missiles/aircraft)
            category == 1 || category == 2 -> {
                for (region in alert.regions) {
                    val current = regionStates[region]
                    regionStates[region] = RegionState(
                        RegionLevel.ALARM,
                        if (current?.level == RegionLevel.ALARM) current.since else alertTime,
                        now
                    )
                }
            }
            // Warning ("in the next minutes")
            category == 12 || category == 14 -> {
                for (region in alert.regions) {
                    val current = regionStates[region]
                    // Warning doesn't downgrade alarm
                    if (current?.level == RegionLevel.ALARM) continue
                    regionStates[region] = RegionState(
                        RegionLevel.WARNING,
                        if (current?.level == RegionLevel.WARNING) current.since else alertTime,
                        now
                    )
                }
            }
        }
    }

    /** Process cached alerts to rebuild state (called on init/resume) */
    fun processAlerts(alerts: List<AlertCacheService.CachedAlert>) {
        // Process in chronological order (oldest first)
        for (alert in alerts.sortedBy { it.timestampMs }) {
            val category = if (alert.category > 0) alert.category
                else RedAlertService.inferCategoryFromTitle(alert.title)
            processAlert(RedAlertService.ActiveAlert(
                title = alert.title,
                regions = alert.regions,
                description = alert.description,
                type = alert.type,
                category = category,
                timestampMs = alert.timestampMs
            ))
        }
    }

    /** Tick: timeout stale states */
    fun processTick(): Int {
        val now = System.currentTimeMillis()
        var cleared = 0
        val toRemove = mutableListOf<String>()

        for ((region, state) in regionStates) {
            val elapsed = now - state.lastUpdate
            val shouldClear = when (state.level) {
                RegionLevel.ALARM -> elapsed > ALARM_TIMEOUT_MS
                RegionLevel.WARNING -> elapsed > WARNING_TIMEOUT_MS
                RegionLevel.EVENT_ENDED -> elapsed > EVENT_ENDED_SHOW_MS
                RegionLevel.CLEAR -> true
            }
            if (shouldClear) {
                toRemove.add(region)
                cleared++
            }
        }
        for (region in toRemove) {
            regionStates.remove(region)
        }
        return cleared
    }

    /** Get all regions with active (non-CLEAR) states */
    fun getActiveRegions(): Map<String, RegionState> {
        return regionStates.filter { it.value.level != RegionLevel.CLEAR }
    }

    /** Get state for a specific region */
    fun getState(region: String): RegionState? {
        return regionStates[region]
    }

    /** Get the color for a region level */
    fun getLevelColor(level: RegionLevel): Int {
        return when (level) {
            RegionLevel.ALARM -> android.graphics.Color.RED
            RegionLevel.WARNING -> android.graphics.Color.parseColor("#FFC107")
            RegionLevel.EVENT_ENDED -> android.graphics.Color.parseColor("#4CAF50")
            RegionLevel.CLEAR -> android.graphics.Color.TRANSPARENT
        }
    }

    /** Clear all state */
    fun clear() {
        regionStates.clear()
    }

    /** Check if any region is active */
    fun hasActiveRegions(): Boolean {
        return regionStates.any { it.value.level != RegionLevel.CLEAR }
    }
}
