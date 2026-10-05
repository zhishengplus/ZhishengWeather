package com.zhisheng.weather.ui.components

import android.animation.ValueAnimator
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.weatherPresentationTime
import com.zhisheng.weather.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val LocalNaturalLight = staticCompositionLocalOf<NaturalLightPalette?> { null }
internal val LocalNaturalPreviewPhase = staticCompositionLocalOf<Double?> { null }
internal val LocalSolarSkyPreview = staticCompositionLocalOf<SolarSkyState?> { null }
internal val LocalImmersiveSky = staticCompositionLocalOf { false }
internal data class HomeBackdrop(val layer: GraphicsLayer, val origin: Offset, val owner: android.view.View)
internal val LocalHomeBackdrop = compositionLocalOf<HomeBackdrop?> { null }

/** One native atmospheric field behind the complete page; content gets its own display list. */
@Composable
internal fun NaturalWeatherSurface(
    weather: WeatherData?, level: AmbienceLevel, night: Boolean,
    modifier: Modifier = Modifier, active: Boolean = true, parallax: () -> Float = { 0f },
    city: City? = null, livingSkyOverride: Boolean? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!isPhosphorVista) {
        Box(modifier.zhishengScreen(), content = content)
        return
    }
    val now = weatherPresentationTime()
    val minutes = weather?.utcOffsetSeconds?.let { Math.floorMod(now / 60_000L + it / 60L, 1440L).toInt() }
        ?: java.time.LocalTime.now().let { it.hour * 60 + it.minute }
    val livingSky = livingSkyOverride ?: if (LocalInspectionMode.current) false else
        SettingsRepository.livingSky.collectAsState(initial = false).value
    val calculatedSolar = remember(livingSky, level, city?.latitude, city?.longitude, now / 60_000L) {
        if (livingSky && level != AmbienceLevel.OFF) solarSkyState(city?.latitude, city?.longitude, now) else null
    }
    val solar = if (livingSky && level != AmbienceLevel.OFF) LocalSolarSkyPreview.current ?: calculatedSolar else null
    val state = naturalLightState(weather, night, minutes, now, solar)
    val colors = naturalLightPalette(state, LocalZhishengPalette.current.isLight)
    val existingClock = LocalVistaGlowPhase.current
    val ownClock = if (!LocalVistaSoftGlow.current) rememberVistaGlowPhase(active && level != AmbienceLevel.OFF) else existingClock
    // Covered pages keep their last frame without invalidating hidden cards and hero artwork.
    val clock = remember(active, ownClock) {
        if (active) ownClock else mutableStateOf(androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { ownClock.value })
    }
    val landing = remember { mutableStateOf<Rect?>(null) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val backdropLayer = rememberGraphicsLayer()
    CompositionLocalProvider(LocalRainLanding provides landing, LocalNaturalLight provides colors, LocalVistaGlowPhase provides clock,
        LocalImmersiveSky provides (android.os.Build.VERSION.SDK_INT >= 33 && level != AmbienceLevel.OFF && !LocalInspectionMode.current),
        LocalHomeBackdrop provides HomeBackdrop(backdropLayer, rootOrigin, androidx.compose.ui.platform.LocalView.current)) {
        Box(modifier.onGloballyPositioned { rootOrigin = it.positionInRoot() }) {
            NaturalWeatherBackdrop(state, colors, level, active, parallax,
                modifier = Modifier.drawWithContent {
                    backdropLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(backdropLayer)
                })
            Box(Modifier.fillMaxSize().graphicsLayer(), content = content)
            RainLandingOverlay(state, colors, level, clock, landing, { rootOrigin }, parallax)
        }
    }
}

private data class NaturalFrame(val state: NaturalLightState, val palette: NaturalLightPalette)
private fun NaturalFrame.mix(other: NaturalFrame, f: Float) = NaturalFrame(
    (if (f < 0.5f) state else other.state).copy(
        daylight = state.daylight + (other.state.daylight - state.daylight) * f,
        twilight = state.twilight + (other.state.twilight - state.twilight) * f,
        cloud = state.cloud + (other.state.cloud - state.cloud) * f,
        intensity = state.intensity + (other.state.intensity - state.intensity) * f,
        wind = state.wind + (other.state.wind - state.wind) * f,
        solar = if (state.solar != null && other.state.solar != null) state.solar.mix(other.state.solar, f)
            else if (f < .5f) state.solar else other.state.solar), palette.mix(other.palette, f))

@Composable
internal fun NaturalWeatherBackdrop(
    state: NaturalLightState, colors: NaturalLightPalette, level: AmbienceLevel,
    active: Boolean = true, parallax: () -> Float = { 0f }, modifier: Modifier = Modifier,
) {
    val light = LocalZhishengPalette.current.isLight
    val clock = LocalVistaGlowPhase.current
    val previewPhase = LocalNaturalPreviewPhase.current
    val foregroundRain = LocalRainLanding.current != null
    val preview = LocalInspectionMode.current
    val sky = remember(preview) {
        if (!preview && android.os.Build.VERSION.SDK_INT >= 33) runCatching { ImmersiveSky() }
            .onFailure { android.util.Log.w("ImmersiveSky", "Sky shader unavailable; using compatible atmosphere", it) }.getOrNull() else null
    }
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    val target = NaturalFrame(state, colors)
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val transition = remember { Animatable(1f) }
    LaunchedEffect(target, active) {
        // A covered home retains its visible frame; city selection starts blending on reveal.
        if (!active) return@LaunchedEffect
        if (target != to) {
            from = from.mix(to, transition.value)
            to = target
            transition.snapTo(0f)
        }
        if (preview || level == AmbienceLevel.OFF || !ValueAnimator.areAnimatorsEnabled() || power?.isPowerSaveMode == true) transition.snapTo(1f)
        else if (transition.value < 1f) transition.animateTo(1f, tween(1200))
    }
    val levelGain = when (level) { AmbienceLevel.OFF -> 0f; AmbienceLevel.SUBTLE -> 0.52f; AmbienceLevel.VIVID -> 0.76f; AmbienceLevel.INTENSE -> 1f }
    val heldPhase = remember { doubleArrayOf(0.0) }
    val skyLayer = rememberGraphicsLayer()
    Box(modifier.fillMaxSize().graphicsLayer().drawWithCache {
        val cloudBrush = Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0.38f), Color.Transparent),
            center = Offset.Zero, radius = 1f)
        val glowBrush = Brush.radialGradient(listOf(Color.White, Color.Transparent), center = Offset.Zero, radius = 1f)
        onDrawBehind {
            val blend = transition.value
            val frame = if (blend >= 1f) to else from.mix(to, blend)
            val phase = previewPhase ?: if (preview) 0.173 else {
                if (active && level != AmbienceLevel.OFF) heldPhase[0] = clock.value
                heldPhase[0]
            }
            // The shared phase is periodic. Rain/snow cycles use integer periods so there is no seam jump.
            val scroll = (parallax() / density).coerceAtLeast(0f)
            val retreat = 1f - smoothNatural(scroll / 440f)
            // Keep a quiet trace of the same sky behind the first information panels.
            // A glass cover needs real scene detail underneath its optical edge.
            val living = frame.state.solar != null
            val depth = minOf(size.height, (if (living) 780.dp else 1250.dp).toPx())
            drawRect(Brush.verticalGradient(0f to androidx.compose.ui.graphics.lerp(frame.palette.base, frame.palette.top, retreat),
                (if (living) .43f else .61f) to androidx.compose.ui.graphics.lerp(frame.palette.base, frame.palette.middle, retreat),
                1f to frame.palette.base, endY = depth))
            val gain = levelGain * retreat
            if (gain > 0.001f) {
                if (sky != null && android.os.Build.VERSION.SDK_INT >= 33) {
                    translate(top = -scroll.coerceAtMost(440f).dp.toPx() * .12f) {
                        val opacity = minOf(.92f, gain * 1.4f) / minOf(.92f, levelGain * 1.4f)
                        sky.drawCached(this, skyLayer, frame.state, frame.palette, phase, levelGain, opacity, light, minOf(size.height, 1120.dp.toPx()),
                            from.state.weather, to.state.weather, blend)
                    }
                } else {
                    drawNaturalLight(frame, phase, gain, light, cloudBrush, glowBrush)
                }
                if (blend < 1f) drawNaturalThermal(from.state, from.palette, phase, gain * (1f - blend), glowBrush)
                drawNaturalThermal(to.state, to.palette, phase, gain * blend, glowBrush)
                if (blend < 1f && !(foregroundRain && from.state.weather in setOf(NaturalWeather.RAIN, NaturalWeather.STORM)))
                    drawNaturalParticles(from, level, phase, gain * (1f - blend), scroll)
                if (!(foregroundRain && to.state.weather in setOf(NaturalWeather.RAIN, NaturalWeather.STORM)))
                    drawNaturalParticles(to, level, phase, gain * blend, scroll)
            }
        }
    })
}

private fun DrawScope.drawNaturalLight(frame: NaturalFrame, phase: Double, gain: Float, light: Boolean, cloudBrush: Brush, glowBrush: Brush) {
    val s = frame.state
    val p = frame.palette
    val w = size.width
    val depth = minOf(size.height, 640.dp.toPx())
    val angle = phase * PI * 2
    val drift = sin(angle).toFloat()
    val sunlight = (1f - s.cloud * 0.88f) * if (s.weather == NaturalWeather.NEUTRAL) 0f else 1f
    fun ellipse(brush: Brush, x: Float, y: Float, rx: Float, ry: Float, alpha: Float, color: Color) {
        translate(x, y) { scale(rx, ry, pivot = Offset.Zero) {
            drawCircle(brush, radius = 1f, center = Offset.Zero, alpha = alpha,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color))
        } }
    }
    // An off-centre pool of light, aligned with the large weather form on the right.
    ellipse(glowBrush, w * (0.81f + drift * 0.025f), depth * 0.27f, w * 0.62f, depth * 0.45f,
        gain * sunlight * if (light) 0.38f else 0.13f, p.light)
    if (s.twilight > 0f) ellipse(glowBrush, w * 0.74f, depth * 0.43f, w * 0.85f, depth * 0.25f,
        gain * s.twilight * (1f - s.cloud * 0.6f) * 0.18f, Color(0xFFFFCDAE))
    val haze = s.weather in setOf(NaturalWeather.FOG, NaturalWeather.HAZE, NaturalWeather.DUST)
    val cloudGain = gain * s.cloud * if (light) 0.52f else 0.065f
    repeat(if (haze) 4 else 3) { i ->
        val wave = sin(angle + i * 1.8).toFloat()
        val cx = w * (0.18f + i * 0.36f + wave * 0.045f)
        val cy = depth * (0.10f + i * if (haze) 0.10f else 0.055f)
        val mistColor = when (s.weather) {
            NaturalWeather.HAZE -> if (light) Color(0xFFE5E3DE) else Color(0xFFB5B7AF)
            NaturalWeather.DUST -> if (light) Color(0xFFD5C4A7) else Color(0xFFBBA787)
            else -> p.cloud
        }
        ellipse(cloudBrush, cx, cy, w * (0.65f + i * 0.08f), depth * if (haze) 0.13f else 0.18f,
            cloudGain, mistColor)
    }
    if (s.weather == NaturalWeather.STORM) {
        val flash = naturalLightning(phase * 72.0) * gain
        ellipse(glowBrush, w * 0.78f, depth * 0.14f, w * 0.40f, depth * 0.20f, flash, Color(0xFFD8E7FF))
    }
}

private fun DrawScope.drawNaturalParticles(frame: NaturalFrame, level: AmbienceLevel, phase: Double, gain: Float, scroll: Float) {
    if (gain <= 0.001f) return
    val s = frame.state
    val w = size.width
    val h = minOf(size.height, 720.dp.toPx())
    val count = naturalParticleBudget(s, level)
    if (s.weather == NaturalWeather.RAIN || s.weather == NaturalWeather.STORM) {
        drawNaturalRain(s, frame.palette, count, phase, gain, scroll)
        return
    }
    fun seed(i: Int, salt: Int): Float {
        var hash = (i + 1) * 374761393 + salt * 668265263
        hash = (hash xor (hash ushr 13)) * 1274126177
        hash = hash xor (hash ushr 16)
        return (hash and 0xFFFFFF) / 16777216f
    }
    fun wrap(v: Double): Float = (v - kotlin.math.floor(v)).toFloat()
    repeat(count) { i ->
        val near = i % 3 == 0
        val speed = when {
            s.intensity >= 0.72f -> if (near) 27 else 18
            s.intensity <= 0.30f -> if (near) 12 else 9
            else -> if (near) 18 else 12
        }
        val x0 = seed(i, 3)
        val stars = s.weather == NaturalWeather.CLEAR || s.weather == NaturalWeather.CLOUDS
        val snow = s.weather == NaturalWeather.SNOW || (s.weather == NaturalWeather.MIXED && i % 2 == 0)
        val horizontal = s.weather == NaturalWeather.DUST || s.weather == NaturalWeather.WIND
        val x = if (horizontal) wrap(x0 + phase * (if (near) 4 else 2)) else
            x0 + sin(phase * 2 * PI * (if (near) 2 else 1) + i).toFloat() * (if (snow) 0.035f else 0.009f)
        val y = if (stars || horizontal) seed(i, 7) * 0.65f else wrap(seed(i, 7) + phase * (if (snow) speed / 3 else speed))
        val mask = naturalParticleMask(x, y, scroll)
        val alpha = mask * gain * (if (near) 0.50f else 0.25f)
        val center = Offset(x * w, y * h)
        val ink = frame.palette.particle.copy(alpha = alpha)
        when {
            stars -> {
                val starLight = s.solar?.let { 1f - smoothNatural((it.altitude + 14f) / 8f) } ?: (1f - s.daylight)
                val starAlpha = alpha * starLight * (1f - s.cloud) * 0.85f
                drawCircle(frame.palette.particle.copy(alpha = starAlpha), (if (near) 1.1f else 0.6f).dp.toPx(), center)
            }
            snow -> {
                val radius = (if (near) 1.7f else 0.85f).dp.toPx()
                drawCircle(ink.copy(alpha = alpha * 0.18f), radius * 2.5f, center)
                drawCircle(ink, radius, center)
                if (near) drawCircle(Color.White.copy(alpha = alpha * 0.65f), radius * 0.55f, center)
            }
            s.weather == NaturalWeather.ICE -> drawCircle(ink, (if (near) 1.65f else 1f).dp.toPx(), center)
            horizontal -> {
                if (s.weather == NaturalWeather.DUST) drawCircle(ink, (if (near) 0.9f else 0.55f).dp.toPx(), center)
                else drawLine(ink.copy(alpha = alpha * 0.35f), center, center + Offset(24.dp.toPx(), -3.dp.toPx()), 0.65.dp.toPx(), StrokeCap.Round)
            }
            else -> {
                val length = (if (near) 11f else 6f) * (0.65f + s.intensity * 0.55f)
                val slant = (1.5f + s.wind * 5f).dp.toPx()
                drawLine(ink, center, center + Offset(-slant, length.dp.toPx()), (if (near) 0.75f else 0.5f).dp.toPx(), StrokeCap.Round)
                if (s.freezing && i % 5 == 0) drawCircle(ink, 0.9.dp.toPx(), center)
            }
        }
    }
}
