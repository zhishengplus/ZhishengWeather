package com.zhisheng.weather.widget

import com.zhisheng.weather.model.MinutePrecip
import com.zhisheng.weather.model.WeatherData

/** No hourly fallback, synthetic zeros, interpolation, or radar claims. */
internal fun widgetMinuteSeries(weather: WeatherData?, now: Long): List<MinutePrecip> {
    if (weather == null) return emptyList()
    val updated = weather.rainMeta?.updateTime ?: weather.fetchedAt ?: return emptyList()
    if (now - updated !in -300_000L..3_600_000L) return emptyList()
    val interval = (weather.rainMeta?.intervalMinutes ?: 1).coerceIn(1, 60) * 60_000L
    return weather.rainMinutes.asSequence()
        .filter { it.precip.isFinite() && it.precip >= 0 && it.timeMillis > now - interval && it.timeMillis <= now + 120 * 60_000L }
        .sortedBy { it.timeMillis }.distinctBy { it.timeMillis }.take(120).toList()
}
