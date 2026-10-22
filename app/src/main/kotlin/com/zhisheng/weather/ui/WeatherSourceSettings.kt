package com.zhisheng.weather.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.zhisheng.weather.data.*
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.theme.zhishengDialogPanel
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import androidx.compose.ui.graphics.Color
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Same inset and type scale as the surrounding settings; no nested scrolling surface. */
@Composable
internal fun WeatherModuleSettingsContent(
    policy: ModuleSourcePolicy,
    available: Set<SourcePref>,
    weather: WeatherData?,
    onCompare: () -> Unit,
    onChoose: (WeatherModule) -> Unit,
    showHeader: Boolean = true,
) {
    val palette = LocalZhishengPalette.current
    var detailsExpanded by rememberSaveable { mutableStateOf(false) }
    val titleStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp)
    val captionStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp)
    @Composable fun Divider() = HorizontalDivider(Modifier.padding(horizontal = 18.dp),
        thickness = .5.dp, color = palette.cardBorder.copy(alpha = .32f))
    Column(Modifier.fillMaxWidth()) {
        if (showHeader) Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("按模块选择来源", color = palette.text, style = titleStyle, fontWeight = FontWeight.Medium)
            Text("点选各模块使用的天气来源", color = palette.textSecondary, style = captionStyle)
        }
        if (showHeader) Divider()
        WeatherModule.entries.forEachIndexed { index, module ->
            val selected = policy[module]
            val evidence = weather?.moduleEvidence?.get(module.key)
            val diagnostic = weather?.sourceDiagnostics?.get(module.key)
            val unavailable = selected != null && selected !in available
            val summary = when {
                unavailable -> "来源不可用 · 查看详情"
                evidence != null -> "当前：${moduleSourceName(evidence.source)}" + if (!diagnostic.isNullOrBlank()) " · 查看详情" else ""
                weather == null -> "等待天气数据"
                modulePresent(weather, module) -> "来源信息待更新"
                else -> "暂无${when (module) {
                    WeatherModule.CURRENT -> "实况"
                    WeatherModule.FORECAST -> "预报"
                    WeatherModule.MINUTELY -> "短时降水"
                    WeatherModule.ALERTS -> "预警"
                    WeatherModule.INDICES -> "生活指数"
                    WeatherModule.AIR -> "空气质量"
                }}数据"
            }
            @Composable fun SourceSummary() = Text(summary,
                color = if (unavailable || !diagnostic.isNullOrBlank()) palette.orange else palette.textSecondary,
                style = captionStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (index > 0) Divider()
            Box {
                BoxWithConstraints(Modifier.fillMaxWidth().clickable(role = Role.Button) { onChoose(module) }
                    .heightIn(min = 68.dp).padding(horizontal = 18.dp, vertical = 12.dp)) {
                    val choiceWidth = maxWidth * .45f
                    val stacked = LocalDensity.current.fontScale > 1.2f || maxWidth < 240.dp
                    if (stacked) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(module.cn, Modifier.weight(1f), color = palette.text, style = titleStyle,
                                    fontWeight = FontWeight.Medium)
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp).rotate(-90f), tint = palette.textSecondary)
                            }
                            Text(selected?.cn ?: "跟随主源", color = palette.mint,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp))
                            SourceSummary()
                        }
                    } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(module.cn, color = palette.text, style = titleStyle, fontWeight = FontWeight.Medium)
                            SourceSummary()
                        }
                        Row(Modifier.widthIn(max = choiceWidth), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(selected?.cn ?: "跟随主源", Modifier.weight(1f, fill = false), color = palette.mint,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
                                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp).rotate(-90f), tint = palette.textSecondary)
                        }
                    }
                    }
                }

            }
        }
        Divider()
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { detailsExpanded = !detailsExpanded }
            .padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("来源详情与说明", Modifier.weight(1f), color = palette.textSecondary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp))
            Icon(Icons.Default.ArrowDropDown,
                if (detailsExpanded) "收起来源详情" else "展开来源详情",
                Modifier.size(18.dp).rotate(if (detailsExpanded) 180f else 0f), tint = palette.textSecondary)
        }
        if (detailsExpanded) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("跟随主源会沿用主天气源。单独指定的来源失败或缺少该模块时，保留主源数据；缺失数据不会补成预报。", color = palette.textSecondary, style = captionStyle)
                weather?.locationMatch?.dataResolution?.let { resolution ->
                    Text("天气覆盖：$resolution${weather.locationMatch.matchedRegionName?.let { " · $it" }.orEmpty()}。设备定位误差不代表天气分辨率。", color = palette.textSecondary, style = captionStyle)
                }
                weather?.sourceDiagnostics?.get("location")?.let { Text(it, color = palette.textSecondary, style = captionStyle) }
                WeatherModule.entries.forEach { module ->
                    val selected = policy[module]
                    val evidence = weather?.moduleEvidence?.get(module.key)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(module.cn, color = palette.text, style = captionStyle, fontWeight = FontWeight.Medium)
                        if (selected != null) {
                            val note = if (selected !in available) "所选来源当前不可用，请检查数据接入设置。"
                            else WeatherSourceCapabilities.map.getValue(selected).note
                            if (note.isNotBlank()) Text(note, color = palette.textSecondary, style = captionStyle)
                        }
                        if (evidence != null) {
                            Text("实际来源：${moduleSourceName(evidence.source)}", color = palette.textSecondary, style = captionStyle)
                            Text(evidence.observationTime?.let { "数据时间：${moduleEvidenceTime(it, weather.utcOffsetSeconds)}" }
                                ?: "上游未提供数据发布时间", color = palette.textSecondary, style = captionStyle)
                            evidence.fetchedAt?.let { Text("取得时间：${moduleEvidenceTime(it, weather.utcOffsetSeconds)}", color = palette.textSecondary, style = captionStyle) }
                        }
                        weather?.sourceDiagnostics?.get(module.key)?.let { Text(it, color = palette.orange, style = captionStyle) }
                    }
                }
            }
        }
        Divider()
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onCompare)
            .padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("比较天气来源", Modifier.weight(1f), color = palette.mint,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
            Text("→", color = palette.mint, fontSize = 18.sp)
        }
    }
}

private fun moduleSourceName(raw: String): String = raw.split('+').map { it.trim() }.distinct().joinToString("、") { code ->
    SourcePref.entries.firstOrNull { it.en.equals(code, ignoreCase = true) }?.cn ?: code
}

private fun moduleEvidenceTime(millis: Long, offsetSeconds: Int?): String {
    val offset = offsetSeconds?.takeIf { it in -18 * 3600..18 * 3600 }
    val zone = offset?.let(ZoneOffset::ofTotalSeconds) ?: ZoneId.systemDefault()
    return DateTimeFormatter.ofPattern("MM-dd HH:mm").format(Instant.ofEpochMilli(millis).atZone(zone)) +
        if (offset == null) "（手机时间）" else "（地点时间）"
}

/** Closing the dialog cancels its effect and the batch of provider calls. */
@Composable
fun WeatherSourceComparison(city: City, onDismiss: () -> Unit) {
    var rows by remember(city.locationKey, city.latitude, city.longitude) { mutableStateOf<List<WeatherRepository.SourceComparison>?>(null) }
    var error by remember(city.locationKey) { mutableStateOf<String?>(null) }
    var retry by remember(city.locationKey) { mutableIntStateOf(0) }
    LaunchedEffect(city.locationKey, city.latitude, city.longitude, retry) {
        rows = null; error = null
        try { rows = WeatherRepository.fetchComparison(city) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { error = "暂时无法完成比较，请检查网络后重试" }
    }
    WeatherOverlayDialog(onDismiss) { modifier ->
        WeatherSourceComparisonPanel(city, rows, error, onRetry = { retry++ }, onDismiss = onDismiss, modifier = modifier)
    }
}

@Composable
internal fun WeatherSourceComparisonPanel(city: City, rows: List<WeatherRepository.SourceComparison>?,
    error: String?, onRetry: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalZhishengPalette.current
    val body = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp)
    val caption = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp)
    val loading = rows == null && error == null
    Surface(modifier.zhishengDialogPanel(glass = true), color = Color.Transparent, contentColor = palette.text) {
        ProvideTextStyle(body) {
            Column {
                Text("来源比较 · ${city.displayName}", Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 25.sp))
                HorizontalDivider(color = palette.cardBorder.copy(alpha = .35f))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("同一地点同时查询；各源观测时刻不同。以下是真实返回值，不作准确率排名。心知和墨迹仅在明确选用时查询，不参与这里的额外比较。", color = palette.textSecondary)
                    when {
                        error != null -> Text(error, color = palette.textSecondary)
                        loading -> {
                            CircularProgressIndicator(color = palette.mint, modifier = Modifier.size(24.dp))
                            Text("正在查询，关闭即可取消", color = palette.textSecondary)
                        }
                        else -> {
                            val items = rows.orEmpty()
                            val commonHour = commonComparisonHour(items, items.firstOrNull()?.requestedAt ?: System.currentTimeMillis())
                            Text("请求时刻：${comparisonTime(items.firstOrNull()?.requestedAt, city)}", color = palette.textSecondary, style = caption)
                            Text(if (commonHour == null) "暂无两个来源共同返回的未来小时，缺失值不补画。" else "共同预报时刻：${comparisonTime(commonHour, city)}", color = palette.textSecondary)
                            items.forEach { row ->
                                val data = row.data
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(row.source.cn, color = palette.mint, fontWeight = FontWeight.SemiBold)
                                    if (data == null) Text(row.diagnostic ?: "未返回数据", color = palette.textSecondary)
                                    else {
                                        Text("观测：${comparisonTime(data.updateTime, city)} · 取得：${comparisonTime(data.fetchedAt, city)}", color = palette.textSecondary, style = caption)
                                        data.current?.let { current ->
                                            Text("温度 ${comparisonValue(current.temperature, "℃")} · 湿度 ${comparisonValue(current.humidity, "%")}")
                                            Text("风速 ${comparisonValue(current.windSpeed, "km/h")} · ${current.weatherText ?: current.condition?.label ?: "天气现象缺失"}")
                                        } ?: Text("未返回当前实况", color = palette.textSecondary)
                                        data.aqi?.let { Text("AQI ${Fmt.aqiValueLabel(it.value, it.estimated)} · " +
                                            if (it.estimated) Fmt.AQI_ESTIMATION_LABEL
                                            else it.standard ?: "标准未标注，不能直接横比", color = palette.textSecondary) }
                                        Text("逐时 ${data.hourly.size} · 逐日 ${data.daily.size} · 短时 ${data.rainMinutes.size} · 预警 ${data.alerts.size}", color = palette.textSecondary, style = caption)
                                        val hour = commonHour?.let { time -> data.hourly.firstOrNull { it.timeMillis == time } }
                                        if (commonHour != null) Text("共同小时温度：${comparisonValue(hour?.temperature, "℃", "未返回")}")
                                        data.moduleEvidence["current"]?.locationScope?.let { Text(it, color = palette.textSecondary, style = caption) }
                                        WeatherSourceCapabilities.map.getValue(row.source).note.takeIf(String::isNotBlank)?.let { Text(it, color = palette.textSecondary, style = caption) }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = palette.cardBorder.copy(alpha = .35f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onRetry, enabled = !loading) { Text(if (error == null) "重新比较" else "重试", color = if (loading) palette.textTertiary else palette.mint) }
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = palette.mint, contentColor = palette.bg)) { Text("关闭") }
                }
            }
        }
    }
}

private fun comparisonValue(value: Double?, unit: String, missing: String = "缺失"): String =
    value?.takeIf(Double::isFinite)?.let { "${String.format(java.util.Locale.ROOT, "%.1f", it)} $unit" } ?: missing

internal fun commonComparisonHour(rows: List<WeatherRepository.SourceComparison>, nowMillis: Long): Long? =
    rows.flatMap { row -> row.data?.hourly.orEmpty().filter { it.timeMillis >= nowMillis && it.temperature?.isFinite() == true }.map { it.timeMillis }.distinct() }
        .groupingBy { it }.eachCount().filterValues { it >= 2 }.keys.minOrNull()

private fun comparisonTime(millis: Long?, city: City): String {
    // For global coordinates the actual provider offset is passed by the row below where possible;
    // UTC labels keep all providers on one unambiguous clock and avoid phone-timezone substitution.
    return millis?.let { DateTimeFormatter.ofPattern("MM-dd HH:mm 'UTC'").format(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC)) } ?: "上游未提供"
}
