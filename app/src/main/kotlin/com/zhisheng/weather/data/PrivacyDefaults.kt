package com.zhisheng.weather.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey

/** Basic diagnostics and version statistics default on. Explicit stored choices always win. */
internal object PrivacyDefaults {
    val statistics = booleanPreferencesKey("device_version_statistics")
    val diagnostics = booleanPreferencesKey("optional_service_diagnostics_v1")
    fun statisticsEnabled(prefs: Preferences) = prefs[statistics] ?: true
    fun diagnosticsEnabled(prefs: Preferences) = prefs[diagnostics] ?: true
}
