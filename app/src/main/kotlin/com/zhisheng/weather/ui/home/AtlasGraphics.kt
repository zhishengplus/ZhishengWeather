package com.zhisheng.weather.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.Astronomy
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sqrt

/** The atlas charts encode data, not decorative weather scenes. No fabricated radar or star maps. */
internal data class AtlasSunPoint(val time: Long, val altitude: Double)

internal fun atlasSunPoints(city: City, start: Long, end: Long): List<AtlasSunPoint> {
    if (end <= start || !city.latitude.isFinite() || !city.longitude.isFinite() ||
        city.latitude !in -90.0..90.0 || city.longitude !in -180.0..180.0) return emptyList()
    return (0..48).map { index ->
        val time = start + (end - start) * index / 48
        AtlasSunPoint(time, Astronomy.sunAltitudeDegrees(time, city.latitude, city.longitude))
    }
}

internal fun atlasTimeFraction(time: Long, start: Long, end: Long): Float =
    if (end <= start) 0f else ((time - start).toDouble() / (end - start)).toFloat().coerceIn(0f, 1f)

internal fun atlasClock(time: Long, zone: String): String = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    .format(Instant.ofEpochMilli(time).atZone(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of("UTC"))))

internal fun atlasTideEndLabel(start: Long, end: Long, zone: String): String {
    val tz = runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of("UTC"))
    val startDate = Instant.ofEpochMilli(start).atZone(tz).toLocalDate()
    val endDate = Instant.ofEpochMilli(end).atZone(tz).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate)
    return when (days) {
        0L -> atlasClock(end, zone)
        1L -> "次日 ${atlasClock(end, zone)}"
        else -> "${endDate.monthValue}/${endDate.dayOfMonth} ${atlasClock(end, zone)}"
    }
}

internal fun atlasNightBounds(start: Long, zone: String): Pair<Long, Long> {
    val time = Instant.ofEpochMilli(start).atZone(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of("UTC")))
    val date = time.toLocalDate().minusDays(if (time.hour < 12) 1 else 0)
    return date.atTime(18, 0).atZone(time.zone).toInstant().toEpochMilli() to
        date.plusDays(1).atTime(12, 0).atZone(time.zone).toInstant().toEpochMilli()
}

@Composable
internal fun AtlasSunChart(city: City?, glow: GlowForecast?, zone: String, height: Dp = 92.dp, selectedTime: Long? = null) {
    val palette = LocalZhishengPalette.current
    val points = remember(city?.locationKey, city?.latitude, city?.longitude, glow) {
        if (city == null || glow == null) emptyList() else
            atlasSunPoints(city, glow.startMillis - 90 * 60_000L, glow.endMillis + 90 * 60_000L)
    }
    if (points.isEmpty()) {
        AtlasUnavailableGraphic("日出日落数据暂缺", height)
        return
    }
    val min = minOf(-8.0, points.minOf { it.altitude })
    val max = maxOf(8.0, points.maxOf { it.altitude })
    Column {
        Canvas(Modifier.fillMaxWidth().height(height).semantics {
            contentDescription = "太阳高度变化图，橙色区域是霞光参考窗口；曲线不是火烧云概率"
        }) {
            val top = 6.dp.toPx(); val bottom = size.height - 6.dp.toPx()
            fun y(alt: Double) = bottom - ((alt - min) / (max - min)).toFloat() * (bottom - top)
            fun x(time: Long) = size.width * atlasTimeFraction(time, points.first().time, points.last().time)
            val horizon = y(0.0)
            drawRect(palette.cyan.copy(alpha = .035f), Offset(0f, horizon), Size(size.width, (bottom - horizon).coerceAtLeast(0f)))
            for (i in 0..4) {
                val xx = size.width * i / 4f
                drawLine(palette.cardBorder, Offset(xx, top), Offset(xx, bottom), 1.dp.toPx())
            }
            val left = x(glow!!.startMillis); val right = x(glow.endMillis)
            drawRect(palette.orange.copy(alpha = .13f), Offset(left, top), Size(right - left, bottom - top))
            drawLine(palette.textTertiary, Offset(0f, horizon), Offset(size.width, horizon), 1.dp.toPx())
            val path = Path().apply {
                points.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.time), y(p.altitude)) else lineTo(x(p.time), y(p.altitude)) }
            }
            drawPath(path, palette.orange, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            selectedTime?.let { time ->
                drawLine(palette.textSecondary, Offset(x(time), top), Offset(x(time), bottom), 1.dp.toPx())
                val selected = points.minByOrNull { kotlin.math.abs(it.time - time) }!!
                drawCircle(palette.text, 4.dp.toPx(), Offset(x(selected.time), y(selected.altitude)))
            }
            points.minByOrNull { kotlin.math.abs(it.altitude) }?.let {
                drawCircle(palette.card, 7.dp.toPx(), Offset(x(it.time), y(it.altitude)))
                drawCircle(palette.orange, 5.dp.toPx(), Offset(x(it.time), y(it.altitude)))
            }
        }
        AtlasAxis(atlasClock(points.first().time, zone), "地平线 0°", atlasClock(points.last().time, zone))
    }
}

@Composable
internal fun AtlasMoon(fraction: Double?, modifier: Modifier = Modifier) {
    val palette = LocalZhishengPalette.current
    Canvas(modifier.semantics { contentDescription = fraction?.let { "月面照明约 ${(it * 100).toInt()}%" } ?: "月光数据暂缺" }) {
        val radius = size.minDimension * .40f
        drawCircle(if (palette.isLight) palette.textSecondary else palette.cardBorder, radius, center)
        if (fraction != null && fraction.isFinite()) {
            // A neutral illuminated disc: no invented waxing/waning orientation or constellation position.
            val illumination = fraction.coerceIn(0.0, 1.0).toFloat()
            val step = 1.dp.toPx()
            var yy = -radius
            while (yy <= radius) {
                val limb = sqrt((radius * radius - yy * yy).coerceAtLeast(0f))
                drawLine(if (palette.isLight) palette.surface else palette.text, Offset(center.x + (1 - 2 * illumination) * limb, center.y + yy),
                    Offset(center.x + limb, center.y + yy), step)
                yy += step
            }
        }
        drawCircle(palette.textTertiary, radius, center, style = Stroke(1.dp.toPx()))
    }
}

@Composable
internal fun AtlasNightBar(stars: StarForecast?, zone: String) {
    if (stars == null) return
    val palette = LocalZhishengPalette.current
    val (localStart, end) = remember(stars.startMillis, zone) { atlasNightBounds(stars.startMillis, zone) }
    Canvas(Modifier.fillMaxWidth().height(18.dp).semantics { contentDescription = "本夜较佳拍摄窗口 ${sceneWindow(stars.startMillis, stars.endMillis, zone)}" }) {
        drawRect(palette.textTertiary.copy(alpha = .14f), Offset(0f, 4.dp.toPx()), Size(size.width, 10.dp.toPx()))
        val left = atlasTimeFraction(stars.startMillis, localStart, end) * size.width
        val right = atlasTimeFraction(stars.endMillis, localStart, end) * size.width
        drawRect(palette.cyan, Offset(left, 4.dp.toPx()), Size((right - left).coerceAtLeast(1f), 10.dp.toPx()))
        for (i in 0..6) drawLine(palette.card, Offset(size.width * i / 6, 4.dp.toPx()), Offset(size.width * i / 6, 14.dp.toPx()), 1.dp.toPx())
    }
    AtlasAxis(atlasClock(localStart, zone), "拍摄窗口", atlasClock(end, zone))
}

@Composable
internal fun AtlasTideChart(coast: CoastalForecast?, height: Dp = 76.dp, selected: MarinePoint? = null) {
    val palette = LocalZhishengPalette.current
    val points = remember(coast?.points) { coastalDisplayPoints(coast?.points.orEmpty()) }
    val segments = remember(points) { tideSegments(points) }
    val values = points.mapNotNull { it.seaLevelM?.takeIf(Double::isFinite) }
    if (values.isEmpty()) {
        AtlasUnavailableGraphic("暂无有效潮位曲线", height)
        return
    }
    val low = values.min() - .08; val high = values.max() + .08
    val first = points.first().timeMillis; val last = points.last().timeMillis
    Column {
        Canvas(Modifier.fillMaxWidth().height(height).semantics {
            contentDescription = "模型潮位曲线，${values.min()}至${values.max()}米；缺测断线，非测站潮汐"
        }) {
            fun at(p: MarinePoint) = Offset(size.width * atlasTimeFraction(p.timeMillis, first, last),
                size.height * (1 - ((p.seaLevelM!! - low) / (high - low)).toFloat()))
            for (i in 1..3) drawLine(palette.cardBorder, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4), 1.dp.toPx())
            points.filter { it.seaLevelM?.isFinite() == true }.forEach { drawCircle(palette.cyan, 2.dp.toPx(), at(it)) }
            segments.forEach { (a, b) ->
                val aa = at(a); val bb = at(b)
                val fill = Path().apply { moveTo(aa.x, size.height); lineTo(aa.x, aa.y); lineTo(bb.x, bb.y); lineTo(bb.x, size.height); close() }
                drawPath(fill, palette.cyan.copy(alpha = .10f))
                drawLine(palette.cyan, aa, bb, 2.dp.toPx(), StrokeCap.Round)
            }
            selected?.let {
                val xx = size.width * atlasTimeFraction(it.timeMillis, first, last)
                drawLine(palette.textSecondary, Offset(xx, 0f), Offset(xx, size.height), 1.dp.toPx())
                if (it.seaLevelM?.isFinite() == true) drawCircle(palette.text, 4.dp.toPx(), at(it))
            }
            listOfNotNull(coast?.nextHigh, coast?.nextLow).filter { it.timeMillis in first..last }.forEach { turning ->
                val point = at(MarinePoint(turning.timeMillis, turning.heightM))
                drawCircle(palette.card, 5.dp.toPx(), point)
                drawCircle(palette.orange, 3.dp.toPx(), point)
            }
        }
        AtlasAxis(atlasClock(first, coast!!.zoneId), "模型潮位 / m", atlasTideEndLabel(first, last, coast.zoneId))
    }
}

@Composable
internal fun AtlasUnavailableGraphic(label: String, height: Dp = 76.dp) {
    val palette = LocalZhishengPalette.current
    Column {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            for (i in 1..3) drawLine(palette.cardBorder, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4), 1.dp.toPx())
            drawLine(palette.textTertiary, Offset(size.width * .44f, size.height / 2), Offset(size.width * .56f, size.height / 2), 2.dp.toPx())
        }
        Text(label, color = palette.textSecondary, style = MaterialTheme.typography.bodySmall, fontFamily = ZhishengReading)
    }
}

@Composable
internal fun AtlasAxis(left: String, middle: String, right: String) {
    // No subcomposition here: these graphs also live in intrinsically equal-height home rows.
    Row(Modifier.fillMaxWidth()) {
        Text(left, Modifier.weight(3f), color = ZhishengTextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        Text(middle, Modifier.weight(4f), color = ZhishengTextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
        Text(right, Modifier.weight(3f), color = ZhishengTextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.End)
    }
}
