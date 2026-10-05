package com.zhisheng.weather.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.zhisheng.weather.model.ThermalModifier

/** Sky chroma follows daylight; paper/glass and reading contrast follow the chosen app theme. */
internal fun livingSkyPalette(state: NaturalLightState, light: Boolean, original: NaturalLightPalette): NaturalLightPalette {
    val sun = state.solar ?: return original
    val tops = if (light) listOf(0xFFBAC9E3, 0xFFB7C9E6, 0xFFC6C5E2, 0xFFCECEE1, 0xFFB3D3E9)
        else listOf(0xFF0B142C, 0xFF17294D, 0xFF292748, 0xFF31334D, 0xFF15344D)
    val horizons = if (light) listOf(0xFFCCD6E8, 0xFFD0D5EC, 0xFFEBD1D4, 0xFFF0DABF, 0xFFCEE6F0)
        else listOf(0xFF16233C, 0xFF25375A, 0xFF503447, 0xFF513E35, 0xFF254057)
    val edges = listOf(0xFF7183AF, 0xFF839CCC, 0xFFE8A58E, 0xFFFFD5A1, 0xFFF4F4F0)
    val (index, weight) = solarStop(sun.altitude)
    fun sample(values: List<Long>): Color = lerp(Color(values[index]), Color(values[minOf(index + 1, values.lastIndex)]), weight)
    val visibility = solarColourVisibility(state)
    val dawnTint = if (sun.rising) .13f * sun.warmth else 0f
    val phaseTop = lerp(sample(tops), if (light) Color(0xFFD6D5E8) else Color(0xFF383452), dawnTint)
    val top = when (state.thermal) {
        ThermalModifier.HOT -> lerp(phaseTop, if (light) Color(0xFFE9D5BC) else Color(0xFF4B3540), .12f)
        ThermalModifier.COLD -> lerp(phaseTop, if (light) Color(0xFFD9E9F3) else Color(0xFF26475D), .12f)
        ThermalModifier.NONE -> phaseTop
    }
    val horizon = lerp(sample(horizons), if (light) Color(0xFFF0DFD9) else Color(0xFF493C48), dawnTint)
    val visibleTop = lerp(original.top, top, visibility)
    val cloudShadow = if (light) lerp(visibleTop, Color(0xFFE8EDF5), .20f) else
        boundedNightCloud(lerp(visibleTop, Color(0xFF52647D), .22f))
    val edge = sample(edges)
    // On dark pages cloud edges are bounded too: readable small type takes priority over a white glare.
    val phaseLit = if (light) lerp(Color(0xFFFFFFFF), edge, sun.warmth * .48f + sun.blue * .20f)
        else boundedNightCloud(lerp(cloudShadow, edge, .28f + sun.warmth * .04f))
    // The shader reads this directly: attenuating only the gradient/veil leaves rainy cloud edges too warm.
    val neutralLit = if (light) Color(0xFFF3F5F7) else boundedNightCloud(Color(0xFF53647B))
    val lit = lerp(neutralLit, phaseLit, visibility)
    val veil = lerp(original.cloud, lit, visibility).let { if (light) it else boundedNightCloud(it) }
    return original.copy(top = visibleTop,
        middle = lerp(original.middle, horizon, visibility), light = lerp(original.light, edge, visibility),
        cloud = veil,
        cloudShadow = cloudShadow, cloudLit = lit)
}

/** All cloud layers remain inside the same contrast envelope as the dark sky. */
private fun boundedNightCloud(color: Color): Color {
    if (color.luminance() <= .055f) return color
    var lower = 0f
    var upper = 1f
    repeat(10) {
        val scale = (lower + upper) / 2f
        if (lerp(Color.Black, color, scale).luminance() <= .055f) lower = scale else upper = scale
    }
    return lerp(Color.Black, color, lower)
}
