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

// 主屏空间位置：搜索在左（城市抽屉/添加城市），设置在右上角，实验室在设置更里一层。
internal enum class AppScreen {
    HOME, SEARCH, DAILY_FORECAST, HISTORY, RADAR, TYPHOON, SETTINGS, ATMOSPHERE_LAB
}

internal fun AppScreen.navSlot(): Int = when (this) {
    AppScreen.SEARCH -> 0
    AppScreen.HOME -> 1
    AppScreen.DAILY_FORECAST -> 2
    AppScreen.HISTORY -> 3
    AppScreen.RADAR -> 4
    AppScreen.TYPHOON -> 5
    AppScreen.SETTINGS -> 6
    AppScreen.ATMOSPHERE_LAB -> 7
}

internal fun overlayEnter(screen: AppScreen): EnterTransition {
    val fromRight = screen.navSlot() > AppScreen.HOME.navSlot()
    return fadeIn(tween(160, easing = LinearOutSlowInEasing)) +
        slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { width ->
            val dx = (width * 0.22f).toInt().coerceAtLeast(1)
            if (fromRight) dx else -dx
        }
}

internal fun overlayExit(screen: AppScreen): ExitTransition {
    val toRight = screen.navSlot() > AppScreen.HOME.navSlot()
    return fadeOut(tween(140, easing = FastOutLinearInEasing)) +
        slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { width ->
            val dx = (width * 0.12f).toInt().coerceAtLeast(1)
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
            val dx = (width * 0.12f).toInt().coerceAtLeast(1)
            if (forward) -dx else dx
        }
    return enter togetherWith exit
}
