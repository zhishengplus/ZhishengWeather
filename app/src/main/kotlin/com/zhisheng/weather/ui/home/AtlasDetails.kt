package com.zhisheng.weather.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.zhisheng.weather.ui.components.SignalSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.Astronomy
import com.zhisheng.weather.R
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.theme.*
import kotlin.math.roundToInt

@Composable
internal fun AtlasGlowDetail(scene: SceneWeatherData?, city: City?) {
    if (isPhosphorVista) {
        VistaGlowDetail(scene, city)
        return
    }
    val sky = scene?.sky
    val glow = sky?.glow
    if (glow == null || city == null) {
        AtlasMissing(scene?.skyError ?: sky?.unavailableReason ?: "暂时没有可用霞光预报", "返回首页刷新后重试；无数据时不推断火烧云。")
        return
    }
    Text("${glow.kind.label} · ${glow.grade}", color = ZhishengOrange, style = MaterialTheme.typography.headlineMedium)
    AtlasCaption("参考时段 ${sceneWindow(glow.startMillis, glow.endMillis, sky.zoneId)}")
    AtlasStale(sky.stale)
    val points = remember(city, glow) { atlasSunPoints(city, glow.startMillis - 90 * 60_000L, glow.endMillis + 90 * 60_000L) }
    if (points.isNotEmpty()) {
        var index by remember(glow, city.locationKey) { mutableFloatStateOf(24f) }
        val selected = points[index.roundToInt().coerceIn(points.indices)]
        Column(Modifier.fillMaxWidth().atlasDetailSurface().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AtlasValue("${atlasClock(selected.time, sky.zoneId)} · 太阳高度", atlasNumber(selected.altitude, "°"))
            AtlasSunChart(city, glow, sky.zoneId, 180.dp, selected.time)
            AtlasScrubber(index, points.lastIndex, "选择太阳高度时间", { index = it })
            AtlasCaption("拖动查看 · 橙色区域是参考窗口 · 横线为地平线")
        }
    }
    AtlasReasons("为什么这样判断", glow.reasons)
    AtlasSource(sky.source, sky.updatedAtMillis, sky.zoneId)
    AtlasCaption("太阳高度按经纬度计算，不是霞光概率。${glow.quality}；天气与现场遮挡仍会影响能否拍到。")
}

@Composable
internal fun AtlasStarsDetail(scene: SceneWeatherData?, city: City?) {
    val sky = scene?.sky
    val stars = sky?.stars
    if (stars == null) {
        AtlasMissing(scene?.skyError ?: sky?.unavailableReason ?: "暂时没有可用星空窗口", "返回首页刷新后重试；无数据不等于不适合拍摄。")
        return
    }
    Text(stars.grade, color = ZhishengCyan, style = MaterialTheme.typography.headlineMedium)
    AtlasCaption(stars.recommendation)
    AtlasStale(sky.stale)
    Column(Modifier.fillMaxWidth().atlasDetailSurface().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            AtlasMoon(stars.moonIllumination, Modifier.size(if (LocalDensity.current.fontScale > 1.25f) 72.dp else 104.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AtlasValue("月面照明", "${(stars.moonIllumination * 100).roundToInt()}%")
            }
        }
        AtlasCaption("仅在月亮升起时计入月光影响")
        HorizontalDivider(color = ZhishengCardBorder)
        AtlasCaption("今晚参考时段")
        Text(sceneWindow(stars.startMillis, stars.endMillis, sky.zoneId), color = ZhishengText, style = MaterialTheme.typography.titleMedium)
        AtlasNightBar(stars, sky.zoneId)
        if (city != null) AtlasNightAltitude(city, stars, sky.zoneId)
    }
    AtlasReasons("拍摄条件", stars.reasons)
    AtlasSource(sky.source, sky.updatedAtMillis, sky.zoneId)
    AtlasCaption("这是本夜天气条件，不代表波特尔等级。月面图只表示照明比例，不表示现场朝向；未评估光污染与地形遮挡。")
}

@Composable
private fun AtlasNightAltitude(city: City, stars: StarForecast, zone: String) {
    val points = remember(city, stars) { atlasSunPoints(city, stars.startMillis, stars.endMillis) }
    if (points.isEmpty()) return
    var index by remember(stars) { mutableFloatStateOf(0f) }
    val selected = points[index.roundToInt().coerceIn(points.indices)]
    val palette = LocalZhishengPalette.current
    val low = minOf(-20.0, points.minOf { it.altitude })
    val high = maxOf(0.0, points.maxOf { it.altitude })
    Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "拍摄窗口内太阳高度曲线，参考线为负18度" }) {
        fun at(point: AtlasSunPoint) = Offset(size.width * atlasTimeFraction(point.time, points.first().time, points.last().time),
            size.height * (1 - ((point.altitude - low) / (high - low)).toFloat()))
        val reference = size.height * (1 - ((-18 - low) / (high - low)).toFloat())
        drawLine(palette.textTertiary, Offset(0f, reference), Offset(size.width, reference), 1.dp.toPx())
        points.zipWithNext().forEach { (a, b) -> drawLine(palette.cyan, at(a), at(b), 2.dp.toPx()) }
        val location = at(selected)
        drawLine(palette.textSecondary, Offset(location.x, 0f), Offset(location.x, size.height), 1.dp.toPx())
        drawCircle(palette.text, 4.dp.toPx(), location)
    }
    AtlasCaption("${atlasClock(selected.time, zone)} · 太阳高度 ${atlasNumber(selected.altitude, "°")} · 参考线 −18°")
    AtlasScrubber(index, points.lastIndex, "选择夜间太阳高度时间", { index = it })
}

@Composable
internal fun AtlasCoastDetail(scene: SceneWeatherData?, unit: String) {
    val coast = scene?.coast
    if (coast == null) {
        AtlasMissing(scene?.coastError ?: "暂时没有附近海域预报", "可换到沿海地点后刷新。")
        return
    }
    val points = remember(coast.points) { coastalDisplayPoints(coast.points) }
    val available = points.filter { it.seaLevelM?.isFinite() == true }
    val chartCoast = remember(coast, points) { coast.copy(points = points) }
    var selectedTime by rememberSaveable(coast.zoneId, coast.gridDistanceKm) { mutableStateOf<Long?>(null) }
    val selectedIndex = available.indices.minByOrNull { kotlin.math.abs(available[it].timeMillis - (selectedTime ?: available.first().timeMillis)) } ?: 0
    val selected = available.getOrNull(selectedIndex)
    Text(if (available.isEmpty()) "海况预报" else coast.trend.label, color = ZhishengCyan, style = MaterialTheme.typography.headlineMedium)
    AtlasStale(coast.stale)
    if (selected != null) {
        Column(Modifier.fillMaxWidth().atlasDetailSurface().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AtlasValue("预估潮位 · ${sceneStamp(selected.timeMillis, coast.zoneId)}", atlasNumber(selected.seaLevelM, "m"))
            AtlasCaption("以平均海平面为基准")
            AtlasTideChart(chartCoast, 192.dp, selected)
            if (available.size > 1) {
                AtlasScrubber(selectedIndex.toFloat(), available.lastIndex, "选择逐时潮位") { selectedTime = available[it.roundToInt().coerceIn(available.indices)].timeMillis }
                AtlasCaption("拖动下方时间轴查看潮位变化")
            }
        }
    }
    val turning = listOfNotNull(
        coast.nextHigh?.takeIf { it.heightM.isFinite() }?.let { "下次涨潮高点" to "${sceneStamp(it.timeMillis, coast.zoneId)} · ${atlasNumber(it.heightM, "m")}" },
        coast.nextLow?.takeIf { it.heightM.isFinite() }?.let { "下次退潮低点" to "${sceneStamp(it.timeMillis, coast.zoneId)} · ${atlasNumber(it.heightM, "m")}" },
    )
    if (turning.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        turning.forEach { (label, value) -> AtlasCaption("$label  $value") }
    }
    AtlasSeaVectors(coast)
    val facts = coastalDisplayFacts(coast, unit)
    if (facts.isNotEmpty()) Column(Modifier.fillMaxWidth().atlasDetailSurface().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("海况详情", color = ZhishengText, style = MaterialTheme.typography.titleMedium)
        val columns = if (LocalDensity.current.fontScale > 1.25f) 1 else 2
        facts.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AtlasCaption(label)
                        Text(value, color = ZhishengText, style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (row.size < columns) Spacer(Modifier.weight(1f))
            }
        }
    }
    AtlasSource(coast.source, coast.updatedAtMillis, coast.zoneId)
    AtlasCaption("模型时刻 ${sceneStamp(coast.sampleTimeMillis, coast.zoneId)}")
    coast.gridDistanceKm.takeIf { it.isFinite() && it >= 0 }?.let { AtlasCaption("代表网格距地点约 ${atlasNumber(it, "km")}；小时采样拐点仅为近似。") }
    AtlasCaption("非测站潮汐表，不能用于航海或判断下水安全；港湾、河口可能有明显偏差。", ZhishengOrange)
}

@Composable
private fun AtlasSeaVectors(coast: CoastalForecast) {
    val directions = listOf("浪来自" to coast.waveDirectionDeg, "海流流向" to coast.currentDirectionDeg)
        .filter { it.second?.isFinite() == true }
    if (directions.isEmpty()) return
    val palette = LocalZhishengPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("浪向与流向", color = ZhishengText, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            directions.forEach { (label, angle) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(88.dp).semantics { contentDescription = "$label ${atlasNumber(angle, "°")}" }) {
                        val radius = size.minDimension * .40f
                        drawCircle(palette.cardBorder, radius, center, style = Stroke(1.dp.toPx()))
                        drawLine(palette.textTertiary, Offset(center.x, 0f), Offset(center.x, 8.dp.toPx()), 2.dp.toPx())
                        angle?.takeIf(Double::isFinite)?.let {
                            val radians = Math.toRadians(it)
                            val tip = Offset(center.x + kotlin.math.sin(radians).toFloat() * radius, center.y - kotlin.math.cos(radians).toFloat() * radius)
                            drawLine(palette.cyan, center, tip, 2.dp.toPx())
                            drawCircle(palette.orange, 3.dp.toPx(), tip)
                        }
                    }
                    AtlasCaption("$label ${atlasNumber(angle, "°")}")
                }
            }
        }
        AtlasCaption("顶部刻度为北；浪向表示来向，海流表示去向。")
    }
}

@Composable
internal fun AtlasDarkDetail() {
    if (isPhosphorVista) {
        Column(Modifier.fillMaxWidth().atlasDetailSurface().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AtlasCaption("当前地点 · 夜空亮度")
                    Text("暂无数据", color = ZhishengText, style = MaterialTheme.typography.headlineMedium)
                }
                PhosphorIcon(R.drawable.ph_moon_stars, "暗空环境", Modifier.size(52.dp), ZhishengCyan)
            }
            AtlasCaption("波特尔等级与光污染背景尚未接入。")
        }
        AtlasReasons("与星空拍摄有什么不同", listOf("星空拍摄看本夜云量、月光与可拍摄时段。", "暗空环境看地点长期的夜空亮度，不能用今天的天气评分代替。"))
        Column(Modifier.fillMaxWidth().atlasDetailSurface().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("数据说明", color = ZhishengText, style = MaterialTheme.typography.titleMedium)
            AtlasCaption("数据源：尚未接入 · 更新时间：暂无")
            AtlasCaption("在接入经过核对的光污染数据前，不显示推测等级或暗空地图。")
        }
        return
    }
    Text("暗空数据尚未接入", color = ZhishengText, style = MaterialTheme.typography.headlineSmall)
    AtlasUnavailableGraphic("当前地点的波特尔等级：暂无数据", 96.dp)
    AtlasReasons("与星空天气分开看", listOf("星空拍摄窗口看本夜云量、月光等天气条件。", "波特尔等级描述地点的夜空亮度，不能从今天的天气评分换算。"))
    AtlasCaption("接入经过核对的光污染数据前，不显示推测等级或虚构暗空地图。")
}

@Composable
private fun AtlasMissing(title: String, help: String) {
    Text(title, color = ZhishengText, style = MaterialTheme.typography.titleLarge)
    AtlasCaption(help)
}

@Composable
private fun AtlasValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AtlasCaption(label)
        Text(value, color = ZhishengText, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun AtlasMetricPair(left: String, leftValue: String, right: String, rightValue: String) {
    if (isPhosphorVista && leftValue == "—" && rightValue == "—") return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        listOf(left to leftValue, right to rightValue).filter { !isPhosphorVista || it.second != "—" }.forEach { (label, value) ->
            Column(Modifier.weight(1f).then(if (isPhosphorVista) Modifier.atlasDetailSurface().padding(14.dp) else Modifier), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AtlasCaption(label)
                Text(value, color = ZhishengText, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun AtlasReasons(title: String, reasons: List<String>) {
    if (reasons.none { it.isNotBlank() }) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = ZhishengCardBorder)
        Text(title, color = ZhishengText, style = MaterialTheme.typography.titleMedium)
        reasons.filter { it.isNotBlank() }.forEach { AtlasCaption(it) }
    }
}

@Composable
private fun AtlasSource(source: String, time: Long, zone: String) {
    AtlasCaption((if (isPhosphorVista) "数据源 " else "") + "$source · 获取于 ${sceneStamp(time, zone)}")
}

@Composable
private fun Modifier.atlasDetailSurface(): Modifier = if (isPhosphorVista) zhishengPanel(containerColor = ZhishengCard) else background(ZhishengCard)

@Composable
private fun AtlasScrubber(value: Float, max: Int, label: String, change: (Float) -> Unit) {
    SignalSlider(value = value, onValueChange = change, valueRange = 0f..max.coerceAtLeast(1).toFloat(),
        steps = (max - 1).coerceAtLeast(0), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = label },
        colors = SliderDefaults.colors(thumbColor = ZhishengCyan, activeTrackColor = ZhishengCyan,
            inactiveTrackColor = ZhishengCardBorder, activeTickColor = ZhishengCyan, inactiveTickColor = ZhishengCardBorder))
}
