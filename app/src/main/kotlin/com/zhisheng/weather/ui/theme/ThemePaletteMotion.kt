package com.zhisheng.weather.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalInspectionMode

/** Retain the current painted palette when a second theme choice interrupts the first. */
@Composable
internal fun rememberThemePalette(target: ZhishengPalette): ZhishengPalette {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    val preview = LocalInspectionMode.current
    LaunchedEffect(target) {
        if (target == to) return@LaunchedEffect
        from = from.mix(to, progress.value)
        to = target
        progress.snapTo(0f)
        if (preview || !ValueAnimator.areAnimatorsEnabled()) progress.snapTo(1f)
        else progress.animateTo(1f, tween(260))
    }
    return from.mix(to, progress.value)
}

private fun ZhishengPalette.mix(other: ZhishengPalette, fraction: Float) = ZhishengPalette(
    isLight = if (fraction < .5f) isLight else other.isLight,
    bg = lerp(bg, other.bg, fraction), surface = lerp(surface, other.surface, fraction),
    card = lerp(card, other.card, fraction), cardBorder = lerp(cardBorder, other.cardBorder, fraction),
    mint = lerp(mint, other.mint, fraction), orange = lerp(orange, other.orange, fraction),
    cyan = lerp(cyan, other.cyan, fraction), red = lerp(red, other.red, fraction),
    warning = lerp(warning, other.warning, fraction), text = lerp(text, other.text, fraction),
    textSecondary = lerp(textSecondary, other.textSecondary, fraction),
    textTertiary = lerp(textTertiary, other.textTertiary, fraction),
)
