@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.zhisheng.weather.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.model.*
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import com.zhisheng.weather.ui.home.HourlyEdgeOptics
import com.zhisheng.weather.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

private data class HistoryMark(
    val key: Long,
    val label: String,
    val high: Double?,
    val low: Double?,
    val condition: WeatherCondition?,
    val precipitationMm: Double?,
    val windKmh: Double?,
    val forecast: Boolean,
    val mean: Double? = null,
    val windDirectionDeg: Double? = null,
) {
    val meanOnly: Boolean get() = high == null && low == null && mean != null
    val hasTemperature: Boolean get() = high != null || low != null || mean != null
}

/** Display-unit temperature. The shared range uses the same conversion as the home daily bars. */
internal fun historyShownTemperature(celsius: Double?, unit: String): Double? =
    celsius?.takeIf(Double::isFinite)?.let { if (unit == "f") it * 9.0 / 5.0 + 32.0 else it }

/** Include every plotted value, including isolated mean-only points, in the shared scale. */
internal fun historyPlotScale(celsius: List<Double?>, unit: String): Pair<Double, Double> {
    val values = celsius.mapNotNull { historyShownTemperature(it, unit) }
    val min = values.minOrNull() ?: return 0.0 to 1.0
    val max = values.maxOrNull() ?: min
    return if (min == max) (min - 0.5) to (max + 0.5) else min to max
}

/**
 * Shared-scale span for one day, matching the home daily bar: the period's coldest
 * temperature sits at 0 and the warmest at 1. A missing day returns null so the track
 * stays empty. A single available temperature becomes a short mark, not a bar stretched
 * across the whole scale.
 */
/** A ribbon needs two neighboring days that both have a high and a low. Isolated years stay in the range list. */
internal fun historyRibbonHasBand(hasBothTemperatures: List<Boolean>): Boolean {
    var streak = 0
    for (present in hasBothTemperatures) {
        if (present) {
            streak++
            if (streak >= 2) return true
        } else streak = 0
    }
    return false
}

internal fun historyBarSpan(
    low: Double?,
    high: Double?,
    scaleMin: Double,
    scaleMax: Double,
    unit: String,
): Pair<Float, Float>? {
    val shownLow = historyShownTemperature(low, unit)
    val shownHigh = historyShownTemperature(high, unit)
    val startValue = shownLow ?: shownHigh ?: return null
    val endValue = shownHigh ?: shownLow ?: return null
    val range = (scaleMax - scaleMin).coerceAtLeast(1.0)
    val lo = minOf(startValue, endValue).let { ((it - scaleMin) / range).toFloat() }.coerceIn(0f, 1f)
    val hi = maxOf(startValue, endValue).let { ((it - scaleMin) / range).toFloat() }.coerceIn(0f, 1f)
    val maxW = (1f - lo).coerceAtLeast(0f)
    val minW = minOf(0.03f, maxW)
    return lo to (hi - lo).coerceIn(minW, maxW)
}

@Composable
internal fun VistaRecentHistory(week: RecentWeatherWeek, tempUnit: String, windUnit: String, endDate: LocalDate) {
    val marks = remember(week, endDate) {
        (6L downTo 0L).map { endDate.minusDays(it) }.map { date ->
            val day = week.days.find { it.localDate == date }
            HistoryMark(date.toEpochDay(), date.format(dayHeading), day?.high, day?.low,
                day?.weatherCode?.let { day.condition }, day?.precipitationMm, day?.windMaxKmh, false, mean = day?.mean)
        }
    }
    HistoryAtlas(recentWeekInsight(week, tempUnit), marks, tempUnit, windUnit, recentWeek = true)
}

@Composable
internal fun VistaSameDayHistory(review: HistoricalReview, forecast: DailyWeather?, tempUnit: String, windUnit: String,
    years: Int, today: LocalDate, progress: String?, onDateChange: (LocalDate) -> Unit, onYearsChange: (Int) -> Unit) {
    var dateEditor by rememberSaveable { mutableStateOf(false) }
    val chronological = remember(review, forecast) {
        val yearsOldestFirst = historicalTargetDates(review.referenceDate, review.requestedCount).reversed()
        val past = yearsOldestFirst.map { date ->
            val day = review.days.find { it.localDate == date }
            HistoryMark(date.toEpochDay(), "${date.year}年", day?.high, day?.low,
                day?.weatherCode?.let { day.condition }, day?.precipitationMm, day?.windMaxKmh, false, mean = day?.mean)
        }
        val current = forecast?.let {
            HistoryMark(review.referenceDate.toEpochDay(), "今年", it.high, it.low, it.condition, it.precipMm, it.windSpeed, true,
                windDirectionDeg = it.windDirectionDeg)
        }
        past + listOfNotNull(current)
    }
    if (dateEditor) HistoryDateDialog(review.referenceDate, today, { dateEditor = false }) {
        onDateChange(it); dateEditor = false
    }
    val comparison = forecast?.let { compareWithHistorical(it.high, it.low, review.summary) }
    val insight = when {
        forecast != null && comparison != null ->
            "今年预报 ${temperatureEnds(forecast.high, forecast.low, tempUnit)} · ${comparison.band.label}"
        forecast != null -> "今年预报 ${temperatureEnds(forecast.high, forecast.low, tempUnit)}"
        else -> "${review.requestedYearRange} · ${review.days.size}份同日记录"
    }
    HistoryAtlas(insight, chronological, tempUnit, windUnit, recentWeek = false) {
        TextButton(onClick = { dateEditor = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("查看日期 · ${review.referenceDate.format(dayHeading)}", Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = ZhishengText, maxLines = 2)
        }
        WeatherFeatureTabs(listOf("近5年", "近10年"), if (years == 5) 0 else 1) { onYearsChange(if (it == 0) 5 else 10) }
        progress?.let { Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary) }
    }
}

@Composable
private fun HistoryAtlas(insight: String, chronological: List<HistoryMark>,
    tempUnit: String, windUnit: String, recentWeek: Boolean, controls: (@Composable ColumnScope.() -> Unit)? = null) {
    val scale = remember(chronological, tempUnit) {
        historyPlotScale(chronological.flatMap { mark -> listOf(mark.high, mark.low, mark.mean.takeIf { mark.meanOnly }) }, tempUnit)
    }
    var selectedKey by rememberSaveable(chronological.first().key, chronological.last().key) {
        mutableLongStateOf(chronological.lastOrNull { it.hasTemperature }?.key ?: chronological.last().key)
    }
    val selected = chronological.firstOrNull { it.key == selectedKey } ?: chronological.last()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (controls != null) item { Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = controls) }
        item {
            Column(Modifier.fillMaxWidth().zhishengPanel().padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(insight, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, color = ZhishengText)
                    HistorySelectedDay(selected, tempUnit, windUnit)
                }
                if (chronological.any { it.hasTemperature }) {
                    HistoryRibbon(chronological, scale, tempUnit, selected.key) { selectedKey = it }
                }
            }
        }
        item {
            WeatherSourceDisclosure(
                if (recentWeek) "Open-Meteo · 近期天气档案" else "Open-Meteo · 历史估算",
                if (recentWeek) "显示最近七个完整自然日的格点化天气估算，不等同于当地单个气象站的原始观测。缺失日期保留空位；仅有平均温度时单独显示，不补成高低温范围。已获取记录保存在手机上。"
                else "往年记录由观测与模型共同还原，是历史再分析数据，不等同于单个气象站的原始记录。近5年或10年是逐年同日样本，不是30年气候平均。空白年份没有记录，不补零；仅有平均温度时单独显示，不补成高低温范围。今年预报使用当前天气源，与历史估算分开标记；预报风速可能为风力换算估值，不代表全天最大风速。已获取记录保存在手机上。",
            )
        }
    }
}

@Composable
private fun HistorySelectedDay(mark: HistoryMark, tempUnit: String, windUnit: String) {
    val missing = !mark.hasTemperature
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(mark.label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge, color = if (mark.forecast) ZhishengMint else ZhishengTextSecondary)
        if (missing) {
            Text("暂无记录", Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall, color = ZhishengText)
        } else if (mark.meanOnly) {
            Text(historyTemperatureDescription(mark, tempUnit), Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall, color = ZhishengText)
        } else {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val ink = historyLineInk(LocalZhishengPalette.current.isLight)
                mark.high?.let { Text("${Fmt.temp(it, tempUnit)}°", style = MaterialTheme.typography.headlineMedium, color = ink.warm, maxLines = 1) }
                mark.low?.let { Text("${Fmt.temp(it, tempUnit)}°", style = MaterialTheme.typography.headlineMedium, color = ink.cool, maxLines = 1) }
            }
        }
        mark.condition?.takeIf { it != WeatherCondition.UNKNOWN }?.let { condition ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WeatherIcon(condition, Modifier.size(28.dp))
                Text(condition.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = ZhishengText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        historyMetricLine(mark, windUnit)?.let {
            Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = ZhishengTextSecondary)
        }
    }
}

@Composable
private fun HistoryRibbon(marks: List<HistoryMark>, scale: Pair<Double, Double>, unit: String, selectedKey: Long, onSelect: (Long) -> Unit) {
    val palette = LocalZhishengPalette.current
    val ink = historyLineInk(palette.isLight)
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val scroll = rememberScrollState()
    val chartTop = 74.dp * fontScale
    val chartSpan = 156.dp * fontScale
    val chartBottom = chartTop + chartSpan
    val labelRoom = 22.dp * fontScale
    val plotTop = chartTop + labelRoom
    val plotBottom = chartBottom - labelRoom
    val stripHeight = chartBottom + 48.dp * fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val minColumn = 58.dp * fontScale
        val columnWidth = if (minColumn * marks.size <= maxWidth) maxWidth / marks.size.coerceAtLeast(1) else minColumn
        val totalWidth = columnWidth * marks.size
        val columnPx = with(LocalDensity.current) { columnWidth.toPx() }
        var pinned by remember(marks.first().key, marks.last().key) { mutableStateOf(false) }
        LaunchedEffect(scroll.maxValue) {
            if (!pinned && scroll.maxValue > 0) {
                scroll.scrollTo(scroll.maxValue)
                pinned = true
            }
        }
        LaunchedEffect(scroll, columnPx) {
            snapshotFlow { scroll.isScrollInProgress to scroll.maxValue }.distinctUntilChanged()
                .collect { (moving, max) ->
                    if (!moving && max > 0 && pinned) {
                        val target = ((scroll.value / columnPx).roundToInt() * columnPx).roundToInt().coerceIn(0, max)
                        if (abs(target - scroll.value) > 1) scroll.animateScrollTo(target)
                    }
                }
        }
        HistoryGlassViewport(scroll, Modifier.fillMaxWidth().height(stripHeight)) {
        Box(Modifier.fillMaxWidth().horizontalScroll(scroll).height(stripHeight).semantics {
            contentDescription = marks.joinToString("；") { mark ->
                listOf(mark.label, historyTemperatureDescription(mark, unit), mark.condition?.label, historyColumnWind(mark))
                    .filter { !it.isNullOrBlank() && it != WeatherCondition.UNKNOWN.label }.joinToString(" ")
            }
        }) {
            Canvas(Modifier.width(totalWidth).height(stripHeight)) {
                val columnPx = columnWidth.toPx()
                val top = plotTop.toPx()
                val bottom = plotBottom.toPx()
                val span = (scale.second - scale.first).coerceAtLeast(1.0)
                fun x(index: Int) = columnPx * (index + 0.5f)
                fun y(value: Double) = bottom - ((value - scale.first) / span).toFloat() * (bottom - top)
                val shown = marks.map {
                    historyShownTemperature(it.high, unit) to historyShownTemperature(it.low, unit)
                }
                for (index in 1 until shown.size) {
                    val previousHigh = shown[index - 1].first
                    val high = shown[index].first
                    val previousLow = shown[index - 1].second
                    val low = shown[index].second
                    if (previousHigh != null && high != null && previousLow != null && low != null) {
                        val band = Path().apply {
                            moveTo(x(index - 1), y(previousHigh))
                            lineTo(x(index), y(high))
                            lineTo(x(index), y(low))
                            lineTo(x(index - 1), y(previousLow))
                            close()
                        }
                        drawPath(band, Brush.verticalGradient(listOf(ink.warmSoft.copy(alpha = 0.22f), ink.coolSoft.copy(alpha = 0.16f)),
                            startY = top, endY = bottom))
                    }
                    if (previousHigh != null && high != null) {
                        drawLine(ink.warmSoft, Offset(x(index - 1), y(previousHigh)), Offset(x(index), y(high)), 1.6.dp.toPx(), StrokeCap.Round)
                    }
                    if (previousLow != null && low != null) {
                        drawLine(ink.coolSoft, Offset(x(index - 1), y(previousLow)), Offset(x(index), y(low)), 1.6.dp.toPx(), StrokeCap.Round)
                    }
                }
                shown.forEachIndexed { index, (high, low) ->
                    if (high != null && low != null) {
                        drawLine(palette.textTertiary.copy(alpha = 0.28f), Offset(x(index), y(high)), Offset(x(index), y(low)), 1.dp.toPx(), StrokeCap.Round)
                    }
                    val chosen = marks[index].key == selectedKey
                    val radius = if (chosen) 4.4.dp.toPx() else 3.4.dp.toPx()
                    fun point(value: Double, color: Color) {
                        val center = Offset(x(index), y(value))
                        drawCircle(palette.card, radius + 1.8.dp.toPx(), center)
                        drawCircle(color, radius, center)
                        if (chosen) drawCircle(palette.mint, radius + 3.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
                    }
                    high?.let { point(it, ink.warm) }
                    low?.let { point(it, ink.cool) }
                    if (marks[index].meanOnly) {
                        historyShownTemperature(marks[index].mean, unit)?.let { value ->
                            drawCircle(if (chosen) palette.mint else palette.text, radius, Offset(x(index), y(value)))
                        }
                    }
                }
            }
            Row(Modifier.width(totalWidth).height(stripHeight)) {
                marks.forEach { mark ->
                    HistoryTrendColumn(mark, columnWidth, plotTop, plotBottom, scale, unit, mark.key == selectedKey) { onSelect(mark.key) }
                }
            }
        }
        }
    }
}

@Composable
private fun HistoryTrendColumn(mark: HistoryMark, columnWidth: androidx.compose.ui.unit.Dp, chartTop: androidx.compose.ui.unit.Dp,
    chartBottom: androidx.compose.ui.unit.Dp, scale: Pair<Double, Double>, unit: String, selected: Boolean, onSelect: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val span = (scale.second - scale.first).coerceAtLeast(1.0)
    fun yOf(celsius: Double?): androidx.compose.ui.unit.Dp? {
        val shown = historyShownTemperature(celsius, unit) ?: return null
        val ratio = ((shown - scale.first) / span).toFloat().coerceIn(0f, 1f)
        return chartBottom - (chartBottom - chartTop) * ratio
    }
    val heading = if (mark.forecast || mark.label.endsWith("年")) mark.label else {
        runCatching { LocalDate.ofEpochDay(mark.key).format(DateTimeFormatter.ofPattern("EEE", Locale.CHINA)) }.getOrDefault(mark.label)
    }
    val dateLine = if (mark.forecast || mark.label.endsWith("年")) "" else {
        runCatching { LocalDate.ofEpochDay(mark.key).format(DateTimeFormatter.ofPattern("M/d", Locale.CHINA)) }.getOrDefault("")
    }
    val wind = historyColumnWind(mark)
    Box(Modifier.width(columnWidth).fillMaxHeight().clip(RoundedCornerShape(14.dp))
        .background(if (selected) LocalZhishengPalette.current.mint.copy(alpha = 0.12f) else Color.Transparent)
        .vistaClick(listOf(mark.label, historyTemperatureDescription(mark, unit), wind).filterNotNull().joinToString(" "), onSelect)) {
        Column(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(heading, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium, color = if (selected || mark.forecast) ZhishengMint else ZhishengText)
            if (dateLine.isNotEmpty()) {
                Text(dateLine, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            }
            mark.condition?.takeIf { it != WeatherCondition.UNKNOWN }?.let { WeatherIcon(it, Modifier.padding(top = 2.dp).size(22.dp)) }
        }
        if (!mark.hasTemperature) {
            Text("暂无", Modifier.align(Alignment.Center), style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, maxLines = 1)
        } else if (mark.meanOnly) {
            yOf(mark.mean)?.let { y ->
                Text("平均 ${Fmt.temp(mark.mean, unit)}°", Modifier.align(Alignment.TopCenter).offset(y = y - 24.dp * fontScale).fillMaxWidth(),
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge, color = ZhishengText)
            }
        } else {
            yOf(mark.high)?.let { y ->
                Text("${Fmt.temp(mark.high, unit)}°", Modifier.align(Alignment.TopCenter).offset(y = y - 22.dp * fontScale).fillMaxWidth(),
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge, color = historyLineInk(LocalZhishengPalette.current.isLight).warm)
            }
            yOf(mark.low)?.let { y ->
                Text("${Fmt.temp(mark.low, unit)}°", Modifier.align(Alignment.TopCenter).offset(y = y + 12.dp * fontScale).fillMaxWidth(),
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge, color = historyLineInk(LocalZhishengPalette.current.isLight).cool)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (!mark.meanOnly) {
                HistoryTemperatureTrack(mark.low, mark.high, scale.first, scale.second, unit,
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp))
            }
            Text(wind ?: "", Modifier.fillMaxWidth().padding(top = 2.dp),
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary)
        }
    }
}

@Composable
private fun HistoryGlassViewport(scroll: androidx.compose.foundation.ScrollState, modifier: Modifier, content: @Composable () -> Unit) {
    val inspection = LocalInspectionMode.current
    val edgeOptics = remember(inspection) {
        if (!inspection && Build.VERSION.SDK_INT >= 33) HourlyEdgeOptics() else null
    }
    val refract = LocalHomeSurfaceStyle.current == HomeSurfaceStyle.FRAGRANCE_GLASS && edgeOptics != null
    Box(modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val edge = ((if (refract) 14.dp else 12.dp).toPx() / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.16f)
        drawRect(Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = if (scroll.value > 0) 0f else 1f),
            edge to Color.Black,
            (1f - edge) to Color.Black,
            1f to Color.Black.copy(alpha = if (scroll.value < scroll.maxValue) 0f else 1f),
        ), blendMode = BlendMode.DstIn)
    }) {
        Box(Modifier.fillMaxWidth().graphicsLayer {
            if (refract && Build.VERSION.SDK_INT >= 33) {
                renderEffect = edgeOptics?.effect(
                    width = size.width,
                    height = size.height,
                    edgeWidth = 20.dp.toPx(),
                    hasLeft = scroll.value > 0,
                    hasRight = scroll.value < scroll.maxValue,
                )
            }
        }) { content() }
    }
}

private data class HistoryLineInk(val warm: Color, val cool: Color, val warmSoft: Color, val coolSoft: Color)

/** Dusty clay and gray-blue for the history lines. Numbers use the deeper step of the same hue. */
private fun historyLineInk(light: Boolean): HistoryLineInk = if (light) HistoryLineInk(
    warm = Color(0xFF9C7352), cool = Color(0xFF5E7C8C),
    warmSoft = Color(0xFFC4A484), coolSoft = Color(0xFF8EABBA),
) else HistoryLineInk(
    warm = Color(0xFFD2B48C), cool = Color(0xFF9BB4C4),
    warmSoft = Color(0xFFC6A67C), coolSoft = Color(0xFF7E9AAB),
)

private fun historyColumnWind(mark: HistoryMark): String? {
    val force = Fmt.windForce(mark.windKmh)
    val direction = com.zhisheng.weather.data.WeatherRepository.windDirection(mark.windDirectionDeg)?.let { "${it}风" }
    return listOfNotNull(direction, force).takeIf { it.isNotEmpty() }?.joinToString("")
}

@Composable
private fun HistoryTemperatureTrack(low: Double?, high: Double?, scaleMin: Double, scaleMax: Double, unit: String, modifier: Modifier) {
    val palette = LocalZhishengPalette.current
    val span = historyBarSpan(low, high, scaleMin, scaleMax, unit)
    Canvas(modifier.height(10.dp)) {
        val stroke = 6.dp.toPx()
        val cy = size.height / 2f
        val inset = stroke / 2f
        if (size.width <= stroke) return@Canvas
        drawLine(palette.textTertiary.copy(alpha = 0.28f), Offset(inset, cy), Offset(size.width - inset, cy), stroke, StrokeCap.Round)
        if (span == null) return@Canvas
        val usable = size.width - stroke
        val left = inset + usable * span.first
        val right = inset + usable * (span.first + span.second)
        val ink = historyLineInk(palette.isLight)
        drawPath(Path().apply { moveTo(left, cy); lineTo(right.coerceAtLeast(left), cy) },
            Brush.horizontalGradient(listOf(ink.coolSoft, ink.warmSoft), startX = left, endX = right.coerceAtLeast(left + 1f)),
            style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

private fun historyMetricLine(mark: HistoryMark, windUnit: String): String? = listOfNotNull(
    mark.precipitationMm?.takeIf(Double::isFinite)?.let { "降水 " + String.format(Locale.CHINA, "%.1f mm", it) },
    mark.windKmh?.let { Fmt.wind(it, windUnit) }?.let { (if (mark.forecast) "预报风速 " else "最大风速 ") + it },
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

private fun historyTemperatureDescription(mark: HistoryMark, unit: String): String = when {
    mark.meanOnly -> "平均 ${Fmt.temp(mark.mean, unit)}°"
    mark.hasTemperature -> temperatureEnds(mark.high, mark.low, unit)
    else -> "暂无记录"
}

private fun temperatureEnds(high: Double?, low: Double?, unit: String): String =
    "${high?.let { Fmt.temp(it, unit) + "°" } ?: "—"} / ${low?.let { Fmt.temp(it, unit) + "°" } ?: "—"}"

private val dayHeading = DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA)

private val weekdayNarrow = mapOf(
    DayOfWeek.MONDAY to "一", DayOfWeek.TUESDAY to "二", DayOfWeek.WEDNESDAY to "三",
    DayOfWeek.THURSDAY to "四", DayOfWeek.FRIDAY to "五", DayOfWeek.SATURDAY to "六", DayOfWeek.SUNDAY to "日",
)

internal fun historyWeekdayLabels(): List<String> {
    val first = WeekFields.of(Locale.CHINA).firstDayOfWeek
    return List(7) { weekdayNarrow.getValue(first.plus(it.toLong())) }
}

internal fun historyMonthGrid(month: LocalDate): List<List<LocalDate?>> {
    val first = month.withDayOfMonth(1)
    val shift = (first.dayOfWeek.value - WeekFields.of(Locale.CHINA).firstDayOfWeek.value).mod(7)
    val cells = MutableList<LocalDate?>(shift + first.lengthOfMonth()) { null }
    repeat(first.lengthOfMonth()) { cells[shift + it] = first.plusDays(it.toLong()) }
    while (cells.size % 7 != 0) cells.add(null)
    return cells.chunked(7)
}

@Composable
internal fun HistoryDateDialog(date: LocalDate, today: LocalDate, onDismiss: () -> Unit, onSave: (LocalDate) -> Unit) {
    var pending by rememberSaveable(date) { mutableLongStateOf(date.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(pending)
    WeatherOverlayDialog(onDismiss) { modifier ->
        HistoryDateContent(modifier, selected, today, { pending = it.toEpochDay() }, onDismiss) { onSave(selected) }
    }
}

@Composable
internal fun HistoryDateContent(modifier: Modifier, selected: LocalDate, today: LocalDate,
    onChange: (LocalDate) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    val palette = LocalZhishengPalette.current
    Column(modifier.zhishengPanel()) {
        LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("选择回看日期", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, color = ZhishengText) }
            item { Text(selected.format(dayHeading), Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall, color = ZhishengText) }
            item {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA).format(selected), Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = ZhishengText)
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { onChange(selected.plusMonths(-1)) }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("上月", color = ZhishengTextSecondary, maxLines = 1) }
                        TextButton(onClick = { onChange(selected.plusMonths(1)) }, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("下月", color = ZhishengTextSecondary, maxLines = 1) }
                    }
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            historyWeekdayLabels().forEach { name ->
                                Text(name, Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium, color = ZhishengTextTertiary)
                            }
                        }
                        historyMonthGrid(selected).forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { date ->
                                    if (date == null) {
                                        Spacer(Modifier.weight(1f).heightIn(min = 48.dp))
                                    } else {
                                        val chosen = date == selected
                                        Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                                            .background(if (chosen) palette.mint.copy(alpha = 0.16f) else Color.Transparent)
                                            .clickable(role = Role.Button) { onChange(date) }
                                            .semantics {
                                                this.selected = chosen
                                                contentDescription = date.format(dayHeading)
                                            },
                                            contentAlignment = Alignment.Center) {
                                            Text(date.dayOfMonth.toString(), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (chosen || date == today) ZhishengText else ZhishengTextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { TextButton(onClick = { onChange(today) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("回到今天", color = ZhishengText) } }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            TextButton(onClick = onDismiss, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("取消", color = ZhishengTextSecondary) }
            TextButton(onClick = onSave, Modifier.weight(1f).heightIn(min = 48.dp)) { Text("查看", color = ZhishengText) }
        }
    }
}
