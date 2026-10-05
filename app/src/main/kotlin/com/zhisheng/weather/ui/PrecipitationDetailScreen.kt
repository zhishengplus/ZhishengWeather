package com.zhisheng.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.model.Nowcast
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.theme.*
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.util.Locale
import kotlin.math.abs

/** One source of truth for the summary card and its detail, in either theme. */
@Composable
internal fun rememberPrecipitationPresentation(data: WeatherData): PrecipitationPresentation {
    val previewTime = LocalWeatherPreviewTime.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(data, lifecycle, previewTime) {
      if (previewTime != null) return@LaunchedEffect
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
      }
    }
    return remember(data, now, previewTime) { precipitationPresentation(data, previewTime ?: now) }
}

@Composable
fun PrecipitationDetailScreen(data: WeatherData, cityName: String?, onBack: () -> Unit) {
    val presentation = rememberPrecipitationPresentation(data)
    val chrome = LocalZhishengChrome.current
    LazyColumn(
        Modifier.fillMaxSize().zhishengScreen().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = chrome.pagePadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = ZhishengText)
                }
                Column(Modifier.weight(1f)) {
                    Text("短时降水", color = ZhishengText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    cityName?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(presentation.summary, fontSize = 28.sp, lineHeight = 38.sp,
                    fontWeight = FontWeight.Medium, color = ZhishengText)
                precipitationCoverageLabel(presentation, data.utcOffsetSeconds)?.let {
                    Text(it, color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (presentation.points.isNotEmpty() || presentation.history.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().zhishengPanel().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("降水强度", Modifier.weight(1f), color = ZhishengText, fontWeight = FontWeight.Medium)
                        Text("滑动查看", color = ZhishengTextTertiary,
                            style = MaterialTheme.typography.labelSmall)
                    }
                    PrecipitationTimeline(presentation, data.utcOffsetSeconds, interactive = true)
                    Text("mm/h · 每小时降水量",
                        color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            item {
                Text("暂无可查看的降水曲线", color = ZhishengTextSecondary,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(precipitationSourceLabel(data, presentation), color = ZhishengTextSecondary,
                    style = MaterialTheme.typography.bodySmall)
                Text(precipitationHistoryLabel(data, presentation), color = ZhishengTextSecondary,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

internal fun precipitationCoverageLabel(p: PrecipitationPresentation, offset: Int?): String? {
    val start = p.coverageStart ?: return null
    val end = p.coverageEnd ?: return null
    val crossesDate = Fmt.date(start, offset) != Fmt.date(end, offset)
    return if (start == end) "预报时间 ${Fmt.clock(start, offset)}"
    else if (crossesDate) "预报时段 ${Fmt.stamp(start, offset)}—${Fmt.stamp(end, offset)}"
    else "预报时段 ${Fmt.clock(start, offset)}—${Fmt.clock(end, offset)}"
}

internal fun precipitationSourceLabel(data: WeatherData, p: PrecipitationPresentation): String = buildList {
    if (p.hourlyFallback) {
        add("彩云 · 小时级预报（分钟数据暂缺）")
        p.intervalMinutes?.let { add("$it 分钟间隔") }
        return@buildList
    }
    add(Nowcast.sourceLabel(data.rainMeta?.source ?: data.blockSources["minutely"] ?: data.dataSource))
    p.intervalMinutes?.let { add("$it 分钟间隔") }
    // A general weather timestamp is not a minutely forecast publication time.
    data.rainMeta?.updateTime?.let { add("更新于 ${Fmt.stamp(it, data.utcOffsetSeconds)}") }
}.joinToString(" · ")

internal fun precipitationHistoryLabel(data: WeatherData, p: PrecipitationPresentation): String =
    if (p.history.isEmpty()) "过去两小时：暂无回溯数据"
    else "过去：${Nowcast.sourceLabel(data.rainHistorySource)} · 模型回溯（非实测）· 15 分钟间隔"

internal fun precipitationCompactSource(data: WeatherData, p: PrecipitationPresentation): String = buildList {
    if (p.history.isNotEmpty()) add("回溯 ${if (data.rainHistorySource == "OPEN-METEO") "OM" else Nowcast.sourceLabel(data.rainHistorySource)} 模型")
    if (p.points.isNotEmpty()) add("${if (p.hourlyFallback) "逐时" else "预报"} ${Nowcast.sourceLabel(data.rainMeta?.source ?: data.blockSources["minutely"] ?: data.dataSource)}")
}.joinToString(" · ")

@Composable
internal fun PrecipitationTimeline(
    p: PrecipitationPresentation,
    utcOffsetSeconds: Int?,
    interactive: Boolean = false,
) {
    val now = p.nowMillis ?: p.points.firstOrNull()?.timeMillis ?: return
    val start = now - 7_200_000L
    val end = now + 7_200_000L
    val history = p.history.filter { it.timeMillis in start..now }
    val future = p.points.filter { it.timeMillis in now..end }
    val points = (history + future).distinctBy { it.timeMillis }.sortedBy { it.timeMillis }
    var selectedTime by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedIndex = points.indexOfFirst { it.timeMillis == selectedTime }.takeIf { it >= 0 } ?: 0
    val selected = points.getOrNull(selectedIndex)
    val duration = (end - start).coerceAtLeast(1L)
    val ceiling = Nowcast.precipChartCeiling(points).coerceAtLeast(0.1f)
    val lineColor = ZhishengCyan
    val glowColor = ZhishengMint
    val ruleColor = ZhishengCardBorder
    val numberColor = ZhishengText
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (interactive && selected != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${Fmt.clock(selected.timeMillis, utcOffsetSeconds)} · ${if (selected.timeMillis < now) "模型回溯" else "预报"}", color = ZhishengTextSecondary,
                        style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(String.format(Locale.US, "%.2f", selected.precip), color = numberColor,
                        fontSize = 26.sp, fontWeight = FontWeight.Normal)
                    Text("mm/h", color = ZhishengTextTertiary,
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(if (interactive) 192.dp else 64.dp)
            .semantics {
                contentDescription = selected?.let { "降水强度，${Fmt.clock(it.timeMillis, utcOffsetSeconds)}，${it.precip} 毫米每小时" }
                    ?: "过去两小时与未来两小时降水，暂无数据"
                if (interactive && points.isNotEmpty()) {
                    customActions = listOf(
                        CustomAccessibilityAction("上一个时间") {
                            selectedTime = points[(selectedIndex - 1).coerceAtLeast(0)].timeMillis; true
                        },
                        CustomAccessibilityAction("下一个时间") {
                            selectedTime = points[(selectedIndex + 1).coerceAtMost(points.lastIndex)].timeMillis; true
                        },
                    )
                }
            }
            .then(if (interactive) Modifier.pointerInput(points) {
                detectTapGestures { position ->
                    val inset = 4.dp.toPx()
                    val right = 32.dp.toPx()
                    val target = start + (((position.x - inset) / (size.width - inset - right)).coerceIn(0f, 1f).toDouble() * duration).toLong()
                    selectedTime = points.minByOrNull { abs(it.timeMillis - target) }?.timeMillis
                }
            }.pointerInput(points) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val inset = 4.dp.toPx()
                    val right = 32.dp.toPx()
                    val target = start + (((change.position.x - inset) / (size.width - inset - right)).coerceIn(0f, 1f).toDouble() * duration).toLong()
                    selectedTime = points.minByOrNull { abs(it.timeMillis - target) }?.timeMillis
                }
            } else Modifier)) {
            val sideInset = 4.dp.toPx()
            val rightInset = if (interactive) 32.dp.toPx() else sideInset
            val topInset = 10.dp.toPx()
            val bottomInset = 8.dp.toPx()
            val plotWidth = (size.width - sideInset - rightInset).coerceAtLeast(1f)
            val base = size.height - bottomInset
            val plotHeight = (base - topInset).coerceAtLeast(1f)
            fun x(index: Int): Float = sideInset + (points[index].timeMillis - start).toFloat() / duration * plotWidth
            fun y(index: Int): Float = base - points[index].precip / ceiling * plotHeight
            val nowX = sideInset + plotWidth / 2f
            drawRect(glowColor.copy(alpha = .045f), Offset(sideInset, topInset),
                androidx.compose.ui.geometry.Size(plotWidth / 2f, plotHeight))
            drawLine(ruleColor.copy(alpha = .85f), Offset(nowX, topInset), Offset(nowX, base),
                1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            val levels = if (interactive) 2 else 1
            for (level in 0..levels) {
                val axisY = base - plotHeight * level / levels
                drawLine(
                    ruleColor.copy(alpha = if (level == 0) 0.40f else 0.30f),
                    Offset(sideInset, axisY), Offset(size.width - rightInset, axisY),
                    if (level == 0) 1.dp.toPx() else 0.7.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            fun drawSeries(series: List<com.zhisheng.weather.model.MinutePrecip>, color: Color, gap: Long) {
                val groups = mutableListOf<MutableList<com.zhisheng.weather.model.MinutePrecip>>()
                series.forEach { sample ->
                    if (groups.isEmpty() || sample.timeMillis - groups.last().last().timeMillis > gap) groups.add(mutableListOf())
                    groups.last().add(sample)
                }
                groups.forEach { group ->
                    val offsets = group.map { Offset(sideInset + (it.timeMillis - start).toFloat() / duration * plotWidth,
                        base - it.precip / ceiling * plotHeight) }
                    if (offsets.size == 1) drawCircle(color, 2.dp.toPx(), offsets.first())
                    else {
                        val path = weatherCurve(offsets)
                        val fill = Path().apply { addPath(path); lineTo(offsets.last().x, base); lineTo(offsets.first().x, base); close() }
                        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .15f), Color.Transparent), topInset, base))
                        drawPath(path, color.copy(alpha = .86f), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            // 历史（回溯）与预报按各自网格采样，“现在”虚线两侧最近样本之间常隔着
            // 一个 15 分钟档位，两条曲线各自止步就在中缝断开。各把对方的边界样本
            // 并进自己的序列，让曲线真正跨过虚线相连；颜色仍以虚线为界。
            val seam = history.isNotEmpty() && future.isNotEmpty() &&
                future.first().timeMillis > history.last().timeMillis
            drawSeries(if (seam) history + future.take(1) else history, glowColor, 15 * 60_000L)
            drawSeries(if (seam) listOf(history.last()) + future else future, lineColor,
                maxOf(p.intervalMinutes ?: 15, 15) * 60_000L)
            if (interactive && selected != null) {
                val selectedPoint = Offset(x(selectedIndex), y(selectedIndex))
                drawLine(lineColor.copy(alpha = 0.18f), Offset(selectedPoint.x, topInset),
                    Offset(selectedPoint.x, base), 1.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(numberColor.copy(alpha = 0.95f), 3.5.dp.toPx(), selectedPoint)
                drawCircle(Color.White, 1.5.dp.toPx(), selectedPoint)
            }
        }
        if (history.isEmpty()) Text("历史数据暂缺", Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
            color = ZhishengTextTertiary, fontSize = 10.sp)
        if (future.isEmpty()) Text("预报数据暂缺", Modifier.align(Alignment.CenterEnd).padding(end = 36.dp),
            color = ZhishengTextTertiary, fontSize = 10.sp)
        if (interactive) Column(Modifier.align(Alignment.CenterEnd).height(192.dp).padding(top = 2.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
            listOf(ceiling, ceiling / 2f, 0f).forEach {
                Text(String.format(Locale.US, if (ceiling < 1f) "%.2f" else "%.1f", it),
                    color = ZhishengTextTertiary, fontSize = 10.sp)
            }
        }
        }
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = if (interactive) 32.dp else 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("2 小时前", color = ZhishengTextSecondary, style = MaterialTheme.typography.labelSmall)
            Text("现在", color = ZhishengTextSecondary, style = MaterialTheme.typography.labelSmall)
            Text("2 小时后", color = ZhishengTextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}
