@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.zhisheng.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.theme.*
import java.util.Locale

@Composable
internal fun VistaForecastScreen(
    city: City?, days: List<DailyWeather>, yesterday: YesterdayInfo?,
    tempUnit: String, windUnit: String, utcOffsetSeconds: Int?, onBack: () -> Unit,
) {
    val chrome = LocalZhishengChrome.current
    val nowMillis = weatherPresentationTime()
    val track = (listOfNotNull(yesterdayForecastDay(yesterday, utcOffsetSeconds, nowMillis)) + days)
        .distinctBy { it.dateMillis }.sortedBy { it.dateMillis }
    var selectedDate by rememberSaveable(city?.locationKey) {
        mutableStateOf(days.firstOrNull()?.dateMillis ?: track.firstOrNull()?.dateMillis)
    }
    val selected = track.firstOrNull { it.dateMillis == selectedDate } ?: days.firstOrNull() ?: track.firstOrNull()
    Column(Modifier.fillMaxSize().zhishengScreen().statusBarsPadding().navigationBarsPadding()) {
        FeaturePageHeader(if (days.isEmpty()) "多天天气" else "${days.size}日天气预报", "", onBack)
        LazyColumn(Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = chrome.pagePadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                city?.displayName?.let { Text(it, color = ZhishengTextSecondary, style = MaterialTheme.typography.bodyMedium) }
            }
            if (selected == null) {
                item { Text("暂无逐日预报，返回主页刷新后再试", color = ZhishengTextSecondary) }
            } else {
                item {
                    ForecastDateTrack(track, selected.dateMillis, tempUnit, utcOffsetSeconds) { selectedDate = it }
                }
                item {
                    val visual = forecastDayNightVisual(selected)
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${forecastTemporalLabel(selected.dateMillis, utcOffsetSeconds)} · ${forecastDateLabel(selected.dateMillis, utcOffsetSeconds)}",
                                    color = ZhishengTextSecondary, style = MaterialTheme.typography.bodyMedium)
                                Text(selected.weatherText ?: selected.condition?.label ?: "天气预报",
                                    color = ZhishengText, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                            }
                            WeatherIcon(visual.dayCondition, Modifier.size(72.dp))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Fmt.temp(selected.low, tempUnit)?.let { Text("最低 $it°", color = ZhishengText, style = MaterialTheme.typography.titleLarge) }
                            Fmt.temp(selected.high, tempUnit)?.let { Text("最高 $it°", color = ZhishengText, style = MaterialTheme.typography.titleLarge) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            ForecastPhase("白天", visual.dayLabel, visual.dayCondition, Modifier.weight(1f))
                            ForecastPhase("夜间", visual.nightLabel, visual.nightCondition, Modifier.weight(1f))
                        }
                    }
                }
                if (vistaForecastFacts(selected, windUnit).isNotEmpty()) item {
                    Column(Modifier.fillMaxWidth().zhishengPanel().padding(chrome.panelPadding),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("当日详情", color = ZhishengText, fontWeight = FontWeight.SemiBold)
                        val details = vistaForecastFacts(selected, windUnit)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp), maxItemsInEachRow = 2) {
                            details.forEach { (label, value) ->
                                Column(Modifier.widthIn(min = 120.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(label, color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
                                    Text(value, color = ZhishengText, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
                if (days.isNotEmpty()) item {
                    val digest = buildForecastDigest(days, tempUnit, utcOffsetSeconds, city?.locationKey.orEmpty(), nowMillis)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("天气娘简报", color = ZhishengTextSecondary, style = MaterialTheme.typography.labelLarge)
                        Text(digest.headline, color = ZhishengText, style = MaterialTheme.typography.bodyLarge)
                        Text(digest.overview, color = ZhishengTextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ForecastPhase(label: String, description: String, condition: WeatherCondition, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WeatherIcon(condition, Modifier.size(30.dp))
        Column {
            Text(label, color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
            Text(description, color = ZhishengTextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Every date stays selectable. Sparse/missing temperatures break the curve, never bridge it. */
@Composable
private fun ForecastDateTrack(days: List<DailyWeather>, selected: Long, unit: String, offset: Int?, onSelect: (Long) -> Unit) {
    val palette = LocalZhishengPalette.current
    val column = 68.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val scroll = rememberScrollState()
    val values = days.flatMap { listOfNotNull(it.low, it.high) }.filter(Double::isFinite)
    val min = (values.minOrNull() ?: 0.0) - 2
    val max = (values.maxOrNull() ?: min + 1) + 2
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("温度变化", color = ZhishengText, fontWeight = FontWeight.SemiBold)
            Text("最高 / 最低", color = ZhishengTextSecondary, style = MaterialTheme.typography.labelMedium)
        }
        Column(Modifier.fillMaxWidth().horizontalScroll(scroll)) {
            Canvas(Modifier.width(column * days.size).height(100.dp)) {
                val inset = 8.dp.toPx()
                fun x(index: Int) = column.toPx() * (index + 0.5f)
                fun y(value: Double) = size.height - inset - ((value - min) / (max - min)).toFloat() * (size.height - inset * 2)
                val active = days.indexOfFirst { it.dateMillis == selected }
                if (active >= 0) drawLine(palette.textTertiary.copy(alpha = 0.25f),
                    Offset(x(active), 0f), Offset(x(active), size.height), 1.dp.toPx())
                listOf(DailyWeather::high to palette.orange, DailyWeather::low to palette.cyan).forEach { (valueOf, color) ->
                    val path = Path()
                    var connected = false
                    days.forEachIndexed { index, day ->
                        val value = valueOf(day)?.takeIf(Double::isFinite)
                        if (value == null) connected = false
                        else {
                            if (connected) path.lineTo(x(index), y(value)) else path.moveTo(x(index), y(value))
                            connected = true
                            drawCircle(color, if (index == active) 4.dp.toPx() else 2.dp.toPx(), Offset(x(index), y(value)))
                        }
                    }
                    drawPath(path, color, style = Stroke(2.dp.toPx()))
                }
            }
            Row(Modifier.selectableGroup()) {
                days.forEach { day ->
                    val active = day.dateMillis == selected
                    Column(Modifier.width(column).selectable(active, role = Role.Tab, onClick = { onSelect(day.dateMillis) })
                        .padding(horizontal = 4.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(forecastTemporalLabel(day.dateMillis, offset), color = if (active) palette.mint else palette.textSecondary,
                            style = MaterialTheme.typography.labelLarge)
                        Text(forecastDateLabel(day.dateMillis, offset), color = palette.textTertiary,
                            style = MaterialTheme.typography.labelSmall)
                        WeatherIcon(forecastDayNightVisual(day).dayCondition, Modifier.size(28.dp))
                        Text(day.weatherText?.takeIf { it.isNotBlank() } ?: day.condition?.label ?: "暂无天气",
                            color = palette.textSecondary, style = MaterialTheme.typography.labelSmall,
                            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("${Fmt.temp(day.high, unit) ?: "—"}°", color = palette.orange,
                            style = MaterialTheme.typography.titleSmall)
                        Text("${Fmt.temp(day.low, unit) ?: "—"}°", color = palette.cyan,
                            style = MaterialTheme.typography.titleSmall)
                        Box(Modifier.width(20.dp).height(2.dp).background(if (active) palette.mint else androidx.compose.ui.graphics.Color.Transparent))
                    }
                }
            }
        }
    }
}

internal fun vistaForecastFacts(day: DailyWeather, windUnit: String): List<Pair<String, String>> = buildList {
    val wind = listOfNotNull(com.zhisheng.weather.data.WeatherRepository.windDirection(day.windDirectionDeg),
        day.windSpeed?.takeIf { it.isFinite() && it >= 0 }?.let { Fmt.wind(it, windUnit) })
    if (wind.isNotEmpty()) add("风向风速" to wind.joinToString(" · "))
    day.windGust?.takeIf { it.isFinite() && it >= 0 }?.let { Fmt.wind(it, windUnit) }?.let { add("阵风" to it) }
    day.precipProbability?.takeIf { it in 0..100 }?.let { add("降水概率" to "$it%") }
    day.precipMm?.takeIf { it.isFinite() && it >= 0 }?.let { add("降水量" to String.format(Locale.US, "%.1f mm", it)) }
    day.aqi?.takeIf { it in 0..1000 }?.let { add("空气质量指数" to it.toString()) }
    day.humidity?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let { add("湿度" to "${it.toInt()}%") }
    day.cloudCover?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let { add("云量" to "${it.toInt()}%") }
    day.uvIndex?.takeIf { it >= 0 }?.let { add("紫外线指数" to it.toString()) }
    day.sunrise?.takeIf(String::isNotBlank)?.let { add("日出" to it) }
    day.sunset?.takeIf(String::isNotBlank)?.let { add("日落" to it) }
    Fmt.moonPhaseZh(day.moonPhase)?.let { add("月相" to it) }
    day.moonrise?.takeIf(String::isNotBlank)?.let { add("月出" to it) }
    day.moonset?.takeIf(String::isNotBlank)?.let { add("月落" to it) }
}
