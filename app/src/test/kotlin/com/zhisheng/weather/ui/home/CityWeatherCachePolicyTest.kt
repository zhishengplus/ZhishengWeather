package com.zhisheng.weather.ui.home

import com.zhisheng.weather.data.CachedWeather
import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.City
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class CityWeatherCachePolicyTest {
    private val now = 1_800_000_000_000L
    @Test fun sameKeyDifferentMeasuredCoordinatesRejectsMemoryAndLiveWeather() {
        val city = com.zhisheng.weather.model.City("当前位置", "", 38.5, 102.2, "geo:38.500,102.200",
            coordinateKind = com.zhisheng.weather.model.CoordinateKind.DEVICE)
        val previous = cached().let { it.copy(data = it.data.copy(locationMatch =
            com.zhisheng.weather.model.WeatherLocationMatch(38.5001, 102.2001, 38.5, 102.2))) }
        assertNull(usableCityWeatherCache(previous, SourcePref.XIAOMI, now, city = city))
        assertEquals(previous, usableCityWeatherCache(previous, SourcePref.XIAOMI, now,
            city = city.copy(latitude = 38.5001, longitude = 102.2001)))
    }
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

    @Test fun aLateSaveTimeCannotMakeAnOlderFetchReplaceNewerLiveWeather() = runBlocking {
        val city = City("test", "", 39.9, 116.4, "test")
        val fresh = cached(1_000).let { it.copy(data = it.data.copy(fetchedAt = now - 1_000)) }
        val old = cached(60_000).let { it.copy(data = it.data.copy(fetchedAt = now - 60_000), savedAtMillis = now) }
        val cache = mutableMapOf(city.locationKey to fresh)
        assertEquals(fresh, mergeCityWeatherCache(cache, city, old, SourcePref.XIAOMI, { now }) { true })
        assertEquals(fresh, cache[city.locationKey])
    }

    @Test fun aDelayedDiskValidationCannotOverwriteLiveWeatherThatArrivesFirst() = runBlocking {
        withTimeout(5_000) {
            val city = City("test", "", 39.9, 116.4, "test")
            val cache = mutableMapOf<String, CachedWeather>()
            val readingDisk = CompletableDeferred<Unit>()
            val releaseDisk = CompletableDeferred<Unit>()
            val old = cached(60_000)
            val fresh = cached(1_000)
            val diskJob = launch {
                mergeCityWeatherCache(cache, city, old, SourcePref.XIAOMI, { now }) {
                    if (it === old.data) { readingDisk.complete(Unit); releaseDisk.await() }
                    true
                }
            }
            readingDisk.await()
            mergeCityWeatherCache(cache, city, fresh, SourcePref.XIAOMI, { now }) { true }
            releaseDisk.complete(Unit)
            diskJob.join()
            assertEquals(fresh, cache[city.locationKey])
        }
    }

    @Test fun rejectingOldMemoryAfterASuspensionCannotEraseNewLiveWeather() = runBlocking {
        withTimeout(5_000) {
            val city = City("test", "", 39.9, 116.4, "test")
            val old = cached(60_000)
            val fresh = cached(1_000)
            val cache = mutableMapOf(city.locationKey to old)
            val checkingOld = CompletableDeferred<Unit>()
            val releaseOld = CompletableDeferred<Unit>()
            val pruneJob = launch {
                mergeCityWeatherCache(cache, city, null, SourcePref.XIAOMI, { now }) {
                    if (it === old.data) { checkingOld.complete(Unit); releaseOld.await(); false } else true
                }
            }
            checkingOld.await()
            mergeCityWeatherCache(cache, city, fresh, SourcePref.XIAOMI, { now }) { true }
            releaseOld.complete(Unit)
            pruneJob.join()
            assertEquals(fresh, cache[city.locationKey])
        }
    }

    @Test fun incompatibleCandidatesDoNotReplaceValidLiveWeather() = runBlocking {
        val city = City("test", "", 39.9, 116.4, "test")
        val fresh = cached(1_000)
        val wrongSource = cached(source = "OPEN-METEO")
        val cache = mutableMapOf(city.locationKey to fresh)
        mergeCityWeatherCache(cache, city, wrongSource, SourcePref.XIAOMI, { now }) { it.dataSource == "XIAOMI" }
        assertEquals(fresh, cache[city.locationKey])
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
