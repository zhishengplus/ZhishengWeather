package com.zhisheng.weather.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

internal fun rainSeed(index: Int, salt: Int): Float {
    var hash = (index + 1) * 374761393 + salt * 668265263
    hash = (hash xor (hash ushr 13)) * 1274126177
    return ((hash xor (hash ushr 16)) and 0xFFFFFF) / 16777216f
}

/** Integer periods preserve both position and velocity across the shared 72-second seam. */
internal fun rainTravel(phase: Double, index: Int): Float {
    val cycles = 48 + (index % 5) * 12
    val value = rainSeed(index, 7) + phase * cycles
    return (value - floor(value)).toFloat()
}

internal data class RainFlight(val progress: Float, val x: Float, val depth: Float, val seconds: Float)

/** Speed and motion blur share one depth; a new flight gets a new horizontal position.
 *  Fall speed opens up with intensity: drizzle drifts for seconds, downpour rushes past. */
internal fun rainFlight(phase: Double, index: Int, intensity: Float = 0.52f, drizzle: Boolean = false): RainFlight {
    val depth = rainSeed(index, 11)
    // 周期必须取整才能跨 72 秒接缝无缝衔接；强度只改变这一整数。
    val cycles = (((34 + depth * 62).toInt()) * (0.5f + intensity * 0.75f) * (if (drizzle) .60f else 1f)).toInt().coerceAtLeast(10)
    val elapsed = rainSeed(index, 7) + phase * cycles
    val lap = floor(elapsed).toInt()
    val wrappedLap = Math.floorMod(lap, cycles)
    return RainFlight((elapsed - floor(elapsed)).toFloat(), rainSeed(index, 101 + wrappedLap), depth, 72f / cycles)
}

internal fun DrawScope.drawRainStreak(head: Offset, velocity: Offset, depth: Float,
    palette: NaturalLightPalette, alpha: Float, drizzle: Boolean = false) {
    val smear = velocity * (.014f + depth * .012f) * if (drizzle) .55f else 1f
    val width = ((.32f + depth * .65f) * if (drizzle) .75f else 1f).dp.toPx()
    // Taper both ends: no solid rounded bars or glowing "heads".
    val weights = floatArrayOf(.18f, .62f, 1f, .38f)
    repeat(4) { part ->
        drawLine(palette.particle.copy(alpha = (alpha * weights[part]).coerceIn(0f, 1f)),
            head - smear * (1f - part / 4f), head - smear * (1f - (part + 1) / 4f),
            width, StrokeCap.Butt)
    }
}

/** Distant fine rain and sparse foreground streaks share a consistent wind vector. */
internal fun DrawScope.drawNaturalRain(state: NaturalLightState, palette: NaturalLightPalette,
    count: Int, phase: Double, gain: Float, scroll: Float) {
    val h = minOf(size.height, 720.dp.toPx())
    repeat(count) { index ->
        val flight = rainFlight(phase, index, state.intensity, state.drizzle)
        val depth = flight.depth
        val travel = flight.progress
        val y = travel * 1.16f - .08f
        val lean = .025f + state.wind * .085f
        val x = flight.x - lean * (travel - .5f)
        val alpha = naturalParticleMask(x, y, scroll) * gain * (.28f + depth * .36f) *
            (0.55f + state.intensity * 0.55f)
        if (alpha < .002f) return@repeat
        val head = Offset(x * size.width, y * h)
        drawRainStreak(head, Offset(-lean * size.width, h * 1.16f) / flight.seconds, depth, palette, alpha, state.drizzle)
    }
    if (state.freezing) drawNaturalFrostEdge(palette, phase, gain * (1f - smoothNatural(scroll / 340f)),
        Offset(size.width, h * .12f), Offset(size.width, h * .62f), inward = Offset(-1f, 0f))
    drawNaturalRunoff(state, palette, phase, gain, scroll, h)
}

/**
 * 下淌水珠专用的遮罩：雨丝用的 naturalParticleMask 在 72% 高度以下完全隐没，
 * 而水珠要沿整幅“玻璃”下滑。这里放宽下缘（到 ~0.9 才隐没），左列阅读区、
 * 滚动淡出与左右边缘保护与雨丝保持同一套规则。
 */
private fun runoffFieldMask(x: Float, y: Float, scrollDp: Float): Float {
    val vertical = smoothNatural((y - 0.48f) / 0.10f) * (1f - smoothNatural((y - 0.76f) / 0.14f))
    val reading = 0.18f + 0.82f * smoothNatural((x - 0.35f) / 0.40f)
    val scroll = 1f - smoothNatural(scrollDp / 340f)
    val edge = smoothNatural(x / 0.035f) * (1f - smoothNatural((x - 0.965f) / 0.035f))
    return vertical * reading * scroll * edge
}

/** 下淌水珠：极慢、稀疏，贴面下滑的小水珠拖着渐隐的水痕，大雨才明显。 */
private fun DrawScope.drawNaturalRunoff(state: NaturalLightState, palette: NaturalLightPalette,
    phase: Double, gain: Float, scroll: Float, h: Float) {
    if (state.freezing || (state.weather != NaturalWeather.RAIN && state.weather != NaturalWeather.STORM)) return
    val drops = 4 + (state.intensity * 7f).toInt()
    repeat(drops) { i ->
        val x0 = rainSeed(i, 31)
        val rate = 1 + i % 3
        val elapsed = rainSeed(i, 33) + phase * rate
        val y = (elapsed - floor(elapsed)).toFloat()
        val x = x0 + sin(phase * 2 * PI + i * 1.7).toFloat() * 0.008f
        val mask = runoffFieldMask(x, y, scroll) * gain * (0.45f + state.intensity * 0.55f)
        if (mask < .01f) return@repeat
        val cx = x * size.width
        val cy = y * h
        val headLen = 4f + rainSeed(i, 35) * 4f
        val trailLen = 14f + rainSeed(i, 37) * 16f
        val ink = palette.particle
        // 水痕：头顶上方渐隐的一段细线
        drawLine(ink.copy(alpha = .15f * mask),
            Offset(cx, cy - (headLen + trailLen).dp.toPx()), Offset(cx, cy - headLen.dp.toPx()),
            0.8f.dp.toPx(), StrokeCap.Round)
        // 水珠头部
        drawLine(ink.copy(alpha = .40f * mask),
            Offset(cx, cy - headLen.dp.toPx()), Offset(cx, cy),
            1.3f.dp.toPx(), StrokeCap.Round)
    }
}
