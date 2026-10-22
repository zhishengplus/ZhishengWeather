package com.zhisheng.weather.data

import com.zhisheng.weather.model.WeatherData

/** Wall-clock correction must not make a previously saved future stamp win forever. */
internal fun shouldReplaceWeatherCache(previous: WeatherData?, candidate: WeatherData, nowMillis: Long): Boolean {
    val incoming = candidate.fetchedAt ?: 0L
    val existing = previous?.fetchedAt ?: 0L
    // A response completed before a backward clock correction may arrive afterwards.
    // Do not let it replace data obtained under the corrected clock.
    if (incoming > nowMillis) return false
    if (incoming <= 0L) return existing <= 0L
    return existing > nowMillis || incoming >= existing
}

/** Prefer a usable new-clock snapshot even if another cache still contains a future stamp. */
internal fun latestWeatherCache(candidates: List<WeatherData>, nowMillis: Long): WeatherData? =
    candidates.filter { (it.fetchedAt ?: 0L) <= nowMillis }.ifEmpty { candidates }
        .maxByOrNull { it.fetchedAt ?: 0L }
