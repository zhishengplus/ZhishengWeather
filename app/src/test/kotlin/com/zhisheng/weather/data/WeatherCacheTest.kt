package com.zhisheng.weather.data

import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.WeatherData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCacheTest {
    private val now = 2_000_000_000_000L

    @Test
    fun offlineCacheExpiresInsteadOfPresentingYesterdayAsCurrentWeather() {
        val current = WeatherData(current = CurrentWeather(temperature = 20.0), updateTime = now - 60_000L)
        assertTrue(CachedWeather(current, now - 60_000L).isUsableOfflineAt(now))
        assertFalse(CachedWeather(current, now - MAX_OFFLINE_WEATHER_AGE_MS - 1L).isUsableOfflineAt(now))
    }

    @Test
    fun freshlyDownloadedButOldProviderObservationIsRejected() {
        val staleObservation = WeatherData(
            current = CurrentWeather(temperature = 20.0),
            updateTime = now - MAX_OFFLINE_WEATHER_AGE_MS - 1L,
        )
        assertFalse(CachedWeather(staleObservation, now).isUsableOfflineAt(now))
    }
}
