/* Hallmark · pre-emit critique: P5 H5 E4 S5 R5 V4 */
/* Hallmark · genre: atmospheric technical utility · macrostructure: Workbench · design-system: design.md · designed-as-app */
package com.zhisheng.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.R
import com.zhisheng.weather.model.BriefingEmote
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.YesterdayInfo
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengReading
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class ForecastDayNightVisual(
    val dayLabel: String,
    val nightLabel: String,
    val dayCondition: WeatherCondition,
    val nightCondition: WeatherCondition,
)

internal fun forecastDayNightVisual(daily: DailyWeather): ForecastDayNightVisual {
    val fallback = daily.condition ?: WeatherCondition.UNKNOWN
    val text = daily.weatherText?.trim().orEmpty().ifBlank { fallback.label }
    val parts = text.split("转", limit = 2).map(String::trim)
    val dayLabel = parts.firstOrNull().orEmpty().ifBlank { fallback.label }
    val nightLabel = parts.getOrNull(1).orEmpty().ifBlank { dayLabel }
    return ForecastDayNightVisual(
        dayLabel = dayLabel,
        nightLabel = nightLabel,
        dayCondition = forecastConditionForLabel(dayLabel, night = false, fallback),
        nightCondition = forecastConditionForLabel(nightLabel, night = true, fallback),
    )
}

internal fun forecastConditionForLabel(
    label: String,
    night: Boolean,
    fallback: WeatherCondition,
): WeatherCondition = when {
    "雷" in label -> WeatherCondition.THUNDERSTORM
    "冰雹" in label -> WeatherCondition.HAIL
    "雨夹雪" in label -> WeatherCondition.SLEET
    "冻" in label && "雨" in label -> WeatherCondition.FREEZING_RAIN
    "雪" in label -> WeatherCondition.SNOW
    "小雨" in label || "毛毛雨" in label -> WeatherCondition.DRIZZLE
    "雨" in label -> WeatherCondition.RAIN
    "雾" in label -> WeatherCondition.FOG
    "霾" in label -> WeatherCondition.HAZE
    "沙" in label || "尘" in label -> WeatherCondition.SAND
    "风" in label -> WeatherCondition.WIND
    "阴" in label -> WeatherCondition.OVERCAST
    "云" in label -> if (night) WeatherCondition.PARTLY_CLOUDY_NIGHT else WeatherCondition.PARTLY_CLOUDY
    "晴" in label -> if (night) WeatherCondition.CLEAR_NIGHT else WeatherCondition.CLEAR
    night && fallback == WeatherCondition.CLEAR -> WeatherCondition.CLEAR_NIGHT
    night && fallback == WeatherCondition.PARTLY_CLOUDY -> WeatherCondition.PARTLY_CLOUDY_NIGHT
    else -> fallback
}

@Composable
fun DailyForecastScreen(
    city: City?,
    days: List<DailyWeather>,
    yesterday: YesterdayInfo?,
    tempUnit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
    onBack: () -> Unit,
) {
    val visibleDays = days.take(15)
    val trackDays = listOfNotNull(yesterdayForecastDay(yesterday, utcOffsetSeconds)) + visibleDays
    val pageTitle = if (visibleDays.size >= 15) "15日天气预报" else "${visibleDays.size}日天气预报"
    val sectionTitle = if (visibleDays.size >= 15) "未来十五日" else "未来${visibleDays.size}日"
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ZhishengBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        FeaturePageHeader(pageTitle, "FORECAST ARRAY", onBack)
        if (visibleDays.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                TerminalPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
                        Text("FORECAST CHANNEL / EMPTY", style = MaterialTheme.typography.labelSmall, color = ZhishengOrange)
                        Spacer(Modifier.height(8.dp))
                        Text("当前城市暂未返回逐日预报", style = MaterialTheme.typography.titleMedium, color = ZhishengText)
                        Spacer(Modifier.height(4.dp))
                        Text("返回主页刷新后再试", style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                    }
                }
            }
            return@Column
        }

        ForecastMeta(city, visibleDays.size, tempUnit)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { FeatureSectionTitle(1, sectionTitle, "DAY / NIGHT / RANGE") }
            item {
                TerminalPanel(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    ForecastWorkbench(trackDays, tempUnit, windUnit, utcOffsetSeconds)
                }
            }
            item { FeatureSectionTitle(2, "天气娘简报", "WEATHER GIRL") }
            item {
                ForecastSummaryPanel(
                    digest = buildForecastDigest(visibleDays, tempUnit, utcOffsetSeconds, city?.locationKey.orEmpty()),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        }
    }
}

internal fun yesterdayForecastDay(
    yesterday: YesterdayInfo?,
    utcOffsetSeconds: Int?,
    nowMillis: Long = System.currentTimeMillis(),
): DailyWeather? {
    yesterday ?: return null
    if (yesterday.high == null && yesterday.low == null && yesterday.condition == null) return null
    val zone = Fmt.zoneId(utcOffsetSeconds)
    val dateMillis = Instant.ofEpochMilli(nowMillis)
        .atZone(zone)
        .toLocalDate()
        .minusDays(1)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
    return DailyWeather(
        dateMillis = dateMillis,
        high = yesterday.high,
        low = yesterday.low,
        condition = yesterday.condition,
        weatherText = yesterday.condition?.label,
    )
}

internal fun forecastTemporalLabel(
    epochMillis: Long,
    utcOffsetSeconds: Int?,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val zone = Fmt.zoneId(utcOffsetSeconds)
    val target = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return when (target) {
        today.minusDays(1) -> "昨天"
        else -> Fmt.dailyDayLabel(epochMillis, nowMillis, utcOffsetSeconds)
    }
}

@Composable
private fun ForecastMeta(city: City?, count: Int, tempUnit: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                city?.displayName ?: "当前城市",
                style = MaterialTheme.typography.titleMedium,
                color = ZhishengText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "已接收 $count 日预报 · ${Fmt.unitSuffix(tempUnit)}",
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary,
            )
        }
        Text(
            "横向滑动  →",
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengMint,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun ForecastWorkbench(
    days: List<DailyWeather>,
    tempUnit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
) {
    val palette = LocalZhishengPalette.current
    val scrollState = rememberScrollState()
    val columnWidth = 86.dp
    val boardHeight = 400.dp
    val chartTop = 132.dp
    val chartBottom = 226.dp
    val totalWidth = columnWidth * days.size
    val converted = days.flatMap { day ->
        listOfNotNull(
            day.low?.let { convertTemperature(it, tempUnit) },
            day.high?.let { convertTemperature(it, tempUnit) },
        )
    }
    val minimum = (converted.minOrNull() ?: 0.0) - 1.0
    val maximum = (converted.maxOrNull() ?: minimum + 1.0) + 1.0
    val span = (maximum - minimum).coerceAtLeast(1.0)

    fun yOffset(value: Double): Dp {
        val ratio = ((value - minimum) / span).toFloat().coerceIn(0f, 1f)
        return chartBottom - (chartBottom - chartTop) * ratio
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("DAY", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(6.dp).background(ZhishengOrange))
            Spacer(Modifier.width(5.dp))
            Text("HIGH", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(6.dp).background(ZhishengCyan))
            Spacer(Modifier.width(5.dp))
            Text("LOW", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            Spacer(Modifier.weight(1f))
            Text("NIGHT", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        }
        HorizontalDivider(color = ZhishengCardBorder, thickness = 1.dp)
        Box(Modifier.fillMaxWidth().height(boardHeight).horizontalScroll(scrollState)) {
            Box(Modifier.width(totalWidth).height(boardHeight)) {
                Canvas(Modifier.width(totalWidth).height(boardHeight)) {
                    val columnPx = columnWidth.toPx()
                    val chartTopPx = chartTop.toPx()
                    val chartBottomPx = chartBottom.toPx()
                    fun x(index: Int): Float = columnPx * (index + 0.5f)
                    fun y(value: Double): Float = chartBottomPx -
                        ((value - minimum) / span).toFloat() * (chartBottomPx - chartTopPx)

                    val todayIndex = days.indexOfFirst {
                        forecastTemporalLabel(it.dateMillis, utcOffsetSeconds) == "今天"
                    }
                    if (todayIndex >= 0) {
                        drawRect(
                            palette.orange.copy(alpha = 0.045f),
                            topLeft = Offset(columnPx * todayIndex, 0f),
                            size = Size(columnPx, size.height),
                        )
                        drawRect(
                            palette.orange,
                            topLeft = Offset(columnPx * todayIndex, 0f),
                            size = Size(columnPx, 3.dp.toPx()),
                        )
                    }
                    repeat(days.size + 1) { index ->
                        drawLine(
                            palette.cardBorder.copy(alpha = 0.72f),
                            Offset(columnPx * index, 0f),
                            Offset(columnPx * index, size.height),
                            1f,
                        )
                    }
                    repeat(3) { guide ->
                        val guideY = chartTopPx + guide * (chartBottomPx - chartTopPx) / 2f
                        drawLine(
                            palette.cardBorder.copy(alpha = 0.72f),
                            Offset(0f, guideY),
                            Offset(size.width, guideY),
                            1f,
                        )
                    }

                    fun drawSeries(selector: (DailyWeather) -> Double?, color: androidx.compose.ui.graphics.Color) {
                        val path = Path()
                        var drawing = false
                        days.forEachIndexed { index, day ->
                            val value = selector(day)?.let { convertTemperature(it, tempUnit) }
                            if (value == null) {
                                drawing = false
                            } else if (!drawing) {
                                path.moveTo(x(index), y(value))
                                drawing = true
                            } else {
                                path.lineTo(x(index), y(value))
                            }
                        }
                        drawPath(path, color.copy(alpha = 0.9f), style = Stroke(width = 2.5f, cap = StrokeCap.Round))
                        days.forEachIndexed { index, day ->
                            selector(day)?.let { raw ->
                                convertTemperature(raw, tempUnit).let { value ->
                                    drawCircle(color, radius = 4.2f, center = Offset(x(index), y(value)))
                                }
                            }
                        }
                    }
                    drawSeries(DailyWeather::high, palette.orange)
                    drawSeries(DailyWeather::low, palette.cyan)

                    // 小米式的过去态：保留同一条温度轨迹，但让昨天整列退到背景层。
                    val yesterdayIndex = days.indexOfFirst {
                        forecastTemporalLabel(it.dateMillis, utcOffsetSeconds) == "昨天"
                    }
                    if (yesterdayIndex >= 0) {
                        drawRect(
                            palette.bg.copy(alpha = 0.46f),
                            topLeft = Offset(columnPx * yesterdayIndex, 0f),
                            size = Size(columnPx, size.height),
                        )
                    }
                }

                Row(Modifier.width(totalWidth).height(boardHeight)) {
                    days.forEach { day ->
                        ForecastDayColumn(day, tempUnit, windUnit, utcOffsetSeconds, columnWidth, ::yOffset)
                    }
                }
            }
        }
        HorizontalDivider(color = ZhishengCardBorder, thickness = 1.dp)
        Row(
            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("数据轨道", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            Spacer(Modifier.weight(1f))
            Text(
                if (scrollState.canScrollForward) "继续向左滑动查看后续日期" else "已到预报末端",
                style = MaterialTheme.typography.labelSmall,
                color = if (scrollState.canScrollForward) ZhishengMint else ZhishengTextTertiary,
            )
        }
    }
}

@Composable
private fun ForecastDayColumn(
    day: DailyWeather,
    tempUnit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
    width: Dp,
    yOffset: (Double) -> Dp,
) {
    val visual = forecastDayNightVisual(day)
    val temporalLabel = forecastTemporalLabel(day.dateMillis, utcOffsetSeconds)
    val isToday = temporalLabel == "今天"
    val isYesterday = temporalLabel == "昨天"
    Box(Modifier.width(width).height(400.dp).alpha(if (isYesterday) 0.58f else 1f)) {
        Text(
            temporalLabel,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (isToday) ZhishengOrange else ZhishengTextSecondary,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
        Text(
            forecastDateLabel(day.dateMillis, utcOffsetSeconds),
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 34.dp),
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengTextTertiary,
            maxLines = 1,
        )
        WeatherIcon(visual.dayCondition, Modifier.align(Alignment.TopCenter).offset(y = 62.dp).size(30.dp))
        Text(
            visual.dayLabel,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 95.dp).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        day.high?.let { raw ->
            Text(
                Fmt.temp(raw, tempUnit)?.let { "$it°" } ?: "—",
                modifier = Modifier.align(Alignment.TopCenter).offset(y = yOffset(convertTemperature(raw, tempUnit)) - 22.dp),
                style = MaterialTheme.typography.titleSmall,
                color = ZhishengOrange,
                fontWeight = FontWeight.Bold,
            )
        }
        day.low?.let { raw ->
            Text(
                Fmt.temp(raw, tempUnit)?.let { "$it°" } ?: "—",
                modifier = Modifier.align(Alignment.TopCenter).offset(y = yOffset(convertTemperature(raw, tempUnit)) + 7.dp),
                style = MaterialTheme.typography.titleSmall,
                color = ZhishengCyan,
                fontWeight = FontWeight.Bold,
            )
        }
        forecastTemperatureRangeLabel(day, tempUnit)?.let { rangeLabel ->
            val high = day.high ?: return@let
            val low = day.low ?: return@let
            val rangeCenter = (yOffset(convertTemperature(high, tempUnit)) +
                yOffset(convertTemperature(low, tempUnit))) / 2f
            Text(
                rangeLabel,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = rangeCenter - 7.dp),
                color = ZhishengTextTertiary,
                fontSize = 10.sp,
                letterSpacing = 0.4.sp,
                maxLines = 1,
            )
        }
        WeatherIcon(visual.nightCondition, Modifier.align(Alignment.TopCenter).offset(y = 259.dp).size(28.dp))
        Text(
            visual.nightLabel,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 291.dp).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        forecastDetailLabels(day, windUnit).forEachIndexed { index, label ->
            Text(
                label,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = 323.dp + 26.dp * index),
                style = MaterialTheme.typography.labelSmall,
                color = if (label.startsWith("降水")) ZhishengCyan else ZhishengTextSecondary,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal fun convertTemperature(celsius: Double, unit: String): Double =
    if (unit == "f") celsius * 9.0 / 5.0 + 32.0 else celsius

internal fun forecastDateLabel(epochMillis: Long, utcOffsetSeconds: Int?): String =
    DateTimeFormatter.ofPattern("M/d", Locale.US)
        .format(Instant.ofEpochMilli(epochMillis).atZone(Fmt.zoneId(utcOffsetSeconds)))

internal fun forecastDetailLabels(day: DailyWeather, windUnit: String): List<String> = buildList {
    Fmt.probability(day.precipProbability)?.let { add("降水 $it") }
    day.windSpeed
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let { Fmt.wind(it, windUnit) }
        ?.let { add("风 $it") }
    day.sunrise
        ?.takeIf { it.isNotBlank() }
        ?.let { add("日出 $it") }
}

internal fun forecastTemperatureRangeLabel(day: DailyWeather, tempUnit: String): String? {
    val high = day.high?.takeIf(Double::isFinite) ?: return null
    val low = day.low?.takeIf(Double::isFinite) ?: return null
    val range = kotlin.math.abs(convertTemperature(high, tempUnit) - convertTemperature(low, tempUnit))
    return "温差 ${kotlin.math.round(range).toInt()}°"
}

internal data class ForecastDigest(
    val dayCount: Int,
    val headline: String,
    val overview: String,
    val note: String,
    val emote: BriefingEmote,
    val highValue: String,
    val highDate: String,
    val lowValue: String,
    val lowDate: String,
    val rainValue: String,
    val rangeValue: String,
)

// 15 日简报：趋势一律按摄氏度判断（显示单位只影响数字），雨雪分开统计，句子按天轮换。
internal fun buildForecastDigest(
    days: List<DailyWeather>,
    tempUnit: String,
    utcOffsetSeconds: Int?,
    seedKey: String = "",
): ForecastDigest {
    if (days.isEmpty()) {
        return ForecastDigest(
            dayCount = 0,
            headline = "暂时没有拿到逐日预报，先回主页刷新看看。",
            overview = "有了预报数据，我再帮你把这几天说清楚。",
            note = "这页每天打开都会是新的。",
            emote = BriefingEmote.CLOUDY,
            highValue = "—",
            highDate = "—",
            lowValue = "—",
            lowDate = "—",
            rainValue = "0 日",
            rangeValue = "—",
        )
    }

    fun isSnow(day: DailyWeather): Boolean =
        day.condition in setOf(WeatherCondition.SNOW, WeatherCondition.SLEET, WeatherCondition.FREEZING_RAIN, WeatherCondition.FREEZING_DRIZZLE) ||
            "雪" in day.weatherText.orEmpty()

    fun isRain(day: DailyWeather): Boolean {
        if (isSnow(day)) return false
        return day.condition?.isPrecipitation == true ||
            "雨" in day.weatherText.orEmpty() ||
            (day.precipProbability ?: 0) >= 30
    }

    val rainDays = days.count { isRain(it) }
    val snowDays = days.count { isSnow(it) }
    val wetDays = rainDays + snowDays
    var streak = 0
    var maxWetStreak = 0
    for (day in days) {
        streak = if (isRain(day) || isSnow(day)) streak + 1 else 0
        if (streak > maxWetStreak) maxWetStreak = streak
    }

    val highDays = days.filter { it.high != null }
    val lowDays = days.filter { it.low != null }
    val hottest = highDays.maxByOrNull { requireNotNull(it.high) }
    val coldest = lowDays.minByOrNull { requireNotNull(it.low) }
    val widest = days.filter { it.high != null && it.low != null }
        .maxByOrNull { requireNotNull(it.high) - requireNotNull(it.low) }
    val firstAverage = highDays.take(3).mapNotNull { it.high }.averageOrNull()
    val lastAverage = highDays.takeLast(3).mapNotNull { it.high }.averageOrNull()
    val trendCelsius = if (firstAverage != null && lastAverage != null) lastAverage - firstAverage else 0.0
    val magnitude = if (tempUnit == "f") kotlin.math.round(kotlin.math.abs(trendCelsius) * 9.0 / 5.0).toInt()
    else kotlin.math.round(kotlin.math.abs(trendCelsius)).toInt()

    val heatDays = days.count { (it.high ?: Double.NEGATIVE_INFINITY) >= 35.0 }
    val windyDays = days.count { (it.windSpeed ?: 0.0) >= 30.0 }
    val sunnyDays = days.count { it.condition in setOf(WeatherCondition.CLEAR, WeatherCondition.CLEAR_NIGHT) }
    val cloudyDays = days.count { it.condition in setOf(WeatherCondition.OVERCAST, WeatherCondition.PARTLY_CLOUDY, WeatherCondition.PARTLY_CLOUDY_NIGHT, WeatherCondition.CLOUDY) }
    val stormDays = days.count { it.condition in setOf(WeatherCondition.THUNDERSTORM, WeatherCondition.HAIL) || "雷" in it.weatherText.orEmpty() }
    val fogDays = days.count { it.condition == WeatherCondition.FOG || "雾" in it.weatherText.orEmpty() }
    val hazeSandDays = days.count { it.condition in setOf(WeatherCondition.HAZE, WeatherCondition.SAND) || "霾" in it.weatherText.orEmpty() || "沙" in it.weatherText.orEmpty() }
    val maxHigh = days.mapNotNull { it.high }.maxOrNull() ?: Double.NEGATIVE_INFINITY
    val minLow = days.mapNotNull { it.low }.minOrNull() ?: Double.POSITIVE_INFINITY
    val todayWet = days.firstOrNull()?.let { isRain(it) || isSnow(it) } ?: false
    val tomorrowWet = days.getOrNull(1)?.let { isRain(it) || isSnow(it) } ?: false
    val tomorrowSnow = days.getOrNull(1)?.let { isSnow(it) } ?: false
    var wetRuns = 0
    var inWetRun = false
    for (day in days) {
        val wet = isRain(day) || isSnow(day)
        if (wet && !inWetRun) wetRuns++
        inWetRun = wet
    }
    val weekendWet = days.any {
        (isRain(it) || isSnow(it)) &&
            Instant.ofEpochMilli(it.dateMillis).atZone(Fmt.zoneId(utcOffsetSeconds)).dayOfWeek.value in 6..7
    }
    val rangeDays = days.filter { it.high != null && it.low != null }
    val widestRange = rangeDays.maxOfOrNull { requireNotNull(it.high) - requireNotNull(it.low) } ?: 0.0
    val bigRangeDays = rangeDays.count { requireNotNull(it.high) - requireNotNull(it.low) >= 12.0 }
    val n = days.size

    // 分级链：连续雨雪 > 明显降温 > 极端高温 > 冰点 > 持续高温 > 明天雨雪 > 有雪 > 雷雨多 > 周末雨雪 > 雨雪偏多
    //        > 切换频繁 > 明显回暖 > 转凉 > 小幅回暖 > 多风 > 雾霾沙 > 昼夜温差大 > 晴多 > 阴天多 > 平稳
    val headline: String
    val emote: BriefingEmote
    when {
        maxWetStreak >= 3 -> {
            headline = forecastPick(listOf(
                "接下来有连着几天雨雪，中间好天气不多。",
                "有一段连阴雨雪，晾晒的事往后放放。",
                "雨雪会连着来几天，鞋子和伞都要备好。",
                "接下来几天雨水接二连三，出门多留点时间。",
                "接下来是连着的雨雪天，通勤路上慢一点。",
                "雨雪天会连着来，家里通风和晾晒都先等等。",
            ), days, utcOffsetSeconds, seedKey, 11)
            emote = BriefingEmote.RAIN
        }
        trendCelsius <= -6.0 -> {
            headline = forecastPick(listOf(
                "接下来会明显降温 ${magnitude}°，厚外套提前翻出来。",
                "后半段会冷一截，最高温平均会降 ${magnitude}° 左右。",
                "后面要明显转冷，最高温平均降 ${magnitude}° 左右，冬衣可以准备了。",
                "降温已经排上日程，平均最高温会低 ${magnitude}° 左右。",
                "接下来冷空气要来了，最高温平均降 ${magnitude}° 左右。",
                "后面会有一波明显降温，最高温平均低 ${magnitude}° 左右。",
            ), days, utcOffsetSeconds, seedKey, 13)
            emote = BriefingEmote.COLD
        }
        maxHigh >= 38.0 -> {
            headline = forecastPick(listOf(
                "这几天里会有极端高温，最热的一天要格外当心。",
                "最高温会冲到很吓人的水平，午后千万别硬扛。",
                "预报里有极端高温的日子，正午前后尽量别出门。",
                "接下来会热到让人发怵，防暑降温提前安排好。",
                "最热的一天会很夸张，空调和风扇都别省着。",
            ), days, utcOffsetSeconds, seedKey, 71)
            emote = BriefingEmote.HOT
        }
        minLow <= 0.0 -> {
            headline = forecastPick(listOf(
                "这阵子最低温会到冰点附近，路面可能结冰。",
                "后面有接近冰点的日子，早晚出门留意脚下。",
                "最低温会碰到冰点，水管和花草提前安顿好。",
                "接下来有接近冰点的低温，老人孩子出门多小心。",
                "预报里最低温贴着冰点走，保暖和防滑都要做。",
            ), days, utcOffsetSeconds, seedKey, 73)
            emote = BriefingEmote.COLD
        }
        heatDays >= 3 -> {
            headline = forecastPick(listOf(
                "接下来热天不少，午后出门记得补水防暑。",
                "高温会持续一阵子，正午前后尽量待在凉快的地方。",
                "这几天暑气不退，风扇和空调可以提前准备好。",
                "接下来有连着的高温天，老人和孩子多留意。",
                "热会持续，冰水、防晒、遮阳帽都用得上。",
                "高温天连着来，作息可以往早晚挪一挪。",
            ), days, utcOffsetSeconds, seedKey, 17)
            emote = BriefingEmote.HOT
        }
        tomorrowWet -> {
            headline = forecastPick(
                when {
                    tomorrowSnow -> listOf(
                        "明天可能下雪，路上慢一点，穿暖一点。",
                        "雪从明天开始，出行多留意路面。",
                        "明天有雪的可能，保暖和防滑先准备好。",
                    )
                    todayWet -> listOf(
                        "明天还有雨，伞先别收。",
                        "这雨还没下完，明天出门继续带伞。",
                        "明天还有雨雪，路上照旧慢一点。",
                    )
                    else -> listOf(
                        "明天就有雨，出门把伞装包里。",
                        "雨从明天开始，通勤路上记得带伞。",
                        "明天可能下雨，出门前多看我一眼。",
                    )
                },
                days, utcOffsetSeconds, seedKey, 79,
            )
            emote = BriefingEmote.RAIN
        }
        snowDays >= 1 -> {
            headline = forecastPick(listOf(
                "接下来有 ${snowDays} 天可能下雪，路面结冰要当心。",
                "雪已经在预报里了，保暖和防滑都提前准备好。",
                "后面会有降雪，出门早一点，路上慢一点。",
                "雪天已经在路上了，出门前先想好怎么穿。",
                "雪天不远了，鞋底防滑可以先看看。",
            ), days, utcOffsetSeconds, seedKey, 23)
            emote = BriefingEmote.RAIN
        }
        stormDays >= 2 -> {
            headline = forecastPick(listOf(
                "接下来雷雨天不少，听到雷声就回室内。",
                "后面有几天雷雨，别在高处和树下停留。",
                "雷雨会来报到几次，户外活动提前避一避。",
                "接下来有雷雨日，带伞的同时也留意雷声。",
            ), days, utcOffsetSeconds, seedKey, 83)
            emote = BriefingEmote.RAIN
        }
        weekendWet -> {
            headline = forecastPick(listOf(
                "这个周末有雨雪，出去玩前先看一眼预报。",
                "周末的天气不太给力，雨雪可能来串门。",
                "周末可能有雨雪，外出安排个备选更稳妥。",
                "周末和雨雪撞上了，出门记得带伞。",
            ), days, utcOffsetSeconds, seedKey, 89)
            emote = BriefingEmote.RAIN
        }
        wetDays >= maxOf(3, n / 3) -> {
            headline = forecastPick(listOf(
                "这段时间雨雪偏多，出门常备一把伞。",
                "接下来雨雪天不少，洗晒尽量挑晴天。",
                "雨雪会时不时来一下，包里放把伞更稳妥。",
                "晴雨来回切换，出门前记得看我一眼。",
                "雨雪天数不算少，晾晒都要先看天。",
                "接下来要常和雨雪打照面，伞放在顺手的地方。",
            ), days, utcOffsetSeconds, seedKey, 29)
            emote = BriefingEmote.RAIN
        }
        wetRuns >= 3 -> {
            headline = forecastPick(listOf(
                "天气会反复变脸，晴雨切换好几次。",
                "接下来晴一阵雨一阵，出门前瞄一眼预报。",
                "晴雨切换比较频繁，包里常备伞最省心。",
                "这几天天气爱变卦，出门前先问问我。",
            ), days, utcOffsetSeconds, seedKey, 97)
            emote = BriefingEmote.RAIN
        }
        trendCelsius >= 5.0 -> {
            headline = forecastPick(listOf(
                "接下来会明显回暖 ${magnitude}°，厚衣服可以歇一歇。",
                "后半段最高温平均会升 ${magnitude}° 左右，暖意会比较明显。",
                "后面会暖和不少，平均最高温能升 ${magnitude}° 左右。",
                "回暖比较明显，最高温平均升 ${magnitude}° 左右，被子可以晒晒。",
                "后面会有一波明显回暖，最高温平均升 ${magnitude}° 左右。",
                "接下来越走越暖，最高温平均高 ${magnitude}° 左右。",
            ), days, utcOffsetSeconds, seedKey, 19)
            emote = BriefingEmote.HOT
        }
        trendCelsius <= -2.0 -> {
            headline = forecastPick(listOf(
                "后半段会慢慢凉下来，早晚记得添一件。",
                "接下来会一点点转凉，薄外套可以备上。",
                "气温会慢慢往下走，早晚体感明显凉一些。",
                "后半段更凉，出门多带一件不亏。",
                "气温会一点点往下挪，凉意越来越明显。",
                "接下来凉意会加重，早晚温差要留意。",
            ), days, utcOffsetSeconds, seedKey, 31)
            emote = BriefingEmote.COLD
        }
        trendCelsius >= 2.0 -> {
            headline = forecastPick(listOf(
                "后半段会慢慢暖起来，中午会比现在热一些。",
                "接下来会一点点回暖，白天体感更舒服。",
                "气温会慢慢往上走，中午出门会热一点。",
                "后半段更暖，衣服可以穿得轻一点。",
                "后面几天会暖上一点，白天出门更舒服。",
                "气温会小步往上走，外套可以薄一点。",
            ), days, utcOffsetSeconds, seedKey, 37)
            emote = BriefingEmote.HOT
        }
        windyDays >= 4 -> {
            headline = forecastPick(listOf(
                "接下来风天不少，轻的东西别放窗边。",
                "后面几天风比较勤，骑车出门多留意侧风。",
                "风会经常来报到，晾晒记得夹紧。",
                "接下来风天偏多，围巾和帽子都用得上。",
                "风天会接二连三，出门前看看窗外的树就知道。",
                "接下来风不小，骑车出门提前挑条稳的路。",
            ), days, utcOffsetSeconds, seedKey, 41)
            emote = BriefingEmote.WIND
        }
        fogDays >= 2 || hazeSandDays >= 2 -> {
            headline = forecastPick(
                when {
                    fogDays >= 2 && hazeSandDays < 2 -> listOf(
                        "接下来雾天不少，开车出门多留神。",
                        "后面有几天雾，早晨出门给自己多留点时间。",
                        "雾会来几趟，开车记得开灯慢行。",
                    )
                    hazeSandDays >= 2 && fogDays < 2 -> listOf(
                        "接下来空气不太干净的日子不少，口罩提前备好。",
                        "后面有霾或沙尘的日子，敏感的人少在户外久待。",
                        "空气会浑浊几天，开窗前先看一眼空气指数。",
                    )
                    else -> listOf(
                        "接下来雾霾天会来几趟，防护和开车都要注意。",
                        "后面雾和霾轮着来，口罩和车灯都用得上。",
                        "雾霾会交替出现，呼吸道敏感的人多留意。",
                    )
                },
                days, utcOffsetSeconds, seedKey, 101,
            )
            emote = BriefingEmote.CLOUDY
        }
        widestRange >= 15.0 || bigRangeDays >= 5 -> {
            headline = forecastPick(listOf(
                "早晚温差拉得很大，出门多带一件，中午再脱。",
                "昼夜温差明显，穿衣按洋葱来最省心。",
                "温差大的日子不少，早出晚归记得添减衣服。",
                "接下来早晚冷、中午暖，穿衣分层最舒服。",
            ), days, utcOffsetSeconds, seedKey, 103)
            emote = if (sunnyDays * 2 >= n) BriefingEmote.SUNNY else BriefingEmote.CLOUDY
        }
        sunnyDays * 3 >= n * 2 -> {
            headline = forecastPick(listOf(
                "接下来晴天居多，洗晒和户外都能安排。",
                "这段时间阳光充足，适合出去玩的日子不少。",
                "晴天占了大半，出门记得防晒。",
                "天气以晴为主，心情可以跟着亮一点。",
                "阳光会常来值班，被子床单可以排队洗晒。",
                "接下来多数日子是晴天，紫外线也要留意。",
            ), days, utcOffsetSeconds, seedKey, 43)
            emote = BriefingEmote.SUNNY
        }
        cloudyDays * 3 >= n * 2 -> {
            headline = forecastPick(listOf(
                "接下来阴天偏多，光线会暗一些。",
                "后面多云和阴天占多数，太阳露面少。",
                "阴天会多起来，晾晒的时间要挑一挑。",
                "接下来天色偏沉，但天气本身还算安稳。",
            ), days, utcOffsetSeconds, seedKey, 107)
            emote = BriefingEmote.CLOUDY
        }
        else -> {
            headline = forecastPick(listOf(
                "接下来这些天天气整体平稳，安排出行不用太纠结。",
                "这段时间天气没什么大动静，按计划来就行。",
                "未来几天气温起伏不大，日常安排照旧。",
                "接下来天气比较省心，该干嘛干嘛。",
                "天气暂时平稳，有变化我会第一时间写在这里。",
                "接下来是安安稳稳的天气，日子照常过。",
            ), days, utcOffsetSeconds, seedKey, 47)
            emote = if (sunnyDays * 2 >= n) BriefingEmote.SUNNY else BriefingEmote.CLOUDY
        }
    }

    val highText = hottest?.high?.let { "${Fmt.temp(it, tempUnit)}°" } ?: "—"
    val lowText = coldest?.low?.let { "${Fmt.temp(it, tempUnit)}°" } ?: "—"
    val firstYear = days.firstOrNull()?.let {
        Instant.ofEpochMilli(it.dateMillis).atZone(Fmt.zoneId(utcOffsetSeconds)).toLocalDate().year
    }

    val temperatureCopy = if (hottest != null && coldest != null) {
        forecastPick(listOf(
            "白天最高大约 $highText，夜里最低大约 $lowText。",
            "这几天最高能到 $highText，最低会到 $lowText 左右。",
            "白天最高在 $highText 上下，夜里最冷大约 $lowText。",
            "白天最高 $highText 左右，夜里最低能到 $lowText。",
        ), days, utcOffsetSeconds, seedKey, 53)
    } else {
        "温度数据还不够完整，先看看每天的天气变化吧。"
    }
    val wetCopy = when {
        rainDays >= 1 && snowDays >= 1 ->
            "我看到有 $rainDays 天可能下雨，还有 $snowDays 天可能飘雪，伞和厚衣服都留意。"
        rainDays >= 1 -> forecastPick(listOf(
            "我看到有 $rainDays 天可能下雨，伞先别收得太远。",
            "我看到有 $rainDays 天可能下雨，出门前多看我一眼。",
            "我看到有 $rainDays 天可能下雨，洗晒挑没雨的日子。",
            "我看到有 $rainDays 天可能下雨，那几天出门带把伞。",
        ), days, utcOffsetSeconds, seedKey, 59)
        snowDays >= 1 ->
            "我看到有 $snowDays 天可能下雪，路面结冰要当心。"
        else -> forecastPick(listOf(
            "目前没看到明显雨雪，洗晒和出行都比较省心。",
            "这段时间没有明显的雨雪，行程可以放心安排。",
            "接下来没看到雨雪的影子，出行和洗晒都放心。",
        ), days, utcOffsetSeconds, seedKey, 61)
    }
    val overview = "$temperatureCopy$wetCopy"

    val note = forecastPick(listOf(
        "越往后的天气越容易变，这页每天打开都会是新的。",
        "后半程的预报准确度会下降，我每天都会把新预报写进来。",
        "预报越往后越容易变，临近的日子我会说得更准。",
        "预报越往后越飘，前三天最靠谱。",
    ), days, utcOffsetSeconds, seedKey, 67)

    val rangeValue = widest?.let { day ->
        val range = requireNotNull(day.high) - requireNotNull(day.low)
        val displayRange = if (tempUnit == "f") range * 9.0 / 5.0 else range
        "${kotlin.math.round(displayRange).toInt()}°"
    } ?: "—"
    return ForecastDigest(
        dayCount = days.size,
        headline = headline,
        overview = overview,
        note = note,
        emote = emote,
        highValue = highText,
        highDate = hottest?.let { forecastMetricDate(it, utcOffsetSeconds, firstYear) } ?: "—",
        lowValue = lowText,
        lowDate = coldest?.let { forecastMetricDate(it, utcOffsetSeconds, firstYear) } ?: "—",
        rainValue = "$wetDays 日",
        rangeValue = rangeValue,
    )
}

// 简报轮换：按首个日期 + 天数 + 城市取种子，同一天同一城市句子稳定，跨天自然换句。
private fun forecastPick(
    pool: List<String>,
    days: List<DailyWeather>,
    utcOffsetSeconds: Int?,
    seedKey: String,
    salt: Int,
): String {
    val firstEpochDay = days.firstOrNull()?.let {
        Instant.ofEpochMilli(it.dateMillis).atZone(Fmt.zoneId(utcOffsetSeconds)).toLocalDate().toEpochDay()
    } ?: 0L
    val seed = firstEpochDay * 31L + days.size * 7L + seedKey.hashCode().toLong() * 13L + salt
    return pool[Math.floorMod(seed, pool.size.toLong()).toInt()]
}

// 简报指标日期：与首日同年用 M/d，跨年才带上年份，避免 12 月到 1 月的日期分不清。
private fun forecastMetricDate(day: DailyWeather, utcOffsetSeconds: Int?, firstYear: Int?): String {
    val date = Instant.ofEpochMilli(day.dateMillis).atZone(Fmt.zoneId(utcOffsetSeconds)).toLocalDate()
    return if (firstYear != null && date.year != firstYear) {
        date.format(DateTimeFormatter.ofPattern("yyyy/M/d", Locale.US))
    } else {
        date.format(DateTimeFormatter.ofPattern("M/d", Locale.US))
    }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

internal fun forecastHasRainSignal(day: DailyWeather): Boolean =
    day.condition?.isPrecipitation == true ||
        (day.precipProbability ?: 0) >= 30 ||
        day.weatherText.orEmpty().let { "雨" in it || "雪" in it }

@Composable
private fun ForecastSummaryPanel(
    digest: ForecastDigest,
    modifier: Modifier = Modifier,
) {
    val placement = forecastGirlPlacement(digest.emote)
    TerminalPanel(modifier) {
        Column {
            Box(Modifier.fillMaxWidth().heightIn(min = 150.dp)) {
                Image(
                    painter = painterResource(forecastGirlRes(digest.emote)),
                    contentDescription = null,
                    modifier = Modifier
                        // 固定头像相对标题的视觉基线，避免正文行数变化时人物跟着下沉。
                        .align(Alignment.TopStart)
                        .offset(x = placement.x.dp, y = placement.y.dp)
                        .size(128.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 116.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
                ) {
                    Text(
                        "枳生天气娘 · 未来${digest.dayCount}日",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengOrange,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        digest.headline,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = ZhishengReading),
                        color = ZhishengText,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        digest.overview,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = ZhishengReading),
                        color = ZhishengTextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ForecastMetric("最热", "${digest.highValue} · ${digest.highDate}", Modifier.weight(1f))
                        ForecastMetric("最冷", "${digest.lowValue} · ${digest.lowDate}", Modifier.weight(1f))
                        ForecastMetric("雨雪", digest.rainValue, Modifier.weight(1f))
                        ForecastMetric("温差", digest.rangeValue, Modifier.weight(1f))
                    }
                }
            }
            HorizontalDivider(color = ZhishengCardBorder, thickness = 1.dp)
            Text(
                digest.note,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = ZhishengReading),
                color = ZhishengTextTertiary,
            )
        }
    }
}

@Composable
private fun ForecastMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = ZhishengMint,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class ForecastGirlPlacement(val x: Int, val y: Int)

private fun forecastGirlPlacement(emote: BriefingEmote): ForecastGirlPlacement = when (emote) {
    BriefingEmote.SUNNY -> ForecastGirlPlacement(-14, 4)
    BriefingEmote.CLOUDY -> ForecastGirlPlacement(-10, 4)
    BriefingEmote.RAIN -> ForecastGirlPlacement(-4, 4)
    BriefingEmote.HOT -> ForecastGirlPlacement(2, 4)
    BriefingEmote.COLD -> ForecastGirlPlacement(-14, 5)
    BriefingEmote.WIND -> ForecastGirlPlacement(-9, 5)
    BriefingEmote.NIGHT -> ForecastGirlPlacement(-1, 5)
    BriefingEmote.ALERT -> ForecastGirlPlacement(4, 5)
}

private fun forecastGirlRes(emote: BriefingEmote): Int = when (emote) {
    BriefingEmote.SUNNY -> R.drawable.weather_girl_emote_sunny
    BriefingEmote.CLOUDY -> R.drawable.weather_girl_emote_cloudy
    BriefingEmote.RAIN -> R.drawable.weather_girl_emote_rain
    BriefingEmote.HOT -> R.drawable.weather_girl_emote_hot
    BriefingEmote.COLD -> R.drawable.weather_girl_emote_cold
    BriefingEmote.WIND -> R.drawable.weather_girl_emote_wind
    BriefingEmote.NIGHT -> R.drawable.weather_girl_emote_night
    BriefingEmote.ALERT -> R.drawable.weather_girl_emote_alert
}
