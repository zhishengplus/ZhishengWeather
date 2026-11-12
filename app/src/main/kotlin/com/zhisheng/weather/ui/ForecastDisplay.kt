@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.zhisheng.weather.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.theme.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.forecastDisplayStore by preferencesDataStore(name = "forecast_display")
private val verticalKey = booleanPreferencesKey("vertical")

@Composable
internal fun rememberForecastVertical(): Pair<Boolean, (Boolean) -> Unit> {
    if (LocalInspectionMode.current) return false to {}
    val context = LocalContext.current.applicationContext
    val flow = remember(context) { context.forecastDisplayStore.data.map { it[verticalKey] ?: false }.catch { if (it is IOException) emit(false) else throw it } }
    val vertical by flow.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    return vertical to { next -> scope.launch {
        try { context.forecastDisplayStore.edit { it[verticalKey] = next } } catch (_: IOException) { }
    }; Unit }
}

@Composable
internal fun ForecastLayoutToggle(vertical: Boolean, onChange: (Boolean) -> Unit) {
    WeatherFeatureTabs(listOf("横向", "纵向"), if (vertical) 1 else 0) { onChange(it == 1) }
}

/** The same fact set serves home expansion and both full forecast orientations. */
@Composable
internal fun ForecastDayFacts(day: DailyWeather, windUnit: String, modifier: Modifier = Modifier) {
    val facts = vistaForecastFacts(day, windUnit)
    if (facts.isEmpty()) return
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), maxItemsInEachRow = 2) {
        facts.forEach { (label, value) ->
            Column(Modifier.widthIn(min = 110.dp, max = 180.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
                Text(value, color = ZhishengText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
internal fun ForecastDayList(days: List<DailyWeather>, selectedMillis: Long?, unit: String, windUnit: String,
    offset: Int?, onSelect: (Long) -> Unit) {
    var expandedDate by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        days.forEach { day ->
            val selected = day.dateMillis == selectedMillis
            Column(Modifier.fillMaxWidth().zhishengCompactPanel()
                .clickable(role = Role.Button) { expandedDate = if (expandedDate == day.dateMillis) null else day.dateMillis; onSelect(day.dateMillis) }.semantics { this.selected = selected }
                .padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.width(62.dp)) {
                        Text(forecastTemporalLabel(day.dateMillis, offset), color = if (selected) ZhishengMint else ZhishengText,
                            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                        Text(forecastDateLabel(day.dateMillis, offset), color = ZhishengTextTertiary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    WeatherIcon(day.condition, Modifier.size(28.dp))
                    Text(day.weatherText?.takeIf(String::isNotBlank) ?: day.condition?.label ?: "天气暂缺",
                        Modifier.weight(1f), color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
                    Column(horizontalAlignment = Alignment.End) {
                        Text((Fmt.temp(day.high, unit) ?: "—") + "°", color = ZhishengText, style = MaterialTheme.typography.titleMedium)
                        Text((Fmt.temp(day.low, unit) ?: "—") + "°", color = ZhishengTextTertiary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (expandedDate == day.dateMillis) ForecastDayFacts(day, windUnit)
            }
        }
    }
}
