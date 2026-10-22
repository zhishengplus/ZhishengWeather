package com.zhisheng.weather.ui.components

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherData
import kotlin.math.PI
import kotlin.math.sin

/** Only real, already loaded weather belongs in a paging endpoint. This never fetches data. */
internal data class SkyCityFrame(val weather: WeatherData, val city: City?, val night: Boolean)
internal data class SkyPagingTransition(val from: SkyCityFrame, val to: SkyCityFrame)

internal data class NaturalSkyFrame(
    val state: NaturalLightState,
    val palette: NaturalLightPalette,
    val weatherWeights: List<Float> = NaturalWeather.entries.map { if (it == state.weather) 1f else 0f },
    val identity: String? = null,
) {
    fun mix(other: NaturalSkyFrame, fraction: Float): NaturalSkyFrame {
        val f = fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        if (f == 0f) return this
        if (f == 1f) return other
        return NaturalSkyFrame((if (f < .5f) state else other.state).copy(
            daylight = state.daylight + (other.state.daylight - state.daylight) * f,
            twilight = state.twilight + (other.state.twilight - state.twilight) * f,
            cloud = state.cloud + (other.state.cloud - state.cloud) * f,
            intensity = state.intensity + (other.state.intensity - state.intensity) * f,
            wind = state.wind + (other.state.wind - state.wind) * f,
            solar = if (state.solar != null && other.state.solar != null) state.solar.mix(other.state.solar, f)
                else if (f < .5f) state.solar else other.state.solar), palette.mix(other.palette, f),
            weatherWeights.indices.map { weatherWeights[it] + (other.weatherWeights[it] - weatherWeights[it]) * f },
            if (f < .5f) identity else other.identity)
    }
}

internal enum class SkyRenderQuality(val resolutionScale: Float, val particleScale: Float) {
    HIGH(.5f, 1f), SIMPLIFIED(.375f, .6f), STATIC(.5f, 1f),
}

internal data class SkyRenderCapabilities(
    val apiLevel: Int,
    val lowMemory: Boolean = false,
    val powerSave: Boolean = false,
    val animationsEnabled: Boolean = true,
    val shaderAvailable: Boolean = true,
)

internal fun skyRenderQuality(capabilities: SkyRenderCapabilities, active: Boolean, enabled: Boolean): SkyRenderQuality = when {
    !active || !enabled || capabilities.powerSave || !capabilities.animationsEnabled -> SkyRenderQuality.STATIC
    capabilities.apiLevel < 33 || capabilities.lowMemory || !capabilities.shaderAvailable -> SkyRenderQuality.SIMPLIFIED
    else -> SkyRenderQuality.HIGH
}

/** A short drag keeps the same renderer; the density eases back to full at both segment ends. */
internal fun skyPagingParticleScale(fraction: Float, paging: Boolean): Float =
    if (!paging || !fraction.isFinite()) 1f else 1f - .35f * sin(fraction.coerceIn(0f, 1f) * PI).toFloat()

internal fun skyCloudFrameIntervalNanos(quality: SkyRenderQuality, paging: Boolean): Long = when {
    quality == SkyRenderQuality.STATIC -> Long.MAX_VALUE
    quality == SkyRenderQuality.SIMPLIFIED -> 50_000_000L
    paging -> 33_333_333L
    else -> 0L
}

internal val LocalSkyRenderQuality = staticCompositionLocalOf { SkyRenderQuality.HIGH }
internal val LocalSkyRenderBudget = staticCompositionLocalOf<SkyRenderQuality?> { null }
internal val LocalSkyRenderCapabilities = staticCompositionLocalOf<SkyRenderCapabilities?> { null }
internal val LocalSkyPagingFraction = staticCompositionLocalOf<() -> Float> { { 0f } }
internal val LocalSkyPagingActive = staticCompositionLocalOf<() -> Boolean> { { false } }

/** Pausing changes update policy, never the weather's appearance or particle density. */
@Composable
internal fun rememberSkyRenderBudget(quality: SkyRenderQuality, capabilities: SkyRenderCapabilities): SkyRenderQuality {
    val initial = skyRenderQuality(capabilities.copy(powerSave = false, animationsEnabled = true), active = true, enabled = true)
    var lastMovingBudget by remember { mutableStateOf(initial) }
    val budget = if (quality == SkyRenderQuality.STATIC) lastMovingBudget else quality
    SideEffect { if (quality != SkyRenderQuality.STATIC) lastMovingBudget = quality }
    return budget
}

/** The shared source may keep serving other surfaces while this sky is paused. Resume in place. */
internal class SkyPhaseClock(initial: Double) {
    private var phase = initial
    private var source = initial
    private var sampling = true
    fun pause() { sampling = false }
    fun sample(next: Double, running: Boolean): Double {
        if (!running) { pause(); return phase }
        if (sampling) phase = (phase + (next - source + 1.0) % 1.0) % 1.0
        source = next
        sampling = true
        return phase
    }
}

/** Refresh only when the system setting or foreground lifecycle changes; no polling clock. */
@Composable
internal fun rememberSkyRenderCapabilities(): SkyRenderCapabilities {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preview = LocalInspectionMode.current
    val activity = remember(context) { context.getSystemService(ActivityManager::class.java) }
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    fun current() = SkyRenderCapabilities(Build.VERSION.SDK_INT,
        lowMemory = activity?.isLowRamDevice == true || (activity?.memoryClass ?: 256) <= 128,
        powerSave = power?.isPowerSaveMode == true,
        animationsEnabled = !preview && ValueAnimator.areAnimatorsEnabled())
    var capabilities by remember(context, preview) { mutableStateOf(current()) }
    if (!preview) DisposableEffect(context, lifecycle) {
        fun refresh() { capabilities = current() }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = refresh()
        }
        val resume = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        refresh()
        lifecycle.addObserver(resume)
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        ContextCompat.registerReceiver(context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            lifecycle.removeObserver(resume)
            context.contentResolver.unregisterContentObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    return capabilities
}
