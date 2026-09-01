/* Hallmark · pre-emit critique: P5 H5 E5 S5 R5 V5 */
/* Hallmark · genre: atmospheric technical utility · macrostructure: Long Document · design-system: DESIGN.md · designed-as-app */
package com.zhisheng.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.data.HistoricalWeatherRepository
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.HistoricalComparison
import com.zhisheng.weather.model.HistoricalDay
import com.zhisheng.weather.model.HistoricalReview
import com.zhisheng.weather.model.RecentWeatherWeek
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.cityDate
import com.zhisheng.weather.model.cityZone
import com.zhisheng.weather.model.compareWithHistorical
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengReading
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.floor

private sealed interface HistoryScreenState {
    data class Loading(val completed: Int, val total: Int) : HistoryScreenState
    data class Ready(val review: HistoricalReview) : HistoryScreenState
    data class Error(val message: String, val hint: String = "检查网络后可以重新读取") : HistoryScreenState
}

private sealed interface RecentWeekState {
    data object Loading : RecentWeekState
    data class Ready(val week: RecentWeatherWeek) : RecentWeekState
    data class Error(val message: String, val hint: String = "检查网络后可以重新读取") : RecentWeekState
}

private enum class HistoryView { RECENT_WEEK, SAME_DAY }

private data class HistoryLoadProgress(
    val date: LocalDate,
    val completed: Int,
    val total: Int,
)

internal data class RecentRangeDisplay(
    val dates: String,
    val years: String,
)

@Composable
fun HistoryScreen(
    city: City?,
    weather: WeatherData?,
    tempUnit: String,
    windUnit: String,
    utcOffsetSeconds: Int?,
    onBack: () -> Unit,
) {
    val today = remember(city?.locationKey, utcOffsetSeconds) { LocalDate.now(cityZone(utcOffsetSeconds)) }
    // 海外城市的时区可能晚于城市列表首帧到达；把时区也作为保存键，
    // 避免在当地跨日边界仍沿用手机时区初始化出的错误日期。
    var selectedEpochDay by rememberSaveable(city?.locationKey, utcOffsetSeconds) {
        mutableLongStateOf(today.toEpochDay())
    }
    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)
    var viewName by rememberSaveable { mutableStateOf(HistoryView.RECENT_WEEK.name) }
    val view = HistoryView.valueOf(viewName)
    var years by rememberSaveable { mutableIntStateOf(10) }
    var retry by remember { mutableIntStateOf(0) }
    var backgroundProgress by remember { mutableStateOf<HistoryLoadProgress?>(null) }
    var state by remember(city?.locationKey, retry) {
        mutableStateOf<HistoryScreenState>(HistoryScreenState.Loading(0, years))
    }
    var recentState by remember(city?.locationKey, retry) { mutableStateOf<RecentWeekState>(RecentWeekState.Loading) }

    LaunchedEffect(city?.locationKey, selectedDate, years, retry, view) {
        if (view != HistoryView.SAME_DAY) return@LaunchedEffect
        if (city == null) {
            state = HistoryScreenState.Error("先在主页选择一座城市", "回主页选中城市后再进来")
            return@LaunchedEffect
        }
        val keepCurrentContent = state is HistoryScreenState.Ready
        if (keepCurrentContent) backgroundProgress = HistoryLoadProgress(selectedDate, 0, years)
        else state = HistoryScreenState.Loading(0, years)
        state = try {
            val review = HistoricalWeatherRepository.loadReview(city, selectedDate, years) { done, total ->
                if (keepCurrentContent) backgroundProgress = HistoryLoadProgress(selectedDate, done, total)
                else state = HistoryScreenState.Loading(done, total)
            }
            if (review.days.isEmpty()) HistoryScreenState.Error("这一天暂时没有往年的温度记录", "换一天或调整年份范围再看看")
            else HistoryScreenState.Ready(review)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            if (keepCurrentContent) state else HistoryScreenState.Error("往年数据读取失败")
        } finally {
            backgroundProgress = null
        }
    }

    LaunchedEffect(city?.locationKey, today, retry, view) {
        if (view != HistoryView.RECENT_WEEK) return@LaunchedEffect
        if (city == null) {
            recentState = RecentWeekState.Error("先在主页选择一座城市", "回主页选中城市后再进来")
            return@LaunchedEffect
        }
        recentState = RecentWeekState.Loading
        recentState = try {
            RecentWeekState.Ready(HistoricalWeatherRepository.loadPastWeek(city, today))
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            RecentWeekState.Error("过去7天数据读取失败")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(ZhishengBg)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        FeaturePageHeader("天气回看", "WEATHER MEMORY", onBack)
        HistoryViewToggle(view) { viewName = it.name }
        if (view == HistoryView.RECENT_WEEK) {
            when (val current = recentState) {
                RecentWeekState.Loading -> RecentWeekLoading()
                is RecentWeekState.Error -> FeatureErrorState(
                    title = current.message,
                    action = "重新读取",
                    hint = current.hint,
                    onAction = { retry++ },
                )
                is RecentWeekState.Ready -> RecentWeekContent(
                    city = city!!,
                    week = current.week,
                    tempUnit = tempUnit,
                    windUnit = windUnit,
                )
            }
        } else {
            when (val current = state) {
                is HistoryScreenState.Loading -> HistoryLoading(current.completed, current.total)
                is HistoryScreenState.Error -> FeatureErrorState(
                    title = current.message,
                    action = "重新读取",
                    hint = current.hint,
                    onAction = { retry++ },
                )
                is HistoryScreenState.Ready -> HistoryContent(
                    city = city!!,
                    weather = weather,
                    review = current.review,
                    tempUnit = tempUnit,
                    windUnit = windUnit,
                    years = years,
                    today = today,
                    backgroundProgress = backgroundProgress,
                    onDateChange = { selectedEpochDay = it.toEpochDay() },
                    onYearsChange = { years = it },
                )
            }
        }
    }
}

@Composable
private fun HistoryViewToggle(selected: HistoryView, onSelect: (HistoryView) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp)
            .height(50.dp).background(ZhishengSurface).border(1.dp, ZhishengCardBorder),
    ) {
        HistoryViewChoice("过去7天", "RECENT LOG", selected == HistoryView.RECENT_WEEK, Modifier.weight(1f)) {
            onSelect(HistoryView.RECENT_WEEK)
        }
        Box(Modifier.width(1.dp).fillMaxSize().background(ZhishengCardBorder))
        HistoryViewChoice("往年同日", "YEAR TRACE", selected == HistoryView.SAME_DAY, Modifier.weight(1f)) {
            onSelect(HistoryView.SAME_DAY)
        }
    }
}

@Composable
private fun HistoryViewChoice(
    title: String,
    code: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val accent = if (selected) ZhishengMint else ZhishengTextSecondary
    Column(
        modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = "查看$title", onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = accent, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Text(code, style = MaterialTheme.typography.labelSmall, color = if (selected) ZhishengCyan else ZhishengTextTertiary, letterSpacing = 0.8.sp)
        if (selected) Box(Modifier.align(Alignment.CenterHorizontally).width(54.dp).height(2.dp).background(accent))
    }
}

@Composable
private fun RecentWeekLoading() {
    FeatureBootLoader(
        channel = "RECENT WEATHER LOG",
        lines = listOf(
            "RECENT PORT ......... OPEN",
            "LOCK COMPLETE DAYS ... OK",
            "FETCH 7-DAY TRACE ......",
            "BUILD DAILY LOG ........",
        ),
        status = "正在读取过去7天",
    )
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
        status = if (completed == 0) "正在定位这一天的往年天气" else "已找到 $completed 年，继续读取其余年份",
        progress = if (total > 0) completed / total.toFloat() else 0f,
    )
}

@Composable
private fun RecentWeekContent(
    city: City,
    week: RecentWeatherWeek,
    tempUnit: String,
    windUnit: String,
) {
    val summary = week.summary
    val range = recentRangeDisplay(week)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    range.dates,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 28.sp),
                    color = ZhishengOrange,
                    maxLines = 1,
                )
                Text(
                    "${range.years} · ${city.displayName} · 过去7天",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengTextTertiary,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    recentWeekInsight(week, tempUnit),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = ZhishengReading,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 26.sp,
                    ),
                    color = ZhishengText,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "只显示已经结束的日期；今天的数据尚未完整，不进入统计。",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = ZhishengReading),
                    color = ZhishengTextTertiary,
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryMetric("平均高温", summary.averageHigh?.let { "${Fmt.temp(it, tempUnit)}°" } ?: "记录不足")
                    SummaryMetric("平均低温", summary.averageLow?.let { "${Fmt.temp(it, tempUnit)}°" } ?: "记录不足")
                    SummaryMetric("累计降水", formatMm(week.totalPrecipitationMm))
                }
            }
        }
        item { FeatureSectionTitle(1, "七日观测带", "7-DAY WEATHER STRIP") }
        item {
            Text(
                "横向滑动，每列是一天的天气、温度、降水和风速。",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = ZhishengReading),
                color = ZhishengTextTertiary,
            )
        }
        item { RecentWeekWeatherStrip(week.days, tempUnit, windUnit) }
        item { FeatureSectionTitle(2, "数据说明", "SOURCE NOTE") }
        item {
            TerminalPanel(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("OPEN-METEO // RECENT ARCHIVE", style = MaterialTheme.typography.labelMedium, color = ZhishengCyan)
                    Text(
                        "过去7天来自 Forecast API 的近期归档，是滚动的七个完整自然日，不是固定的周一到周日。它适合回看天气变化，但仍是格点化数据，不等同于当地单个气象站原始观测。",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = ZhishengReading),
                        color = ZhishengTextSecondary,
                    )
                    Text("CACHE // 当天成功读取后保存在本机", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                }
            }
        }
    }
}

@Composable
private fun HistoryContent(
    city: City,
    weather: WeatherData?,
    review: HistoricalReview,
    tempUnit: String,
    windUnit: String,
    years: Int,
    today: LocalDate,
    backgroundProgress: HistoryLoadProgress?,
    onDateChange: (LocalDate) -> Unit,
    onYearsChange: (Int) -> Unit,
) {
    val forecast = weather?.daily?.firstOrNull {
        cityDate(it.dateMillis, weather.utcOffsetSeconds) == review.referenceDate
    }?.takeIf { !review.referenceDate.isBefore(today) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp),
    ) {
        item {
            HistoryHero(
                city = city,
                review = review,
                forecast = forecast,
                tempUnit = tempUnit,
                years = years,
                today = today,
                backgroundProgress = backgroundProgress,
                onDateChange = onDateChange,
                onYearsChange = onYearsChange,
            )
        }
        item { FeatureSectionTitle(1, "温度落点", "TEMPERATURE BAND") }
        item { TemperatureBandPanel(review, forecast, tempUnit) }
        item { HistoricalRecords(review, tempUnit) }
        item { FeatureSectionTitle(2, "逐年回望", "YEAR MEMORY") }
        item {
            Text(
                "左右滑动查看每一年；卡片只放这一年的关键记录。",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = ZhishengReading),
                color = ZhishengTextTertiary,
            )
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(review.days, key = HistoricalDay::date) { day -> HistoricalYearCard(day, tempUnit, windUnit) }
            }
        }
        if (review.missingCount > 0) {
            item {
                Text(
                    "${review.missingCount} 个年份没有可比较的温度，已自动略过；其余记录仍可正常阅读。",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = ZhishengReading),
                    color = ZhishengOrange,
                )
            }
        }
        item { FeatureSectionTitle(3, "数据说明", "SOURCE NOTE") }
        item {
            TerminalPanel(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("OPEN-METEO // BEST MATCH", style = MaterialTheme.typography.labelMedium, color = ZhishengCyan)
                    Text(
                        "往年数据来自历史再分析：观测与模型共同还原当地天气，不等同于单个气象站的原始记录。近5年或10年是逐年同日样本，不是30年气候平均。",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = ZhishengReading),
                        color = ZhishengTextSecondary,
                    )
                    Text(
                        "“整体偏暖/偏凉”取高温差与低温差的平均：温差不到 ±1.5℃ 视为接近往年，超过 ±3.5℃ 判为明显偏暖或偏凉，介于两者之间为略偏暖或略偏凉。",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = ZhishengReading),
                        color = ZhishengTextSecondary,
                    )
                    if (forecast != null) {
                        Text(
                            "“今年预报”来自你当前选用的天气源，与往年再分析数据分开标记。",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = ZhishengReading),
                            color = ZhishengTextSecondary,
                        )
                    }
                    Text("CACHE // 已成功的往年记录保存在本机", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                }
            }
        }
    }
}

@Composable
private fun HistoryHero(
    city: City,
    review: HistoricalReview,
    forecast: DailyWeather?,
    tempUnit: String,
    years: Int,
    today: LocalDate,
    backgroundProgress: HistoryLoadProgress?,
    onDateChange: (LocalDate) -> Unit,
    onYearsChange: (Int) -> Unit,
) {
    val summary = review.summary
    val comparison = compareWithHistorical(forecast?.high, forecast?.low, summary)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        DateNavigator(
            date = review.referenceDate,
            subtitle = "${city.displayName} · ${review.requestedYearRange}年 · 同日",
            isToday = review.referenceDate == today,
            onPrevious = { onDateChange(review.referenceDate.minusDays(1)) },
            onNext = { onDateChange(review.referenceDate.plusDays(1)) },
            onToday = { onDateChange(today) },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            historyInsight(review, forecast, comparison, tempUnit, today),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = ZhishengReading,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 26.sp,
            ),
            color = ZhishengText,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            if (forecast != null) "今年用当前预报，往年用再分析，两类数据分开展示。" else "没有当前预报时，只展示往年记录，不与今天做比较。",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = ZhishengReading),
            color = ZhishengTextTertiary,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "实际纳入 ${review.days.size} / ${review.requestedCount} 年：${review.includedYearsText}",
            style = MaterialTheme.typography.labelSmall,
            color = ZhishengCyan,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SummaryMetric("平均高温", summary.averageHigh?.let { "${Fmt.temp(it, tempUnit)}°" } ?: "记录不足")
            SummaryMetric("平均低温", summary.averageLow?.let { "${Fmt.temp(it, tempUnit)}°" } ?: "记录不足")
            SummaryMetric(
                "有雨年份",
                summary.precipitationSampleCount.takeIf { it > 0 }
                    ?.let { "${summary.rainyYears} / $it" }
                    ?: "记录不足",
            )
        }
        Spacer(Modifier.height(14.dp))
        YearRangeToggle(years, onYearsChange)
        backgroundProgress?.let { progress ->
            Spacer(Modifier.height(10.dp))
            HistoryProgress(progress.completed, progress.total)
            Spacer(Modifier.height(6.dp))
            Text(
                "正在读取 ${progress.date.format(DateTimeFormatter.ofPattern("MM.dd", Locale.US))} · ${progress.completed}/${progress.total}，页面保持当前内容",
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengOrange,
            )
        }
    }
}

@Composable
private fun DateNavigator(
    date: LocalDate,
    subtitle: String,
    isToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        DateArrow("‹", "前一天", onPrevious)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                date.format(DateTimeFormatter.ofPattern("MM.dd", Locale.US)),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 28.sp),
                color = ZhishengOrange,
                maxLines = 1,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!isToday) {
            Box(
                Modifier.height(44.dp).clickable(role = Role.Button, onClickLabel = "回到今天", onClick = onToday)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("今天", style = MaterialTheme.typography.labelMedium, color = ZhishengMint, maxLines = 1)
            }
        }
        DateArrow("›", "后一天", onNext)
    }
}

@Composable
private fun DateArrow(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).border(1.dp, ZhishengCardBorder)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall, color = ZhishengText)
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = ZhishengMint, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun YearRangeToggle(years: Int, onYearsChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp).background(ZhishengSurface).border(1.dp, ZhishengCardBorder)) {
        YearChoice("近5年", years == 5, Modifier.weight(1f)) { onYearsChange(5) }
        Box(Modifier.width(1.dp).height(44.dp).background(ZhishengCardBorder))
        YearChoice("近10年", years == 10, Modifier.weight(1f)) { onYearsChange(10) }
    }
}

@Composable
private fun YearChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) ZhishengMint else ZhishengTextSecondary
    Box(
        modifier = modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        if (selected) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(color))
    }
}

@Composable
private fun TemperatureBandPanel(review: HistoricalReview, forecast: DailyWeather?, tempUnit: String) {
    TerminalPanel(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("每条竖线是一年的低温到高温", style = MaterialTheme.typography.bodySmall.copy(fontFamily = ZhishengReading), color = ZhishengTextSecondary)
                Spacer(Modifier.weight(1f))
                Text("${review.days.size} 年", style = MaterialTheme.typography.labelSmall, color = ZhishengOrange)
            }
            Spacer(Modifier.height(9.dp))
            TemperatureBandChart(review.days, forecast)
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendMark(ZhishengCyan, "低温")
                Spacer(Modifier.width(12.dp))
                LegendMark(ZhishengOrange, "高温")
                if (forecast != null) {
                    Spacer(Modifier.width(12.dp))
                    LegendMark(ZhishengMint, "今年预报", outlined = true)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "均高 ${review.summary.averageHigh?.let { Fmt.temp(it, tempUnit) } ?: "?"}° / 均低 ${review.summary.averageLow?.let { Fmt.temp(it, tempUnit) } ?: "?"}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengTextTertiary,
                )
            }
        }
    }
}

@Composable
private fun RecentWeekWeatherStrip(days: List<HistoricalDay>, tempUnit: String, windUnit: String) {
    val palette = LocalZhishengPalette.current
    val ordered = remember(days) { days.sortedBy(HistoricalDay::date) }
    val columnWidth = 88.dp
    val stripHeight = 350.dp
    val chartTop = 152.dp
    val chartBottom = 244.dp
    val values = ordered.flatMap { listOfNotNull(it.low, it.high) }
    val min = (values.minOrNull() ?: 0.0) - 1.0
    val max = (values.maxOrNull() ?: min + 1.0) + 1.0
    val span = (max - min).coerceAtLeast(1.0)
    fun yOffset(value: Double): androidx.compose.ui.unit.Dp {
        val ratio = ((value - min) / span).toFloat().coerceIn(0f, 1f)
        return chartBottom - (chartBottom - chartTop) * ratio
    }
    val scrollState = rememberScrollState(Int.MAX_VALUE)

    TerminalPanel(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp)) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("时间从左到右", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                Spacer(Modifier.weight(1f))
                LegendMark(ZhishengOrange, "高温")
                Spacer(Modifier.width(10.dp))
                LegendMark(ZhishengCyan, "低温")
            }
            Box(
                Modifier.fillMaxWidth().height(stripHeight).horizontalScroll(scrollState),
            ) {
                val totalWidth = columnWidth * ordered.size.coerceAtLeast(1)
                Canvas(Modifier.width(totalWidth).height(stripHeight)) {
                    val columnPx = columnWidth.toPx()
                    val chartTopPx = chartTop.toPx()
                    val chartBottomPx = chartBottom.toPx()
                    fun x(index: Int): Float = columnPx * (index + 0.5f)
                    fun y(value: Double): Float = chartBottomPx - ((value - min) / span).toFloat() * (chartBottomPx - chartTopPx)

                    if (ordered.isNotEmpty()) {
                        drawRect(
                            palette.mint.copy(alpha = 0.055f),
                            topLeft = Offset(columnPx * ordered.lastIndex, 0f),
                            size = Size(columnPx, size.height),
                        )
                    }
                    repeat(ordered.size + 1) { index ->
                        drawLine(
                            palette.cardBorder.copy(alpha = 0.72f),
                            Offset(columnPx * index, 0f),
                            Offset(columnPx * index, size.height),
                            1f,
                        )
                    }
                    repeat(3) { guide ->
                        val guideY = chartTopPx + guide * (chartBottomPx - chartTopPx) / 2f
                        drawLine(palette.cardBorder, Offset(0f, guideY), Offset(size.width, guideY), 1f)
                    }

                    fun drawSeries(selector: (HistoricalDay) -> Double?, color: androidx.compose.ui.graphics.Color) {
                        val path = Path()
                        var drawing = false
                        ordered.forEachIndexed { index, day ->
                            val value = selector(day)
                            if (value == null) {
                                drawing = false
                            } else if (!drawing) {
                                path.moveTo(x(index), y(value))
                                drawing = true
                            } else {
                                path.lineTo(x(index), y(value))
                            }
                        }
                        drawPath(path, color.copy(alpha = 0.78f), style = Stroke(width = 2.6f, cap = StrokeCap.Round))
                        ordered.forEachIndexed { index, day ->
                            selector(day)?.let { value ->
                                drawCircle(color, radius = 4.2f, center = Offset(x(index), y(value)))
                            }
                        }
                    }
                    drawSeries(HistoricalDay::high, palette.orange)
                    drawSeries(HistoricalDay::low, palette.cyan)
                }

                Row(Modifier.width(totalWidth).height(stripHeight)) {
                    ordered.forEachIndexed { index, day ->
                        val latest = index == ordered.lastIndex
                        Box(Modifier.width(columnWidth).height(stripHeight)) {
                            Column(
                                Modifier.fillMaxWidth().padding(top = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    if (latest) "昨天" else weekdayLabel(day.localDate),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (latest) palette.mint else palette.textSecondary,
                                    fontWeight = if (latest) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(
                                    day.localDate.format(DateTimeFormatter.ofPattern("M/d", Locale.CHINA)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textTertiary,
                                )
                                Spacer(Modifier.height(8.dp))
                                if (day.condition != WeatherCondition.UNKNOWN) {
                                    WeatherIcon(day.condition, Modifier.size(30.dp))
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        day.condition.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.textSecondary,
                                        maxLines = 1,
                                    )
                                } else {
                                    Spacer(Modifier.height(33.dp))
                                    Text("未记录", style = MaterialTheme.typography.labelSmall, color = palette.textTertiary)
                                }
                            }

                            day.high?.let { high ->
                                Text(
                                    "${Fmt.temp(high, tempUnit)}°",
                                    modifier = Modifier.fillMaxWidth().offset(y = yOffset(high) - 27.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = palette.text,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            day.low?.let { low ->
                                Text(
                                    "${Fmt.temp(low, tempUnit)}°",
                                    modifier = Modifier.fillMaxWidth().offset(y = yOffset(low) + 8.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = palette.text,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            Column(
                                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    when {
                                        day.precipitationMm == null -> "降水未记录"
                                        day.precipitationMm >= 0.1 -> "雨 ${formatMm(day.precipitationMm)}"
                                        else -> "无明显降水"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if ((day.precipitationMm ?: 0.0) >= 0.1) palette.cyan else palette.textTertiary,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    day.windMaxKmh?.let { "风 ${Fmt.wind(it, windUnit)}" } ?: "风速未记录",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendMark(color: androidx.compose.ui.graphics.Color, label: String, outlined: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) {
            if (outlined) drawRect(color, style = Stroke(width = 2f)) else drawRect(color)
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
    }
}

private data class TemperaturePoint(val label: String, val low: Double?, val high: Double?, val forecast: Boolean)

@Composable
private fun TemperatureBandChart(days: List<HistoricalDay>, forecast: DailyWeather?) {
    val points = remember(days, forecast) {
        buildList {
            days.sortedBy(HistoricalDay::date).forEach { day ->
                add(TemperaturePoint(day.localDate.year.toString(), day.low, day.high, false))
            }
            if (forecast?.high != null || forecast?.low != null) add(TemperaturePoint("今年", forecast.low, forecast.high, true))
        }
    }
    TemperatureTraceChart(points)
}

@Composable
private fun TemperatureTraceChart(points: List<TemperaturePoint>) {
    val palette = LocalZhishengPalette.current
    val values = points.flatMap { listOfNotNull(it.low, it.high) }
    if (values.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(128.dp), contentAlignment = Alignment.Center) {
            Text("温度记录不足，暂时无法绘图", style = MaterialTheme.typography.bodySmall, color = ZhishengTextTertiary)
        }
        return
    }
    val min = (values.minOrNull() ?: 0.0) - 1.0
    val max = (values.maxOrNull() ?: min + 1.0) + 1.0
    Canvas(Modifier.fillMaxWidth().height(142.dp)) {
        val top = 10f
        val bottom = size.height - 10f
        val span = (max - min).coerceAtLeast(1.0)
        fun x(index: Int): Float = if (points.size == 1) size.width / 2f else index / points.lastIndex.toFloat() * size.width
        fun y(value: Double): Float = bottom - ((value - min) / span).toFloat() * (bottom - top)
        repeat(3) { guide ->
            val gy = top + guide * (bottom - top) / 2f
            drawLine(palette.cardBorder, Offset(0f, gy), Offset(size.width, gy), 1f)
        }
        points.zipWithNext().forEachIndexed { index, (a, b) ->
            if (a.high != null && b.high != null) drawLine(palette.orange.copy(alpha = 0.55f), Offset(x(index), y(a.high)), Offset(x(index + 1), y(b.high)), 2f)
            if (a.low != null && b.low != null) drawLine(palette.cyan.copy(alpha = 0.55f), Offset(x(index), y(a.low)), Offset(x(index + 1), y(b.low)), 2f)
        }
        points.forEachIndexed { index, point ->
            val px = x(index)
            val marker = if (point.forecast) palette.mint else palette.textTertiary
            if (point.low != null && point.high != null) {
                drawLine(marker.copy(alpha = if (point.forecast) 0.9f else 0.45f), Offset(px, y(point.low)), Offset(px, y(point.high)), if (point.forecast) 4f else 2f, cap = StrokeCap.Round)
            }
            point.low?.let { low ->
                if (point.forecast) drawRect(palette.mint, Offset(px - 4f, y(low) - 4f), Size(8f, 8f), style = Stroke(width = 2f))
                else drawCircle(palette.cyan, 4f, Offset(px, y(low)))
            }
            point.high?.let { high ->
                if (point.forecast) drawRect(palette.mint, Offset(px - 4f, y(high) - 4f), Size(8f, 8f), style = Stroke(width = 2f))
                else drawCircle(palette.orange, 4f, Offset(px, y(high)))
            }
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Text(points.first().label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        Spacer(Modifier.weight(1f))
        Text(points.getOrNull(points.lastIndex / 2)?.label.orEmpty(), style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        Spacer(Modifier.weight(1f))
        Text(points.last().label, style = MaterialTheme.typography.labelSmall, color = if (points.last().forecast) ZhishengMint else ZhishengTextTertiary)
    }
}

@Composable
private fun HistoricalRecords(review: HistoricalReview, tempUnit: String) {
    val summary = review.summary
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RecordItem("历史最高", summary.warmest?.let { "${it.localDate.year} · ${Fmt.temp(it.high, tempUnit)}°" } ?: "记录不足", Modifier.weight(1f))
        RecordItem("历史最低", summary.coldest?.let { "${it.localDate.year} · ${Fmt.temp(it.low, tempUnit)}°" } ?: "记录不足", Modifier.weight(1f))
        val wettest = summary.wettest?.takeIf { (it.precipitationMm ?: 0.0) >= 0.1 }
        val wettestText = when {
            summary.precipitationSampleCount == 0 -> "记录不足"
            wettest != null -> "${wettest.localDate.year} · ${formatMm(wettest.precipitationMm)}"
            else -> "均无明显雨量"
        }
        RecordItem("历史最湿", wettestText, Modifier.weight(1f))
    }
}

@Composable
private fun RecordItem(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengOrange)
        Text(value, style = MaterialTheme.typography.labelMedium, color = ZhishengText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HistoricalYearCard(day: HistoricalDay, tempUnit: String, windUnit: String) {
    TerminalPanel(Modifier.width(232.dp).height(154.dp)) {
        Column(Modifier.fillMaxSize().padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day.localDate.year.toString(), style = MaterialTheme.typography.titleLarge, color = ZhishengOrange)
                Spacer(Modifier.weight(1f))
                if (day.condition != WeatherCondition.UNKNOWN) {
                    WeatherIcon(day.condition, Modifier.size(28.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(day.condition.label, style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                } else {
                    Text("现象未记录", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                }
            }
            Spacer(Modifier.height(12.dp))
            val temperatureText = when {
                day.low != null && day.high != null -> "${Fmt.temp(day.low, tempUnit)}°  →  ${Fmt.temp(day.high, tempUnit)}°"
                day.mean != null -> "平均 ${Fmt.temp(day.mean, tempUnit)}°"
                day.high != null -> "最高 ${Fmt.temp(day.high, tempUnit)}°"
                day.low != null -> "最低 ${Fmt.temp(day.low, tempUnit)}°"
                else -> "温度记录不完整"
            }
            Text(temperatureText, style = MaterialTheme.typography.titleMedium, color = ZhishengText, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "降水 ${formatMm(day.precipitationMm)}",
                style = MaterialTheme.typography.labelSmall,
                color = if ((day.precipitationMm ?: 0.0) >= 0.1) ZhishengCyan else ZhishengTextTertiary,
            )
            Text(
                day.windMaxKmh?.let { "最大风速 ${Fmt.wind(it, windUnit)}" } ?: "风速未记录",
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary,
            )
        }
    }
}

private fun historyInsight(
    review: HistoricalReview,
    forecast: DailyWeather?,
    comparison: HistoricalComparison?,
    tempUnit: String,
    today: LocalDate,
): String {
    if (forecast != null && comparison != null) {
        val dateLabel = if (review.referenceDate == today) "今天" else review.referenceDate.format(DateTimeFormatter.ofPattern("M月d日"))
        val forecastText = when {
            forecast.low != null && forecast.high != null ->
                "最高 ${Fmt.temp(forecast.high, tempUnit)}°、最低 ${Fmt.temp(forecast.low, tempUnit)}°"
            forecast.high != null -> "最高 ${Fmt.temp(forecast.high, tempUnit)}°"
            else -> "最低 ${Fmt.temp(forecast.low, tempUnit)}°"
        }
        val deltas = buildList {
            comparison.highDelta?.let { add("高温${deltaPhrase(it, tempUnit)}") }
            comparison.lowDelta?.let { add("低温${deltaPhrase(it, tempUnit)}") }
        }.joinToString("、")
        return "${dateLabel}预计 $forecastText。较往年同日，$deltas，整体${comparison.band.label}。"
    }
    val high = review.summary.averageHigh?.let { Fmt.temp(it, tempUnit) }
    val low = review.summary.averageLow?.let { Fmt.temp(it, tempUnit) }
    val years = review.days.size
    return when {
        high != null && low != null -> "在可用的${years}个年份同日记录中，最高温平均约 $high°、最低温平均约 $low°。"
        high != null -> "在可用的${years}个年份同日记录中，最高温平均约 $high°。"
        low != null -> "在可用的${years}个年份同日记录中，最低温平均约 $low°。"
        else -> "已找到 $years 个年份的可用记录，左右滑动查看每一年。"
    }
}

internal fun recentRangeDisplay(week: RecentWeatherWeek): RecentRangeDisplay {
    val start = week.startDate ?: return RecentRangeDisplay("日期未记录", "年份未记录")
    val end = week.endDate ?: start
    val years = if (start.year == end.year) start.year.toString() else "${start.year}—${end.year}"
    return RecentRangeDisplay(
        dates = "${start.format(DateTimeFormatter.ofPattern("MM.dd", Locale.CHINA))}—" +
            end.format(DateTimeFormatter.ofPattern("MM.dd", Locale.CHINA)),
        years = years,
    )
}

private fun recentWeekInsight(week: RecentWeatherWeek, tempUnit: String): String {
    val warmest = week.summary.warmest
    val coldest = week.summary.coldest
    return when {
        warmest?.high != null && coldest?.low != null -> {
            "过去7天最高 ${Fmt.temp(warmest.high, tempUnit)}°，出现在${warmest.localDate.format(DateTimeFormatter.ofPattern("M月d日"))}；" +
                "最低 ${Fmt.temp(coldest.low, tempUnit)}°，出现在${coldest.localDate.format(DateTimeFormatter.ofPattern("M月d日"))}。"
        }
        warmest?.high != null -> "过去7天最高 ${Fmt.temp(warmest.high, tempUnit)}°，出现在${warmest.localDate.format(DateTimeFormatter.ofPattern("M月d日"))}。"
        coldest?.low != null -> "过去7天最低 ${Fmt.temp(coldest.low, tempUnit)}°，出现在${coldest.localDate.format(DateTimeFormatter.ofPattern("M月d日"))}。"
        else -> "已找到 ${week.days.size} 天可用记录，向下查看每天的天气。"
    }
}

private fun deltaPhrase(deltaCelsius: Double, tempUnit: String): String {
    val delta = if (tempUnit == "f") deltaCelsius * 9.0 / 5.0 else deltaCelsius
    return when {
        abs(delta) < 0.5 -> "基本持平"
        delta > 0 -> "偏高 ${formatDelta(abs(delta))}°"
        else -> "偏低 ${formatDelta(abs(delta))}°"
    }
}

private fun weekdayLabel(date: LocalDate): String = when (date.dayOfWeek.value) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}

@Composable
private fun HistoryProgress(completed: Int, total: Int) {
    val palette = LocalZhishengPalette.current
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        drawRect(palette.cardBorder)
        if (total > 0) drawRect(palette.mint, size = size.copy(width = size.width * completed / total.toFloat()))
    }
}

@Composable
internal fun FeatureErrorState(
    title: String,
    action: String,
    onAction: () -> Unit,
    hint: String = "检查网络后可以重新读取",
    extra: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("[ ! ]", style = MaterialTheme.typography.headlineMedium, color = ZhishengOrange)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = ZhishengText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(hint, style = MaterialTheme.typography.bodySmall, color = ZhishengTextTertiary)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.background(ZhishengMint.copy(alpha = 0.14f)).clickable(onClick = onAction).padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(action, style = MaterialTheme.typography.labelMedium, color = ZhishengMint)
        }
        extra?.let { Spacer(Modifier.height(12.dp)); it() }
    }
}

private fun formatMm(value: Double?): String = value?.let {
    if (it == floor(it)) "${it.toInt()} mm" else String.format(Locale.US, "%.1f mm", it)
} ?: "未记录"

private fun formatDelta(value: Double): String = if (value >= 9.95) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
