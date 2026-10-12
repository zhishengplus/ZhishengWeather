package com.zhisheng.weather.data

import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class AppearanceDefaultsTest {
    @Test fun naturalLightStartsStrong() { assertEquals(AmbienceLevel.INTENSE, AmbienceLevel.from(null)) }
    @Test fun glowStartsFlowing() { assertEquals(SoftGlowLevel.FLOWING, SoftGlowLevel.from(null)) }
    @Test fun newInstallGetsOneAtomicAppearanceInitialization() = runBlocking {
        val defaults = WeatherAppearanceDefaultsMigration()
        val initial = defaults.migrate(emptyPreferences())
        assertEquals(true, initial[WeatherAppearanceDefaultsMigration.LIVING_SKY])
        assertEquals("intense", initial[WeatherAppearanceDefaultsMigration.AMBIENCE])
        assertEquals(true, initial[WeatherAppearanceDefaultsMigration.SOFT_GLOW])
        assertEquals("flowing", initial[WeatherAppearanceDefaultsMigration.GLOW_LEVEL])
        assertEquals(1, initial[WeatherAppearanceDefaultsMigration.REVISION])
        assertFalse(defaults.shouldMigrate(initial))
    }

    @Test fun firstUpgradeUpdatesOnlyAppearanceAndKeepsOtherSettings() = runBlocking {
        val other = stringPreferencesKey("source_pref")
        val icon = stringPreferencesKey("app_icon")
        val unit = stringPreferencesKey("temp_unit")
        val precise = booleanPreferencesKey("precise_location")
        val old = mutablePreferencesOf(other to "caiyun", icon to "minimal", unit to "f", precise to true,
            WeatherAppearanceDefaultsMigration.LIVING_SKY to false,
            WeatherAppearanceDefaultsMigration.AMBIENCE to "off",
            WeatherAppearanceDefaultsMigration.SOFT_GLOW to false,
            WeatherAppearanceDefaultsMigration.GLOW_LEVEL to "subtle")
        val result = WeatherAppearanceDefaultsMigration().migrate(old)
        assertEquals("caiyun", result[other])
        assertEquals("minimal", result[icon])
        assertEquals("f", result[unit])
        assertEquals(true, result[precise])
        assertEquals(true, result[WeatherAppearanceDefaultsMigration.LIVING_SKY])
        assertEquals("intense", result[WeatherAppearanceDefaultsMigration.AMBIENCE])
        assertEquals(true, result[WeatherAppearanceDefaultsMigration.SOFT_GLOW])
        assertEquals("flowing", result[WeatherAppearanceDefaultsMigration.GLOW_LEVEL])
        assertEquals(false, old[WeatherAppearanceDefaultsMigration.LIVING_SKY])
    }

    @Test fun userChangesSurviveEverySubsequentMigrationAttempt() = runBlocking {
        val defaults = WeatherAppearanceDefaultsMigration()
        val edited = defaults.migrate(emptyPreferences()).toMutablePreferences().apply {
            this[WeatherAppearanceDefaultsMigration.LIVING_SKY] = false
            this[WeatherAppearanceDefaultsMigration.AMBIENCE] = "off"
            this[WeatherAppearanceDefaultsMigration.SOFT_GLOW] = false
            this[WeatherAppearanceDefaultsMigration.GLOW_LEVEL] = "subtle"
        }
        assertFalse(defaults.shouldMigrate(edited))
        assertEquals(edited, defaults.migrate(edited))
        edited[WeatherAppearanceDefaultsMigration.REVISION] = 2
        assertFalse(defaults.shouldMigrate(edited))
        assertEquals(edited, defaults.migrate(edited))
    }

    @Test fun actualDataStorePersistsMigrationAndLaterUserChoicesAcrossReopen() = runBlocking {
        val folder = java.nio.file.Files.createTempDirectory("appearance-defaults-test").toFile()
        val file = java.io.File(folder, "settings.preferences_pb")
        var job = SupervisorJob()
        fun open() = PreferenceDataStoreFactory.create(
            migrations = listOf(WeatherAppearanceDefaultsMigration()),
            scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
        try {
            val first = open()
            val initial = first.data.first()
            assertEquals(1, initial[WeatherAppearanceDefaultsMigration.REVISION])
            assertEquals(true, initial[WeatherAppearanceDefaultsMigration.LIVING_SKY])
            first.edit {
                it[WeatherAppearanceDefaultsMigration.LIVING_SKY] = false
                it[WeatherAppearanceDefaultsMigration.AMBIENCE] = "subtle"
                it[WeatherAppearanceDefaultsMigration.SOFT_GLOW] = false
                it[WeatherAppearanceDefaultsMigration.GLOW_LEVEL] = "subtle"
            }
            job.cancelAndJoin()
            job = SupervisorJob()
            val reopened = open().data.first()
            assertEquals(false, reopened[WeatherAppearanceDefaultsMigration.LIVING_SKY])
            assertEquals("subtle", reopened[WeatherAppearanceDefaultsMigration.AMBIENCE])
            assertEquals(false, reopened[WeatherAppearanceDefaultsMigration.SOFT_GLOW])
            assertEquals("subtle", reopened[WeatherAppearanceDefaultsMigration.GLOW_LEVEL])
            assertEquals(1, reopened[WeatherAppearanceDefaultsMigration.REVISION])
        } finally {
            job.cancelAndJoin()
            file.delete()
            folder.delete()
        }
    }
}
