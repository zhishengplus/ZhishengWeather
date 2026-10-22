package com.zhisheng.weather.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.*
import org.junit.Test

class PrivacyDefaultsTest {
    @Test fun `fresh installation enables only basic diagnostics and statistics defaults`() {
        val prefs = mutablePreferencesOf()
        assertTrue(PrivacyDefaults.statisticsEnabled(prefs))
        assertTrue(PrivacyDefaults.diagnosticsEnabled(prefs))
        assertTrue(prefs.asMap().isEmpty())
    }
    @Test fun `upgrade never overrides explicit choices and repeated initialization cannot reenable`() {
        for (statistics in listOf(true, false)) for (diagnostics in listOf(true, false)) {
            val prefs = mutablePreferencesOf(PrivacyDefaults.statistics to statistics, PrivacyDefaults.diagnostics to diagnostics)
            assertEquals(statistics, PrivacyDefaults.statisticsEnabled(prefs)); assertEquals(diagnostics, PrivacyDefaults.diagnosticsEnabled(prefs))
        }
        val partial = mutablePreferencesOf(PrivacyDefaults.statistics to false)
        assertFalse(PrivacyDefaults.statisticsEnabled(partial)); assertTrue(PrivacyDefaults.diagnosticsEnabled(partial))
    }
    @Test fun `explicit disabling survives repeated reads`() {
        val prefs = mutablePreferencesOf(PrivacyDefaults.statistics to false, PrivacyDefaults.diagnostics to false)
        repeat(3) { assertFalse(PrivacyDefaults.statisticsEnabled(prefs)); assertFalse(PrivacyDefaults.diagnosticsEnabled(prefs)) }
    }
}
