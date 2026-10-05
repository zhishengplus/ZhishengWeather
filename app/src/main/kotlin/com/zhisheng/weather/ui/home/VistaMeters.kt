package com.zhisheng.weather.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengWarning

internal fun vistaMeterFraction(en: String, cur: CurrentWeather): Double? = when (en) {
    "HUMIDITY" -> cur.humidity?.takeIf { it.isFinite() && it in 0.0..100.0 }?.div(100.0)
    "CLOUD" -> cur.cloudCover?.takeIf { it.isFinite() && it in 0.0..100.0 }?.div(100.0)
    "UV" -> cur.uvIndex?.takeIf { it >= 0 }?.div(11.0)?.coerceAtMost(1.0)
    else -> null
}

internal fun vistaHumidityCaption(percent: Double): String = when {
    percent < 30 -> "空气偏干"
    percent < 60 -> "湿度适宜"
    percent < 80 -> "空气偏湿"
    else -> "湿度很高"
}

internal fun vistaCloudCaption(percent: Double): String = when {
    percent < 20 -> "天空较晴"
    percent < 60 -> "云量适中"
    else -> "云层较厚"
}

internal fun vistaUvCaption(index: Int): String = when {
    index <= 2 -> "较弱，可正常外出"
    index <= 5 -> "中等，外出宜防晒"
    index <= 7 -> "较强，注意防护"
    index <= 10 -> "很强，减少暴晒"
    else -> "极强，避免长时间户外"
}

internal fun vistaEnergySegments(fraction: Double, count: Int = 16): Int =
    (count * fraction.coerceIn(0.0, 1.0)).toInt().coerceIn(0, count)

@Composable
internal fun VistaVerticalBlocks(fraction: Double, accent: Color, track: Color) {
    Canvas(Modifier.size(width = 6.dp, height = 22.dp)) {
        val count = 5
        val gap = 2.dp.toPx()
        val blockHeight = (size.height - gap * (count - 1)) / count
        val filled = fraction.coerceIn(0.0, 1.0).toFloat() * count
        repeat(count) { index ->
            val alpha = (filled - index).coerceIn(0f, 1f)
            val color = if (alpha == 0f) track.copy(alpha = 0.6f) else accent.copy(alpha = 0.35f + 0.65f * alpha)
            drawRoundRect(color, Offset(0f, size.height - blockHeight - index * (blockHeight + gap)),
                Size(size.width, blockHeight), CornerRadius(0.8.dp.toPx()))
        }
    }
}

@Composable
internal fun VistaSignalStack(
    fraction: Double,
    accent: Color,
    track: Color,
    modifier: Modifier = Modifier,
) {
    val tracks = 3
    val segments = 6
    Canvas(
        modifier
            .size(22.dp)
            .semantics { contentDescription = "读数约 ${vistaEnergySegments(fraction, 100)}%" },
    ) {
        val bar = 3.dp.toPx()
        val gapY = 2.5.dp.toPx()
        val gapX = 1.4.dp.toPx()
        val totalH = tracks * bar + (tracks - 1) * gapY
        val startY = ((size.height - totalH) / 2f).coerceAtLeast(0f)
        val totalGapX = gapX * (segments - 1)
        val width = ((size.width - totalGapX) / segments).coerceAtLeast(1f)
        val radius = CornerRadius(bar / 2f)
        val filled = segments * fraction.coerceIn(0.0, 1.0).toFloat()
        repeat(tracks) { row ->
            val y = startY + row * (bar + gapY)
            repeat(segments) { index ->
                val on = (filled - index).coerceIn(0f, 1f)
                val color = if (on <= 0f) track else accent.copy(alpha = 0.28f + 0.72f * on)
                drawRoundRect(color, Offset(index * (width + gapX), y), Size(width, bar), radius)
            }
        }
    }
}

@Composable
internal fun vistaMeterAccent(en: String, cur: CurrentWeather): Color = when (en) {
    "UV" -> when (cur.uvIndex ?: 0) {
        in 0..2 -> ZhishengMint
        in 3..5 -> ZhishengWarning
        else -> ZhishengOrange
    }
    else -> ZhishengCyan
}

/** 经典终端的读数条：一排方形刻度，与 VistaSignalStack 同一分数语义，读数更直观。 */
@Composable
internal fun ClassicMeterBar(
    fraction: Double,
    accent: Color,
    track: Color,
    modifier: Modifier = Modifier,
) {
    val segments = 10
    Canvas(
        modifier
            .size(width = 58.dp, height = 6.dp)
            .semantics { contentDescription = "读数约 ${vistaEnergySegments(fraction, 100)}%" },
    ) {
        val gap = 1.6.dp.toPx()
        val segWidth = ((size.width - gap * (segments - 1)) / segments).coerceAtLeast(1f)
        val filled = segments * fraction.coerceIn(0.0, 1.0).toFloat()
        repeat(segments) { index ->
            val on = (filled - index).coerceIn(0f, 1f)
            val color = if (on <= 0f) track else accent.copy(alpha = 0.30f + 0.70f * on)
            drawRect(color, Offset(index * (segWidth + gap), 0f), Size(segWidth, size.height))
        }
    }
}
