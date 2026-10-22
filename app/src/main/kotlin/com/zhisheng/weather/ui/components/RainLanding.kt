package com.zhisheng.weather.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.theme.LocalZhishengChrome

internal data class RainLandingSurface(val cityKey: String?, val weather: WeatherData?, val bounds: Rect)

/** Each retained city owns its edge and removes only its own registration. */
internal class RainLandingRegistry {
    private val surfaces = mutableStateMapOf<Any, RainLandingSurface>()
    fun update(owner: Any, cityKey: String?, weather: WeatherData?, bounds: Rect) {
        surfaces[owner] = RainLandingSurface(cityKey, weather, bounds)
    }
    fun updateWeather(owner: Any, cityKey: String?, weather: WeatherData?) {
        surfaces[owner]?.let { surfaces[owner] = it.copy(cityKey = cityKey, weather = weather) }
    }
    fun remove(owner: Any) { surfaces.remove(owner) }
    fun snapshot(): List<RainLandingSurface> = surfaces.values.toList()
}

internal val LocalRainLanding = staticCompositionLocalOf<RainLandingRegistry?> { null }

/** Real module bounds follow both page translation and vertical scrolling. */
@Composable
internal fun RainLandingTarget(enabled: Boolean, cityKey: String? = null, weather: WeatherData? = null,
    content: @Composable () -> Unit) {
    val landing = LocalRainLanding.current
    if (!enabled || landing == null) { content(); return }
    val owner = remember { Any() }
    DisposableEffect(landing, owner) { onDispose { landing.remove(owner) } }
    SideEffect { landing.updateWeather(owner, cityKey, weather) }
    Box(Modifier.onGloballyPositioned { coordinates ->
        val origin = coordinates.positionInRoot()
        landing.update(owner, cityKey, weather, Rect(origin.x, origin.y,
            origin.x + coordinates.size.width, origin.y + coordinates.size.height))
    }) { content() }
}

@Composable
internal fun RainLandingOverlay(state: NaturalLightState, palette: NaturalLightPalette,
    level: AmbienceLevel, clock: State<Double>, landing: RainLandingRegistry,
    root: () -> Offset, parallax: () -> Float, skyFrame: () -> NaturalSkyFrame? = { null }) {
    if (level == AmbienceLevel.OFF) return
    val inset = LocalZhishengChrome.current.pagePadding
    val preview = LocalNaturalPreviewPhase.current
    val quality = LocalSkyRenderQuality.current
    val budget = LocalSkyRenderBudget.current ?: quality
    val pagingFraction = LocalSkyPagingFraction.current
    val pagingActive = LocalSkyPagingActive.current
    val heldPhase = remember { doubleArrayOf(0.0) }
    val gain = when (level) { AmbienceLevel.SUBTLE -> .48f; AmbienceLevel.VIVID -> .76f; else -> 1f }
    Box(Modifier.fillMaxSize().drawBehind {
        val frame = skyFrame()
        val visibleState = frame?.state ?: state
        val visiblePalette = frame?.palette ?: palette
        val rainWeight = frame?.let { it.weatherWeights[NaturalWeather.RAIN.ordinal] +
            it.weatherWeights[NaturalWeather.STORM.ordinal] + it.weatherWeights[NaturalWeather.MIXED.ordinal] }
            ?: if (state.weather in setOf(NaturalWeather.RAIN, NaturalWeather.STORM, NaturalWeather.MIXED)) 1f else 0f
        val origin = root()
        val margin = inset.toPx() + 20.dp.toPx()
        val planes = visibleRainLandingPlanes(landing.snapshot().map { surface ->
            RainLandingPlane(surface.cityKey, surface.weather?.current?.condition,
                surface.bounds.left - origin.x + margin, surface.bounds.right - origin.x - margin,
                surface.bounds.top - origin.y)
        }, size.width, size.height, 140.dp.toPx())
        if (rainWeight < .001f && planes.isEmpty()) return@drawBehind
        val visibility = gain * skyPagingParticleScale(pagingFraction(), pagingActive())
        if (visibility < .01f) return@drawBehind
        val retreat = 1f - smoothNatural(parallax() / density / 440f)
        val phase = preview ?: if (quality == SkyRenderQuality.STATIC) heldPhase[0] else {
            clock.value.also { heldPhase[0] = it }
        }
        planes.filter { it.freezing }.forEach { plane ->
            drawNaturalFrostEdge(visiblePalette, phase, visibility,
                Offset(plane.left, plane.top), Offset(plane.right, plane.top), inward = Offset(0f, -1f))
        }
        val count = rainLandingParticleBudget(visibleState.intensity, visibleState.drizzle, gain, budget.particleScale)
        clipRect {
            repeat(count) drops@{ i ->
                val flight = rainFlight(phase, i, visibleState.intensity, visibleState.drizzle)
                val x = flight.x * size.width
                val plane = rainLandingContact(planes, x)
                // No real wet card means rain leaves the view without a fabricated collision floor.
                val edge = plane?.top ?: size.height + 36.dp.toPx()
                val top = maxOf(100.dp.toPx(), edge - 510.dp.toPx())
                val depth = flight.depth
                val weight = if (plane != null) 1f else rainWeight * retreat
                val alpha = visibility * weight * (.12f + depth * depth * .40f)
                if (alpha < .002f) return@drops
                val fallFraction = (1f - RAIN_SPLASH_SECONDS / flight.seconds).coerceAtLeast(.1f)
                val slope = .025f + visibleState.wind * .09f
                val pathHeight = edge - top + 36.dp.toPx()
                if (flight.progress < fallFraction) {
                    val y = top - 36.dp.toPx() + pathHeight * flight.progress / fallFraction
                    val head = Offset(x + (edge - y) * slope, y)
                    val reading = if (head.x < size.width * .54f && y < edge - 160.dp.toPx()) .40f else 1f
                    val fadeIn = smoothNatural((y - top) / 40.dp.toPx())
                    clipRect(top = top, bottom = minOf(edge, size.height)) {
                        val speed = pathHeight / (flight.seconds * fallFraction)
                        drawRainStreak(head, Offset(-slope * speed, speed), depth, visiblePalette,
                            alpha * reading * fadeIn, visibleState.drizzle)
                    }
                } else if (plane != null && !plane.freezing && depth > .70f) {
                    val seconds = (flight.progress - fallFraction) * flight.seconds
                    repeat(if (visibleState.drizzle) 2 else 3) beads@{ bead ->
                        val drop = rainSplashBead(i, bead, seconds, visibleState.wind) ?: return@beads
                        val center = Offset(x + drop.xDp.dp.toPx(), edge + drop.yDp.dp.toPx())
                        if (center.x >= plane.left && center.x < plane.right)
                            drawNaturalWaterBead(center, drop.radiusDp.dp.toPx(), visiblePalette, alpha * drop.opacity)
                    }
                }
            }
        }
    })
}
