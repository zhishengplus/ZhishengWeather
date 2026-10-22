package com.zhisheng.weather.ui.home

import kotlin.math.abs

internal data class HourlyTemperaturePoint(
    /** Vertical position within the inset chart, from warm (0) to cool (1). */
    val fraction: Float,
    val connectsToPrevious: Boolean,
)

/**
 * Keep one coordinate system for the whole scrollable timeline. An eight-Celsius-degree
 * minimum span lets a nearly steady forecast remain visually steady, in either display unit.
 * Missing temperatures and gaps longer than 90 minutes never imply a continuous forecast.
 */
internal fun hourlyTemperatureTrend(items: List<HourlyDisplayItem>): List<HourlyTemperaturePoint?> {
    val temperatures = items.map { it.weather.temperature?.takeIf(Double::isFinite) }
    val valid = temperatures.filterNotNull()
    val low = valid.minOrNull() ?: return List(items.size) { null }
    val high = valid.maxOrNull() ?: low
    val span = (high - low).coerceAtLeast(8.0)
    val middle = low / 2.0 + high / 2.0
    return temperatures.mapIndexed { index, temperature ->
        temperature?.let {
            val previous = items.getOrNull(index - 1)
            val gap = previous?.let { items[index].weather.timeMillis - it.weather.timeMillis }
            HourlyTemperaturePoint(
                fraction = (0.5 + (middle - temperature) / span).toFloat().coerceIn(0f, 1f),
                connectsToPrevious = temperatures.getOrNull(index - 1) != null &&
                    gap != null && gap in 1L..5_400_000L,
            )
        }
    }
}

/** X uses hourly-cell widths; the vertical coordinate shares the readings' 0..1 scale. */
internal data class HourlyTemperatureKnot(val x: Float, val fraction: Float)

internal data class HourlyTemperatureSegment(
    val start: HourlyTemperatureKnot,
    val firstControl: HourlyTemperatureKnot,
    val secondControl: HourlyTemperatureKnot,
    val end: HourlyTemperatureKnot,
)

internal data class HourlyTemperatureCurve(val segments: List<HourlyTemperatureSegment>)

/**
 * Each uninterrupted run owns its neighbours and tangents. Shape-preserving cubic interpolation
 * keeps real extrema intact, while a one-sided slope lets a rising/falling edge keep flowing.
 * Only a run touching the timeline edge gets a half-cell continuation, confined to the same
 * vertical range. These drawing-only endpoints are never observations or temperature labels.
 */
internal fun hourlyTemperatureCurves(trend: List<HourlyTemperaturePoint?>): List<HourlyTemperatureCurve> {
    val curves = mutableListOf<HourlyTemperatureCurve>()
    var start = 0
    while (start < trend.size) {
        if (trend[start] == null) { start++; continue }
        var end = start
        while (end + 1 < trend.size && trend[end + 1]?.connectsToPrevious == true) end++
        if (end > start) {
            val knots = (start..end).mapTo(mutableListOf()) { index ->
                HourlyTemperatureKnot(index + 0.5f, trend[index]!!.fraction)
            }
            val readingSlopes = hourlyTemperatureTangents(knots)
            val first = knots.first()
            val last = knots.last()
            if (start == 0) knots.add(0, HourlyTemperatureKnot(0f,
                (first.fraction - readingSlopes.first() * 0.5f).coerceIn(0f, 1f)))
            if (end == trend.lastIndex) knots.add(HourlyTemperatureKnot(trend.size.toFloat(),
                (last.fraction + readingSlopes.last() * 0.5f).coerceIn(0f, 1f)))
            val tangents = hourlyTemperatureTangents(knots)
            val segments = knots.zipWithNext().mapIndexed { index, (left, right) ->
                val handle = (right.x - left.x) / 3f
                HourlyTemperatureSegment(left,
                    HourlyTemperatureKnot(left.x + handle, left.fraction + tangents[index] * handle),
                    HourlyTemperatureKnot(right.x - handle, right.fraction - tangents[index + 1] * handle), right)
            }
            curves += HourlyTemperatureCurve(segments)
        }
        start = end + 1
    }
    return curves
}

/** Weighted harmonic interior slopes also accommodate the half-width edge intervals. */
private fun hourlyTemperatureTangents(knots: List<HourlyTemperatureKnot>): List<Float> {
    val widths = knots.zipWithNext { left, right -> right.x - left.x }
    val slopes = knots.zipWithNext { left, right -> (right.fraction - left.fraction) / (right.x - left.x) }
    if (slopes.size == 1) return listOf(slopes.single(), slopes.single())
    fun endpoint(firstWidth: Float, nextWidth: Float, firstSlope: Float, nextSlope: Float): Float {
        val slope = ((2f * firstWidth + nextWidth) * firstSlope - firstWidth * nextSlope) /
            (firstWidth + nextWidth)
        return when {
            slope * firstSlope <= 0f -> 0f
            firstSlope * nextSlope <= 0f && abs(slope) > 3f * abs(firstSlope) -> 3f * firstSlope
            else -> slope
        }
    }
    return knots.indices.map { index ->
        when (index) {
            0 -> endpoint(widths[0], widths[1], slopes[0], slopes[1])
            knots.lastIndex -> endpoint(widths.last(), widths[widths.lastIndex - 1],
                slopes.last(), slopes[slopes.lastIndex - 1])
            else -> {
                val incoming = slopes[index - 1]
                val outgoing = slopes[index]
                if (incoming * outgoing <= 0f) 0f else {
                    val incomingWeight = 2f * widths[index] + widths[index - 1]
                    val outgoingWeight = widths[index] + 2f * widths[index - 1]
                    (incomingWeight + outgoingWeight) /
                        (incomingWeight / incoming + outgoingWeight / outgoing)
                }
            }
        }
    }
}
