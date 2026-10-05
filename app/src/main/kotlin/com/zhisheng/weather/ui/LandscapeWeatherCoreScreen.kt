package com.zhisheng.weather.ui

import com.zhisheng.weather.ui.components.weatherSharedBounds
import com.zhisheng.weather.ui.home.dataSourceShortLabel
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.R
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.BuildConfig
import com.zhisheng.weather.i18n.uiText
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.phaseAwareCondition
import com.zhisheng.weather.ui.components.WeatherAmbience
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.components.isNightAt
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.ui.theme.alertLevelColor
import com.zhisheng.weather.ui.theme.zhishengScreen
import com.zhisheng.weather.ui.theme.zhishengPanel
import com.zhisheng.weather.ui.theme.zhishengCompactPanel
import com.zhisheng.weather.ui.theme.isPhosphorVista
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import androidx.lifecycle.repeatOnLifecycle

/** 气象中枢：时间、当前天气、日照进度和未来趋势在同一画布上协同表达。 */
@Composable
internal fun LandscapeWeatherCoreScreen(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    onExitLandscape: () -> Unit,
    onSettings: () -> Unit,
) {
    val data = uiState.weather
    val current = data?.current
    val offset = data?.utcOffsetSeconds
    val previewTime = LocalWeatherPreviewTime.current
    var liveTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val nowMillis = previewTime ?: liveTime
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(offset, previewTime, lifecycle) {
        if (previewTime != null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
        while (true) {
            liveTime = System.currentTimeMillis()
            delay(1_000L - liveTime % 1_000L)
        }
        }
    }
    val zone = remember(offset) {
        offset?.takeIf { it in -18 * 3_600..18 * 3_600 }
            ?.let(ZoneOffset::ofTotalSeconds)
            ?: ZoneId.systemDefault()
    }
    val cityNow = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val clock = cityNow.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
    val seconds = cityNow.format(DateTimeFormatter.ofPattern("ss", Locale.US))
    val date = cityNow.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
    val today = data?.todayDaily(nowMillis)
    val night = isNightAt(today?.sunrise, today?.sunset, cityNow.hour * 60 + cityNow.minute)
    val hours = data?.hourly.orEmpty().filter { it.timeMillis >= nowMillis - 30 * 60_000L }.take(6)

    if (isPhosphorVista) {
        VistaLandscapeWeatherCore(uiState, nowMillis, night, clock, seconds, date, onRefresh, onExitLandscape, onSettings)
        return
    }

    Box(Modifier.fillMaxSize().zhishengScreen()) {
        WeatherAmbience(data, uiState.prefs.ambience, night = night)
        if (!isPhosphorVista) WeatherCoreSignalField(night)
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            val clockSize = (maxHeight.value * 0.31f).coerceIn(74f, 150f).sp
            val iconSize = minOf(maxHeight * 0.18f, 78.dp)
            Column(Modifier.fillMaxSize()) {
                WeatherCoreTopRail(
                    city = uiState.selectedCity?.displayName ?: "枳生天气",
                    loading = uiState.loading,
                    onRefresh = onRefresh,
                    onExitLandscape = onExitLandscape,
                    onSettings = onSettings,
                )
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Column(
                        Modifier.weight(1.22f).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    date,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isPhosphorVista) ZhishengTextSecondary else ZhishengOrange,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    uiState.selectedCity?.let { Fmt.coordinates(it.latitude, it.longitude) }.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ZhishengTextTertiary,
                                    letterSpacing = 1.sp,
                                )
                            }
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    clock,
                                    fontSize = clockSize,
                                    lineHeight = clockSize,
                                    color = ZhishengText,
                                    fontWeight = if (isPhosphorVista) FontWeight.Light else FontWeight.Bold,
                                    letterSpacing = (if (isPhosphorVista) -4 else -6).sp,
                                )
                                Column(
                                    modifier = Modifier.padding(start = 12.dp, bottom = 11.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("秒", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                                    Text(
                                        seconds,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = ZhishengCyan,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WeatherIcon(
                                data?.let { phaseAwareCondition(current?.condition, it, nowMillis) }
                                    ?: current?.condition,
                                Modifier.weatherSharedBounds("current-condition").size(iconSize),
                            )
                            Spacer(Modifier.width(12.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    Fmt.temp(current?.temperature, uiState.tempUnit) ?: "--",
                                    modifier = Modifier.weatherSharedBounds("current-temperature"),
                                    style = MaterialTheme.typography.displayMedium,
                                    color = ZhishengText,
                                    fontWeight = if (isPhosphorVista) FontWeight.Light else FontWeight.Bold,
                                )
                                Text("°", style = MaterialTheme.typography.headlineMedium, color = if (isPhosphorVista) ZhishengTextSecondary else ZhishengOrange)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    current?.weatherText ?: current?.condition?.label ?: "等待天气数据",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (isPhosphorVista) ZhishengText else ZhishengCyan,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(
                                        current?.feelsLike?.let { "体感 ${Fmt.temp(it, uiState.tempUnit)}°" },
                                        current?.windSpeed?.let { Fmt.wind(it, uiState.prefs.windUnit) },
                                    ).joinToString("  ·  ").ifBlank { "实时气象正在同步" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ZhishengTextSecondary,
                                    maxLines = 1,
                                )
                            }
                        }

                        // 与澄空版同样的预警呈现：横向版式里逐条列出，不只显示第一条。
                        data?.alerts.orEmpty().forEach { alert ->
                            Row(
                                Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("!", style = MaterialTheme.typography.labelMedium, color = ZhishengOrange, fontWeight = FontWeight.Bold)
                                Text(
                                    alert.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = alertLevelColor(alert.severity),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        WeatherCoreSunTrack(today, cityNow.hour * 60 + cityNow.minute)
                    }

                    WeatherVectorPanel(
                        modifier = Modifier.weight(0.90f).fillMaxHeight(),
                        hours = hours,
                        unit = uiState.tempUnit,
                        offset = offset,
                        high = Fmt.temp(today?.high, uiState.tempUnit)?.plus("°") ?: "--",
                        low = Fmt.temp(today?.low, uiState.tempUnit)?.plus("°") ?: "--",
                        humidity = current?.humidity?.let { "${it.toInt()}%" } ?: "--",
                        aqi = data?.aqi?.value?.toString(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().height(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "数据源 ${dataSourceShortLabel(data?.dataSource)} · 更新 ${(data?.updateTime ?: data?.fetchedAt)?.let { Fmt.clock(it, offset) } ?: "暂无"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextTertiary,
                        maxLines = 1,
                    )
                    Text(
                        if (night) "夜间观测" else "日间观测",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengMint,
                        letterSpacing = 1.2.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherCoreTopRail(
    city: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onExitLandscape: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isPhosphorVista) {
                Box(Modifier.size(7.dp).background(ZhishengOrange))
                Spacer(Modifier.width(10.dp))
            }
            Column {
                Text(city, style = MaterialTheme.typography.titleMedium, color = ZhishengText, fontWeight = FontWeight.Bold)
                if (!isPhosphorVista) Text(
                    "ZHISHENG WEATHER CORE / ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengTextTertiary,
                    letterSpacing = 1.5.sp,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isPhosphorVista) (if (loading) "更新中" else "刷新天气") else "● ${if (loading) "SYNC" else "LIVE"}",
                modifier = Modifier.clickable(role = Role.Button, onClick = onRefresh)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = if (loading) ZhishengOrange else ZhishengMint,
                letterSpacing = 1.4.sp,
            )
            StandbyPortraitButton(onClick = onExitLandscape)
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = onSettings,
                modifier = Modifier.size(if (isPhosphorVista) 48.dp else 38.dp).zhishengCompactPanel(),
            ) {
                if (isPhosphorVista) PhosphorIcon(R.drawable.ph_gear, uiText("设置"), Modifier.size(20.dp), ZhishengTextSecondary)
                else Icon(Icons.Default.Settings, contentDescription = uiText("设置"), tint = ZhishengOrange, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
internal fun WeatherCoreSunTrack(today: DailyWeather?, nowMinutes: Int) {
    val progress = sunTrackProgress(today?.sunrise, today?.sunset, nowMinutes)
    val borderColor = ZhishengCardBorder
    val orange = ZhishengOrange
    val cyan = ZhishengCyan
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("日出 ${today?.sunrise ?: "--:--"}", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            Text("太阳轨迹", style = MaterialTheme.typography.labelSmall, color = ZhishengOrange, letterSpacing = 1.2.sp)
            Text("日落 ${today?.sunset ?: "--:--"}", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        }
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val start = Offset(5f, size.height * 0.72f)
            val end = Offset(size.width - 5f, size.height * 0.72f)
            drawLine(borderColor, start, end, 2f)
            drawLine(orange.copy(alpha = 0.70f), start, Offset(start.x + (end.x - start.x) * progress, start.y), 3f)
            val x = start.x + (end.x - start.x) * progress
            drawCircle(orange.copy(alpha = 0.20f), 10f, Offset(x, start.y))
            drawCircle(orange, 4f, Offset(x, start.y))
            drawLine(cyan.copy(alpha = 0.32f), Offset(x, 2f), Offset(x, size.height - 1f), 1f)
        }
    }
}

@Composable
private fun WeatherVectorPanel(
    modifier: Modifier,
    hours: List<HourlyWeather>,
    unit: String,
    offset: Int?,
    high: String,
    low: String,
    humidity: String,
    aqi: String?,
) {
    Column(
        modifier.zhishengPanel(containerColor = if (isPhosphorVista) ZhishengSurface else ZhishengSurface.copy(alpha = 0.64f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("未来六小时", style = MaterialTheme.typography.labelMedium, color = ZhishengOrange, letterSpacing = 1.3.sp)
            Text("温度与降水", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        }
        WeatherVectorGraph(hours, unit)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            hours.forEach { hour ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(Fmt.hour(hour.timeMillis, offset), style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                    // 与澄空版一致：每小时也要能看到天气图标，而不是只有数字。
                    WeatherIcon(hour.condition, Modifier.size(22.dp))
                    Text(
                        Fmt.temp(hour.temperature, unit)?.plus("°") ?: "--",
                        style = MaterialTheme.typography.titleSmall,
                        color = ZhishengText,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        Fmt.probability(hour.precipProb) ?: "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = if ((hour.precipProb ?: 0) > 0) ZhishengCyan else ZhishengTextTertiary,
                    )
                }
            }
        }
        if (hours.any { it.precipProb != null }) {
            Text("百分比为降水概率", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            WeatherCoreDatum("最高", high)
            WeatherCoreDatum("最低", low)
            WeatherCoreDatum("湿度", humidity)
            aqi?.let { WeatherCoreDatum("空气质量", it) }
        }
        Text("未来六小时趋势已就绪", style = MaterialTheme.typography.labelSmall,
            color = ZhishengMint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WeatherCoreDatum(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = ZhishengMint, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun WeatherVectorGraph(hours: List<HourlyWeather>, unit: String) {
    val values = hours.map { hour ->
        hour.temperature?.takeIf(Double::isFinite)?.let { if (unit == "f") it * 9.0 / 5.0 + 32.0 else it }
    }
    val borderColor = ZhishengCardBorder
    val cyan = ZhishengCyan
    Canvas(Modifier.fillMaxWidth().height(72.dp)) {
        drawLine(borderColor, Offset(0f, size.height - 8f), Offset(size.width, size.height - 8f), 1f)
        if (hours.isEmpty() || values.all { it == null }) return@Canvas
        val available = values.filterNotNull()
        val min = available.minOrNull() ?: 0.0
        val max = available.maxOrNull() ?: min + 1.0
        val span = (max - min).coerceAtLeast(1.0)
        val step = size.width / hours.size
        val path = Path()
        var started = false
        values.forEachIndexed { index, value ->
            if (value != null) {
                val x = (index + 0.5f) * step
                val y = 10f + ((max - value) / span).toFloat() * (size.height - 30f)
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
                drawCircle(cyan, if (index == 0) 4f else 2.5f, Offset(x, y))
            } else {
                started = false
            }
            val rain = (hours[index].precipProb ?: 0).coerceIn(0, 100) / 100f
            if (rain > 0f) {
                val barWidth = (size.width / hours.size) * 0.44f
                val barHeight = 4f + rain * 13f
                val centerX = (index + 0.5f) * size.width / hours.size
                drawRect(
                    cyan.copy(alpha = 0.32f + rain * 0.48f),
                    Offset(centerX - barWidth / 2f, size.height - 8f - barHeight),
                    Size(barWidth, barHeight),
                )
            }
        }
        drawPath(path, cyan.copy(alpha = 0.78f), style = Stroke(width = 2f))
    }
}

@Composable
private fun WeatherCoreSignalField(night: Boolean) {
    val context = LocalContext.current
    val motion = remember {
        runCatching {
            AndroidSettings.Global.getFloat(
                context.contentResolver,
                AndroidSettings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }.getOrDefault(true)
    }
    val transition = rememberInfiniteTransition(label = "weather-core-signal")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "weather-core-sweep",
    )
    val angle = if (motion) sweep else 218f
    val cyan = ZhishengCyan
    val orange = ZhishengOrange
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width * 0.73f, size.height * 0.50f)
        val radius = size.minDimension * 0.38f
        drawCircle(cyan.copy(alpha = if (night) 0.055f else 0.035f), radius, center)
        drawArc(
            color = cyan.copy(alpha = 0.18f),
            startAngle = angle,
            sweepAngle = 74f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = 2f),
        )
        val gridColor = cyan.copy(alpha = 0.045f)
        repeat(8) { i ->
            val x = size.width * (i + 1) / 9f
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
        }
        repeat(4) { i ->
            val y = size.height * (i + 1) / 5f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
        }
        val arm = 36f
        drawLine(orange.copy(alpha = 0.38f), Offset(14f, 14f), Offset(14f + arm, 14f), 2f)
        drawLine(orange.copy(alpha = 0.38f), Offset(14f, 14f), Offset(14f, 14f + arm), 2f)
    }
}

internal fun sunTrackProgress(sunrise: String?, sunset: String?, nowMinutes: Int): Float {
    val rise = clockMinutes(sunrise) ?: return 0.5f
    val set = clockMinutes(sunset) ?: return 0.5f
    if (set <= rise) return 0.5f
    return ((nowMinutes - rise).toFloat() / (set - rise).toFloat()).coerceIn(0f, 1f)
}

private fun clockMinutes(raw: String?): Int? {
    val match = Regex("(\\d{1,2}):(\\d{2})").find(raw.orEmpty()) ?: return null
    val hour = match.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val minute = match.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return hour * 60 + minute
}
