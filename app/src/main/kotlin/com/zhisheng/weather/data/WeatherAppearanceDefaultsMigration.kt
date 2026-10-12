package com.zhisheng.weather.data

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Apply this release's appearance once, before any setting is read or edited. */
internal class WeatherAppearanceDefaultsMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        (currentData[REVISION] ?: 0) < 1

    override suspend fun migrate(currentData: Preferences): Preferences {
        if (!shouldMigrate(currentData)) return currentData
        return currentData.toMutablePreferences().apply {
            this[LIVING_SKY] = true
            this[AMBIENCE] = AmbienceLevel.INTENSE.key
            this[SOFT_GLOW] = true
            this[GLOW_LEVEL] = SoftGlowLevel.FLOWING.key
            this[REVISION] = 1
        }
    }

    override suspend fun cleanUp() = Unit

    companion object {
        internal val REVISION = intPreferencesKey("weather_appearance_defaults_revision")
        internal val LIVING_SKY = booleanPreferencesKey("living_sky")
        internal val AMBIENCE = stringPreferencesKey("ambience")
        internal val SOFT_GLOW = booleanPreferencesKey("vista_soft_glow")
        internal val GLOW_LEVEL = stringPreferencesKey("vista_soft_glow_level")
    }
}
