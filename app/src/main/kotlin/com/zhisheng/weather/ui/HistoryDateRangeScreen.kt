@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.zhisheng.weather.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.HistoricalWeatherRepository
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import com.zhisheng.weather.ui.theme.*
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun HistoryDateRangeScreen(city: City?, today: LocalDate, tempUnit: String, windUnit: String) {
    var startEpoch by rememberSaveable(city?.locationKey) { mutableLongStateOf(today.minusDays(1).toEpochDay()) }
    var endEpoch by rememberSaveable(city?.locationKey) { mutableLongStateOf(startEpoch) }
    val start = LocalDate.ofEpochDay(startEpoch); val end = LocalDate.ofEpochDay(endEpoch)
    var picker by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember(city?.locationKey, start, end, retry) { mutableStateOf(true) }
    var result by remember(city?.locationKey, start, end, retry) { mutableStateOf<HistoricalDateRange?>(null) }
    var failure by remember(city?.locationKey, start, end, retry) { mutableStateOf<String?>(null) }
    LaunchedEffect(city?.locationKey, start, end, retry) {
        loading = true; result = null; failure = null
        if (city == null) { loading = false; failure = "先在主页选择一座城市"; return@LaunchedEffect }
        try { result = HistoricalWeatherRepository.loadDateRange(city, start, end, today) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failure = "历史天气读取失败，请检查网络后重试" }
        finally { loading = false }
    }
    if (picker) HistoryRangePicker(start, end, today, { picker = false }) { a, b ->
        startEpoch = a.toEpochDay(); endEpoch = b.toEpochDay(); picker = false
    }
    val chrome = LocalZhishengChrome.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(chrome.pagePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().zhishengPanel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("按日期找天气", color = ZhishengText, style = MaterialTheme.typography.titleLarge)
                Text(if (start == end) start.toString() else "$start — $end", color = ZhishengMint, style = MaterialTheme.typography.titleMedium)
                Text("直接查询选定年份；支持单日、日期段和整月。", color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { picker = true }) { Text("选择年、月、日", color = ZhishengText) }
            }
        }
        if (loading) item { Text("正在读取所选日期…", color = ZhishengTextSecondary) }
        failure?.let { message -> item {
            Column { Text(message, color = ZhishengOrange); TextButton(onClick = { retry++ }) { Text("重新读取") } }
        } }
        result?.let { range ->
            if (range.missingCount > 0) item {
                Text("${range.missingCount} 天暂无可用记录，空缺未补成零；近期档案可能尚未齐全。", color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { retry++ }) { Text("重新读取") }
            }
            val byDate = range.days.associateBy { it.localDate }
            items(range.dates, key = { it.toEpochDay() }) { date ->
                val day = byDate[date]
                Column(Modifier.fillMaxWidth().zhishengPanel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)), color = ZhishengText, style = MaterialTheme.typography.titleMedium)
                            Text(day?.condition?.label ?: "暂无记录", color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        if (day != null) WeatherIcon(day.condition, Modifier.size(36.dp))
                    }
                    if (day != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            day.high?.let { Text("最高 ${Fmt.temp(it, tempUnit)}°", color = ZhishengText) }
                            day.low?.let { Text("最低 ${Fmt.temp(it, tempUnit)}°", color = ZhishengTextSecondary) }
                            if (day.high == null && day.low == null) day.mean?.let { Text("平均 ${Fmt.temp(it, tempUnit)}°", color = ZhishengText) }
                            day.precipitationMm?.let { Text("降水 ${String.format(Locale.CHINA, "%.1f", it)} mm", color = ZhishengCyan) }
                            day.windMaxKmh?.let { Text("最大风速 ${Fmt.wind(it, windUnit)}", color = ZhishengTextSecondary) }
                            day.gustMaxKmh?.let { Text("最大阵风 ${Fmt.wind(it, windUnit)}", color = ZhishengTextSecondary) }
                        }
                    }
                }
            }
            item { WeatherSourceDisclosure("Open-Meteo · 历史天气", "历史记录来自格点化再分析，近期七天使用近期档案，并非单站原始观测。只展示实际返回的日期和数值；不提供未来历史记录。") }
        }
    }
}

@Composable
internal fun HistoryRangePicker(start: LocalDate, end: LocalDate, today: LocalDate, onDismiss: () -> Unit,
    onSave: (LocalDate, LocalDate) -> Unit) {
    var mode by rememberSaveable { mutableIntStateOf(if (start == end) 0 else 1) }
    var from by rememberSaveable { mutableStateOf(listOf(start.year.toString(), start.monthValue.toString(), start.dayOfMonth.toString())) }
    var to by rememberSaveable { mutableStateOf(listOf(end.year.toString(), end.monthValue.toString(), end.dayOfMonth.toString())) }
    var error by remember { mutableStateOf<String?>(null) }
    WeatherOverlayDialog(onDismiss) { modifier ->
        Column(modifier.zhishengPanel()) {
            LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text("选择历史日期", color = ZhishengText, style = MaterialTheme.typography.titleLarge) }
                item { WeatherFeatureTabs(listOf("单日", "区间", "整月"), mode) { mode = it; error = null } }
                item { Text(if (mode == 1) "开始日期" else if (mode == 2) "选择年月" else "选择日期", color = ZhishengTextSecondary) }
                item { HistoryDateFields(from, mode != 2) { from = it; error = null } }
                if (mode == 1) {
                    item { Text("结束日期", color = ZhishengTextSecondary) }
                    item { HistoryDateFields(to, true) { to = it; error = null } }
                }
                item { Text("可查询1940年起、今天以前的日期，每次最多366天。", color = ZhishengTextTertiary, style = MaterialTheme.typography.bodySmall) }
                error?.let { message -> item { Text(message, color = ZhishengOrange) } }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消", color = ZhishengTextSecondary) }
                TextButton(onClick = {
                    val a = runCatching { LocalDate.of(from[0].toInt(), from[1].toInt(), if (mode == 2) 1 else from[2].toInt()) }.getOrNull()
                    val b = when (mode) {
                        1 -> runCatching { LocalDate.of(to[0].toInt(), to[1].toInt(), to[2].toInt()) }.getOrNull()
                        2 -> a?.let { minOf(YearMonth.from(it).atEndOfMonth(), today.minusDays(1)) }
                        else -> a
                    }
                    if (a == null || b == null) error = "请输入有效年月日，注意该年的闰日"
                    else { error = historyDateRangeError(a, b, today); if (error == null) onSave(a, b) }
                }) { Text("查看天气", color = ZhishengText) }
            }
        }
    }
}

@Composable
private fun HistoryDateFields(values: List<String>, withDay: Boolean, onChange: (List<String>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("年", "月", "日").take(if (withDay) 3 else 2).forEachIndexed { i, label ->
            OutlinedTextField(values[i], { text -> if (text.length <= (if (i == 0) 4 else 2) && text.all(Char::isDigit))
                onChange(values.toMutableList().apply { this[i] = text }) }, Modifier.weight(if (i == 0) 1.4f else 1f),
                label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ZhishengText, unfocusedTextColor = ZhishengText,
                    focusedBorderColor = ZhishengMint, unfocusedBorderColor = ZhishengCardBorder))
        }
    }
}
