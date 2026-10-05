package com.zhisheng.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.ui.theme.LocalVistaGlowLevel
import com.zhisheng.weather.ui.theme.LocalVistaGlowPhase
import com.zhisheng.weather.ui.theme.LocalVistaSoftGlow
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import kotlin.math.sin

/** Softly lit weather forms. One native drawing language at both hero and forecast sizes. */
@Composable
internal fun VistaWeatherArtwork(condition: WeatherCondition?, modifier: Modifier, hero: Boolean = false) {
    if (condition == null || condition == WeatherCondition.UNKNOWN) {
        WeatherUnavailableIcon(modifier)
        return
    }
    val light = LocalZhishengPalette.current.isLight
    val ambient = LocalNaturalLight.current
    val clock = LocalVistaGlowPhase.current
    val moving = hero && LocalVistaSoftGlow.current
    val motion = LocalVistaGlowLevel.current.drift
    Canvas(modifier.semantics { contentDescription = condition.label }) {
        // Read the existing lifecycle/reduced-motion aware clock only in the draw phase.
        val lift = if (moving) sin(clock.value * Math.PI * 2).toFloat() * 1.4f * motion else 0f
        drawVistaWeatherArtwork(condition, light, hero, ambient, lift)
    }
}

/** Shared static drawing used by Compose and desktop RemoteViews. */
internal fun DrawScope.drawVistaWeatherArtwork(
    condition: WeatherCondition, light: Boolean, hero: Boolean = false,
    ambient: NaturalLightPalette? = null, lift: Float = 0f,
) {
    val side = size.minDimension
    translate((size.width - side) / 2f, (size.height - side) / 2f) {
            scale(side / 100f, side / 100f, pivot = Offset.Zero) {
                translate(0f, lift) {
                    when (condition) {
                        WeatherCondition.CLEAR -> sunForm(50f, 49f, 29f, hero)
                        WeatherCondition.CLEAR_NIGHT -> {
                            // A crescent contains much less lit area than a full sun disc.
                            // Give it a slightly larger silhouette at forecast size.
                            if (hero) moonForm(true, light)
                            else scale(1.12f, pivot = Offset(50f, 50f)) { moonForm(false, light) }
                        }
                        WeatherCondition.PARTLY_CLOUDY -> {
                            sunForm(66f, 32f, 21f, hero)
                            cloudForm(light, 14f, !hero, ambient)
                        }
                        WeatherCondition.PARTLY_CLOUDY_NIGHT -> {
                            scale(0.72f, pivot = Offset(92f, 5f)) { moonForm(false, light) }
                            cloudForm(light, 14f, !hero, ambient)
                        }
                        WeatherCondition.CLOUDY, WeatherCondition.OVERCAST -> {
                            if (condition == WeatherCondition.OVERCAST) {
                                scale(0.76f, pivot = Offset(84f, -4f)) { cloudForm(light, 0f, !hero, ambient) }
                            }
                            cloudForm(light, 3f, !hero, ambient)
                        }
                        WeatherCondition.WIND -> windForm(light)
                        WeatherCondition.FOG, WeatherCondition.HAZE, WeatherCondition.SAND -> {
                            cloudForm(light, -7f, !hero, ambient)
                            val ink = if (condition == WeatherCondition.SAND) Color(0xFFCBA16C) else Color(0xFF91ABBE)
                            drawLine(ink, Offset(18f, 72f), Offset(78f, 72f), 3f, StrokeCap.Round)
                            drawLine(ink.copy(alpha = 0.65f), Offset(29f, 82f), Offset(86f, 82f), 3f, StrokeCap.Round)
                            if (condition == WeatherCondition.HAZE) drawCircle(ink, 2f, Offset(18f, 82f))
                        }
                        else -> {
                            cloudForm(light, -9f, !hero, ambient)
                            precipitationForm(condition, light)
                        }
                    }
                }
            }
        }
}

private fun DrawScope.sunForm(x: Float, y: Float, radius: Float, halo: Boolean) {
    if (halo) drawCircle(Brush.radialGradient(listOf(Color(0x55FFCE70), Color(0x00FFCE70)),
        center = Offset(x, y), radius = radius * 1.65f), radius * 1.65f, Offset(x, y))
    drawCircle(Brush.radialGradient(
        0f to Color(0xFFFFF1B5), 0.50f to Color(0xFFFFD06B), 0.86f to Color(0xFFF5A946), 1f to Color(0xFFE99236),
        center = Offset(x - radius * 0.35f, y - radius * 0.46f), radius = radius * 1.65f), radius, Offset(x, y))
    drawCircle(Brush.radialGradient(listOf(Color(0xBBFFF6CB), Color(0x00FFF6CB)),
        center = Offset(x - radius * 0.3f, y - radius * 0.4f), radius = radius * 1.15f), radius, Offset(x, y))
}

private fun DrawScope.moonForm(halo: Boolean, light: Boolean) {
    val centre = Offset(50f, 49f)
    if (halo) drawCircle(Brush.radialGradient(listOf(Color(0x506E9FF7), Color(0x006E9FF7)),
        center = centre, radius = 48f), 48f, centre)
    val crescent = Path().apply {
        moveTo(65f, 19f)
        cubicTo(39f, 11f, 18f, 31f, 22f, 55f)
        cubicTo(26f, 82f, 61f, 92f, 80f, 68f)
        cubicTo(57f, 73f, 40f, 60f, 42f, 40f)
        cubicTo(44f, 28f, 53f, 21f, 65f, 19f)
        close()
    }
    drawPath(crescent, Brush.linearGradient(
        listOf(Color(0xFFFFFFFF), Color(0xFFDCEBFF), Color(0xFF8EB4E3)), Offset(23f, 19f), Offset(75f, 85f)))
    drawPath(crescent, if (light && !halo) Color(0xFF789CC8) else Color(0x8895B6DE), style = Stroke(if (light && !halo) 1.2f else 0.6f))
    if (halo) {
        drawCircle(Color(0xDDAFC8EA), 1.3f, Offset(79f, 25f))
        drawCircle(Color(0xAA7D9DCE), 0.9f, Offset(91f, 43f))
        drawCircle(Color(0xAA7D9DCE), 0.8f, Offset(18f, 20f))
    }
}

private fun DrawScope.cloudForm(light: Boolean, y: Float, compact: Boolean, ambient: NaturalLightPalette? = null) {
    val cloud = Path().apply {
        moveTo(24f, 70f + y)
        cubicTo(3f, 70f + y, 1f, 43f + y, 23f, 41f + y)
        cubicTo(25f, 14f + y, 66f, 11f + y, 74f, 40f + y)
        cubicTo(97f, 36f + y, 103f, 69f + y, 79f, 70f + y)
        close()
    }
    val base = if (light && compact) Color(0xFF6F97BA) else if (light) Color(0xFF91B4D3) else Color(0xFF789BBC)
    val bottom = ambient?.let { androidx.compose.ui.graphics.lerp(base, it.particle, 0.18f) } ?: base
    val highlight = ambient?.let { androidx.compose.ui.graphics.lerp(Color.White, it.light, 0.10f) } ?: Color.White
    drawPath(cloud, Brush.linearGradient(
        0f to highlight, 0.40f to Color(0xFFF4FAFF), 0.72f to Color(0xFFD5E6F4), 1f to bottom,
        start = Offset(34f, 20f + y), end = Offset(65f, 79f + y)))
    drawPath(cloud, if (light && compact) Color(0xAA7298B9) else Color(0x4294B4CF), style = Stroke(if (light && compact) 1.1f else 0.55f))
}

private fun DrawScope.precipitationForm(condition: WeatherCondition, light: Boolean) {
    val rain = if (light) Color(0xFF4B98DB) else Color(0xFF8ECDFC)
    fun drop(x: Float, y: Float, length: Float = 10f) =
        drawLine(rain, Offset(x, y), Offset(x - 4f, y + length), 3.5f, StrokeCap.Round)
    fun snow(x: Float, y: Float) {
        drawLine(rain, Offset(x - 4f, y), Offset(x + 4f, y), 1.8f, StrokeCap.Round)
        drawLine(rain, Offset(x - 2f, y - 3.5f), Offset(x + 2f, y + 3.5f), 1.8f, StrokeCap.Round)
        drawLine(rain, Offset(x + 2f, y - 3.5f), Offset(x - 2f, y + 3.5f), 1.8f, StrokeCap.Round)
    }
    when (condition) {
        WeatherCondition.THUNDERSTORM -> drawPath(Path().apply {
            moveTo(52f, 66f); lineTo(39f, 82f); lineTo(50f, 82f); lineTo(44f, 96f)
            lineTo(64f, 75f); lineTo(52f, 75f); close()
        }, Color(0xFFE9AE4D))
        WeatherCondition.HAIL -> { drawCircle(rain, 3.1f, Offset(34f, 77f)); drawCircle(rain, 3.1f, Offset(67f, 84f)) }
        WeatherCondition.SNOW -> { snow(34f, 78f); snow(66f, 84f) }
        WeatherCondition.SLEET -> { drop(36f, 71f); snow(65f, 82f) }
        WeatherCondition.FREEZING_RAIN, WeatherCondition.FREEZING_DRIZZLE -> {
            drop(34f, 71f, if (condition == WeatherCondition.FREEZING_DRIZZLE) 5f else 10f)
            snow(65f, 79f)
            drawLine(rain.copy(alpha = 0.6f), Offset(23f, 93f), Offset(75f, 93f), 2f, StrokeCap.Round)
        }
        WeatherCondition.DRIZZLE -> { drop(38f, 72f, 5f); drop(67f, 78f, 5f) }
        else -> { drop(28f, 70f); drop(51f, 77f); drop(76f, 70f) }
    }
}

private fun DrawScope.windForm(light: Boolean) {
    val ink = if (light) Color(0xFF71A4C9) else Color(0xFF9BC9E9)
    val path = Path().apply {
        moveTo(14f, 38f); lineTo(64f, 38f); cubicTo(85f, 38f, 80f, 12f, 66f, 20f)
        moveTo(9f, 52f); lineTo(87f, 52f)
        moveTo(20f, 67f); lineTo(60f, 67f); cubicTo(78f, 67f, 75f, 89f, 61f, 81f)
    }
    drawPath(path, ink, style = Stroke(4f, cap = StrokeCap.Round))
}
