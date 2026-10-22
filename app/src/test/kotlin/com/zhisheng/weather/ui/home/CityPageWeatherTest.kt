package com.zhisheng.weather.ui.home

import com.zhisheng.weather.data.CachedWeather
import com.zhisheng.weather.model.*
import org.junit.Assert.*
import org.junit.Test

class CityPageWeatherTest {
    @Test fun switchRefreshHonorsTheOneMinuteCacheAndClockRollback() {
        assertTrue(citySwitchNeedsRefresh(null, 120_000))
        assertFalse(citySwitchNeedsRefresh(60_001, 120_000))
        assertTrue(citySwitchNeedsRefresh(60_000, 120_000))
        assertTrue(citySwitchNeedsRefresh(120_001, 120_000))
    }
    private val now = 1_800_000_000_000L
    private val city = City("当前位置", "", 38.5, 102.2, "geo:38.500,102.200", coordinateKind = CoordinateKind.DEVICE)
    private val weather = WeatherData(current = CurrentWeather(temperature = 11.0), updateTime = now,
        locationMatch = WeatherLocationMatch(38.5, 102.2, 38.5, 102.2))
    private val preview = CachedWeather(weather, now - 60_000)
    @Test fun aFetchErrorWithoutCoordinatesRemainsAnErrorForItsOwnCity() {
        val failure = WeatherData(error = "请求超时")
        assertEquals(failure, cityPageWeather(city, city.locationKey, failure, null, null, now).data)
        assertEquals(failure, cityPageWeather(city, city.locationKey, failure, null, preview, now).data)
    }
    @Test fun anotherCityNeverBorrowsTheSelectedCityTemperatureOrError() {
        assertNull(cityPageWeather(city, "another-city", weather, null, null, now).data)
        assertNull(cityPageWeather(city, "another-city", WeatherData(error = "失败"), null, null, now).data)
        assertEquals(weather, cityPageWeather(city, "another-city", weather, null, preview, now).data)
    }
    @Test fun aChangedDeviceCoordinateRejectsBothLiveAndPreview() {
        val moved = city.copy(latitude = 38.5001)
        assertNull(cityPageWeather(moved, city.locationKey, weather, null, preview, now).data)
    }
    @Test fun expiredFutureOrIncompletePreviewNeverBecomesWeather() {
        for (bad in listOf(preview.copy(savedAtMillis = now + 1), preview.copy(savedAtMillis = now - 24 * 60 * 60_000L - 1),
            preview.copy(data = weather.copy(current = null)), preview.copy(data = weather.copy(error = "失败")),
            preview.copy(data = weather.copy(updateTime = now - 25 * 60 * 60_000L)))) {
            assertNull(cityPageWeather(city, null, null, null, bad, now).data)
        }
    }
    @Test fun theLiveResultWinsAndKeepsItsActualFallbackAge() {
        val current = weather.copy(current = CurrentWeather(temperature = 12.0))
        val result = cityPageWeather(city, city.locationKey, current, 1234L, preview, now)
        assertEquals(current, result.data)
        assertEquals(1234L, result.staleAgeMillis)
    }
    @Test fun aValidPreviewShowsItsRealAgeAndMissingDataStaysEmpty() {
        val cached = cityPageWeather(city, null, null, null, preview, now)
        assertEquals(weather, cached.data)
        assertEquals(60_000L, cached.staleAgeMillis)
        assertEquals(CityPageWeather(null, null), cityPageWeather(city, null, null, null, null, now))
    }
    @Test fun aNeighbourSnapshotRendersImmediatelyInsteadOfBlankWhileRefreshing() {
        val snapshot = CachedWeather(weather, now - 5_000L)
        val olderPreview = CachedWeather(weather, now - 120_000L)
        val result = cityPageWeather(city, "another-city", null, null, olderPreview, now, snapshot = snapshot)
        assertEquals(weather, result.data)
        assertEquals(5_000L, result.staleAgeMillis)
    }
    @Test fun aNewerDiskPreviewStillWinsOverAnOlderSnapshot() {
        val snapshot = CachedWeather(weather, now - 120_000L)
        val newerPreview = CachedWeather(weather, now - 5_000L)
        val result = cityPageWeather(city, "another-city", null, null, newerPreview, now, snapshot = snapshot)
        assertEquals(5_000L, result.staleAgeMillis)
    }
    @Test fun aSnapshotNeverOverridesTheSelectedCityLiveResult() {
        val current = weather.copy(current = CurrentWeather(temperature = 15.0))
        val result = cityPageWeather(city, city.locationKey, current, 0L, preview, now,
            snapshot = CachedWeather(weather, now - 5_000L))
        assertEquals(current, result.data)
        assertEquals(0L, result.staleAgeMillis)
    }
    @Test fun anExpiredSnapshotIsIgnored() {
        val expired = CachedWeather(weather, now - 24 * 60 * 60_000L - 1)
        assertNull(cityPageWeather(city, null, null, null, null, now, snapshot = expired).data)
    }
}
