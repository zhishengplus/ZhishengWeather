package com.zhisheng.weather.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Preview-only clock. The production surface always uses the real clock. */
internal val LocalWeatherPreviewTime = staticCompositionLocalOf<Long?> { null }

// Provide one state object rather than changing the local itself every minute:
// only consumers that read the clock are invalidated, including skippable children.
private val LocalWeatherPresentationClock = staticCompositionLocalOf<State<Long>?> { null }

@Composable
internal fun ProvideWeatherPresentationClock(content: @Composable () -> Unit) {
    if (LocalWeatherPresentationClock.current != null ||
        LocalWeatherPreviewTime.current != null || LocalInspectionMode.current
    ) {
        content()
        return
    }
    val clock = remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val now = System.currentTimeMillis()
                clock.longValue = now
                // Align with wall-clock minutes so hour/day labels roll over on time.
                delay(60_000L - Math.floorMod(now, 60_000L))
            }
        }
    }
    CompositionLocalProvider(LocalWeatherPresentationClock provides clock, content = content)
}

@Composable
internal fun weatherPresentationTime(): Long = LocalWeatherPreviewTime.current
    ?: LocalWeatherPresentationClock.current?.value
    ?: System.currentTimeMillis()
