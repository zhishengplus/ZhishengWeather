package com.zhisheng.weather.ui.home

import com.zhisheng.weather.data.CachedWeather
import com.zhisheng.weather.data.isUsableOfflineAt
import com.zhisheng.weather.data.weatherCoordinatesMatch
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherData

internal data class CityPageWeather(val data: WeatherData?, val staleAgeMillis: Long?)

internal fun citySwitchNeedsRefresh(savedAt: Long?, now: Long): Boolean =
    savedAt == null || now < savedAt || now - savedAt >= 60_000L

/** Both snapshot and preview must pass coordinate/provider checks before they count as content. */
internal fun cityPageWeather(city: City, selectedKey: String?, live: WeatherData?, liveStaleAge: Long?,
    preview: CachedWeather?, now: Long, snapshot: CachedWeather? = null): CityPageWeather {
    val ownedLive = live?.takeIf {
        selectedKey == city.locationKey && (it.current == null || weatherCoordinatesMatch(city, it))
    }
    // An actual fetch failure belongs to this city even though it has no coordinate evidence.
    // Keep that error/retry state; never turn it into an endless "fetching" placeholder.
    if (ownedLive != null) return CityPageWeather(ownedLive, liveStaleAge)
    // Prefer the freshest usable in-memory snapshot over the disk preview so a page never
    // blanks while the next fetch runs; a stale entry still renders with its real age.
    val cached = listOfNotNull(snapshot, preview)
        .filter { entry ->
            entry.data.current != null && entry.data.error == null &&
                entry.isUsableOfflineAt(now) && weatherCoordinatesMatch(city, entry.data)
        }
        .maxByOrNull { it.savedAtMillis }
    return CityPageWeather(cached?.data, cached?.let { now - it.savedAtMillis })
}
