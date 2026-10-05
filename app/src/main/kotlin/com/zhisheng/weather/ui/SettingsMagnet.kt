package com.zhisheng.weather.ui

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Positions and velocity are measured in tabs, independent of density. */
internal fun resistedTabPosition(position: Float, lastIndex: Int): Float {
    if (!position.isFinite() || lastIndex <= 0) return 0f
    val boundary = position.coerceIn(0f, lastIndex.toFloat())
    val excess = position - boundary
    return boundary + excess / (1f + abs(excess) * 6f) * 0.42f
}

internal fun magneticTabTarget(position: Float, velocity: Float, lastIndex: Int): Int {
    if (!position.isFinite() || lastIndex <= 0) return 0
    val bounded = position.coerceIn(0f, lastIndex.toFloat())
    val safeVelocity = velocity.takeIf { it.isFinite() } ?: 0f
    // A flick advances at most one neighbouring stop; it cannot fly across the island.
    val target = when {
        safeVelocity > 1.8f -> floor(bounded + 0.15f).toInt() + 1
        safeVelocity < -1.8f -> ceil(bounded - 0.15f).toInt() - 1
        else -> bounded.roundToInt()
    }
    return target.coerceIn(0, lastIndex)
}
