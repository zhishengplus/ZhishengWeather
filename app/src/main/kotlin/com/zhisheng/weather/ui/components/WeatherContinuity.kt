@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.zhisheng.weather.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.zhisheng.weather.ui.theme.isPhosphorVista

internal data class WeatherContinuity(
    val shared: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
)

internal val LocalWeatherContinuity = staticCompositionLocalOf<WeatherContinuity?> { null }

/** Only real, matching weather content moves between orientations. */
@Composable
internal fun Modifier.weatherSharedBounds(key: String): Modifier {
    val continuity = LocalWeatherContinuity.current
    if (continuity == null || !isPhosphorVista) return this
    return with(continuity.shared) {
        this@weatherSharedBounds.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = continuity.visibility,
            boundsTransform = { _, _ -> tween(420, easing = FastOutSlowInEasing) },
        )
    }
}
