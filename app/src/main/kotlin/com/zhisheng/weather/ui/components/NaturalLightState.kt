package com.zhisheng.weather.ui.components

import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.WeatherIntensity
import com.zhisheng.weather.model.ThermalModifier
import kotlin.math.abs

/** Natural light is independent of the interface's light/dark preference. */
internal enum class NaturalWeather { CLEAR, CLOUDS, OVERCAST, RAIN, STORM, SNOW, MIXED, ICE, FOG, HAZE, DUST, WIND, NEUTRAL }

internal data class NaturalLightState(
    val weather: NaturalWeather,
    val daylight: Float,
    val twilight: Float,
    val intensity: Float,
    val cloud: Float,
    val wind: Float,
    val freezing: Boolean,
    val thermal: ThermalModifier = ThermalModifier.NONE,
    val drizzle: Boolean = false,
    val solar: SolarSkyState? = null,
)

internal fun naturalWeatherOf(condition: WeatherCondition?): NaturalWeather = when (condition) {
    null, WeatherCondition.UNKNOWN -> NaturalWeather.NEUTRAL
    WeatherCondition.CLEAR, WeatherCondition.CLEAR_NIGHT -> NaturalWeather.CLEAR
    WeatherCondition.PARTLY_CLOUDY, WeatherCondition.PARTLY_CLOUDY_NIGHT -> NaturalWeather.CLOUDS
    WeatherCondition.CLOUDY, WeatherCondition.OVERCAST -> NaturalWeather.OVERCAST
    WeatherCondition.RAIN, WeatherCondition.DRIZZLE, WeatherCondition.FREEZING_RAIN,
    WeatherCondition.FREEZING_DRIZZLE -> NaturalWeather.RAIN
    WeatherCondition.THUNDERSTORM -> NaturalWeather.STORM
    WeatherCondition.SNOW -> NaturalWeather.SNOW
    WeatherCondition.SLEET -> NaturalWeather.MIXED
    WeatherCondition.HAIL -> NaturalWeather.ICE
    WeatherCondition.FOG -> NaturalWeather.FOG
    WeatherCondition.HAZE -> NaturalWeather.HAZE
    WeatherCondition.SAND -> NaturalWeather.DUST
    WeatherCondition.WIND -> NaturalWeather.WIND
}

internal fun smoothNatural(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

/** A 50-minute light transition centred on each event. Invalid astronomy uses the known night state. */
internal fun naturalDaylight(sunrise: String?, sunset: String?, minutes: Int, night: Boolean): Pair<Float, Float> {
    val rise = clockMinutes(sunrise)
    val set = clockMinutes(sunset)
    if (rise == null || set == null || rise >= set || minutes !in 0..1439) return (if (night) 0f else 1f) to 0f
    val day = smoothNatural((minutes - rise + 25f) / 50f) * (1f - smoothNatural((minutes - set + 25f) / 50f))
    val twilight = (1f - minOf(abs(minutes - rise), abs(minutes - set)) / 45f).coerceIn(0f, 1f)
    return day to smoothNatural(twilight)
}

internal fun naturalLightState(data: WeatherData?, night: Boolean, minutes: Int, now: Long, solar: SolarSkyState? = null): NaturalLightState {
    val current = data?.current
    val condition = current?.condition
    val kind = naturalWeatherOf(condition)
    val astro = data?.todayDaily(now)
    val (day, twilight) = naturalDaylight(astro?.sunrise, astro?.sunset, minutes,
        night || condition == WeatherCondition.CLEAR_NIGHT || condition == WeatherCondition.PARTLY_CLOUDY_NIGHT)
    val strength = when (current?.profile?.intensity) {
        WeatherIntensity.LIGHT -> 0.28f
        WeatherIntensity.MODERATE -> 0.52f
        WeatherIntensity.HEAVY -> 0.78f
        WeatherIntensity.EXTREME -> 1f
        null -> when (condition) {
            WeatherCondition.DRIZZLE, WeatherCondition.FREEZING_DRIZZLE -> 0.24f
            WeatherCondition.THUNDERSTORM, WeatherCondition.HAIL -> 0.75f
            else -> 0.48f
        }
    }
    val cloudDefault = when (kind) {
        NaturalWeather.CLEAR, NaturalWeather.NEUTRAL -> 0f
        NaturalWeather.CLOUDS, NaturalWeather.WIND -> 0.4f
        NaturalWeather.FOG, NaturalWeather.HAZE, NaturalWeather.DUST -> 0.65f
        else -> 0.85f
    }
    // A low cloud field must not turn an explicitly overcast/rainy condition into a clear sky.
    val measuredCloud = current?.cloudCover?.takeIf { it.isFinite() && it in 0.0..100.0 }?.toFloat()?.div(100f)
    val cloud = if (kind == NaturalWeather.NEUTRAL) 0f else
        (measuredCloud ?: cloudDefault).coerceIn(cloudDefault * 0.75f, if (kind == NaturalWeather.CLEAR) 0.25f else 1f)
    val wind = current?.windSpeed?.takeIf { it.isFinite() && it >= 0 }?.toFloat()?.div(65f)?.coerceIn(0f, 1f) ?: 0f
    val legacy = NaturalLightState(kind, day, twilight, strength, cloud, wind,
        condition == WeatherCondition.FREEZING_RAIN || condition == WeatherCondition.FREEZING_DRIZZLE,
        current?.profile?.thermal ?: ThermalModifier.NONE,
        condition == WeatherCondition.DRIZZLE || condition == WeatherCondition.FREEZING_DRIZZLE)
    return if (solar == null || kind == NaturalWeather.NEUTRAL) legacy else
        legacy.copy(solar = solar, daylight = solar.daylight, twilight = solar.warmth * solarColourVisibility(legacy))
}

internal fun naturalParticleBudget(state: NaturalLightState, level: AmbienceLevel): Int {
    val maximum = when (state.weather) {
        NaturalWeather.RAIN -> if (state.drizzle) 44 else 66
        NaturalWeather.STORM -> 84
        NaturalWeather.SNOW, NaturalWeather.MIXED -> 54
        NaturalWeather.ICE -> 36
        NaturalWeather.DUST -> 42
        NaturalWeather.WIND -> 12
        NaturalWeather.CLEAR, NaturalWeather.CLOUDS -> if (state.daylight < 0.5f) 22 else 0
        else -> 0
    }
    val levelGain = when (level) { AmbienceLevel.OFF -> 0f; AmbienceLevel.SUBTLE -> 0.5f; AmbienceLevel.VIVID -> 0.75f; AmbienceLevel.INTENSE -> 1f }
    return (maximum * levelGain * (0.45f + state.intensity * 0.55f)).toInt().coerceIn(0, 84)
}

/** The left temperature column and all lower reading content stay visually still. */
internal fun naturalParticleMask(x: Float, y: Float, scrollDp: Float): Float {
    val top = smoothNatural((y - 0.045f) / 0.09f)
    val lower = 1f - smoothNatural((y - 0.36f) / 0.36f)
    val reading = 0.18f + 0.82f * smoothNatural((x - 0.35f) / 0.40f)
    val scroll = 1f - smoothNatural(scrollDp / 340f)
    val edge = smoothNatural(x / 0.035f) * (1f - smoothNatural((x - 0.965f) / 0.035f))
    return top * lower * reading * scroll * edge
}

/** Periodic local cloud illumination, never an abrupt full-screen flash. */
internal fun naturalLightning(seconds: Double): Float {
    val t = (seconds % 36.0).toFloat()
    return if (t in 12f..13.6f) {
        val p = (t - 12f) / 1.6f
        smoothNatural(minOf(p * 2f, (1f - p) * 2f)) * 0.08f
    } else 0f
}
