package com.zhisheng.weather.ui.home

internal fun isHorizontalCitySwipe(dx: Float, dy: Float, threshold: Float): Boolean =
    dx.isFinite() && dy.isFinite() && kotlin.math.abs(dx) >= threshold &&
        kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.5f

internal fun citySwipeTarget(keys: List<String>, selectedKey: String?, dx: Float): String? {
    if (!dx.isFinite() || dx == 0f) return null
    val index = keys.indexOf(selectedKey)
    if (index < 0) return null
    return keys.getOrNull(index + if (dx < 0f) 1 else -1)
}
