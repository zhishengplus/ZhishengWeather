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
    val nativePoints: List<MinutePrecip> = points,
)

/** The game must never replace rain, a partial window, or an hourly fallback. */
internal fun PrecipitationPresentation.hasDrySkyWindow(): Boolean {
    val now = nowMillis ?: return false
    val interval = intervalMinutes?.takeIf { it in 1..15 } ?: return false
    if (!dry || hourlyFallback || points.size < 2) return false
    val step = interval * Nowcast.MINUTE_MS
    val first = nativePoints.firstOrNull()?.timeMillis ?: return false
    val last = nativePoints.lastOrNull()?.timeMillis ?: return false
    // Judge completeness at the provider's timestamp. Requiring a new 120-minute
    // horizon at every frame would expire a complete response as soon as it arrives.
    // A native 15-minute bucket can begin before now; never extend its actual end.
    if (now - first !in -Nowcast.MINUTE_MS..(15 * Nowcast.MINUTE_MS) ||
        last + step - first < 120 * Nowcast.MINUTE_MS ||
        points.first().timeMillis > now + Nowcast.MINUTE_MS) return false
    return nativePoints.zipWithNext().all { (a, b) -> b.timeMillis - a.timeMillis in 1..step } &&
        points.zipWithNext().all { (a, b) -> b.timeMillis - a.timeMillis in 1..step }
}

// Presentation only: preserve actual timestamps and never turn a missing series into dry weather.
internal fun precipitationPresentation(data: WeatherData, nowMillis: Long): PrecipitationPresentation {
    val valid = data.rainMinutes.filter { it.precip.isFinite() && it.precip >= 0f }
    val declaredInterval = data.rainMeta?.intervalMinutes?.takeIf { it in 1..180 }
    val nativeInterval = declaredInterval ?: 1
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
    val interval = if (hourlyFallback) measuredInterval ?: 60 else
        declaredInterval ?: measuredInterval ?: nativeInterval.takeIf { points.size == 1 }
    // A removed provider sample is an unknown interval, not a new, coarser sampling rate.
    // Metadata wins over observed spacing; unlabelled legacy samples retain their measured spacing.
    val step = (interval ?: nativeInterval).toLong() * Nowcast.MINUTE_MS
    val hasGap = points.zipWithNext().any { (a, b) -> b.timeMillis - a.timeMillis !in 1..step }
    val coversNow = points.any { it.timeMillis <= nowMillis && nowMillis - it.timeMillis < step } ||
        points.firstOrNull()?.timeMillis?.let { it in nowMillis..(nowMillis + Nowcast.MINUTE_MS) } == true
    val wet = points.any { it.precip >= Nowcast.WET_THRESHOLD }
    val dry = points.size >= 2 && points.last().timeMillis > nowMillis && !wet && !currentPrecip && !hasGap && coversNow
    val timing = Nowcast.rainTiming(points, nowMillis, currentPrecip = currentPrecip,
        intervalMinutes = if (hourlyFallback) 60 else nativeInterval)
    val summary = when {
        currentPrecip -> "当前有降水"
        wet && timing.rainingNow -> "短时预报提示当前有降水"
        wet && measuredInterval != null && measuredInterval <= 5 && timing.minutesUntilStart != null ->
            "${if (nativeInterval > 1) "预计约" else "预计"} ${timing.minutesUntilStart} 分钟后有降水"
        wet -> "预报时段内有降水"
        points.isNotEmpty() && (hasGap || !coversNow) -> "预报数据有缺口，已返回时刻暂无降水"
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
        points.lastOrNull()?.timeMillis, interval, hourlyFallback, history, nowMillis,
        valid.groupBy { it.timeMillis }.values.map { samples -> samples.maxBy { it.precip } }
            .sortedBy { it.timeMillis })
}
