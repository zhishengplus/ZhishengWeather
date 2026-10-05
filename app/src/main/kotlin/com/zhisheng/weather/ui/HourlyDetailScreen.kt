package com.zhisheng.weather.ui

import android.animation.ValueAnimator
import android.os.PowerManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.R
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.home.HourlyDisplayItem
import com.zhisheng.weather.ui.home.hourlyDisplayItems
import com.zhisheng.weather.ui.theme.*
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.flow.drop

/** 图表专用彩色墨水：浅色=参考稿同款饱和色，深色=磷光亮色。只画线/柱/图例色块，不用于正文文字。
 *  AQI 六档按国标 HJ 633-2012：优绿/良黄/轻度橙/中度红/重度紫/严重栗（国标无蓝，蓝属美标）。 */
private data class ChartPalette(
    val tempMain: Color, val tempSecond: Color, val uv: Color,
    val wind: Color, val gust: Color,
    val aqiExcellent: Color, val aqiFine: Color, val aqiLightPoll: Color,
    val aqiModeratePoll: Color, val aqiHeavyPoll: Color, val aqiSeverePoll: Color,
)

@Composable
private fun rememberChartPalette(): ChartPalette {
    val light = LocalZhishengPalette.current.isLight
    return if (light) ChartPalette(
        tempMain = Color(0xFFE3A008), tempSecond = Color(0xFFE05A18),
        uv = Color(0xFF8C52D6),
        wind = Color(0xFF2E6FD8), gust = Color(0xFF1FA055),
        aqiExcellent = Color(0xFF21A356), aqiFine = Color(0xFFEFB800), aqiLightPoll = Color(0xFFFF7E00),
        aqiModeratePoll = Color(0xFFE8382B), aqiHeavyPoll = Color(0xFF8F3F97), aqiSeverePoll = Color(0xFF7E0023),
    ) else ChartPalette(
        tempMain = Color(0xFFFFD24A), tempSecond = Color(0xFFFF9830),
        uv = Color(0xFFB78CFF),
        wind = Color(0xFF38C8F0), gust = Color(0xFF50FF50),
        aqiExcellent = Color(0xFF35D95A), aqiFine = Color(0xFFFFE14D), aqiLightPoll = Color(0xFFFF9E2C),
        aqiModeratePoll = Color(0xFFFF5A4F), aqiHeavyPoll = Color(0xFFC77BE0), aqiSeverePoll = Color(0xFFE07A6E),
    )
}

/**
 * 逐时详情：温度/体感、空气质量、紫外线、风力四张紧凑图表卡。
 * 选中时刻是共享 MutableIntState——只在画布绘制期读取（拖动仅重绘不重组），
 * 这是拖动顺滑的关键；图例数值单独订阅，变更时只重组小图例行。
 */
@Composable
fun HourlyDetailScreen(
    data: WeatherData,
    cityName: String?,
    tempUnit: String,
    windUnit: String,
    onBack: () -> Unit,
) {
    val chrome = LocalZhishengChrome.current
    val palette = rememberChartPalette()
    val nowMs = weatherPresentationTime()
    val displayBase = hourlyDisplayItems(data.current, data.hourly, nowMs, data.aqi?.value)
    // “现在”观测点补齐：实况 AQI、下一小时的紫外线/体感/阵风，避免各图起点出现空洞。
    val display = displayBase.mapIndexed { i, item ->
        if (!item.isNow) item else item.copy(weather = item.weather.copy(
            uvIndex = item.weather.uvIndex ?: displayBase.getOrNull(i + 1)?.weather?.uvIndex,
            feelsLike = item.weather.feelsLike ?: displayBase.getOrNull(i + 1)?.weather?.feelsLike,
            windGust = item.weather.windGust ?: displayBase.getOrNull(i + 1)?.weather?.windGust,
        ))
    }
    val selected = rememberSaveable { mutableIntStateOf(0) }
    val hourlyHaptic = LocalHapticFeedback.current
    LaunchedEffect(selected, hourlyHaptic) {
        snapshotFlow { selected.intValue }.drop(1).collect {
            hourlyHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val offset = data.utcOffsetSeconds
    LazyColumn(
        Modifier.fillMaxSize().zhishengScreen().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = chrome.pagePadding, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = ZhishengText)
                }
                Column(Modifier.weight(1f)) {
                    Text("逐时详情", color = ZhishengText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    cityName?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (display.size >= 2) item {
            Text("在任意图表左右拖动，其他图表同步对准同一时刻",
                color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        if (display.size < 2) {
            item {
                Text("暂无逐时数据", color = ZhishengTextSecondary,
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            val times = display.map { it.weather.timeMillis }
            val bubbleStamps = hourlyBubbleLabels(display, offset, nowMs)
            item {
                ChartCard(R.drawable.ph_thermometer, palette.tempMain, "温度",
                    legend = {
                        val h = display[selected.value.coerceIn(0, display.lastIndex)].weather
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            LegendSwatch(palette.tempMain, "${tempText(h.temperature, tempUnit)}")
                            LegendSwatch(palette.tempSecond, "体感${tempText(h.feelsLike, tempUnit)}")
                        }
                    },
                ) {
                    HourlyCurveChart(
                        selected = selected,
                        times = times,
                        accent = palette.tempMain,
                        series = listOf(
                            HourlySeries(display.map { it.weather.temperature }, palette.tempMain, fill = true),
                            HourlySeries(display.map { it.weather.feelsLike }, palette.tempSecond, fill = true),
                        ),
                        floorAtZero = false,
                        onSelect = { selected.value = it },
                        bubbleText = { i ->
                            val h = display[i].weather
                            bubbleStamps[i] + " " +
                                tempText(h.temperature, tempUnit) +
                                (h.feelsLike?.let { " 体感${tempText(h.feelsLike, tempUnit)}" } ?: "")
                        },
                    )
                    HourlyAxisLabels(display, offset, nowMs)
                }
            }
            if (display.any { it.weather.aqi != null }) {
                item {
                    ChartCard(R.drawable.ph_leaf, palette.aqiExcellent, "空气质量",
                        legend = {
                            val aqi = display[selected.value.coerceIn(0, display.lastIndex)].weather.aqi
                            Text(aqi?.let { "$it ${aqiLevelText(it)}" } ?: "--",
                                color = ZhishengTextSecondary, style = MaterialTheme.typography.labelMedium)
                        },
                    ) {
                        HourlyAqiBars(
                            selected = selected,
                            times = times,
                            aqi = display.map { it.weather.aqi },
                            palette = palette,
                            utcOffsetSeconds = offset,
                            bubbleStamps = bubbleStamps,
                            onSelect = { selected.value = it },
                        )
                        HourlyAxisLabels(display, offset, nowMs)
                    }
                }
            }
            if (display.count { it.weather.uvIndex != null } >= 2) {
                item {
                    ChartCard(R.drawable.ph_sun, palette.uv, "紫外线",
                        legend = {
                            val uv = display[selected.value.coerceIn(0, display.lastIndex)].weather.uvIndex
                            Text(uv?.let { uvLevelText(it) } ?: "--",
                                color = ZhishengTextSecondary, style = MaterialTheme.typography.labelMedium)
                        },
                    ) {
                        HourlyCurveChart(
                            selected = selected,
                            times = times,
                            accent = palette.uv,
                            series = listOf(
                                HourlySeries(display.map { it.weather.uvIndex?.toDouble() },
                                    palette.uv, fill = true),
                            ),
                            floorAtZero = true,
                            onSelect = { selected.value = it },
                            bubbleText = { i ->
                                val uv = display[i].weather.uvIndex
                                bubbleStamps[i] + " " +
                                    (uv?.let { uvLevelText(it) } ?: "--")
                            },
                        )
                        HourlyAxisLabels(display, offset, nowMs)
                    }
                }
            }
            if (display.any { it.weather.windSpeed != null }) {
                item {
                    ChartCard(R.drawable.ph_wind, palette.wind, "风力",
                        legend = {
                            val h = display[selected.value.coerceIn(0, display.lastIndex)].weather
                            val wind = Fmt.windForce(h.windSpeed)
                                ?.let { windDirText(h.windDirectionDeg) + "风$it" }
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                LegendSwatch(palette.wind, wind ?: "--")
                                h.windGust?.let {
                                    LegendSwatch(palette.gust, "阵风${Fmt.windForce(it) ?: "--"}")
                                }
                            }
                        },
                    ) {
                        HourlyCurveChart(
                            selected = selected,
                            times = times,
                            accent = palette.wind,
                            series = listOf(
                                HourlySeries(display.map { it.weather.windSpeed }, palette.wind, fill = true),
                                HourlySeries(display.map { it.weather.windGust }, palette.gust, fill = true),
                            ),
                            floorAtZero = true,
                            onSelect = { selected.value = it },
                            bubbleText = { i ->
                                val h = display[i].weather
                                val wind = Fmt.windForce(h.windSpeed)
                                    ?.let { windDirText(h.windDirectionDeg) + "风$it" }
                                val gust = h.windGust?.let { Fmt.windForce(it) }
                                bubbleStamps[i] + " " +
                                    listOfNotNull(wind, gust?.let { "阵风$it" }).joinToString(" ")
                            },
                        )
                        HourlyAxisLabels(display, offset, nowMs)
                    }
                }
            }
            item {
                HourlyScrubberCard(selected, times, offset, display, tempUnit)
            }
        }
    }
}

@Composable
private fun ChartCard(
    iconRes: Int,
    accent: Color,
    title: String,
    legend: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().zhishengPanel().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhosphorIcon(iconRes, null, Modifier.size(17.dp), accent)
                Text(title, color = ZhishengText, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
            }
            legend()
        }
        content()
    }
}

/** 图例：彩线色块 + 正文色文字（色块承担彩色识别，文字保对比度）。 */
@Composable
private fun LegendSwatch(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.width(11.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(text, color = ZhishengText, style = MaterialTheme.typography.labelMedium)
    }
}

/** 时间横轴按实际预报时刻标注；只有实时观测点标为“现在”。 */
@Composable
private fun HourlyAxisLabels(display: List<HourlyDisplayItem>, utcOffsetSeconds: Int?, nowMillis: Long) {
    val labels = hourlyAxisLabels(display, utcOffsetSeconds, nowMillis)
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { (_, text) ->
            Text(text, color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

internal fun hourlyAxisLabels(
    display: List<HourlyDisplayItem>,
    utcOffsetSeconds: Int?,
    nowMillis: Long,
): List<Pair<Int, String>> {
    val times = display.map { it.weather.timeMillis }
    if (times.size < 2) return emptyList()
    val marks = listOf(0, times.size / 3, times.size * 2 / 3, times.lastIndex).distinct()
    val today = com.zhisheng.weather.model.cityDate(nowMillis, utcOffsetSeconds)
    return marks.mapIndexed { mi, idx ->
        val date = com.zhisheng.weather.model.cityDate(times[idx], utcOffsetSeconds)
        idx to when {
            display[idx].isNow -> "现在"
            mi == marks.lastIndex && date == today.plusDays(1) -> "明天"
            date != today -> "${date.monthValue}/${date.dayOfMonth} ${Fmt.clock(times[idx], utcOffsetSeconds)}"
            else -> Fmt.clock(times[idx], utcOffsetSeconds)
        }
    }
}

internal fun hourlyBubbleLabels(
    display: List<HourlyDisplayItem>,
    utcOffsetSeconds: Int?,
    nowMillis: Long,
): List<String> {
    return display.map { bubbleStamp(it.weather.timeMillis, nowMillis, utcOffsetSeconds) }
}

private fun bubbleStamp(timeMillis: Long, nowMillis: Long, offset: Int?): String {
    val date = com.zhisheng.weather.model.cityDate(timeMillis, offset)
    val prefix = if (date == com.zhisheng.weather.model.cityDate(nowMillis, offset)) "今天"
    else "${date.monthValue}/${date.dayOfMonth}"
    return "$prefix ${Fmt.clock(timeMillis, offset)}"
}

private fun tempText(celsius: Double?, unit: String): String {
    val value = celsius?.takeIf { it.isFinite() } ?: return "--"
    val shown = if (unit == "f") value * 9.0 / 5.0 + 32.0 else value
    return "${shown.roundToInt()}°"
}

private fun aqiLevelText(value: Int): String = when {
    value <= 50 -> "优"
    value <= 100 -> "良"
    value <= 150 -> "轻度污染"
    value <= 200 -> "中度污染"
    value <= 300 -> "重度污染"
    else -> "严重污染"
}

private fun uvLevelText(uv: Int): String = when {
    uv <= 2 -> "$uv 弱"
    uv <= 5 -> "$uv 中等"
    uv <= 7 -> "$uv 强"
    uv <= 10 -> "$uv 很强"
    else -> "$uv 极强"
}

private fun windDirText(deg: Double?): String {
    deg?.takeIf { it.isFinite() } ?: return ""
    val names = arrayOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")
    return names[(((deg % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]
}

private data class HourlySeries(val values: List<Double?>, val color: Color, val fill: Boolean)

/**
 * 单/双序列平滑曲线图。选中时刻只在绘制期从 [selected] 读取——
 * 拖动选择只重绘画布，不重组任何组合层。
 */
@Composable
private fun HourlyCurveChart(
    selected: MutableIntState,
    times: List<Long>,
    accent: Color,
    series: List<HourlySeries>,
    floorAtZero: Boolean,
    onSelect: (Int) -> Unit,
    bubbleText: (Int) -> String,
) {
    val measurer = rememberTextMeasurer()
    // 色彩访问器是 @Composable getter，组合层读取后捕获进画布闭包。
    val axisColor = ZhishengText.copy(alpha = .16f)
    val bubbleFill = ZhishengBg
    val bubbleTextColor = ZhishengText
    Canvas(
        Modifier.fillMaxWidth().height(102.dp)
            .hourlySelect(times.size, onSelect)
            .semantics { contentDescription = "逐时曲线图，点按或拖动选择时刻" },
    ) {
        val si = selected.value.coerceIn(0, times.lastIndex)
        val n = times.size
        if (n < 2) return@Canvas
        val inset = 4.dp.toPx()
        val top = 24.dp.toPx()
        val base = size.height - 2.dp.toPx()
        val plotHeight = (base - top).coerceAtLeast(1f)
        val pool = series.flatMap { s -> s.values.filterNotNull() }.filter { it.isFinite() }
        val min0 = pool.minOrNull() ?: return@Canvas
        val max0 = pool.maxOrNull() ?: return@Canvas
        var min = min0
        var max = if (max0 - min0 < 1e-6) min0 + 1.0 else max0
        if (floorAtZero) min = minOf(0.0, min)
        val pad = (max - min) * 0.14
        if (!floorAtZero) min -= pad
        max += pad
        fun x(i: Int): Float = inset + (size.width - inset * 2) * i / (n - 1)
        fun y(v: Double): Float = base - ((v.coerceAtLeast(min) - min) / (max - min)).toFloat() * plotHeight
        series.forEach { s ->
            val run = s.values.mapIndexed { i, v ->
                if (v != null && v.isFinite()) Offset(x(i), y(v)) else null
            }.filterNotNull()
            if (run.size < 2) return@forEach
            val path = weatherCurve(run)
            if (s.fill) {
                val fillPath = Path().apply {
                    addPath(path); lineTo(run.last().x, base); lineTo(run.first().x, base); close()
                }
                drawPath(fillPath, Brush.verticalGradient(
                    listOf(s.color.copy(alpha = .18f), Color.Transparent), top, base))
            }
            drawPath(path, s.color,
                style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // 选中时刻：序列圆点 + 向下的细线 + 顶部气泡（气泡描边带主题色，同参考稿）
        val sx = x(si)
        drawLine(axisColor, Offset(sx, top - 6.dp.toPx()), Offset(sx, base), 0.75.dp.toPx())
        series.firstOrNull()?.let { s ->
            val v = s.values.getOrNull(si)
            if (v != null && v.isFinite()) drawCircle(s.color, 3.dp.toPx(), Offset(sx, y(v)))
        }
        val layout = measurer.measure(
            bubbleText(si),
            TextStyle(fontSize = 9.sp, color = bubbleTextColor),
        )
        val bubbleW = layout.size.width + 16.dp.toPx()
        val bubbleH = layout.size.height + 8.dp.toPx()
        val bx = (sx - bubbleW / 2f).coerceIn(1.dp.toPx(), (size.width - bubbleW - 1.dp.toPx()).coerceAtLeast(1.dp.toPx()))
        val by = (top - 6.dp.toPx() - bubbleH).coerceAtLeast(1.dp.toPx())
        drawRoundRect(bubbleFill, Offset(bx, by), Size(bubbleW, bubbleH), CornerRadius(6.dp.toPx()))
        drawRoundRect(
            accent.copy(alpha = .55f), Offset(bx, by), Size(bubbleW, bubbleH), CornerRadius(6.dp.toPx()),
            style = Stroke(0.9.dp.toPx()),
        )
        drawText(layout, topLeft = Offset(bx + 8.dp.toPx(), by + 4.dp.toPx()))
    }
}

/** 空气质量柱状图：逐时一根、等级色映射，柱顶标数值（同参考稿）。 */
@Composable
private fun HourlyAqiBars(
    selected: MutableIntState,
    times: List<Long>,
    aqi: List<Int?>,
    palette: ChartPalette,
    utcOffsetSeconds: Int?,
    bubbleStamps: List<String>,
    onSelect: (Int) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val emptyBar = ZhishengCardBorder.copy(alpha = .6f)
    val bubbleFill = ZhishengBg
    val bubbleTextColor = ZhishengText
    val labelColor = ZhishengTextTertiary
    fun barColor(value: Int): Color = when {
        value <= 50 -> palette.aqiExcellent
        value <= 100 -> palette.aqiFine
        value <= 150 -> palette.aqiLightPoll
        value <= 200 -> palette.aqiModeratePoll
        value <= 300 -> palette.aqiHeavyPoll
        else -> palette.aqiSeverePoll
    }
    Canvas(
        Modifier.fillMaxWidth().height(102.dp)
            .hourlySelect(times.size, onSelect)
            .semantics { contentDescription = "逐时空气质量图，点按或拖动选择时刻" },
    ) {
        val si = selected.value.coerceIn(0, times.lastIndex)
        val n = times.size
        if (n < 2) return@Canvas
        val top = 24.dp.toPx()
        val base = size.height - 2.dp.toPx()
        val plotHeight = (base - top).coerceAtLeast(1f)
        val ceiling = (aqi.filterNotNull().maxOrNull()?.times(1.12) ?: 60.0).coerceAtLeast(60.0)
        val inset = 4.dp.toPx()
        val slot = (size.width - inset * 2) / n
        val barW = (slot * 0.52f).coerceAtMost(14.dp.toPx())
        aqi.forEachIndexed { i, value ->
            val cx = inset + slot * i + slot / 2f
            if (value == null) {
                drawLine(emptyBar,
                    Offset(cx - barW / 2, base - 1.dp.toPx()), Offset(cx + barW / 2, base - 1.dp.toPx()), 1.dp.toPx())
                return@forEachIndexed
            }
            val hValue = ((value / ceiling).coerceIn(0.04, 1.0) * plotHeight).toFloat()
            val barTop = base - hValue
            drawRoundRect(
                if (i == si) barColor(value) else barColor(value).copy(alpha = .72f),
                Offset(cx - barW / 2, barTop), Size(barW, hValue), CornerRadius(2.5.dp.toPx()),
            )
            val label = measurer.measure("$value", TextStyle(fontSize = 8.sp, color = labelColor))
            drawText(label, topLeft = Offset(
                (cx - label.size.width / 2f).coerceIn(0f, (size.width - label.size.width).coerceAtLeast(0f)),
                (barTop - label.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
        }
        // 选中柱：顶部小气泡（描边随等级色）
        val selValue = aqi.getOrNull(si)
        if (selValue != null) {
            val layout = measurer.measure(
                bubbleStamps[si] + " $selValue ${aqiLevelText(selValue)}",
                TextStyle(fontSize = 9.sp, color = bubbleTextColor),
            )
            val bubbleW = layout.size.width + 16.dp.toPx()
            val bubbleH = layout.size.height + 8.dp.toPx()
            val sx = inset + slot * si + slot / 2f
            val bx = (sx - bubbleW / 2f).coerceIn(1.dp.toPx(), (size.width - bubbleW - 1.dp.toPx()).coerceAtLeast(1.dp.toPx()))
            val by = (top - 6.dp.toPx() - bubbleH).coerceAtLeast(1.dp.toPx())
            drawRoundRect(bubbleFill, Offset(bx, by), Size(bubbleW, bubbleH), CornerRadius(6.dp.toPx()))
            drawRoundRect(
                barColor(selValue).copy(alpha = .55f), Offset(bx, by), Size(bubbleW, bubbleH),
                CornerRadius(6.dp.toPx()), style = Stroke(0.9.dp.toPx()),
            )
            drawText(layout, topLeft = Offset(bx + 8.dp.toPx(), by + 4.dp.toPx()))
        }
    }
}

private fun Modifier.hourlySelect(count: Int, onSelect: (Int) -> Unit): Modifier =
    pointerInput(count) {
        fun pick(xPos: Float) =
            onSelect(((xPos / size.width.toFloat()) * (count - 1)).roundToInt().coerceIn(0, count - 1))
        detectTapGestures { pos -> pick(pos.x) }
    }.pointerInput(count) {
        detectDragGestures { change, _ ->
            change.consume()
            onSelect(((change.position.x / size.width.toFloat()) * (count - 1)).roundToInt().coerceIn(0, count - 1))
        }
    }

/** 底部光轨拖把：澄空语言的一条青光细带——走过的段向亮头渐强,亮头呼吸微光"悦动";下方总结随拖动实时变化。 */
@Composable
private fun HourlyScrubberCard(
    selected: MutableIntState,
    times: List<Long>,
    utcOffsetSeconds: Int?,
    display: List<HourlyDisplayItem>,
    tempUnit: String,
) {
    val context = LocalContext.current
    val animate = remember {
        ValueAnimator.areAnimatorsEnabled() &&
            context.getSystemService(PowerManager::class.java)?.isPowerSaveMode != true
    }
    val glow = rememberInfiniteTransition(label = "scrubGlow")
    val glowPhase by glow.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "glowPhase",
    )
    val trackColor = ZhishengText.copy(alpha = .10f)
    val beamColor = ZhishengCyan
    val knobCore = ZhishengBg
    Column(Modifier.fillMaxWidth().zhishengPanel().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(46.dp)
                .hourlySelect(times.size, onSelect = { selected.value = it }),
        ) {
            val si = selected.value.coerceIn(0, times.lastIndex)
            val n = times.size
            if (n < 2) return@Canvas
            val inset = 10.dp.toPx()
            val midY = size.height / 2f
            val headX = inset + (size.width - inset * 2) * si / (n - 1)
            // 未走过的暗槽
            drawLine(trackColor, Offset(headX, midY), Offset(size.width - inset, midY), 2.dp.toPx(), StrokeCap.Round)
            if (headX > inset) {
                // 走过的光带：向亮头渐强的青光，三层叠出发光感
                val beam = Brush.horizontalGradient(
                    listOf(beamColor.copy(alpha = .12f), beamColor.copy(alpha = .85f)), inset, headX)
                drawLine(beam, Offset(inset, midY), Offset(headX, midY), 6.dp.toPx(), StrokeCap.Round)
                drawLine(beam, Offset(inset, midY), Offset(headX, midY), 3.dp.toPx(), StrokeCap.Round)
                drawLine(beamColor, Offset(inset, midY), Offset(headX, midY), 1.4.dp.toPx(), StrokeCap.Round)
            }
            // 亮头：呼吸光晕 + 芯
            val pulse = if (animate) .28f + .14f * (0.5f + 0.5f * sin(glowPhase * 2.0 * PI).toFloat()) else .32f
            drawCircle(Brush.radialGradient(
                listOf(beamColor.copy(alpha = pulse), Color.Transparent), Offset(headX, midY), radius = 15.dp.toPx()),
                radius = 15.dp.toPx(), center = Offset(headX, midY))
            drawCircle(beamColor, 4.5f.dp.toPx(), Offset(headX, midY))
            drawCircle(knobCore, 1.6f.dp.toPx(), Offset(headX, midY))
        }
        Text(
            scrubSummary(display, selected.value.coerceIn(0, times.lastIndex), tempUnit, times, utcOffsetSeconds),
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
            color = ZhishengTextSecondary, style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** 光轨下的四数据一句话总结：时刻 · 温度(体感) · 空气 · 紫外 · 风。 */
private fun scrubSummary(
    display: List<HourlyDisplayItem>,
    index: Int,
    tempUnit: String,
    times: List<Long>,
    utcOffsetSeconds: Int?,
): String {
    val h = display.getOrNull(index)?.weather ?: return "--"
    val time = times.getOrNull(index)?.let { Fmt.clock(it, utcOffsetSeconds) } ?: return "--"
    val feels = h.feelsLike
        ?.takeIf { it != h.temperature }
        ?.let { " 体感${tempText(h.feelsLike, tempUnit)}" }
    return listOfNotNull(
        time,
        tempText(h.temperature, tempUnit) + (feels ?: ""),
        h.aqi?.let { "空气$it ${aqiLevelText(it)}" },
        h.uvIndex?.let { "紫外 ${uvLevelText(it)}" },
        Fmt.windForce(h.windSpeed)?.let { "${windDirText(h.windDirectionDeg)}风$it" },
    ).joinToString(" · ")
}
