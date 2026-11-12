package com.zhisheng.weather.ui.home

import com.zhisheng.weather.data.CachedWeather
import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.data.isUsableOfflineAt
import com.zhisheng.weather.data.weatherCoordinatesMatch
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherData
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val CITY_WEATHER_REFRESH_MS = 10 * 60_000L

/** Offline data may remain visible while refreshing, but never survives a source or age mismatch. */
internal fun usableCityWeatherCache(entry: CachedWeather?, source: SourcePref, now: Long,
    configurationCompatible: Boolean? = null, city: City? = null): CachedWeather? =
    entry?.takeIf {
        it.data.current != null && it.data.error == null &&
            (configurationCompatible ?: source.matches(it.data)) && it.isUsableOfflineAt(now) &&
            (city == null || weatherCoordinatesMatch(city, it.data))
    }

internal fun chooseCityWeatherCache(
    memory: CachedWeather?, disk: CachedWeather?, source: SourcePref, now: Long,
): CachedWeather? = listOfNotNull(
    usableCityWeatherCache(memory, source, now), usableCityWeatherCache(disk, source, now),
).maxByOrNull { it.data.fetchedAt ?: it.data.updateTime ?: it.savedAtMillis }

/** Called on the UI thread: compatibility reads may suspend while another result is published. */
internal suspend fun mergeCityWeatherCache(
    cache: MutableMap<String, CachedWeather>, city: City, candidate: CachedWeather?, source: SourcePref,
    nowMillis: () -> Long = System::currentTimeMillis,
    configurationCompatible: suspend (WeatherData) -> Boolean,
): CachedWeather? {
    suspend fun checked(entry: CachedWeather?) = entry?.let {
        val compatible = configurationCompatible(it.data)
        usableCityWeatherCache(it, source, nowMillis(), compatible, city)
    }
    val incoming = checked(candidate)
    while (true) {
        val observed = cache[city.locationKey]
        val existing = checked(observed)
        currentCoroutineContext().ensureActive()
        // Never remove or replace a value that arrived during the suspended validation.
        if (cache[city.locationKey] !== observed) continue
        val now = nowMillis()
        val newest = listOfNotNull(existing, incoming)
            .filter { usableCityWeatherCache(it, source, now, configurationCompatible = true, city = city) != null }
            .maxByOrNull { it.data.fetchedAt ?: it.data.updateTime ?: it.savedAtMillis }
        if (newest == null) cache.remove(city.locationKey) else cache[city.locationKey] = newest
        return newest
    }
}

internal fun cityWeatherNeedsRefresh(entry: CachedWeather?, source: SourcePref, now: Long): Boolean =
    usableCityWeatherCache(entry, source, now)?.let {
        now - it.savedAtMillis >= CITY_WEATHER_REFRESH_MS
    } ?: true
