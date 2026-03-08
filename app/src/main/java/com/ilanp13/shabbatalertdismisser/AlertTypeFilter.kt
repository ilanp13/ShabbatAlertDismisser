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
            ?: setOf("missiles", "aircraft", "event", "earthquake", "tsunami")  // Default: show all
    }

    fun setSelectedTypes(context: Context, types: Set<String>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putStringSet(PREF_KEY, types).apply()
    }

    fun shouldShow(context: Context, alertType: String): Boolean {
        val selected = getSelectedTypes(context)
        val normalized = alertType.lowercase()

        return when {
            normalized.contains("missile") || normalized.contains("rocket") -> selected.contains("missiles")
            normalized.contains("aircraft") -> selected.contains("aircraft")
            normalized.contains("event") -> selected.contains("event")
            normalized.contains("earthquake") -> selected.contains("earthquake")
            normalized.contains("tsunami") -> selected.contains("tsunami")
            else -> true  // Show unknown types by default
        }
    }

    fun isAllFiltered(context: Context): Boolean {
        return getSelectedTypes(context).size == 5  // All 5 types selected
    }
}
