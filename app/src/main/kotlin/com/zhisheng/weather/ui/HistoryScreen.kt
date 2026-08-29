package com.zhisheng.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.data.HistoricalWeatherRepository
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.HistoricalDay
import com.zhisheng.weather.model.HistoricalReview
import com.zhisheng.weather.model.cityZone
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCard
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface HistoryScreenState {
    data class Loading(val completed: Int, val total: Int) : HistoryScreenState
    data class Ready(val review: HistoricalReview) : HistoryScreenState
    data class Error(val message: String) : HistoryScreenState
}

@Composable
fun HistoryScreen(
    city: City?,
    tempUnit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
    onBack: () -> Unit,
) {
    var years by rememberSaveable { mutableIntStateOf(5) }
    var retry by remember { mutableIntStateOf(0) }
    var backgroundProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val referenceDate = remember(utcOffsetSeconds) { LocalDate.now(cityZone(utcOffsetSeconds)) }
    var state by remember(city?.locationKey, referenceDate, retry) {
        mutableStateOf<HistoryScreenState>(HistoryScreenState.Loading(0, years))
    }

    LaunchedEffect(city?.locationKey, referenceDate, years, retry) {
        if (city == null) {
            state = HistoryScreenState.Error("先在主页选择一座城市")
            return@LaunchedEffect
        }
        val keepCurrentContent = state is HistoryScreenState.Ready
        if (keepCurrentContent) backgroundProgress = 0 to years
        else state = HistoryScreenState.Loading(0, years)
        state = try {
            val review = HistoricalWeatherRepository.loadReview(city, referenceDate, years) { done, total ->
                if (keepCurrentContent) backgroundProgress = done to total
                else state = HistoryScreenState.Loading(done, total)
            }
            if (review.days.isEmpty()) HistoryScreenState.Error("历史数据暂时没有返回")
            else HistoryScreenState.Ready(review)
        } catch (_: Exception) {
            if (keepCurrentContent) state else HistoryScreenState.Error("历史数据连接失败")
        } finally {
            backgroundProgress = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(ZhishengBg)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        FeaturePageHeader("往年回顾", "WEATHER ARCHIVE", onBack)
        when (val current = state) {
            is HistoryScreenState.Loading -> HistoryLoading(current.completed, current.total)
            is HistoryScreenState.Error -> FeatureErrorState(
                title = current.message,
                action = "重新读取",
                onAction = { retry++ },
            )
            is HistoryScreenState.Ready -> HistoryContent(
                city = city!!,
                review = current.review,
                tempUnit = tempUnit,
                windUnit = windUnit,
                years = years,
                backgroundProgress = backgroundProgress,
                onYearsChange = { years = it },
            )
        }
    }
}

@Composable
private fun HistoryLoading(completed: Int, total: Int) {
    FeatureBootLoader(
        channel = "WEATHER ARCHIVE",
        lines = listOf(
            "ARCHIVE PORT ........ OPEN",
            "RESOLVE DATE INDEX ... OK",
            "FETCH CLIMATE RECORDS ...",
            "BUILD YEAR TRACE ..... ${if (total == 0) "--" else "$completed/$total"}",
        ),
        status = if (completed == 0) "正在定位这一天的往年天气" else "已找到 $completed 年，继续补齐其余年份",
        progress = if (total > 0) completed / total.toFloat() else 0f,
    )
}

@Composable
private fun HistoryContent(
    city: City,
    review: HistoricalReview,
    tempUnit: String,
    windUnit: String,
    years: Int,
    backgroundProgress: Pair<Int, Int>?,
    onYearsChange: (Int) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 36.dp)) {
        item {
            HistoryHero(city, review, tempUnit, years, backgroundProgress, onYearsChange)
        }
        item { FeatureSectionTitle(1, "往年航迹", "YEAR TRACE") }
        items(review.days, key = HistoricalDay::date) { day ->
            HistoricalYearRow(day, review.days, tempUnit, windUnit)
        }
        if (review.missingCount > 0) {
            item {
                Text(
                    "${review.missingCount} 个年份暂未返回，已保留成功结果；下次打开会继续补齐。",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZhishengOrange,
                )
            }
        }
        item { FeatureSectionTitle(2, "数据说明", "SOURCE NOTE") }
        item {
            TerminalPanel(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("OPEN-METEO // BEST MATCH", style = MaterialTheme.typography.labelMedium, color = ZhishengCyan)
                    Text(
                        "这里展示的是历史再分析数据：由观测与模型融合还原，不等同于某座气象站的原始记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = ZhishengTextSecondary,
                    )
                    Text("CACHE // 历史结果已保存在本机", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                }
            }
        }
    }
}

@Composable
private fun HistoryHero(
    city: City,
    review: HistoricalReview,
    tempUnit: String,
    years: Int,
    backgroundProgress: Pair<Int, Int>?,
    onYearsChange: (Int) -> Unit,
) {
    val day = review.referenceDate.format(DateTimeFormatter.ofPattern("MM.dd", Locale.US))
    val summary = review.summary
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(day, style = MaterialTheme.typography.displaySmall, color = ZhishengOrange)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.padding(bottom = 3.dp)) {
                Text(city.displayName, style = MaterialTheme.typography.titleMedium, color = ZhishengText)
                Text("同日回看 · ${review.days.size} 个年份", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            }
        }
        Spacer(Modifier.height(14.dp))
        TerminalPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCell(
                        "平均高温",
                        summary.averageHigh?.let { "${Fmt.temp(it, tempUnit)}°" } ?: "--",
                        Modifier.weight(1f),
                    )
                    SummaryCell(
                        "平均低温",
                        summary.averageLow?.let { "${Fmt.temp(it, tempUnit)}°" } ?: "--",
                        Modifier.weight(1f),
                    )
                    SummaryCell(
                        "最湿年份",
                        summary.wettest?.localDate?.year?.toString() ?: "--",
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    YearChoice("近5年", years == 5, Modifier.weight(1f)) { onYearsChange(5) }
                    YearChoice("近10年", years == 10, Modifier.weight(1f)) { onYearsChange(10) }
                }
                backgroundProgress?.let { (done, total) ->
                    Spacer(Modifier.height(10.dp))
                    HistoryProgress(done, total)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "正在原位补齐 $done/$total，已有内容保持可读",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengOrange,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier) {
    Column(modifier.background(ZhishengCard).padding(horizontal = 10.dp, vertical = 9.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = ZhishengMint, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun YearChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(if (selected) ZhishengMint.copy(alpha = 0.14f) else ZhishengCard)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) ZhishengMint else ZhishengTextSecondary)
    }
}

@Composable
private fun HistoricalYearRow(
    day: HistoricalDay,
    all: List<HistoricalDay>,
    tempUnit: String,
    windUnit: String,
) {
    val min = all.mapNotNull(HistoricalDay::low).minOrNull() ?: 0.0
    val max = all.mapNotNull(HistoricalDay::high).maxOrNull() ?: (min + 1.0)
    TerminalPanel(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day.localDate.year.toString(), style = MaterialTheme.typography.titleMedium, color = ZhishengOrange)
                Spacer(Modifier.width(10.dp))
                WeatherIcon(day.condition, Modifier.size(30.dp))
                Spacer(Modifier.width(8.dp))
                Text(day.condition.label, style = MaterialTheme.typography.bodyMedium, color = ZhishengText)
                Spacer(Modifier.weight(1f))
                Text(
                    "${Fmt.temp(day.low, tempUnit) ?: "--"}°  ${Fmt.temp(day.high, tempUnit) ?: "--"}°",
                    style = MaterialTheme.typography.titleMedium,
                    color = ZhishengText,
                )
            }
            Spacer(Modifier.height(8.dp))
            TemperatureTrace(day.low, day.high, min, max)
            Spacer(Modifier.height(7.dp))
            Row {
                Text(
                    "RAIN ${formatMm(day.precipitationMm)}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = if ((day.precipitationMm ?: 0.0) > 0.0) ZhishengCyan else ZhishengTextTertiary,
                )
                Text(
                    "WIND ${Fmt.wind(day.windMaxKmh, windUnit) ?: "--"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengTextTertiary,
                )
            }
        }
    }
}

@Composable
private fun TemperatureTrace(low: Double?, high: Double?, min: Double, max: Double) {
    val palette = LocalZhishengPalette.current
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        val y = size.height / 2f
        drawLine(palette.cardBorder, Offset(0f, y), Offset(size.width, y), 2f)
        if (low == null || high == null) return@Canvas
        val span = (max - min).coerceAtLeast(1.0)
        val x1 = (((low - min) / span).toFloat() * size.width).coerceIn(0f, size.width)
        val x2 = (((high - min) / span).toFloat() * size.width).coerceIn(0f, size.width)
        drawLine(palette.cyan, Offset(x1, y), Offset(x2, y), 5f, cap = StrokeCap.Round)
        drawCircle(palette.mint, 4f, Offset(x1, y))
        drawCircle(palette.orange, 4f, Offset(x2, y))
    }
}

@Composable
private fun HistoryProgress(completed: Int, total: Int) {
    val palette = LocalZhishengPalette.current
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        drawRect(palette.cardBorder)
        if (total > 0) drawRect(palette.mint, size = size.copy(width = size.width * completed / total.toFloat()))
    }
}

@Composable
internal fun FeatureErrorState(title: String, action: String, onAction: () -> Unit, extra: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("[ ! ]", style = MaterialTheme.typography.headlineMedium, color = ZhishengOrange)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = ZhishengText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("检查网络后可以重新读取", style = MaterialTheme.typography.bodySmall, color = ZhishengTextTertiary)
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.background(ZhishengMint.copy(alpha = 0.14f)).clickable(onClick = onAction).padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(action, style = MaterialTheme.typography.labelMedium, color = ZhishengMint)
        }
        extra?.let { Spacer(Modifier.height(12.dp)); it() }
    }
}

private fun formatMm(value: Double?): String = value?.let {
    if (it == kotlin.math.floor(it)) "${it.toInt()} mm" else String.format(Locale.US, "%.1f mm", it)
} ?: "--"
