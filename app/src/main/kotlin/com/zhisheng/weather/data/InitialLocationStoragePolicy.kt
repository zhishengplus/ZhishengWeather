package com.zhisheng.weather.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Storage-only first-location policy; legacy city tables are already handled. */
internal object InitialLocationStoragePolicy {
    val citiesKey = stringPreferencesKey("cities")
    val backupKey = stringPreferencesKey("cities_backup")
    val selectedKey = stringPreferencesKey("selected_key")
    val completedKey = booleanPreferencesKey("initial_location_setup_complete")

    fun setupRequired(preferences: Preferences): Boolean =
        preferences[completedKey]?.not() ?: !preferences.contains(citiesKey)

    /** Must run inside the edit transaction, so a queued city addition takes priority. */
    fun initializeIfAbsent(preferences: MutablePreferences) {
        if (preferences.contains(citiesKey)) return
        preferences[citiesKey] = "[]"
        preferences[backupKey] = "[]"
        preferences.remove(selectedKey)
        // Manual/skip can complete setup before initialization obtains the transaction.
        if (!preferences.contains(completedKey)) preferences[completedKey] = false
    }
}
