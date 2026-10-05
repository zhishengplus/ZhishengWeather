package com.zhisheng.weather.ui.theme

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
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
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.geometry.Offset

internal const val VISTA_GLOW_CYCLE_SECONDS = 72.0
internal val LocalVistaGlowPhase = staticCompositionLocalOf<State<Double>> { mutableStateOf(0.0) }
internal val LocalVistaGlowLevel = staticCompositionLocalOf { com.zhisheng.weather.data.SoftGlowLevel.SUBTLE }

/** Periodic with matching velocity at the seam. All surfaces sample this same field. */
internal fun vistaGlowDrift(phase: Double, layer: Int): Offset {
    val angle = phase * 2.0 * PI + layer * 2.1
    return Offset((cos(angle) * 0.16).toFloat(), (sin(angle) * 0.10).toFloat())
}

/** One clock for the entire app; state is read only during drawing, never during layout. */
@Composable
internal fun rememberVistaGlowPhase(enabled: Boolean, cycleSeconds: Double = VISTA_GLOW_CYCLE_SECONDS): State<Double> {
    val clock = remember { mutableStateOf(0.0) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preview = LocalInspectionMode.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var motionAllowed by remember { mutableStateOf(false) }
    if (enabled && !preview) {
        DisposableEffect(context, lifecycle) {
            fun refresh() { motionAllowed = ValueAnimator.areAnimatorsEnabled() && power?.isPowerSaveMode != true }
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
    }
    LaunchedEffect(enabled, preview, motionAllowed, lifecycle, cycleSeconds) {
        if (!enabled || preview || !motionAllowed) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (isActive) {
                // Slow atmospheric motion must not request every display vsync on 120Hz screens.
                // Gesture/transition clocks remain independent and can use the full refresh rate.
                delay(30)
                withFrameNanos { frame ->
                    if (last == 0L) last = frame
                    clock.value = (clock.value + (frame - last) / 1_000_000_000.0 / cycleSeconds) % 1.0
                    last = frame
                }
            }
        }
    }
    return clock
}
