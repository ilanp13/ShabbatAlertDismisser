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
        return prefs.getStringSet(PREF_KEY, null)
            ?: setOf("missile", "aircraft", "event")  // Default: show all
    }

    fun setSelectedTypes(context: Context, types: Set<String>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putStringSet(PREF_KEY, types).apply()
    }

    fun shouldShow(context: Context, alertType: String): Boolean {
        val selected = getSelectedTypes(context)
        val normalized = alertType.lowercase().trim()

        return when {
            normalized.isEmpty() -> true  // Show alerts with no type (don't hide them)
            normalized == "missile" || normalized == "missiles" || normalized.contains("rocket") -> selected.contains("missile")
            normalized == "aircraft" -> selected.contains("aircraft")
            normalized == "event" -> selected.contains("event")
            normalized == "earthquake" -> selected.contains("earthquake")
            normalized == "tsunami" -> selected.contains("tsunami")
            else -> true  // Show unknown types by default
        }
    }

    fun isAllFiltered(context: Context): Boolean {
        return getSelectedTypes(context).size == 3  // All 3 types selected
    }
}
