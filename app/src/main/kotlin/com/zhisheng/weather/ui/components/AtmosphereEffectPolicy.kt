package com.zhisheng.weather.ui.components

import kotlin.math.floor

/** A non-looping view of the existing shared phase, without a second timer or frame loop. */
internal class AtmosphereEffectClock {
    private var source: Double? = null
    private var elapsed = 100.0 // Prewarm rain; the source shader's first two seconds are transparent.
    private var running = false
    fun seconds(phase: Double, enabled: Boolean): Double {
        val previous = source
        if (enabled && running && previous != null && phase.isFinite()) elapsed += (phase - previous + 1.0) % 1.0 * 72.0
        if (phase.isFinite()) source = phase
        running = enabled
        return elapsed
    }
}

internal enum class WeatherGlassKind { NONE, RAIN, SNOW, FROST }

internal fun weatherGlassKind(state: NaturalLightState): WeatherGlassKind = when {
    state.freezing -> WeatherGlassKind.FROST
    state.weather in setOf(NaturalWeather.SNOW, NaturalWeather.MIXED) -> WeatherGlassKind.SNOW
    state.weather in setOf(NaturalWeather.RAIN, NaturalWeather.STORM) -> WeatherGlassKind.RAIN
    else -> WeatherGlassKind.NONE
}

internal fun rainVeilIntensity(intensity: Float): Float = when {
    !intensity.isFinite() -> 2.5f
    intensity <= .3f -> 2.5f
    intensity <= .6f -> 2.5f + (intensity - .3f) / .3f * 2f
    else -> 4.5f + (intensity - .6f).coerceIn(0f, .4f) / .4f * .5f
}

internal fun atmosphereTimeTick(seconds: Double, budget: SkyRenderQuality): Long =
    floor(seconds * if (budget == SkyRenderQuality.HIGH) 30.0 else 20.0).toLong()
