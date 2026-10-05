package com.zhisheng.weather.ui.home

import com.zhisheng.weather.data.CachedWeather
import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.data.isUsableOfflineAt

internal const val CITY_WEATHER_REFRESH_MS = 10 * 60_000L

/** Offline data may remain visible while refreshing, but never survives a source or age mismatch. */
internal fun usableCityWeatherCache(entry: CachedWeather?, source: SourcePref, now: Long): CachedWeather? =
    entry?.takeIf {
        it.data.current != null && it.data.error == null && source.matches(it.data) && it.isUsableOfflineAt(now)
    }

internal fun chooseCityWeatherCache(
    memory: CachedWeather?, disk: CachedWeather?, source: SourcePref, now: Long,
): CachedWeather? = listOfNotNull(
    usableCityWeatherCache(memory, source, now), usableCityWeatherCache(disk, source, now),
).maxByOrNull { it.savedAtMillis }

internal fun cityWeatherNeedsRefresh(entry: CachedWeather?, source: SourcePref, now: Long): Boolean =
    usableCityWeatherCache(entry, source, now)?.let {
        now - it.savedAtMillis >= CITY_WEATHER_REFRESH_MS
    } ?: true
