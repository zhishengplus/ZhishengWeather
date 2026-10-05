package com.zhisheng.weather.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.model.ThermalModifier
import kotlin.math.PI
import kotlin.math.sin

/** Thermal information is an accent over the reported weather, never a guessed sun or snowfall. */
internal fun DrawScope.drawNaturalThermal(state: NaturalLightState, palette: NaturalLightPalette,
    phase: Double, gain: Float, glow: Brush) {
    if (gain <= .001f || state.thermal == ThermalModifier.NONE) return
    val depth = minOf(size.height, 580.dp.toPx())
    val angle = phase * 2 * PI
    val hot = state.thermal == ThermalModifier.HOT
    repeat(if (hot) 2 else 1) { i ->
        val drift = sin(angle * (i + 1) + i * 1.8).toFloat()
        val x = size.width * (if (hot) .78f + drift * .025f else .94f)
        val y = depth * (if (hot) .27f + i * .12f + drift * .025f else .24f)
        translate(x, y) { scale(size.width * .42f, depth * (if (hot) .14f else .42f), pivot = Offset.Zero) {
            drawCircle(glow, 1f, Offset.Zero, alpha = gain * (if (hot) .16f else .20f),
                colorFilter = ColorFilter.tint(palette.light))
        } }
    }
    if (!hot) drawNaturalFrostEdge(palette, phase, gain * .55f, Offset(size.width, depth * .08f),
        Offset(size.width, depth * .56f), inward = Offset(-1f, 0f))
}

/** Frost adheres to a surface edge. It never becomes falling crystals in freezing rain. */
internal fun DrawScope.drawNaturalFrostEdge(palette: NaturalLightPalette, phase: Double, gain: Float,
    start: Offset, end: Offset, inward: Offset) {
    if (gain <= .001f) return
    val tangent = end - start
    repeat(14) { i ->
        val center = start + tangent * rainSeed(i, 81)
        val length = (2f + rainSeed(i, 83) * 8f).dp.toPx()
        val pulse = .8f + sin(phase * 2 * PI + i * 1.7).toFloat() * .10f
        val ink = palette.particle.copy(alpha = (gain * pulse * .22f).coerceIn(0f, 1f))
        val tip = center + inward * length
        drawLine(ink, center, tip, .55.dp.toPx(), StrokeCap.Round)
        val branch = if (inward.x == 0f) Offset(length * .35f, 0f) else Offset(0f, length * .35f)
        drawLine(ink, center + inward * length * .35f + branch, center + inward * length * .7f, .4.dp.toPx())
        drawLine(ink, center + inward * length * .35f - branch, center + inward * length * .7f, .4.dp.toPx())
    }
}
