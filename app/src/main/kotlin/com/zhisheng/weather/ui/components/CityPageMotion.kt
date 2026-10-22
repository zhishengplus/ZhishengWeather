package com.zhisheng.weather.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import com.zhisheng.weather.ui.theme.LocalVistaGlowPhase

/** Prepared pages retain their layout and data without subscribing to the frame clock. */
internal enum class CityPageActivity { ACTIVE, PREPARED, STOPPED }

/** Dragging pages retain their drawings; only the settled page starts ambient animation. */
internal fun cityPageActivity(
    index: Int,
    visibleFrom: Int,
    visibleTo: Int,
    settled: Int,
    moving: Boolean,
    enabled: Boolean,
): CityPageActivity {
    if (!enabled || index < 0) return CityPageActivity.STOPPED
    val first: Int
    val last: Int
    if (moving && maxOf(visibleFrom, visibleTo) >= 0) {
        first = minOf(visibleFrom, visibleTo).coerceAtLeast(0)
        last = maxOf(visibleFrom, visibleTo)
    } else {
        if (settled < 0) return CityPageActivity.STOPPED
        first = settled
        last = settled
    }
    return when {
        index in first..last -> if (moving) CityPageActivity.PREPARED else CityPageActivity.ACTIVE
        index.toLong() == first.toLong() - 1L || index.toLong() == last.toLong() + 1L ->
            CityPageActivity.PREPARED
        else -> CityPageActivity.STOPPED
    }
}

internal val LocalCityPageMotionRunning = compositionLocalOf { true }

/**
 * A page shares the app clock while visible and holds its last drawn phase otherwise.
 * A fresh State facade on each activation also invalidates draw consumers once on resume.
 */
@Composable
internal fun ProvideCityPageMotion(running: Boolean, content: @Composable () -> Unit) {
    val enabled = running && LocalCityPageMotionRunning.current
    val source = LocalVistaGlowPhase.current
    val continuity = remember { CityGlowContinuity() }
    val phase = remember(source, enabled) { CityPageGlowPhase(source, enabled, continuity) }
    CompositionLocalProvider(
        LocalCityPageMotionRunning provides enabled,
        LocalVistaGlowPhase provides phase,
        content = content,
    )
}

/** Plain draw-owned values: sampling never writes snapshot state or recomposes the page. */
internal class CityGlowContinuity {
    var phase: Double = 0.0
    var sampled: Boolean = false
}

internal class CityPageGlowPhase(
    private val source: State<Double>,
    private val running: Boolean,
    private val continuity: CityGlowContinuity,
) : State<Double> {
    private var previous: Double? = null

    override val value: Double
        get() {
            // Deliberately do not read source.value here: a hidden page must unsubscribe.
            if (!running) return continuity.phase
            val raw = source.value
            if (!raw.isFinite()) return continuity.phase
            val current = unitPhase(raw)
            if (!continuity.sampled) {
                continuity.phase = current
                continuity.sampled = true
            } else {
                previous?.let { last ->
                    continuity.phase = unitPhase(continuity.phase + unitPhase(current - last))
                }
            }
            previous = current
            return continuity.phase
        }

    private fun unitPhase(value: Double): Double = ((value % 1.0) + 1.0) % 1.0
}
