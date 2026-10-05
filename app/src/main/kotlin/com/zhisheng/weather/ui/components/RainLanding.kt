package com.zhisheng.weather.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.ui.theme.LocalZhishengChrome

internal val LocalRainLanding = staticCompositionLocalOf<MutableState<Rect?>?> { null }

/** The actual first module supplies its edge, including reordering and scroll displacement. */
@Composable
internal fun RainLandingTarget(enabled: Boolean, content: @Composable () -> Unit) {
    val landing = LocalRainLanding.current
    if (!enabled || landing == null) { content(); return }
    DisposableEffect(landing) { onDispose { landing.value = null } }
    Box(Modifier.onGloballyPositioned { coordinates ->
        val origin = coordinates.positionInRoot()
        landing.value = Rect(origin.x, origin.y,
            origin.x + coordinates.size.width, origin.y + coordinates.size.height)
    }) { content() }
}

@Composable
internal fun RainLandingOverlay(state: NaturalLightState, palette: NaturalLightPalette,
    level: AmbienceLevel, clock: State<Double>, landing: State<Rect?>,
    root: () -> Offset, parallax: () -> Float) {
    if (level == AmbienceLevel.OFF || state.weather !in setOf(NaturalWeather.RAIN, NaturalWeather.STORM)) return
    val inset = LocalZhishengChrome.current.pagePadding
    val preview = LocalNaturalPreviewPhase.current
    val gain = when (level) { AmbienceLevel.SUBTLE -> .48f; AmbienceLevel.VIVID -> .76f; else -> 1f }
    Box(Modifier.fillMaxSize().drawBehind {
        val card = landing.value
        val edge = card?.let { it.top - root().y } ?: minOf(size.height, 720.dp.toPx())
        if (edge < 140.dp.toPx()) return@drawBehind
        val visibility = gain * (1f - smoothNatural(parallax() / density / 440f))
        if (visibility < .01f) return@drawBehind
        val left = (card?.left ?: root().x) - root().x + inset.toPx() + 20.dp.toPx()
        val right = (card?.right ?: (root().x + size.width)) - root().x - inset.toPx() - 20.dp.toPx()
        if (right <= left) return@drawBehind
        val phase = preview ?: clock.value
        if (state.freezing && card != null) drawNaturalFrostEdge(palette, phase, visibility,
            Offset(left, edge), Offset(right, edge), inward = Offset(0f, -1f))
        val count = ((46 + state.intensity * 60) * (.65f + gain * .35f) * if (state.drizzle) .65f else 1f).toInt()
        val top = maxOf(100.dp.toPx(), edge - 510.dp.toPx())
        repeat(count) { i ->
            val flight = rainFlight(phase, i, state.intensity, state.drizzle)
            val x = flight.x * size.width
            val depth = flight.depth
            val alpha = visibility * (.12f + depth * depth * .40f)
            val fallFraction = 1f - .10f / flight.seconds
            val slope = .025f + state.wind * .09f
            val pathHeight = edge - top + 36.dp.toPx()
            if (flight.progress < fallFraction) {
                val y = top - 36.dp.toPx() + pathHeight * flight.progress / fallFraction
                val head = Offset(x + (edge - y) * slope, y)
                // Fade over the large temperature and textual reading zones, retain rain at the edges.
                val reading = if (head.x < size.width * .54f && y < edge - 160.dp.toPx()) .40f else 1f
                val fadeIn = smoothNatural((y - top) / 40.dp.toPx())
                clipRect(top = top, bottom = minOf(edge, size.height)) {
                    val speed = pathHeight / (flight.seconds * fallFraction)
                    drawRainStreak(head, Offset(-slope * speed, speed), depth, palette, alpha * reading * fadeIn, state.drizzle)
                }
            } else if (!state.freezing && card != null && depth > .70f && x in left..right && edge < size.height) {
                val seconds = (flight.progress - fallFraction) * flight.seconds
                val fade = (1f - seconds / .10f).coerceIn(0f, 1f)
                // Brief ballistic beads at the actual contact point, no expanding rings.
                repeat(3) { bead ->
                    val seed = rainSeed(i, 60 + bead)
                    val vx = (seed - .5f) * 100f
                    val vy = 35f + rainSeed(i, 70 + bead) * 50f
                    val dx = vx * seconds
                    val dy = -vy * seconds + 420f * seconds * seconds
                    if (dy < 0f) drawCircle(palette.particle.copy(alpha = alpha * fade),
                        (.35f + seed * .38f).dp.toPx(), Offset(x + dx.dp.toPx(), edge + dy.dp.toPx()))
                }
            }
        }
    })
}
