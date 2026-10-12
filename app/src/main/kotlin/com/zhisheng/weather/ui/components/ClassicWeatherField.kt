package com.zhisheng.weather.ui.components

import com.zhisheng.weather.data.AmbienceLevel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.model.ThermalModifier
import com.zhisheng.weather.ui.theme.*
import kotlin.math.PI
import kotlin.math.sin

internal fun classicWeatherMarks(state: NaturalLightState, level: AmbienceLevel): Int {
    if (level == AmbienceLevel.OFF ||
        (state.weather == NaturalWeather.NEUTRAL && state.thermal == ThermalModifier.NONE)) return 0
    val base = when (state.weather) {
        NaturalWeather.RAIN, NaturalWeather.STORM -> 36
        NaturalWeather.SNOW, NaturalWeather.MIXED, NaturalWeather.ICE, NaturalWeather.DUST -> 28
        NaturalWeather.CLEAR, NaturalWeather.CLOUDS -> 14
        NaturalWeather.NEUTRAL -> 12
        else -> 18
    }
    val gain = when (level) { AmbienceLevel.OFF -> 0f; AmbienceLevel.SUBTLE -> .5f; AmbienceLevel.VIVID -> .75f; AmbienceLevel.INTENSE -> 1f }
    return (base * gain).toInt().coerceIn(1, 40)
}

/** Sparse phosphor marks over the existing terminal; no natural-sky palette or extra layout clock. */
@Composable
internal fun ClassicWeatherField(state: NaturalLightState, level: AmbienceLevel,
    active: Boolean, parallax: () -> Float) {
    val clock = rememberVistaGlowPhase(active && level != AmbienceLevel.OFF)
    val palette = LocalZhishengPalette.current
    val count = classicWeatherMarks(state, level)
    val preview = LocalNaturalPreviewPhase.current
    val held = remember { doubleArrayOf(0.173) }
    Box(Modifier.fillMaxSize().graphicsLayer().drawWithCache {
        onDrawBehind {
            if (count == 0) return@onDrawBehind
            val phase = preview ?: if (active) clock.value.also { held[0] = it } else held[0]
            val retreat = 1f - smoothNatural(parallax() / density / 360f)
            val depth = minOf(size.height, 620.dp.toPx())
            val alpha = (if (palette.isLight) .16f else .24f) * retreat
            val color = (if (state.thermal == ThermalModifier.HOT) palette.orange else palette.cyan).copy(alpha = alpha)
            val w = size.width
            fun seed(i: Int, salt: Int) = rainSeed(i, salt)
            fun wrapped(v: Double) = (v - kotlin.math.floor(v)).toFloat()
            repeat(count) { i ->
                val x = w * (.53f + seed(i, 141) * .43f)
                val y = depth * (.13f + wrapped(seed(i, 142) + phase * 12) * .44f)
                when (state.weather) {
                    NaturalWeather.RAIN, NaturalWeather.STORM, NaturalWeather.MIXED -> {
                        val length = (if (state.drizzle) 3f else 5f + state.intensity * 7f).dp.toPx()
                        drawLine(color, Offset(x, y), Offset(x - 2.dp.toPx(), y + length), 1.dp.toPx())
                    }
                    NaturalWeather.SNOW, NaturalWeather.ICE -> {
                        val r = 2.dp.toPx()
                        drawLine(color, Offset(x - r, y), Offset(x + r, y), .8.dp.toPx())
                        drawLine(color, Offset(x, y - r), Offset(x, y + r), .8.dp.toPx())
                    }
                    NaturalWeather.CLEAR -> {
                        val twinkle = .6f + .4f * sin(phase * 2 * PI + i).toFloat()
                        drawCircle(color.copy(alpha = alpha * twinkle), 1.dp.toPx(),
                            Offset(x, depth * (.12f + seed(i, 142) * .38f)))
                    }
                    NaturalWeather.NEUTRAL -> {
                        // An explicit thermal report does not imply clouds, rain or a clear sky.
                        val pulse = .65f + .35f * sin(phase * 2 * PI + i).toFloat()
                        val thermalY = depth * (.15f + seed(i, 142) * .34f)
                        if (state.thermal == ThermalModifier.HOT) {
                            val wave = sin(phase * 2 * PI + i).toFloat() * 4.dp.toPx()
                            drawLine(color.copy(alpha = alpha * pulse), Offset(x, thermalY + wave),
                                Offset(x + 10.dp.toPx(), thermalY - wave), .8.dp.toPx())
                        } else {
                            drawLine(color.copy(alpha = alpha * pulse), Offset(x, thermalY),
                                Offset(x, thermalY + 6.dp.toPx()), .7.dp.toPx())
                        }
                    }
                    else -> {
                        val wave = sin(phase * 2 * PI + i).toFloat() * 8.dp.toPx()
                        drawLine(color.copy(alpha = alpha * .65f), Offset(x + wave, y),
                            Offset(x + wave + (8f + seed(i, 143) * 10f).dp.toPx(), y), .7.dp.toPx())
                    }
                }
            }
            if (state.weather == NaturalWeather.CLEAR && state.daylight >= .5f) {
                val center = Offset(w * .83f, depth * .23f)
                drawCircle(color, 27.dp.toPx(), center, style = Stroke(.8.dp.toPx()))
                drawCircle(color.copy(alpha = alpha * .5f), 35.dp.toPx(), center, style = Stroke(.5.dp.toPx()))
            }
            if (state.freezing || state.thermal == ThermalModifier.COLD) {
                drawLine(color, Offset(w * .96f, depth * .15f), Offset(w * .96f, depth * .48f), 1.dp.toPx())
            }
        }
    })
}
