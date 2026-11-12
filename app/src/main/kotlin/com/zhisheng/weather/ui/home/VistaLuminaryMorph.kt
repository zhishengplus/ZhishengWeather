package com.zhisheng.weather.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.MoonCalc
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.components.clockMinutes
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.weatherPresentationTime
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val sunGlowRadius = 14.dp

/** Measurement-owned coordinates. The Canvas reads these after layout, without a state/remeasure loop. */
private class LuminaryPlotMetrics {
    var sunHorizon = 0f
    var moonHorizon = 0f
    var arcHorizon = 0f
    var arcRise = 0f
}

/** One persistent plot: only point coordinates and opacity change, never the card's measured height. */
@Composable
internal fun VistaLuminaryMotionBody(today: DailyWeather, utcOffsetSeconds: Int?, progress: () -> Float) {
    val palette = LocalZhishengPalette.current
    val nowMillis = weatherPresentationTime()
    val now = vistaNowMinutes(nowMillis, utcOffsetSeconds)
    val rise = clockMinutes(today.sunrise)
    val set = clockMinutes(today.sunset)
    val moonRise = clockMinutes(today.moonrise)
    val moonSet = clockMinutes(today.moonset)
    val status = vistaSunStatus(now, rise, set)
    val daylight = if (rise != null && set != null && set > rise) set - rise else null
    val illumination = MoonCalc.illuminationFraction(nowMillis).coerceIn(0.0, 1.0)
    val sunAbove = remember(rise, set) { spans(rise, set, true) }
    val sunBelow = remember(rise, set) { spans(rise, set, false) }
    val moonAbove = remember(moonRise, moonSet) { spans(moonRise, moonSet, true) }
    val moonBelow = remember(moonRise, moonSet) { spans(moonRise, moonSet, false) }
    val metrics = remember { LuminaryPlotMetrics() }
    val showDetails by remember(progress) { derivedStateOf { luminaryDetailsAlpha(progress()) > 0f } }
    val detailModifier = Modifier.graphicsLayer { alpha = luminaryDetailsAlpha(progress()) }
        .then(if (showDetails) Modifier else Modifier.clearAndSetSemantics {})
    val largeType = LocalDensity.current.fontScale > 1.3f

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Layout(content = {
            Canvas(Modifier) {
                val p = progress().coerceIn(0f, 1f)
                val rx = (size.width / 2f - 18.dp.toPx()).coerceAtLeast(0f)
                val sunTrack = LuminaryMorphTrack(size.width, metrics.sunHorizon, metrics.arcHorizon,
                    15.dp.toPx(), rx, metrics.arcRise, 8.dp.toPx())
                val moonTrack = sunTrack.copy(waveHorizon = metrics.moonHorizon,
                    arcRadiusX = rx * 0.64f, arcRadiusY = metrics.arcRise * 0.64f)
                drawLuminaryMorph(sunTrack, rise, set, now, sunAbove, sunBelow, p, palette.orange, palette.cardBorder, true)
                drawLuminaryMorph(moonTrack, moonRise, moonSet, now, moonAbove, moonBelow, p, palette.cyan, palette.cardBorder, false)
            }
            LuminaryMotionCaption("日出", today.sunrise, palette.orange)
            LuminaryMotionCaption("日落", today.sunset, palette.orange)
            LuminaryMotionCaption("月升", today.moonrise, palette.cyan)
            LuminaryMotionCaption("月落", today.moonset, palette.cyan)
            Text(status ?: "", detailModifier, style = MaterialTheme.typography.labelMedium,
                color = palette.textTertiary, maxLines = 2)
        }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
            val width = constraints.maxWidth
            val loose = Constraints(maxWidth = width)
            val captions = measurables.subList(1, 5).map { it.measure(loose) }
            val statusText = measurables[5].measure(loose)
            val gap = 8.dp.roundToPx()
            val captionHeight = captions.maxOf { it.height }
            val captionWidth = captions.maxOf { it.width }
            val fourFit = captionWidth * 4 + gap * 3 <= width
            val pairFit = captionWidth * 2 + gap <= width
            val groupHeight = if (pairFit) captionHeight else captionHeight * 2 + gap
            val amplitude = 15.dp.toPx()
            val sunHorizon = sunGlowRadius.toPx() + amplitude
            val sunCaptionY = (sunHorizon + amplitude + gap).roundToInt()
            val moonHorizon = sunCaptionY + groupHeight + gap + amplitude
            val moonCaptionY = (moonHorizon + amplitude + gap).roundToInt()
            val arcRows = if (fourFit) 1 else if (pairFit) 2 else 4
            val height = moonCaptionY + arcRows * captionHeight + (arcRows - 1) * gap
            metrics.sunHorizon = sunHorizon
            metrics.moonHorizon = moonHorizon
            metrics.arcHorizon = moonCaptionY - sunGlowRadius.toPx()
            // The status text and complete 14dp halo both fit above the outer arc.
            metrics.arcRise = minOf(88.dp.toPx(), metrics.arcHorizon - statusText.height - sunGlowRadius.toPx()).coerceAtLeast(0f)
            val canvas = measurables[0].measure(Constraints.fixed(width, height))

            fun wavePair(first: Int, r: Int?, s: Int?, y: Int): List<Pair<Int, Int>> {
                val a = captions[first].width
                val b = captions[first + 1].width
                if (!pairFit) return listOf(0 to y, 0 to (y + captionHeight + gap))
                // When four clock captions cannot fit, keep rise/set in readable left/right
                // groups in both styles. Large labels then need no crossing manoeuvre.
                if (!fourFit) return listOf(0 to y, (width - b) to y)
                fun clockX(minute: Int?, ownWidth: Int, fallback: Int) = if (minute == null) fallback else
                    (8.dp.toPx() + (width - 16.dp.toPx()) * vistaAstroClockFraction(minute) - ownWidth / 2f)
                        .roundToInt().coerceIn(0, (width - ownWidth).coerceAtLeast(0))
                val ax = clockX(r, a, 0)
                val bx = clockX(s, b, width - b)
                val separated = ax + a + gap <= bx || bx + b + gap <= ax
                return if (separated) listOf(ax to y, bx to y) else listOf(0 to y, (width - b) to y)
            }
            val wave = wavePair(0, rise, set, sunCaptionY) + wavePair(2, moonRise, moonSet, moonCaptionY)
            val arc = if (fourFit) listOf(
                0 to moonCaptionY,
                (width - captions[1].width) to moonCaptionY,
                (width * 3f / 8f - captions[2].width / 2f).roundToInt() to moonCaptionY,
                (width * 5f / 8f - captions[3].width / 2f).roundToInt() to moonCaptionY,
            ) else if (pairFit) listOf(
                0 to moonCaptionY,
                (width - captions[1].width) to moonCaptionY,
                0 to (moonCaptionY + captionHeight + gap),
                (width - captions[3].width) to (moonCaptionY + captionHeight + gap),
            ) else (0..3).map { 0 to (moonCaptionY + it * (captionHeight + gap)) }
            layout(width, height) {
                canvas.placeRelative(0, 0)
                val p = progress().coerceIn(0f, 1f)
                val positions = captions.indices.map { i ->
                    (wave[i].first + (arc[i].first - wave[i].first) * p) to
                        (wave[i].second + (arc[i].second - wave[i].second) * p)
                }
                // A wrapped moon swaps left/right order. Lift its rise caption over the other
                // caption while they pass, rather than letting two times slide through each other.
                val moonDistance = kotlin.math.abs(positions[2].first + captions[2].width / 2f -
                    positions[3].first - captions[3].width / 2f)
                val halfWidths = (captions[2].width + captions[3].width) / 2f
                fun clearance(points: List<Pair<Int, Int>>) = kotlin.math.abs(points[2].first + captions[2].width / 2f -
                    points[3].first - captions[3].width / 2f) - halfWidths
                val passingRoom = minOf(24.dp.toPx(), clearance(wave), clearance(arc)).coerceAtLeast(1f)
                val passing = ((halfWidths + passingRoom - moonDistance) /
                    passingRoom).coerceIn(0f, 1f)
                val lift = if (pairFit && (wave[2].first - wave[3].first) * (arc[2].first - arc[3].first) < 0)
                    (captionHeight + gap) * passing * passing * (3f - 2f * passing) else 0f
                captions.forEachIndexed { i, caption ->
                    caption.placeRelative(
                        positions[i].first.roundToInt(),
                        (positions[i].second - if (i == 2) lift else 0f).roundToInt(),
                    )
                }
                statusText.placeRelative(width - statusText.width, 0)
            }
        }
        if (largeType) Column(detailModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("照亮 ${(illumination * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            daylight?.let { Text("白昼 ${vistaDurationZh(it)}", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary) }
        } else Row(detailModifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("照亮 ${(illumination * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            daylight?.let { Text("白昼 ${vistaDurationZh(it)}", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary) }
        }
    }
}

private fun spans(rise: Int?, set: Int?, above: Boolean) =
    if (rise != null && set != null) luminaryClockSpans(rise, set, above) else emptyList()

@Composable
private fun LuminaryMotionCaption(label: String, time: String?, color: Color) {
    val palette = LocalZhishengPalette.current
    val formatted = time?.let { raw -> clockMinutes(raw)?.let { m ->
        "${(m / 60).toString().padStart(2, '0')}:${(m % 60).toString().padStart(2, '0')}"
    } ?: raw } ?: "--"
    Column(Modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textTertiary, maxLines = 1)
        Text(formatted, style = MaterialTheme.typography.titleSmall,
            color = if (time == null) palette.textTertiary else color, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

private fun LuminaryPoint.offset() = Offset(x, y)

private fun DrawScope.drawLuminaryMorph(
    track: LuminaryMorphTrack, rise: Int?, set: Int?, now: Int,
    above: List<LuminaryClockSpan>, below: List<LuminaryClockSpan>, p: Float,
    color: Color, rail: Color, sun: Boolean,
) {
    val baseline = track.waveHorizon + (track.arcHorizon - track.waveHorizon) * p
    val left = track.waveInset + (size.width / 2f - track.arcRadiusX - track.waveInset) * p
    val right = size.width - left
    // Two wave rails converge into one shared horizon; avoid doubling its final opacity.
    drawLine(rail.copy(alpha = if (sun) 0.55f + 0.45f * p else 0.55f * (1f - p)),
        Offset(left, baseline), Offset(right, baseline), 1.dp.toPx(), cap = StrokeCap.Round)
    val up = if (rise != null && set != null) vistaAstroUpDuration(rise, set) else null
    if (up == null) {
        val empty = Path()
        for (i in 0..64) {
            val t = i / 64f
            val waveX = track.waveInset + (size.width - track.waveInset * 2f) * t
            val arcX = size.width / 2f - track.arcRadiusX * cos(PI * t).toFloat()
            val arcY = track.arcHorizon - track.arcRadiusY * sin(PI * t).toFloat()
            val x = waveX + (arcX - waveX) * p
            val y = track.waveHorizon + (arcY - track.waveHorizon) * p
            if (i == 0) empty.moveTo(x, y) else empty.lineTo(x, y)
        }
        drawPath(empty, rail, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))))
        return
    }
    val elapsed = Math.floorMod(now - rise!!, VISTA_DAY_MINUTES)
    val visible = elapsed < up
    val orbit = if (visible) elapsed.toFloat() / up else (elapsed - up).toFloat() / (VISTA_DAY_MINUTES - up)
    val complete = if (visible) orbit else 0f

    fun path(spans: List<LuminaryClockSpan>, upper: Boolean, from: Float = 0f, to: Float = 1f, fill: Boolean = false): Path = Path().apply {
        for (span in spans) {
            val lo = maxOf(span.from, from)
            val hi = minOf(span.to, to)
            if (hi <= lo) continue
            val steps = ceil((hi - lo) * 96).toInt().coerceAtLeast(2)
            var first: LuminaryPoint? = null
            var last: LuminaryPoint? = null
            for (i in 0..steps) {
                val q = lo + (hi - lo) * i / steps
                val point = track.point(span.clockStart + span.duration * q, q, upper, up, p)
                if (i == 0) { moveTo(point.x, point.y); first = point } else lineTo(point.x, point.y)
                last = point
            }
            if (fill) {
                lineTo(last!!.x, baseline)
                lineTo(first!!.x, baseline)
                close()
            }
        }
    }
    val top = baseline - (track.waveAmplitude + (track.arcRadiusY - track.waveAmplitude) * p)
    fun fill(from: Float, to: Float, alpha: Float) {
        if (to <= from || alpha <= 0f) return
        drawPath(path(above, true, from, to, true), Brush.verticalGradient(
            listOf(color.copy(alpha = alpha), color.copy(alpha = 0.02f * (1f - p))), startY = top, endY = baseline))
    }
    if (sun) {
        fill(0f, complete, 0.18f - 0.02f * p)
        fill(complete, 1f, 0.18f * (1f - p))
    } else if (p < 1f) fill(0f, 1f, 0.18f * (1f - p))
    if (complete > 0f) drawPath(path(above, true, to = complete), color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    val futureEffect = if (p < 0.001f) null else PathEffect.dashPathEffect(floatArrayOf((7f - 4f * p).dp.toPx(), (5f * p).dp.toPx()))
    drawPath(path(above, true, from = complete), if (visible) color.copy(alpha = 1f - 0.65f * p) else lerp(color, rail, p),
        style = Stroke((2f - 0.5f * p).dp.toPx(), cap = StrokeCap.Round, pathEffect = futureEffect))
    if (p < 1f) drawPath(path(below, false), color.copy(alpha = 0.45f * (1f - p) * (1f - p)),
        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()))))
    // Rise/set anchors use the same geometry as the curve and the moving current-time point.
    for (q in listOf(0f, 1f)) {
        val absolute = rise + if (q == 0f) 0 else up
        val clock = if (q == 1f && absolute % VISTA_DAY_MINUTES == 0) VISTA_DAY_MINUTES.toFloat()
            else Math.floorMod(absolute, VISTA_DAY_MINUTES).toFloat()
        val anchor = track.point(clock, q, true, up, p).offset()
        drawCircle(rail, 2.dp.toPx(), anchor)
    }
    val dot = track.point(now.toFloat(), orbit, visible, up, p).offset()
    val dotAlpha = if (visible) 1f else 1f - p
    if (dotAlpha > 0f) {
        val glow = if (sun) (7f + 7f * p).dp.toPx() else 7.dp.toPx()
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = (0.12f + 0.20f * p) * dotAlpha), Color.Transparent),
            center = dot, radius = glow), glow, dot)
        drawCircle(color.copy(alpha = dotAlpha), (3.5f + (if (sun) 1.5f else 0.5f) * p).dp.toPx(), dot)
        if (p > 0f) drawCircle(Color.White.copy(alpha = 0.85f * p * dotAlpha), 1.6.dp.toPx(), dot)
    }
}
