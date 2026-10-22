package com.zhisheng.weather.ui.components

import com.zhisheng.weather.model.WeatherCondition

internal const val RAIN_SPLASH_SECONDS = .18f

internal data class RainLandingPlane(
    val cityKey: String?, val condition: WeatherCondition?,
    val left: Float, val right: Float, val top: Float,
) {
    val freezing: Boolean get() = condition == WeatherCondition.FREEZING_RAIN ||
        condition == WeatherCondition.FREEZING_DRIZZLE
}

private val LIQUID_RAIN_CONDITIONS = setOf(
    WeatherCondition.RAIN, WeatherCondition.DRIZZLE, WeatherCondition.THUNDERSTORM,
    WeatherCondition.SLEET, WeatherCondition.FREEZING_RAIN, WeatherCondition.FREEZING_DRIZZLE,
)

internal fun hasLiquidRain(condition: WeatherCondition?): Boolean = condition in LIQUID_RAIN_CONDITIONS

/** Contact planes are real, visible raining-city edges; missing data never fabricates one. */
internal fun visibleRainLandingPlanes(planes: List<RainLandingPlane>, width: Float,
    height: Float, minimumTop: Float): List<RainLandingPlane> = planes.mapNotNull { plane ->
    if (!hasLiquidRain(plane.condition) || !plane.left.isFinite() || !plane.right.isFinite() ||
        !plane.top.isFinite() || plane.top !in minimumTop..height ||
        !width.isFinite() || width <= 0f || !height.isFinite()) return@mapNotNull null
    val left = plane.left.coerceAtLeast(0f)
    val right = plane.right.coerceAtMost(width)
    if (right <= left) null else plane.copy(left = left, right = right)
}

/** Overlapping surfaces use the first actual upper edge a drop encounters. */
internal fun rainLandingContact(planes: List<RainLandingPlane>, x: Float): RainLandingPlane? {
    if (!x.isFinite()) return null
    var contact: RainLandingPlane? = null
    for (plane in planes) if (x >= plane.left && x < plane.right &&
        (contact == null || plane.top < contact.top)) contact = plane
    return contact
}

internal fun rainLandingParticleBudget(intensity: Float, drizzle: Boolean, gain: Float, qualityScale: Float): Int {
    if (!intensity.isFinite() || !gain.isFinite() || !qualityScale.isFinite()) return 0
    return ((46f + intensity.coerceIn(0f, 1f) * 60f) * (.65f + gain.coerceIn(0f, 1f) * .35f) *
        qualityScale.coerceIn(0f, 1f) * if (drizzle) .65f else 1f).toInt().coerceIn(0, 106)
}

internal data class RainSplashBead(val xDp: Float, val yDp: Float, val radiusDp: Float, val opacity: Float)

/** Short ballistic arcs above the edge, not rings or a second free-running particle system. */
internal fun rainSplashBead(index: Int, bead: Int, seconds: Float, wind: Float): RainSplashBead? {
    if (!seconds.isFinite() || !wind.isFinite() || index < 0 || bead !in 0..2 ||
        seconds < 0f || seconds >= RAIN_SPLASH_SECONDS) return null
    val seed = rainSeed(index, 60 + bead)
    val vx = (seed - .5f) * 112f + wind.coerceIn(0f, 1f) * -12f
    val vy = 52f + rainSeed(index, 70 + bead) * 36f
    val y = -vy * seconds + 420f * seconds * seconds
    if (y >= 0f && seconds > 0f) return null
    val age = seconds / RAIN_SPLASH_SECONDS
    return RainSplashBead(vx * seconds, y, .42f + seed * .45f, (1f - age) * (1f - age))
}
