package com.zhisheng.weather.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin

// 搜索从城市列表向内进入；返回保留列表。设置、实验室按层级向内进入。
internal enum class AppScreen {
    HOME, SEARCH, DAILY_FORECAST, HISTORY, RADAR, TYPHOON, SETTINGS, ATMOSPHERE_LAB, PRECIPITATION, HOURLY_DETAIL
}

internal fun AppScreen.navSlot(): Int = when (this) {
    AppScreen.SEARCH -> 0
    AppScreen.HOME -> 1
    AppScreen.DAILY_FORECAST -> 2
    AppScreen.PRECIPITATION -> 2
    AppScreen.HOURLY_DETAIL -> 2
    AppScreen.HISTORY -> 3
    AppScreen.RADAR -> 4
    AppScreen.TYPHOON -> 5
    AppScreen.SETTINGS -> 6
    AppScreen.ATMOSPHERE_LAB -> 7
}

internal fun AppScreen.opensFromContent(): Boolean = this in setOf(
    AppScreen.DAILY_FORECAST, AppScreen.PRECIPITATION, AppScreen.HISTORY,
    AppScreen.RADAR, AppScreen.TYPHOON,
)

internal fun overlayEnter(screen: AppScreen, origin: TransformOrigin = TransformOrigin.Center): EnterTransition {
    if (screen.opensFromContent()) return fadeIn(tween(220)) +
        scaleIn(tween(280, easing = FastOutSlowInEasing), initialScale = .9f, transformOrigin = origin)
    val fromRight = screen == AppScreen.SEARCH || screen.navSlot() > AppScreen.HOME.navSlot()
    return fadeIn(tween(160, easing = LinearOutSlowInEasing)) +
        slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { width ->
            val dx = (width * 0.22f).toInt().coerceAtLeast(1)
            if (fromRight) dx else -dx
        }
}

internal fun overlayExit(screen: AppScreen, origin: TransformOrigin = TransformOrigin.Center): ExitTransition {
    if (screen.opensFromContent()) return fadeOut(tween(180)) +
        scaleOut(tween(240, easing = FastOutSlowInEasing), targetScale = .9f, transformOrigin = origin)
    val toRight = screen == AppScreen.SEARCH || screen.navSlot() > AppScreen.HOME.navSlot()
    return fadeOut(tween(140, easing = FastOutLinearInEasing)) +
        slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { width ->
            val dx = (width * 0.22f).toInt().coerceAtLeast(1)
            if (toRight) dx else -dx
        }
}

internal fun screenTransition(initial: AppScreen, target: AppScreen): ContentTransform {
    val forward = target.navSlot() > initial.navSlot()
    val enter = fadeIn(tween(160, easing = LinearOutSlowInEasing)) +
        slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { width ->
            val dx = (width * 0.22f).toInt().coerceAtLeast(1)
            if (forward) dx else -dx
        }
    val exit = fadeOut(tween(140, easing = FastOutLinearInEasing)) +
        slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { width ->
            val dx = (width * 0.22f).toInt().coerceAtLeast(1)
            if (forward) -dx else dx
        }
    return enter togetherWith exit
}
