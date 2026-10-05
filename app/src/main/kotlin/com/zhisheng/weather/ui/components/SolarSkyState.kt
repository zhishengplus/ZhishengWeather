package com.zhisheng.weather.ui.components

import org.shredzone.commons.suncalc.SunPosition
import java.time.Instant
import kotlin.math.sin

/** Geometric sun position: absolute time + selected city's coordinates, never the phone's zone. */
internal data class SolarSkyState(val altitude: Float, val azimuth: Float, val rising: Boolean) {
    val daylight: Float get() = smoothNatural((altitude + 6f) / 18f)
    val warmth: Float get() = smoothNatural((altitude + 7f) / 6f) * (1f - smoothNatural((altitude - 3f) / 12f))
    val blue: Float get() = smoothNatural((altitude + 18f) / 8f) * (1f - smoothNatural((altitude + 6f) / 5f))
    // A soft off-centre light, not a second sun disc competing with the weather artwork.
    val lightX: Float get() = .77f + .11f * sin(Math.toRadians(azimuth.toDouble())).toFloat()
    val lightY: Float get() = .43f - altitude.coerceIn(0f, 60f) / 60f * .20f
}

internal fun solarSkyState(latitude: Double?, longitude: Double?, now: Long): SolarSkyState? {
    if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite() ||
        latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
    return runCatching {
        val instant = Instant.ofEpochMilli(now)
        val position = SunPosition.compute().on(instant).at(latitude, longitude).execute()
        val later = SunPosition.compute().on(instant.plusSeconds(300)).at(latitude, longitude).execute()
        val altitude = position.trueAltitude.toFloat()
        val azimuth = position.azimuth.toFloat()
        if (!altitude.isFinite() || !azimuth.isFinite()) null else
            SolarSkyState(altitude.coerceIn(-90f, 90f), azimuth, later.trueAltitude >= position.trueAltitude)
    }.getOrNull()
}

internal fun SolarSkyState.mix(other: SolarSkyState, fraction: Float): SolarSkyState {
    val turn = ((other.azimuth - azimuth + 540f) % 360f) - 180f
    return SolarSkyState(altitude + (other.altitude - altitude) * fraction,
        (azimuth + turn * fraction + 360f) % 360f, if (fraction < .5f) rising else other.rising)
}

/** Colour attenuation is an illustration of the current weather, not a sunset forecast. */
internal fun solarColourVisibility(state: NaturalLightState): Float = when (state.weather) {
    NaturalWeather.NEUTRAL -> 0f
    NaturalWeather.STORM -> .08f
    NaturalWeather.RAIN, NaturalWeather.MIXED, NaturalWeather.ICE -> .18f
    NaturalWeather.FOG -> .12f
    NaturalWeather.OVERCAST -> .24f
    NaturalWeather.HAZE, NaturalWeather.DUST -> .35f
    else -> (1f - state.cloud * .52f).coerceIn(.45f, 1f)
}

/** Smooth colour stops share the same horizon in either direction of travel. */
internal fun solarStop(altitude: Float): Pair<Int, Float> {
    val stops = floatArrayOf(-18f, -7f, -2f, 4f, 18f)
    val segment = (0 until stops.lastIndex).firstOrNull { altitude < stops[it + 1] } ?: stops.lastIndex
    if (segment == stops.lastIndex) return segment to 0f
    return segment to smoothNatural((altitude - stops[segment]) / (stops[segment + 1] - stops[segment]))
}
