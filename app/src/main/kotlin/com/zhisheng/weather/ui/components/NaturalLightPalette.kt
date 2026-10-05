package com.zhisheng.weather.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.zhisheng.weather.model.ThermalModifier

internal data class NaturalLightPalette(val top: Color, val middle: Color, val base: Color, val light: Color, val cloud: Color,
    val particle: Color, val cloudShadow: Color = cloud, val cloudLit: Color = cloud)

internal fun naturalLightPalette(state: NaturalLightState, light: Boolean): NaturalLightPalette {
    val clear = if (light) Color(0xFFC1DFEF) else Color(0xFF183449)
    val night = if (light) Color(0xFFC4D4E7) else Color(0xFF111D35)
    val weatherTop = when (state.weather) {
        NaturalWeather.CLEAR -> clear
        NaturalWeather.CLOUDS, NaturalWeather.WIND -> if (light) Color(0xFFC8DDE7) else Color(0xFF1D3345)
        NaturalWeather.OVERCAST -> if (light) Color(0xFFC7D4DF) else Color(0xFF26333F)
        NaturalWeather.RAIN, NaturalWeather.MIXED, NaturalWeather.ICE -> if (light) Color(0xFFBCD1DF) else Color(0xFF1D3042)
        NaturalWeather.STORM -> if (light) Color(0xFFB8CCDD) else Color(0xFF202C40)
        NaturalWeather.SNOW -> if (light) Color(0xFFCEDFEA) else Color(0xFF223549)
        NaturalWeather.FOG -> if (light) Color(0xFFDDE5E7) else Color(0xFF29363E)
        NaturalWeather.HAZE -> if (light) Color(0xFFE0DDD3) else Color(0xFF303431)
        NaturalWeather.DUST -> if (light) Color(0xFFE6D8BE) else Color(0xFF393329)
        NaturalWeather.NEUTRAL -> if (light) Color(0xFFE7EDF1) else Color(0xFF202B38)
    }
    val thermalTop = when (state.thermal) {
        ThermalModifier.HOT -> lerp(weatherTop, if (light) Color(0xFFE9D5BC) else Color(0xFF4B3540), .18f)
        ThermalModifier.COLD -> lerp(weatherTop, if (light) Color(0xFFD9E9F3) else Color(0xFF26475D), .18f)
        ThermalModifier.NONE -> weatherTop
    }
    val sky = if (state.weather == NaturalWeather.NEUTRAL) thermalTop else
        lerp(lerp(night, thermalTop, if (light) 0.48f else 0.35f), thermalTop, state.daylight)
    val warm = if (light) Color(0xFFF3E4DC) else Color(0xFF493A3B)
    val twilight = state.twilight * (1f - state.cloud * 0.7f) * 0.38f
    val top = lerp(sky, warm, twilight)
    val base = if (light) Color(0xFFF0F4F6) else Color(0xFF0D1723)
    val middle = lerp(base, top, if (light) 0.44f else 0.40f)
    val sunlight = lerp(Color(0xFFB8CEEF), Color(0xFFFFE3B1), state.daylight)
    val normalGlow = lerp(sunlight, Color(0xFFFFCFAE), state.twilight * 0.65f)
    val glow = when (state.thermal) {
        ThermalModifier.HOT -> lerp(normalGlow, Color(0xFFFFC58D), .45f)
        ThermalModifier.COLD -> lerp(normalGlow, Color(0xFFCAE6FF), .65f)
        ThermalModifier.NONE -> normalGlow
    }
    val original = NaturalLightPalette(top, middle, base, glow,
        if (light) Color(0xFFFFFFFF) else Color(0xFFB1C7D8),
        if (state.freezing) { if (light) Color(0xFF6A91AC) else Color(0xFFD9F0FF) } else when (state.weather) {
            NaturalWeather.DUST -> if (light) Color(0xFF9E845C) else Color(0xFFD4BF96)
            NaturalWeather.SNOW, NaturalWeather.MIXED, NaturalWeather.ICE -> if (light) Color(0xFF7196B1) else Color(0xFFE7F3FF)
            else -> if (light) Color(0xFF577F9B) else Color(0xFFB3D5EE)
        })
    return livingSkyPalette(state, light, original)
}

internal fun NaturalLightPalette.mix(other: NaturalLightPalette, fraction: Float) = NaturalLightPalette(
    lerp(top, other.top, fraction), lerp(middle, other.middle, fraction), lerp(base, other.base, fraction),
    lerp(light, other.light, fraction), lerp(cloud, other.cloud, fraction), lerp(particle, other.particle, fraction),
    lerp(cloudShadow, other.cloudShadow, fraction), lerp(cloudLit, other.cloudLit, fraction))
