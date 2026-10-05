package com.zhisheng.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.ui.theme.ZhishengBg
import kotlinx.coroutines.delay

private const val STANDBY_PIXEL_STEP_MS = 15_000L

// Visit every position in a 13 x 8 physical-pixel grid equally. Reserve the
// leftmost column for the return leg so the end also joins the start by one pixel.
// A rounded sine wave repeatedly dwells at the same positions near its extremes.
private val standbyPixelPath: List<IntOffset> = buildList {
    for (row in 0 until 8) {
        val columns = when {
            row == 0 -> -6..6
            row % 2 == 1 -> 6 downTo -5
            else -> -5..6
        }
        for (column in columns) add(IntOffset(column, row - 4))
    }
    for (y in 3 downTo -3) add(IntOffset(-6, y))
}
private val standbyPixelStart = standbyPixelPath.indexOf(IntOffset.Zero)

/** Integer physical pixels keep glyphs sharp; every step, including wrap, is one pixel. */
internal fun standbyBurnInOffset(elapsedMillis: Long): IntOffset {
    val step = elapsedMillis.coerceAtLeast(0L) / STANDBY_PIXEL_STEP_MS
    val index = (standbyPixelStart + step % standbyPixelPath.size).toInt() % standbyPixelPath.size
    return standbyPixelPath[index]
}

@Composable
internal fun StandbyBurnInProtection(content: @Composable () -> Unit) {
    val offset = remember { mutableStateOf(IntOffset.Zero) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val preview = LocalInspectionMode.current
    val protection = if (preview) true else
        SettingsRepository.standbyBurnInProtection.collectAsState(initial = true).value
    LaunchedEffect(lifecycle, preview, protection) {
        if (preview) return@LaunchedEffect
        if (!protection) {
            offset.value = IntOffset.Zero
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Continue from the last drawn position after a pause. Background time
            // must not cause a visible leap across the grid when the screen returns.
            var index = standbyPixelPath.indexOf(offset.value)
            while (true) {
                delay(STANDBY_PIXEL_STEP_MS)
                index = (index + 1) % standbyPixelPath.size
                offset.value = standbyPixelPath[index]
            }
        }
    }
    StandbyBurnInFrame(offset = { offset.value }, content = content)
}

@Composable
internal fun StandbyBurnInFrame(offset: () -> IntOffset, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(ZhishengBg).clipToBounds()) {
        // Read state in the layer, not composition: updating an offset repositions the
        // cached content without re-laying out the weather. Existing safe margins
        // exceed the six-pixel travel; screen dimensions and touch targets are retained.
        Box(Modifier.fillMaxSize().graphicsLayer {
            val position = offset()
            translationX = position.x.toFloat()
            translationY = position.y.toFloat()
        }) { content() }
    }
}
