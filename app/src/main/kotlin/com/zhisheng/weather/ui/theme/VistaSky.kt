package com.zhisheng.weather.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.components.AmbienceKind
import com.zhisheng.weather.ui.components.ambienceKindOf

/** 天空顶色：每种天气一档（浅色模式全为高亮度粉彩，深色文字对比度不破）。
 *  v3 曝光原则：雨雪雹暴的天空比晴天暗——大气浑浊吸光，白色粒子才对比得出。 */
internal fun ambienceSkyTop(kind: AmbienceKind, night: Boolean, light: Boolean): Color = when (kind) {
    AmbienceKind.CLEAR_DAY -> if (light) Color(0xFFD4E8F2) else Color(0xFF183C57)
    AmbienceKind.STARFIELD -> if (light) Color(0xFFDCE5F2) else Color(0xFF142644)
    AmbienceKind.PARTLY_CLOUDY -> if (light) Color(0xFFD6E2EC) else Color(0xFF1B3149)
    AmbienceKind.OVERCAST -> if (light) Color(0xFFC6CFD8) else Color(0xFF1C2B3B)
    AmbienceKind.DRIZZLE -> if (light) Color(0xFFC7D6E2) else Color(0xFF192C40)
    AmbienceKind.RAIN -> if (light) Color(0xFFC2D1DF) else Color(0xFF17293C)
    AmbienceKind.STORM -> if (light) Color(0xFFB7C2D0) else Color(0xFF152235)
    AmbienceKind.SNOW -> if (light) Color(0xFFC9D3DE) else Color(0xFF1E2D46)
    AmbienceKind.SLEET -> if (light) Color(0xFFC4D0DC) else Color(0xFF1C2C43)
    AmbienceKind.HAIL -> if (light) Color(0xFFC2CDD8) else Color(0xFF1B2B40)
    AmbienceKind.FREEZING_RAIN -> if (light) Color(0xFFC5D0DC) else Color(0xFF1B2C42)
    AmbienceKind.FOG -> if (light) Color(0xFFD2D8DA) else Color(0xFF243039)
    AmbienceKind.HAZE -> if (light) Color(0xFFD3D2C4) else Color(0xFF272E2E)
    AmbienceKind.SAND -> if (light) Color(0xFFDCCFAC) else Color(0xFF2F2D20)
    AmbienceKind.WIND -> if (light) Color(0xFFCFDBE2) else Color(0xFF1C2E44)
    AmbienceKind.NONE -> if (light) Color(0xFFDDE7EB) else Color(0xFF21334A)
}

/** A clear sky fading into the reading surface; opaque base prevents stacked grey glows. */
@Composable
internal fun Modifier.vistaSky(weather: WeatherData?, night: Boolean): Modifier {
    if (!isPhosphorVista || !LocalVistaSoftGlow.current || weather?.current?.condition == null) return this
    val light = LocalZhishengPalette.current.isLight
    val phase = LocalVistaGlowPhase.current
    val level = LocalVistaGlowLevel.current
    val kind = ambienceKindOf(weather.current?.condition, night)
    val top = ambienceSkyTop(kind, night, light)
    val middle = if (light) Color(0xFFEAF1F5) else Color(0xFF12243A)
    val base = if (light) Color(0xFFEDF2F5) else Color(0xFF0C1523)
    val glow = when {
        kind == AmbienceKind.CLEAR_DAY -> Color(0xFFFFDDA3)
        kind == AmbienceKind.PARTLY_CLOUDY && !night -> Color(0xFFFFE7BC)
        kind == AmbienceKind.STARFIELD -> Color(0xFFAFBFFC)
        else -> null
    }
    val glowAlpha = when {
        kind == AmbienceKind.CLEAR_DAY -> if (light) 0.30f else 0.16f
        kind == AmbienceKind.PARTLY_CLOUDY -> if (light) 0.20f else 0.12f
        else -> if (light) 0.22f else 0.14f
    }
    return graphicsLayer().drawWithCache {
        val depth = minOf(size.height, (if (light) 800 else 640).dp.toPx())
        val sky = Brush.verticalGradient(0f to top, (if (light) 0.58f else 0.42f) to middle, 1f to base, endY = depth)
        onDrawBehind {
            drawRect(sky)
            if (glow != null) {
                val drift = vistaGlowDrift(phase.value, 0) * level.drift
                val centre = Offset(size.width * (0.84f + drift.x * 0.12f), 155.dp.toPx())
                drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = glowAlpha), glow.copy(alpha = 0f)),
                    center = centre, radius = size.width * 0.7f), size.width * 0.7f, centre)
            }
        }
    }.graphicsLayer()
}
