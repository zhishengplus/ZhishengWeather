package com.zhisheng.weather.ui.components

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.zhisheng.weather.R
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.theme.LocalVistaGlowPhase
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.weatherPresentationTime
import kotlin.math.floor

internal data class WeatherGlassEnvironment(
    val frame: () -> NaturalSkyFrame?,
    val phase: State<Double>,
    val active: Boolean,
    val quality: SkyRenderQuality,
    val budget: SkyRenderQuality,
    val level: AmbienceLevel,
    val paging: () -> Boolean,
)

internal val LocalWeatherGlassEnvironment = compositionLocalOf<WeatherGlassEnvironment?> { null }
/** Opt in only a real first information card, never dialogs, settings or city previews. */
internal val LocalWeatherGlassTarget = staticCompositionLocalOf { false }

/** Explicitly null data suppresses inherited weather, including while a neighbouring city loads. */
@Composable
internal fun WeatherGlassOwner(weather: WeatherData?, night: Boolean,
    active: Boolean = LocalCityPageMotionRunning.current, content: @Composable () -> Unit) {
    val inherited = LocalWeatherGlassEnvironment.current
    val now = weatherPresentationTime()
    val light = LocalZhishengPalette.current.isLight
    val frame = remember(weather, night, now / 60_000L, light) {
        weather?.let {
            val minutes = it.utcOffsetSeconds?.let { offset -> Math.floorMod(now / 60_000L + offset / 60L, 1440L).toInt() }
                ?: if (night) 22 * 60 else 12 * 60
            val state = naturalLightState(it, night, minutes, now)
            NaturalSkyFrame(state, naturalLightPalette(state, light))
        }
    }
    val phase = LocalVistaGlowPhase.current
    val scoped = inherited?.copy(frame = { frame }, phase = phase, active = inherited.active && active)
    CompositionLocalProvider(LocalWeatherGlassEnvironment provides scoped, content = content)
}

@Composable
internal fun rememberWeatherGlassAtmosphere(): WeatherGlassAtmosphere? {
    val environment = LocalWeatherGlassEnvironment.current
    val target = LocalWeatherGlassTarget.current
    val preview = LocalInspectionMode.current
    val resources = LocalContext.current.resources
    val current = rememberUpdatedState(environment)
    // The capture layer subscribes to discrete output, rather than the shared high-rate clock.
    val phase = remember {
        derivedStateOf(structuralEqualityPolicy()) {
            val context = current.value ?: return@derivedStateOf 0.0
            val moving = context.active && context.quality != SkyRenderQuality.STATIC &&
                !context.paging() && context.frame()?.let { weatherGlassKind(it.state) } == WeatherGlassKind.RAIN
            val source = if (moving) context.phase.value else
                androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { context.phase.value }
            val steps = 72.0 * if (context.budget == SkyRenderQuality.HIGH) 30.0 else 20.0
            if (source.isFinite()) floor(source * steps) / steps else 0.0
        }
    }
    val quantized = rememberUpdatedState(environment?.copy(phase = phase))
    if (!target || environment == null || preview || Build.VERSION.SDK_INT < 33) return null
    return remember(resources) { WeatherGlassAtmosphere(resources, quantized) }
}

/** Filters only the already captured backdrop. The caller draws text and icons afterwards. */
@RequiresApi(33)
internal class WeatherGlassAtmosphere(private val resources: Resources,
    private val environment: State<WeatherGlassEnvironment?>) {
    private val clock = AtmosphereEffectClock()
    private var disabled = false
    private val shaders = mutableMapOf<WeatherGlassKind, RuntimeShader>()
    private data class Frame(val kind: WeatherGlassKind, val width: Float, val height: Float,
        val tick: Long, val intensity: Float, val alpha: Float, val input: RenderEffect?)
    private var recorded: Frame? = null
    private var recordedEffect: RenderEffect? = null

    fun effect(width: Float, height: Float, input: RenderEffect?): RenderEffect? {
        if (disabled) return input
        val context = environment.value ?: return withoutWeather(input)
        val sky = context.frame() ?: return withoutWeather(input)
        if (context.level == AmbienceLevel.OFF) return withoutWeather(input)
        val kind = weatherGlassKind(sky.state)
        if (kind == WeatherGlassKind.NONE) return withoutWeather(input)
        val moving = context.active && context.quality != SkyRenderQuality.STATIC && !context.paging()
        // Snow/frost are settled surface textures. Only moving rain needs clock snapshots.
        val animated = moving && kind == WeatherGlassKind.RAIN
        val phase = if (animated) context.phase.value else androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { context.phase.value }
        val elapsed = clock.seconds(phase, animated)
        val tick = if (kind == WeatherGlassKind.RAIN) atmosphereTimeTick(elapsed, context.budget) else 0L
        val level = when (context.level) { AmbienceLevel.SUBTLE -> .45f; AmbienceLevel.VIVID -> .65f; else -> .82f }
        val frame = Frame(kind, width.coerceAtLeast(1f), height.coerceAtLeast(1f), tick,
            sky.state.intensity.coerceIn(0f, 1f), level, input)
        if (recorded == frame) return recordedEffect ?: input
        return runCatching {
            val shader = shaders.getOrPut(kind) { createShader(kind) }
            shader.setFloatUniform("uResolution", frame.width, frame.height)
            shader.setFloatUniform("uAlpha", frame.alpha)
            if (kind == WeatherGlassKind.RAIN) {
                shader.setFloatUniform("uTime", (tick / if (context.budget == SkyRenderQuality.HIGH) 30.0 else 20.0).toFloat())
                // Preserve h.e's three parameter sets; map our explicit intensity instead of OEM codes.
                val parameters = when {
                    frame.intensity <= .3f -> floatArrayOf(.5f, .5f, 1f, .85f, .6f, .4f)
                    frame.intensity <= .6f -> floatArrayOf(.8f, .8f, 1.5f, 1f, .7f, 1f)
                    else -> floatArrayOf(1f, 1f, 2f, 1.4f, 1f, 1.3f)
                }
                listOf("uStaticDropSize", "uStaticDropAmount", "uStaticDropSpeed", "uRunningDropSize", "uRunningDropAmount", "uRunningDropSpeed")
                    .forEachIndexed { index, name -> shader.setFloatUniform(name, parameters[index]) }
            } else shader.setFloatUniform("uProgress", 1f)
            // Android RenderEffect captures shader uniforms: recreate at the bounded shared-clock tick.
            val weather = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "uTex")
            val result = if (input == null) weather else android.graphics.RenderEffect.createChainEffect(weather, input.asAndroidRenderEffect())
            recorded = frame
            result.asComposeRenderEffect().also { recordedEffect = it }
        }.getOrElse {
            disabled = true
            android.util.Log.w("WeatherGlass", "Weather decoration unavailable; retaining original glass", it)
            input
        }
    }

    private fun withoutWeather(input: RenderEffect?): RenderEffect? {
        clock.seconds(0.0, false)
        return input
    }

    private fun createShader(kind: WeatherGlassKind): RuntimeShader {
        val resource = when (kind) {
            WeatherGlassKind.RAIN -> R.raw.weather_glass_rain
            WeatherGlassKind.SNOW -> R.raw.weather_glass_snow
            WeatherGlassKind.FROST -> R.raw.weather_glass_frost
            WeatherGlassKind.NONE -> error("No weather decoration")
        }
        val shader = RuntimeShader(resources.openRawResource(resource).bufferedReader(Charsets.UTF_8).use { it.readText() })
        if (kind == WeatherGlassKind.SNOW || kind == WeatherGlassKind.FROST) {
            val bitmap = requireNotNull(BitmapFactory.decodeResource(resources,
                if (kind == WeatherGlassKind.SNOW) R.drawable.weather_glass_snow else R.drawable.weather_glass_frost,
                BitmapFactory.Options().apply { inScaled = false }))
            val tile = if (kind == WeatherGlassKind.SNOW) Shader.TileMode.REPEAT else Shader.TileMode.MIRROR
            val texture = BitmapShader(bitmap, tile, tile).apply { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) }
            shader.setInputShader(if (kind == WeatherGlassKind.SNOW) "uSnowTex" else "uFrostTex", texture)
            if (kind == WeatherGlassKind.SNOW) shader.setFloatUniform("uSnowTexWH", bitmap.width.toFloat(), bitmap.height.toFloat())
        }
        return shader
    }
}
