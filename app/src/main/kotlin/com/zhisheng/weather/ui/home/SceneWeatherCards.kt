package com.zhisheng.weather.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.model.CoastalForecast
import com.zhisheng.weather.model.MarinePoint
import com.zhisheng.weather.model.SkyPhotographyForecast
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.theme.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun SkyPhotographyCard(forecast: SkyPhotographyForecast, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ScenePanel(modifier, expanded, { expanded = !expanded }) {
        forecast.glow?.let { glow ->
            SignalTitle("${glow.kind.label} · ${glow.grade}", "GLOW", orange = true)
            Text(sceneWindow(glow.startMillis, glow.endMillis, forecast.zoneId),
                color = ZhishengTextSecondary, style = MaterialTheme.typography.bodyMedium)
            Text(glow.quality, color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
            if (expanded) ReasonList(glow.reasons)
        } ?: Text("近期日出日落或分层云数据不足，暂不判断霞光", color = ZhishengTextSecondary,
            style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(color = ZhishengCardBorder)
        forecast.stars?.let { stars ->
            SignalTitle("星空条件 · ${stars.score}分 ${stars.grade}", "ASTRO")
            Text("本夜较佳窗口 ${sceneWindow(stars.startMillis, stars.endMillis, forecast.zoneId)}",
                color = ZhishengTextSecondary, style = MaterialTheme.typography.bodyMedium)
            Text(stars.recommendation, color = ZhishengText, style = MaterialTheme.typography.bodyMedium)
            if (expanded) {
                ReasonList(stars.reasons)
                Text("月面照明约 ${(stars.moonIllumination * 100).roundToInt()}% · 仅月亮升起时计入月光影响",
                    color = ZhishengTextTertiary, style = MaterialTheme.typography.bodySmall)
            }
        } ?: Text(forecast.unavailableReason ?: "本夜星空条件暂缺",
            color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
        if (expanded) {
            Text("规则分档参考，不是出现概率；未检查远处太阳光路、现场地形遮挡，也未评估光污染（波特尔等级）。",
                color = ZhishengTextTertiary, style = MaterialTheme.typography.bodySmall)
        }
        SourceLine(forecast.source, forecast.updatedAtMillis, forecast.zoneId, forecast.stale)
        Text(if (expanded) "[ 收起判断依据 ]" else "[ 查看判断依据 ]", color = ZhishengCyan,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun CoastalWeatherCard(forecast: CoastalForecast, temperatureUnit: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ScenePanel(modifier, expanded, { expanded = !expanded }) {
        SignalTitle(forecast.trend.label, "COAST")
        Text("附近海域 · 模型潮位趋势 · 非港口潮汐表", color = ZhishengTextSecondary,
            style = MaterialTheme.typography.bodySmall)
        TideChart(forecast)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TurningPoint("下个模型高潮", forecast.nextHigh?.timeMillis, forecast.nextHigh?.heightM, forecast.zoneId, Modifier.weight(1f))
            TurningPoint("下个模型低潮", forecast.nextLow?.timeMillis, forecast.nextLow?.heightM, forecast.zoneId, Modifier.weight(1f))
        }
        Text("高度相对全球平均海平面 · 小时采样近似拐点", color = ZhishengTextTertiary,
            style = MaterialTheme.typography.labelSmall)
        HorizontalDivider(color = ZhishengCardBorder)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("有效浪高", number(forecast.waveHeightM, "m"), Modifier.weight(1f))
            Metric("浪周期", number(forecast.wavePeriodSeconds, "s"), Modifier.weight(1f))
            val temp = forecast.seaTemperatureC?.let { if (temperatureUnit == "f") it * 1.8 + 32 else it }
            Metric("海温", number(temp, if (temperatureUnit == "f") "°F" else "°C"), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("风浪", number(forecast.windWaveHeightM, "m"), Modifier.weight(1f))
            Metric("涌浪", number(forecast.swellHeightM, "m"), Modifier.weight(1f))
            Metric("海流", number(forecast.currentVelocityKmh?.div(3.6), "m/s"), Modifier.weight(1f))
        }
        Text("不能据此判断下水安全或用于航海；港湾、河口可能存在明显偏差。",
            color = ZhishengOrange, style = MaterialTheme.typography.bodySmall)
        if (expanded) {
            ReasonList(listOf(
                "浪来自 ${direction(forecast.waveDirectionDeg)}；海流流向 ${direction(forecast.currentDirectionDeg)}",
                "风浪来自 ${direction(forecast.windWaveDirectionDeg)} · 周期 ${number(forecast.windWavePeriodSeconds, "s")}",
                "涌浪来自 ${direction(forecast.swellDirectionDeg)} · 周期 ${number(forecast.swellPeriodSeconds, "s")}",
                "代表网格距离约 ${number(forecast.gridDistanceKm, "km")}，各变量网格可能不同；不是海岸线距离",
                "当前展示 ${sceneStamp(forecast.sampleTimeMillis, forecast.zoneId)} 小时模型值，不是测站实测",
            ))
            Text("潮位未来24小时（每3小时摘录）", color = ZhishengCyan, style = MaterialTheme.typography.labelMedium)
            forecast.points.filterIndexed { i, _ -> i % 3 == 0 }.forEach { point ->
                Text("${sceneStamp(point.timeMillis, forecast.zoneId)}   ${number(point.seaLevelM, "m")}",
                    color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text("Open-Meteo · DWD / ECMWF / Météo-France / NOAA 等模型，自动匹配；无测站精度承诺。",
                color = ZhishengTextTertiary, style = MaterialTheme.typography.bodySmall)
        }
        SourceLine(forecast.source, forecast.updatedAtMillis, forecast.zoneId, forecast.stale)
        Text(if (expanded) "[ 收起海域详情 ]" else "[ 查看海域详情 ]", color = ZhishengCyan,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun SceneErrorCard(message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)
        .zhishengPanel(containerColor = ZhishengCard).padding(16.dp)) {
        Text(message, color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ScenePanel(modifier: Modifier, expanded: Boolean, toggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .zhishengPanel(containerColor = ZhishengCard)
            .clickable(role = Role.Button, onClickLabel = if (expanded) "收起详情" else "展开详情", onClick = toggle)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content,
    )
}

@Composable
private fun SignalTitle(title: String, signal: String, orange: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), color = if (orange) ZhishengOrange else ZhishengMint,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(signal, color = ZhishengTextTertiary, fontSize = 12.sp, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun SourceLine(source: String, time: Long, zone: String, stale: Boolean) {
    // 来源独占一行，窄屏/大字体不会挤掉操作入口；各场景独立显示过期状态。
    Text("$source · 获取于 ${sceneStamp(time, zone)}${if (stale) " · 缓存，可能已过期" else ""}",
        color = if (stale) ZhishengOrange else ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun ReasonList(reasons: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        reasons.forEach { Text("· $it", color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall) }
    }
}

internal fun tideSegments(points: List<MarinePoint>): List<Pair<MarinePoint, MarinePoint>> =
    points.zipWithNext().filter { (a, b) ->
        a.seaLevelM?.isFinite() == true && b.seaLevelM?.isFinite() == true &&
            b.timeMillis - a.timeMillis in 1..3_600_000L
    }

@Composable
private fun TideChart(forecast: CoastalForecast) {
    val values = forecast.points.mapNotNull { it.seaLevelM?.takeIf(Double::isFinite) }
    val segments = tideSegments(forecast.points)
    if (segments.isEmpty()) {
        Text("潮位曲线暂缺", color = ZhishengTextTertiary, style = MaterialTheme.typography.bodySmall)
        return
    }
    val line = ZhishengCyan
    val grid = ZhishengCardBorder
    val min = values.min()
    val max = values.max()
    val lower = min - 0.05
    val upper = max + 0.05
    val span = (upper - lower).coerceAtLeast(0.1)
    val first = forecast.points.first().timeMillis
    val last = forecast.points.last().timeMillis
    Column {
        Text("潮位 ${number(min, "m")}—${number(max, "m")}", color = ZhishengTextTertiary,
            style = MaterialTheme.typography.labelSmall)
        Canvas(Modifier.fillMaxWidth().height(88.dp).semantics {
            contentDescription = "模型潮位趋势曲线，缺测处断开；展开详情可读逐时值"
        }) {
            drawLine(grid, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1f)
            fun position(point: MarinePoint) = Offset(
                size.width * ((point.timeMillis - first).toDouble() / (last - first).coerceAtLeast(1)).toFloat(),
                size.height * (1 - ((point.seaLevelM!! - lower) / span).toFloat()),
            )
            segments.forEach { (a, b) -> drawLine(line, position(a), position(b), strokeWidth = 2.dp.toPx()) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(sceneStamp(first, forecast.zoneId), color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
            Text(sceneStamp(last, forecast.zoneId), color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TurningPoint(label: String, time: Long?, height: Double?, zone: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
        Text(time?.let { sceneStamp(it, zone) } ?: "24小时内暂无可靠拐点",
            color = ZhishengText, style = MaterialTheme.typography.bodyMedium)
        height?.let { Text(number(it, "m"), color = ZhishengTextSecondary, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
        Text(value, color = ZhishengText, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun sceneStamp(time: Long, zone: String): String =
    DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.US)
        .format(Instant.ofEpochMilli(time).atZone(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of("UTC"))))

internal fun sceneWindow(start: Long, end: Long, zone: String) = "${sceneStamp(start, zone)}—${sceneStamp(end, zone)}"
private fun number(value: Double?, unit: String) =
    value?.takeIf(Double::isFinite)?.let { String.format(Locale.US, "%.1f%s", it, unit) } ?: "--"
private fun direction(degrees: Double?): String {
    if (degrees == null || !degrees.isFinite()) return "--"
    val names = arrayOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")
    return names[(degrees / 45.0).roundToInt().mod(8)]
}
