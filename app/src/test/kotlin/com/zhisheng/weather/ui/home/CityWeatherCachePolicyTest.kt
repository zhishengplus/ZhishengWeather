package com.zhisheng.weather.ui.home

import com.zhisheng.weather.data.CachedWeather
import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.WeatherData
import org.junit.Assert.*
import org.junit.Test

class CityWeatherCachePolicyTest {
    private val now = 1_800_000_000_000L
    private fun cached(age: Long = 0L, source: String = "XIAOMI") = CachedWeather(
        WeatherData(current = CurrentWeather(temperature = 22.0), updateTime = now - age, dataSource = source),
        savedAtMillis = now - age,
    )

    @Test fun expiredMemoryDoesNotSurviveMissingDiskCache() {
        val expired = cached(age = 24 * 60 * 60_000L + 1)
        assertNull(chooseCityWeatherCache(expired, null, SourcePref.XIAOMI, now))
        assertTrue(cityWeatherNeedsRefresh(expired, SourcePref.XIAOMI, now))
    }

    @Test fun changingSourceRejectsBothOldCopies() {
        val previous = cached()
        assertNull(chooseCityWeatherCache(previous, previous, SourcePref.OPEN_METEO, now))
        assertTrue(cityWeatherNeedsRefresh(previous, SourcePref.OPEN_METEO, now))
    }

    @Test fun validOfflineCopyStaysVisibleButRefreshesAfterTenMinutes() {
        val entry = cached(age = CITY_WEATHER_REFRESH_MS)
        assertEquals(entry, chooseCityWeatherCache(entry, null, SourcePref.XIAOMI, now))
        assertTrue(cityWeatherNeedsRefresh(entry, SourcePref.XIAOMI, now))
        assertFalse(cityWeatherNeedsRefresh(cached(age = CITY_WEATHER_REFRESH_MS - 1), SourcePref.XIAOMI, now))
    }

    @Test fun loadingOlderDiskDataCannotReplaceNewerMemory() {
        val fresh = cached(age = 1_000L)
        assertEquals(fresh, chooseCityWeatherCache(fresh, cached(age = 60_000L), SourcePref.XIAOMI, now))
    }

    @Test fun freshSaveTimeCannotHideExpiredProviderObservation() {
        val stale = cached().let { it.copy(data = it.data.copy(updateTime = now - 25 * 60 * 60_000L)) }
        assertNull(usableCityWeatherCache(stale, SourcePref.XIAOMI, now))
    }

    @Test fun invalidWeatherAndMixedSourceAreNeverPresentedAsValidCards() {
        val entry = cached()
        assertNull(usableCityWeatherCache(entry.copy(data = entry.data.copy(current = null)), SourcePref.XIAOMI, now))
        assertNull(usableCityWeatherCache(entry.copy(data = entry.data.copy(error = "failed")), SourcePref.XIAOMI, now))
        assertNull(usableCityWeatherCache(entry.copy(data = entry.data.copy(blockSources = mapOf("daily" to "OPEN-METEO"))), SourcePref.XIAOMI, now))
    }
}
