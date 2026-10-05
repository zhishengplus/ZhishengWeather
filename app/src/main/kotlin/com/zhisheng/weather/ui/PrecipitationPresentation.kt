package com.zhisheng.weather.ui

import com.zhisheng.weather.model.MinutePrecip
import com.zhisheng.weather.model.Nowcast
import com.zhisheng.weather.model.WeatherData

internal data class PrecipitationPresentation(
    val points: List<MinutePrecip>,
    val summary: String,
    val dry: Boolean,
    val coverageStart: Long? = null,
    val coverageEnd: Long? = null,
    val intervalMinutes: Int? = null,
    val hourlyFallback: Boolean = false,
    val history: List<MinutePrecip> = emptyList(),
    val nowMillis: Long? = null,
)

// Presentation only: preserve actual timestamps and never turn a missing series into dry weather.
internal fun precipitationPresentation(data: WeatherData, nowMillis: Long): PrecipitationPresentation {
    val valid = data.rainMinutes.filter { it.precip.isFinite() && it.precip >= 0f }
    val nativeInterval = data.rainMeta?.intervalMinutes?.takeIf { it > 0 } ?: 1
    val activeTimes = Nowcast.samplesAt(valid, nowMillis, nativeInterval).map { it.timeMillis }.toSet()
    val minutePoints = valid.filter { it.timeMillis >= nowMillis - Nowcast.NOW_WINDOW_MS || it.timeMillis in activeTimes }
        .groupBy { it.timeMillis }.values.map { samples -> samples.maxBy { it.precip } }
        .sortedBy { it.timeMillis }
    // Caiyun accounts without minutely permission still return real hourly precipitation.
    // Show those native samples explicitly; never interpolate them into a minute forecast.
    val hourlyPoints = if (minutePoints.isEmpty() && data.dataSource == "CAIYUN") {
        data.hourly.filter { it.timeMillis in nowMillis..(nowMillis + 3 * 3_600_000L) }
            .mapNotNull { hour -> hour.precipMm?.toFloat()?.takeIf { it.isFinite() && it >= 0f }
                ?.let { MinutePrecip(hour.timeMillis, it) } }
            .distinctBy { it.timeMillis }.sortedBy { it.timeMillis }
    } else emptyList()
    val hourlyFallback = hourlyPoints.isNotEmpty()
    val points = if (hourlyFallback) hourlyPoints else minutePoints
    val currentPrecip = data.current?.let {
        it.condition?.isPrecipitation == true || (it.precipMm?.takeIf(Double::isFinite) ?: 0.0) > 0.05
    } == true
    val steps = points.zipWithNext { a, b -> b.timeMillis - a.timeMillis }.distinct()
    val measuredInterval = steps.singleOrNull()?.takeIf { it > 0 && it % Nowcast.MINUTE_MS == 0L }
        ?.div(Nowcast.MINUTE_MS)?.toInt()
    // Some providers expand native 15-minute buckets to minute points. Keep the native precision.
    val interval = measuredInterval?.let { measured ->
        if (hourlyFallback) measured else
            data.rainMeta?.intervalMinutes?.takeIf { it >= measured && it % measured == 0 } ?: measured
    } ?: if (!hourlyFallback && points.size == 1) nativeInterval else null
    val wet = points.any { it.precip >= Nowcast.WET_THRESHOLD }
    val dry = points.size >= 2 && points.last().timeMillis > nowMillis && !wet && !currentPrecip
    val timing = Nowcast.rainTiming(points, nowMillis, currentPrecip = currentPrecip,
        intervalMinutes = if (hourlyFallback) 60 else nativeInterval)
    val summary = when {
        currentPrecip -> "当前有降水"
        wet && timing.rainingNow -> "目前有降水"
        wet && measuredInterval != null && measuredInterval <= 5 && timing.minutesUntilStart != null ->
            "预计 ${timing.minutesUntilStart} 分钟后有降水"
        wet -> "预报时段内有降水"
        dry -> "预报时段内暂无降水"
        points.isNotEmpty() -> "该时刻暂无降水"
        valid.isNotEmpty() -> "短时预报已过期"
        !data.rainNowcast.isNullOrBlank() -> data.rainNowcast.trim()
        else -> data.rainDistanceKm?.takeIf { it.isFinite() && it >= 0.0 }?.let { km ->
            if (km < 0.5) "雨区就在附近"
            else "雨区距此约 ${java.lang.String.format(java.util.Locale.US, "%.1f", km)} km"
        } ?: "暂无短时降水预报"
    }
    val history = data.rainHistory.filter { it.timeMillis in (nowMillis - 7_200_000L)..nowMillis &&
        it.precip.isFinite() && it.precip >= 0f }.distinctBy { it.timeMillis }.sortedBy { it.timeMillis }
    return PrecipitationPresentation(points, summary, dry, points.firstOrNull()?.timeMillis,
        points.lastOrNull()?.timeMillis, interval, hourlyFallback, history, nowMillis)
}
