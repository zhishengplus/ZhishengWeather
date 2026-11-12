package com.zhisheng.weather.ui.home

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** One visible part of a rise-to-set (or set-to-rise) interval on the 24-hour clock. */
internal data class LuminaryClockSpan(
    val clockStart: Float,
    val duration: Int,
    val from: Float,
    val to: Float,
)

internal data class LuminaryPoint(val x: Float, val y: Float)

internal data class LuminaryMorphTrack(
    val width: Float,
    val waveHorizon: Float,
    val arcHorizon: Float,
    val waveAmplitude: Float,
    val arcRadiusX: Float,
    val arcRadiusY: Float,
    val waveInset: Float,
) {
    /** Match the same orbital instant, including both visible pieces of a midnight crossing. */
    fun point(clockMinute: Float, orbit: Float, above: Boolean, up: Int, morph: Float): LuminaryPoint {
        val q = orbit.coerceIn(0f, 1f)
        val p = morph.coerceIn(0f, 1f)
        val duration = if (above) up else VISTA_DAY_MINUTES - up
        val slope = minOf(PI.toFloat() / up, PI.toFloat() / (VISTA_DAY_MINUTES - up))
        val t = if (q <= 0.5f) q * 2f else (1f - q) * 2f
        val altitude = (-2f * t * t * t + 3f * t * t) +
            (t * t * t - 2f * t * t + t) * slope * duration / 2f
        val waveX = waveInset + (width - waveInset * 2f) * (clockMinute / VISTA_DAY_MINUTES).coerceIn(0f, 1f)
        val waveY = waveHorizon - waveAmplitude * altitude * if (above) 1f else -1f
        val theta = PI * (1.0 - q)
        val arcX = width / 2f + if (above) arcRadiusX * cos(theta).toFloat() else arcRadiusX * (1f - q * 2f)
        val arcY = arcHorizon - if (above) arcRadiusY * sin(theta).toFloat() else 0f
        return LuminaryPoint(waveX + (arcX - waveX) * p, waveY + (arcY - waveY) * p)
    }
}

/** Clip in clock space first: wrapped moon paths join instead of drawing a line across midnight. */
internal fun luminaryClockSpans(rise: Int, set: Int, above: Boolean): List<LuminaryClockSpan> {
    val up = vistaAstroUpDuration(rise, set) ?: return emptyList()
    val duration = if (above) up else VISTA_DAY_MINUTES - up
    val base = if (above) rise else rise + up
    return (-2..1).mapNotNull { cycle ->
        val start = (base + cycle * VISTA_DAY_MINUTES).toFloat()
        val from = (-start / duration).coerceIn(0f, 1f)
        val to = ((VISTA_DAY_MINUTES - start) / duration).coerceIn(0f, 1f)
        if (to > from) LuminaryClockSpan(start, duration, from, to) else null
    }
}

internal fun luminaryDetailsAlpha(morph: Float): Float = ((morph - 0.45f) / 0.55f).coerceIn(0f, 1f)
