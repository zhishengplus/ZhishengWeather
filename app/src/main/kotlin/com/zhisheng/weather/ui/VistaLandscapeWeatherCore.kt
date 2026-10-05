@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.zhisheng.weather.ui

import com.zhisheng.weather.ui.home.homeWeatherUpdateStamp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.R
import com.zhisheng.weather.model.phaseAwareCondition
import com.zhisheng.weather.ui.components.*
import com.zhisheng.weather.ui.home.dataSourceShortLabel
import com.zhisheng.weather.ui.theme.*

/** Same weather identity as portrait. Clock remains available without dominating the weather. */
@Composable
internal fun VistaLandscapeWeatherCore(
    state: HomeUiState, now: Long, night: Boolean, clock: String, seconds: String, date: String,
    onRefresh: () -> Unit, onPortrait: () -> Unit, onSettings: () -> Unit,
) {
    val data = state.weather
    val current = data?.current
    val today = data?.todayDaily(now)
    val offset = data?.utcOffsetSeconds
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val hours = data?.hourly.orEmpty().distinctBy { it.timeMillis }.sortedBy { it.timeMillis }
        .filter { it.timeMillis >= now }.take(6)
    NaturalWeatherSurface(data, state.prefs.ambience, night, Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp)) {
            val twoColumns = maxWidth >= 620.dp * scale
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.selectedCity?.displayName ?: "枳生天气", Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge, color = ZhishengText)
                    IconButton(onRefresh, Modifier.size(48.dp)) {
                        PhosphorIcon(R.drawable.ph_arrow_clockwise, if (state.loading) "正在更新天气" else "刷新天气", Modifier.size(22.dp), ZhishengTextSecondary)
                    }
                    StandbyPortraitButton(onPortrait)
                    IconButton(onSettings, Modifier.size(48.dp)) {
                        PhosphorIcon(R.drawable.ph_gear, "设置", Modifier.size(22.dp), ZhishengTextSecondary)
                    }
                }
                val overview: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(date, style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(clock, fontSize = 34.sp, fontWeight = FontWeight.Light, color = ZhishengTextSecondary)
                                    Text("${seconds}秒", Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                                }
                            }
                            WeatherIcon(data?.let { phaseAwareCondition(current?.condition, it, now) } ?: current?.condition,
                                Modifier.weatherSharedBounds("current-condition").size(72.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(Fmt.temp(current?.temperature, state.tempUnit) ?: "—",
                                    Modifier.weatherSharedBounds("current-temperature"), fontSize = 68.sp,
                                    lineHeight = 72.sp, fontWeight = FontWeight.Light, color = ZhishengText)
                                Text("°", color = ZhishengTextSecondary, fontSize = 28.sp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(current?.weatherText ?: current?.condition?.label ?: "天气", color = ZhishengText,
                                    style = MaterialTheme.typography.titleLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Fmt.temp(today?.low, state.tempUnit)?.let { Text("最低 $it°", style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary) }
                                    Fmt.temp(today?.high, state.tempUnit)?.let { Text("最高 $it°", style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary) }
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Fmt.temp(current?.feelsLike, state.tempUnit)?.let { LandscapeFact(R.drawable.ph_thermometer, "体感 $it°") }
                            Fmt.wind(current?.windSpeed, state.prefs.windUnit)?.let { LandscapeFact(R.drawable.ph_wind, it) }
                            current?.windDirectionDeg?.let { LandscapeFact(R.drawable.ph_compass, "风向 ${it.toInt()}°") }
                            data?.aqi?.value?.let { LandscapeFact(R.drawable.ph_leaf, "空气质量 $it") }
                            current?.humidity?.let { LandscapeFact(R.drawable.ph_drop, "湿度 ${it.toInt()}%") }
                        }
                        data?.alerts.orEmpty().forEach { alert ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PhosphorIcon(R.drawable.ph_warning, "预警", Modifier.size(20.dp), ZhishengOrange)
                                Text(alert.title, style = MaterialTheme.typography.bodySmall, color = ZhishengOrange)
                            }
                        }
                    }
                }
                val details: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (hours.isNotEmpty()) Column(Modifier.fillMaxWidth().zhishengPanel().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("逐时天气", color = ZhishengText, style = MaterialTheme.typography.titleSmall)
                            BoxWithConstraints {
                                val trackWidth = maxOf(maxWidth, 48.dp * scale * hours.size)
                                Column(Modifier.horizontalScroll(rememberScrollState())) {
                                    Column(Modifier.width(trackWidth)) {
                                        WeatherVectorGraph(hours, state.tempUnit)
                                        Row {
                                            hours.forEach { hour ->
                                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(Fmt.hour(hour.timeMillis, offset), color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
                                                    WeatherIcon(hour.condition, Modifier.size(24.dp))
                                                    Fmt.temp(hour.temperature, state.tempUnit)?.let { Text("$it°", color = ZhishengText, style = MaterialTheme.typography.titleSmall) }
                                                    hour.precipProb?.takeIf { it in 0..100 }?.let { Text("$it%", color = ZhishengCyan, style = MaterialTheme.typography.labelSmall) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (hours.any { it.precipProb != null }) Text("百分比为降水概率", color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
                        }
                        if (today?.sunrise != null && today.sunset != null) {
                            val cityMinute = java.time.Instant.ofEpochMilli(now).atZone(com.zhisheng.weather.model.cityZone(offset)).let { it.hour * 60 + it.minute }
                            WeatherCoreSunTrack(today, cityMinute)
                        }
                    }
                }
                if (twoColumns) Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.weight(1f)) { overview() }
                    Box(Modifier.weight(1f)) { details() }
                } else { overview(); details() }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.selectedCity?.let { Text("经纬度 ${Fmt.coordinates(it.latitude, it.longitude)}", color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall) }
                    val stamp = homeWeatherUpdateStamp(data)
                    Text("数据源 ${dataSourceShortLabel(data?.dataSource)}" + (stamp?.let { " · ${it.label} ${Fmt.stamp(it.timeMillis, offset)}" } ?: ""),
                        color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
                    if (data?.fetchedAt != null) data.updateTime?.let {
                        Text("数据发布于 ${Fmt.stamp(it, offset)}", color = ZhishengTextTertiary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun LandscapeFact(icon: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PhosphorIcon(icon, null, Modifier.size(17.dp), ZhishengTextTertiary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = ZhishengTextSecondary)
    }
}
