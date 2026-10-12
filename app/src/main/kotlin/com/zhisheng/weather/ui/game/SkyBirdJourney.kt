package com.zhisheng.weather.ui.game

import kotlin.math.min

internal enum class SkyBirdScenery(val title: String) {
    VALLEY("晴空山谷"), PRAIRIE("金色旷野"), PEAKS("云海群峰"), COAST("暮色海湾"), COSMOS("星河之境")
}
internal enum class SkyBirdMedal(val title: String) {
    NONE("初次出发"), BRONZE("铜羽"), SILVER("银翼"), GOLD("金翼"), CRYSTAL("晶羽"), LEGEND("天际传奇")
}
internal data class SkyBirdCelebration(val threshold: Int, val originY: Float, val age: Float = 0f)

internal object SkyBirdJourney {
    const val CELEBRATION_SECONDS = 1.8f
    fun scenery(score: Int) = SkyBirdScenery.entries[(score.coerceAtLeast(0) / 10) % SkyBirdScenery.entries.size]
    fun medal(score: Int) = SkyBirdMedal.entries[min(score.coerceAtLeast(0) / 10, 5)]
    fun medalTitle(score: Int) = if (score >= 50) "传奇${score / 50}阶" else medal(score).title
    fun particleCount(score: Int) = (12 + (score.coerceAtLeast(0) / 10).coerceAtMost(8) * 6)
}

/** Keep the full physics field visible while allowing obstacles to meet the actual canvas edges. */
internal data class SkyBirdViewport(val scale: Float, val left: Float, val top: Float, val minY: Float, val maxY: Float) {
    companion object {
        fun fit(width: Float, height: Float, worldHeight: Float): SkyBirdViewport {
            val scale = min(width / SkyBirdEngine.WIDTH, height / worldHeight)
            val left = (width - SkyBirdEngine.WIDTH * scale) / 2f
            val top = (height - worldHeight * scale) / 2f
            return SkyBirdViewport(scale, left, top, -top / scale, (height - top) / scale)
        }
    }
}
