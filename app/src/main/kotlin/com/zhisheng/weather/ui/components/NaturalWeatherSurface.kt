package com.zhisheng.weather.ui.components

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
import kotlinx.coroutines.flow.collectLatest

internal val LocalNaturalLight = staticCompositionLocalOf<NaturalLightPalette?> { null }
internal val LocalNaturalPreviewPhase = staticCompositionLocalOf<Double?> { null }
internal val LocalSolarSkyPreview = staticCompositionLocalOf<SolarSkyState?> { null }
internal val LocalImmersiveSky = staticCompositionLocalOf { false }
private val LocalSkyShaderFailed = compositionLocalOf<MutableState<Boolean>?> { null }
internal data class HomeBackdrop(val layer: GraphicsLayer, val origin: Offset, val owner: android.view.View)
internal val LocalHomeBackdrop = compositionLocalOf<HomeBackdrop?> { null }

/** One native atmospheric field behind the complete page; content gets its own display list. */
@Composable
internal fun NaturalWeatherSurface(
    weather: WeatherData?, level: AmbienceLevel, night: Boolean,
    modifier: Modifier = Modifier, active: Boolean = true, parallax: () -> Float = { 0f },
    city: City? = null, livingSkyOverride: Boolean? = null,
    pagingTransition: SkyPagingTransition? = null,
    pagingFraction: () -> Float = { 0f }, pagingActive: () -> Boolean = { false },
    content: @Composable BoxScope.() -> Unit,
) {
    if (!isPhosphorVista) {
        val now = weatherPresentationTime()
        val minutes = weather?.utcOffsetSeconds?.let { Math.floorMod(now / 60_000L + it / 60L, 1440L).toInt() }
            ?: java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        val state = naturalLightState(weather, night, minutes, now)
        Box(modifier.zhishengScreen()) {
            ClassicWeatherField(state, level, active, parallax)
            Box(Modifier.fillMaxSize().graphicsLayer(), content = content)
        }
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
    val capabilities = rememberSkyRenderCapabilities()
    val shaderFailed = remember { mutableStateOf(false) }
    val quality = skyRenderQuality(capabilities.copy(shaderAvailable = !shaderFailed.value), active, level != AmbienceLevel.OFF)
    val budget = rememberSkyRenderBudget(quality, capabilities.copy(shaderAvailable = !shaderFailed.value))
    val light = LocalZhishengPalette.current.isLight
    val pagingFrames = remember(pagingTransition, livingSky, level, light, now / 60_000L) {
        pagingTransition?.let { endpoints ->
            fun frame(endpoint: SkyCityFrame): NaturalSkyFrame {
                val localMinutes = endpoint.weather.utcOffsetSeconds?.let { Math.floorMod(now / 60_000L + it / 60L, 1440L).toInt() }
                    ?: minutes
                val localSolar = if (livingSky && level != AmbienceLevel.OFF)
                    solarSkyState(endpoint.city?.latitude, endpoint.city?.longitude, now) else null
                val localState = naturalLightState(endpoint.weather, endpoint.night, localMinutes, now, localSolar)
                return NaturalSkyFrame(localState, naturalLightPalette(localState, light), identity = endpoint.city?.locationKey)
            }
            frame(endpoints.from) to frame(endpoints.to)
        }
    }
    val existingClock = LocalVistaGlowPhase.current
    val ownClock = if (!LocalVistaSoftGlow.current) rememberVistaGlowPhase(quality != SkyRenderQuality.STATIC) else existingClock
    val phaseMemory = remember(ownClock) { SkyPhaseClock(androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { ownClock.value }) }
    val clockRunning by rememberUpdatedState(active && quality != SkyRenderQuality.STATIC)
    SideEffect { if (!clockRunning) phaseMemory.pause() }
    // Keep the same cloud/particle phase on pause and resume, even if the app's shared clock moves.
    val clock = remember(ownClock, phaseMemory) {
        object : State<Double> {
            override val value: Double get() = phaseMemory.sample(
                if (clockRunning) ownClock.value else 0.0, clockRunning)
        }
    }
    val landing = remember { RainLandingRegistry() }
    val visibleFrame = remember { arrayOfNulls<NaturalSkyFrame>(1) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    val backdropLayer = rememberGraphicsLayer()
    CompositionLocalProvider(LocalRainLanding provides landing, LocalNaturalLight provides colors, LocalVistaGlowPhase provides clock,
        LocalImmersiveSky provides (android.os.Build.VERSION.SDK_INT >= 33 && !shaderFailed.value && level != AmbienceLevel.OFF && !LocalInspectionMode.current),
        LocalSkyRenderQuality provides quality, LocalSkyRenderBudget provides budget,
        LocalSkyPagingFraction provides pagingFraction, LocalSkyPagingActive provides pagingActive,
        LocalSkyRenderCapabilities provides capabilities,
        LocalSkyShaderFailed provides shaderFailed,
        LocalWeatherGlassEnvironment provides WeatherGlassEnvironment(
            frame = { visibleFrame[0] ?: NaturalSkyFrame(state, colors) }, phase = clock, active = active,
            quality = quality, budget = budget, level = level, paging = pagingActive),
        LocalHomeBackdrop provides HomeBackdrop(backdropLayer, rootOrigin, androidx.compose.ui.platform.LocalView.current)) {
        Box(modifier.onGloballyPositioned { rootOrigin = it.positionInRoot() }) {
            NaturalWeatherBackdrop(state, colors, level, active, parallax,
                pagingFrames = pagingFrames, pagingFraction = pagingFraction, pagingActive = pagingActive,
                identity = city?.locationKey,
                onFrame = { visibleFrame[0] = it },
                modifier = Modifier.drawWithContent {
                    backdropLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(backdropLayer)
                })
            Box(Modifier.fillMaxSize().graphicsLayer(), content = content)
            RainLandingOverlay(state, colors, level, clock, landing, { rootOrigin }, parallax, skyFrame = { visibleFrame[0] })
        }
    }
}

private typealias NaturalFrame = NaturalSkyFrame

/** Draw memory is deliberately not Snapshot state: sampling never requests another frame. */
private class SkyDisplayMemory {
    var frame: NaturalFrame? = null
    var paging = false
    var expectedEndpoint: NaturalFrame? = null
    var dragAnchor: NaturalFrame? = null
    var dragAnchorKey: String? = null
}

@Composable
internal fun NaturalWeatherBackdrop(
    state: NaturalLightState, colors: NaturalLightPalette, level: AmbienceLevel,
    active: Boolean = true, parallax: () -> Float = { 0f }, modifier: Modifier = Modifier,
    pagingFrames: Pair<NaturalSkyFrame, NaturalSkyFrame>? = null,
    pagingFraction: () -> Float = { 0f }, pagingActive: () -> Boolean = { false },
    onFrame: (NaturalSkyFrame) -> Unit = {},
    identity: String? = null,
) {
    val light = LocalZhishengPalette.current.isLight
    val clock = LocalVistaGlowPhase.current
    val previewPhase = LocalNaturalPreviewPhase.current
    val foregroundRain = LocalRainLanding.current != null
    val preview = LocalInspectionMode.current
    val capabilities = LocalSkyRenderCapabilities.current ?: rememberSkyRenderCapabilities()
    val shaderFailed = LocalSkyShaderFailed.current ?: remember { mutableStateOf(false) }
    val failureHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val sky = remember(preview, capabilities.apiLevel >= 33) {
        if (!preview && !shaderFailed.value && capabilities.apiLevel >= 33 && android.os.Build.VERSION.SDK_INT >= 33) runCatching {
            ImmersiveSky { failureHandler.post { shaderFailed.value = true } }
        }.onFailure {
            failureHandler.post { shaderFailed.value = true }
            android.util.Log.w("ImmersiveSky", "Sky shader unavailable; using compatible atmosphere", it)
        }.getOrNull() else null
    }
    val quality = skyRenderQuality(capabilities.copy(shaderAvailable = sky != null && !shaderFailed.value), active, level != AmbienceLevel.OFF)
    val budget = LocalSkyRenderBudget.current ?: rememberSkyRenderBudget(quality,
        capabilities.copy(shaderAvailable = sky != null && !shaderFailed.value))
    val rainVeil = remember(preview, capabilities.apiLevel >= 33) {
        if (!preview && capabilities.apiLevel >= 33 && android.os.Build.VERSION.SDK_INT >= 33)
            runCatching { RainVeil() }.onFailure {
                android.util.Log.w("RainVeil", "Distant rain shader unavailable; retaining original Canvas weather", it)
            }.getOrNull() else null
    }
    val target = NaturalFrame(state, colors, identity = identity)
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val transition = remember { Animatable(1f) }
    val displayed = remember { SkyDisplayMemory() }
    val currentTarget by rememberUpdatedState(target)
    val currentPaging by rememberUpdatedState(pagingActive)
    LaunchedEffect(active, quality) {
        snapshotFlow { currentTarget to currentPaging() }.collectLatest { (next, paging) ->
            if (!active || paging) return@collectLatest
            if (quality == SkyRenderQuality.STATIC && next == to && displayed.frame != null) {
                // Pausing midway through a weather blend keeps exactly that image. A subsequent
                // explicit weather update can replace it once; resuming continues toward its target.
                from = displayed.frame!!
                to = displayed.frame!!
                transition.snapTo(1f)
                return@collectLatest
            }
            // Pager settlement can precede selected-city delivery. Retain its exact last sky meanwhile.
            val waitingForSelection = displayed.paging && displayed.expectedEndpoint?.identity?.let { it != next.identity } == true
            val destination = if (waitingForSelection) displayed.frame ?: next else next
            if (destination != to || displayed.paging) {
                from = displayed.frame ?: from.mix(to, transition.value)
                to = destination
                transition.snapTo(0f)
            }
            if (!waitingForSelection) { displayed.paging = false; displayed.expectedEndpoint = null }
            if (preview || quality == SkyRenderQuality.STATIC) transition.snapTo(1f)
            else if (transition.value < 1f) transition.animateTo(1f, tween(1200))
        }
    }
    val levelGain = when (level) { AmbienceLevel.OFF -> 0f; AmbienceLevel.SUBTLE -> 0.52f; AmbienceLevel.VIVID -> 0.76f; AmbienceLevel.INTENSE -> 1f }
    val heldPhase = remember { doubleArrayOf(Double.NaN) }
    val skyLayer = rememberGraphicsLayer()
    val rainLayer = rememberGraphicsLayer()
    Box(modifier.fillMaxSize().graphicsLayer().drawWithCache {
        val cloudBrush = Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0.38f), Color.Transparent),
            center = Offset.Zero, radius = 1f)
        val glowBrush = Brush.radialGradient(listOf(Color.White, Color.Transparent), center = Offset.Zero, radius = 1f)
        onDrawBehind {
            val paging = pagingActive()
            val fraction = pagingFraction().takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
            if (paging && !displayed.paging) {
                displayed.dragAnchor = displayed.frame
                displayed.dragAnchorKey = target.identity
            }
            fun endpoint(frame: NaturalFrame): NaturalFrame =
                if (displayed.dragAnchorKey != null && frame.identity == displayed.dragAnchorKey)
                    displayed.dragAnchor?.copy(identity = frame.identity) ?: frame else frame
            val blend = if (paging && pagingFrames != null) fraction else transition.value
            val source = if (paging && pagingFrames != null) endpoint(pagingFrames.first) else from
            val destination = if (paging && pagingFrames != null) endpoint(pagingFrames.second) else to
            val frame = if (!active || (paging && pagingFrames == null)) displayed.frame ?: source.mix(destination, blend)
                else source.mix(destination, blend)
            displayed.frame = frame
            onFrame(frame)
            if (paging) {
                displayed.paging = true
                displayed.expectedEndpoint = if (pagingFrames == null) null else when {
                    fraction < .001f -> pagingFrames.first
                    fraction > .999f -> pagingFrames.second
                    else -> null
                }
            }
            val phase = previewPhase ?: if (preview) 0.173 else {
                if (heldPhase[0].isNaN() || (active && quality != SkyRenderQuality.STATIC && level != AmbienceLevel.OFF)) heldPhase[0] = clock.value
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
                var skyRendered = false
                if (sky != null && !shaderFailed.value && android.os.Build.VERSION.SDK_INT >= 33) {
                    translate(top = -scroll.coerceAtMost(440f).dp.toPx() * .12f) {
                        val opacity = minOf(.92f, gain * 1.4f) / minOf(.92f, levelGain * 1.4f)
                        skyRendered = sky.drawCached(this, skyLayer, frame.state, frame.palette, phase, levelGain, opacity, light, minOf(size.height, 1120.dp.toPx()),
                            frame.weatherWeights, budget,
                            if (quality == SkyRenderQuality.STATIC) 0L else skyCloudFrameIntervalNanos(budget, paging))
                    }
                }
                if (rainVeil != null && android.os.Build.VERSION.SDK_INT >= 33) {
                    rainVeil.drawCached(this, rainLayer, frame, phase, gain,
                        minOf(size.height, 1120.dp.toPx()), budget,
                        moving = active && quality != SkyRenderQuality.STATIC && !paging)
                }
                drawNaturalThermal(frame.state, frame.palette, phase, gain, glowBrush)
                val particleScale = budget.particleScale * skyPagingParticleScale(fraction, paging)
                NaturalWeather.entries.forEach { kind ->
                    val weight = frame.weatherWeights[kind.ordinal]
                    if (weight > .001f) {
                        val weatherFrame = frame.copy(state = frame.state.copy(weather = kind))
                        if (!skyRendered) drawNaturalLight(weatherFrame, phase, gain * weight, light, cloudBrush, glowBrush)
                        if (!(foregroundRain && kind in setOf(NaturalWeather.RAIN, NaturalWeather.STORM)))
                            drawNaturalParticles(weatherFrame, level, phase, gain * weight, scroll, particleScale)
                    }
                }
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
    // Gentle local breathing in clear conditions, with the same reduced-motion clock.
    if (s.weather == NaturalWeather.CLEAR && s.daylight > .5f) {
        ellipse(glowBrush, w * .85f, depth * (.22f + drift * .018f), w * .22f, depth * .16f,
            gain * sunlight * (.055f + (drift + 1f) * .015f), p.light)
    }
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

private fun DrawScope.drawNaturalParticles(frame: NaturalFrame, level: AmbienceLevel, phase: Double, gain: Float, scroll: Float, particleScale: Float = 1f) {
    if (gain <= 0.001f) return
    val s = frame.state
    val w = size.width
    val h = minOf(size.height, 720.dp.toPx())
    val count = (naturalParticleBudget(s, level) * particleScale).toInt()
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
