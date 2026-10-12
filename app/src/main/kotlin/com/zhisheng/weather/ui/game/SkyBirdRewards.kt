package com.zhisheng.weather.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

/** Cosmetic rewards share the flight clock, so pause and closing never leave an animation running. */
internal fun DrawScope.drawSkyBirdRewards(state: SkyBirdState) {
    val level = (state.score / 10).coerceIn(0, 8)
    if (level == 0) return
    val colors = when (SkyBirdJourney.medal(state.score)) {
        SkyBirdMedal.BRONZE -> listOf(Color(0xFFE6B787), Color(0xFFF9D9AA))
        SkyBirdMedal.SILVER -> listOf(Color(0xFFBED7EA), Color(0xFFEFF8F8))
        SkyBirdMedal.GOLD -> listOf(Color(0xFFF2C46F), Color(0xFFFFEBC4))
        SkyBirdMedal.CRYSTAL -> listOf(Color(0xFFAFE5D9), Color(0xFFC9B4ED), Color(0xFFE6F4F8))
        else -> listOf(Color(0xFFF6CD82), Color(0xFFE8A7C1), Color(0xFFBBB4EF), Color(0xFFA5DED1))
    }
    val length = 24f + level * 8f
    if (level >= 3) {
        val ribbon = Path().apply {
            moveTo(SkyBirdEngine.BIRD_X - length, state.birdY + 8f)
            cubicTo(SkyBirdEngine.BIRD_X - length * .7f, state.birdY - 9f,
                SkyBirdEngine.BIRD_X - length * .3f, state.birdY + 6f, SkyBirdEngine.BIRD_X - 12f, state.birdY + 2f)
        }
        drawPath(ribbon, Brush.horizontalGradient(listOf(Color.Transparent) + colors.map { it.copy(alpha = .4f) },
            SkyBirdEngine.BIRD_X - length, SkyBirdEngine.BIRD_X), style = Stroke(2f + level * .28f))
    }
    repeat(2 + level) { i ->
        val x = SkyBirdEngine.BIRD_X - 18f - i * length / (level + 2)
        val y = state.birdY + sin(state.distance * .045f + i * .8f) * (3f + level * .6f)
        star(Offset(x, y), 1.3f + level * .18f, colors[i % colors.size].copy(alpha = .6f * (1f - i.toFloat() / (level + 3))))
    }
    val reward = state.celebration ?: return
    if (state.phase != SkyBirdPhase.FLYING) return
    val t = (reward.age / SkyBirdJourney.CELEBRATION_SECONDS).coerceIn(0f, 1f)
    val origin = Offset(SkyBirdEngine.BIRD_X + 18f, reward.originY)
    val count = SkyBirdJourney.particleCount(reward.threshold)
    repeat(count) { i ->
        val angle = i * 2.39996f
        val distance = (14f + (i * 37 % 67)) * t
        val at = origin + Offset(cos(angle) * distance, sin(angle) * distance + 26f * t * t)
        star(at, (2f + i % 3) * (1f - t * .5f), colors[i % colors.size].copy(alpha = (1f - t) * .88f))
    }
    if (level >= 4) {
        drawCircle(colors.first().copy(alpha = (1f - t) * .45f), 10f + t * 65f, origin, style = Stroke(1.5f))
        if (level >= 6) drawCircle(colors.last().copy(alpha = (1f - t) * .30f), 8f + t * 90f, origin, style = Stroke(1f))
    }
}

private fun DrawScope.star(center: Offset, radius: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius * .32f, center.y - radius * .32f)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + radius * .32f, center.y + radius * .32f)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius * .32f, center.y + radius * .32f)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - radius * .32f, center.y - radius * .32f); close()
    }
    drawPath(p, color)
}
