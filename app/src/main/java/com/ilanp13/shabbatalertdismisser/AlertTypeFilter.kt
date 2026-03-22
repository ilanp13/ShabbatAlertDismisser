package com.ilanp13.shabbatalertdismisser

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Shared utility for managing alert type filters across all tabs
 */
object AlertTypeFilter {

    const val PREF_KEY = "alert_type_filter_selected"

    fun getSelectedTypes(context: Context): Set<String> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val saved = prefs.getStringSet(PREF_KEY, null)
            ?: return setOf("alarm", "warning", "event_ended")
        // Migrate old filter keys to new ones
        if (saved.contains("missile") || saved.contains("aircraft") || saved.contains("event")) {
            val migrated = mutableSetOf<String>()
            if (saved.contains("missile") || saved.contains("aircraft")) migrated.add("alarm")
            if (saved.contains("event")) { migrated.add("warning"); migrated.add("event_ended") }
            prefs.edit().putStringSet(PREF_KEY, migrated).apply()
            return migrated
        }
        return saved
    }

    fun setSelectedTypes(context: Context, types: Set<String>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putStringSet(PREF_KEY, types).apply()
    }

    fun shouldShow(context: Context, alertType: String): Boolean {
        val selected = getSelectedTypes(context)
        val normalized = alertType.lowercase().trim()

        return when {
            normalized.isEmpty() -> true
            normalized == "alarm" || normalized == "missile" || normalized == "missiles" || normalized == "aircraft" || normalized.contains("rocket") -> selected.contains("alarm")
            normalized == "warning" -> selected.contains("warning")
            normalized == "event_ended" || normalized == "event" -> selected.contains("event_ended")
            else -> true
        }
    }

    fun isAllFiltered(context: Context): Boolean {
        return getSelectedTypes(context).size == 3  // All 3 types selected
    }
}
