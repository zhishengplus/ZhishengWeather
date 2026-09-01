/* Hallmark · pre-emit critique: P5 H5 E5 S5 R5 V5 */
/* Hallmark · macrostructure: asymmetric weather command deck · genre: atmospheric
 * theme: existing Zhisheng terminal · states: live · syncing · alert · empty · motion-reduced
 * contrast: pass
 */
package com.zhisheng.weather.ui

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
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

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
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(offset) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L - nowMillis % 1_000L)
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

    Box(Modifier.fillMaxSize().background(ZhishengBg)) {
        WeatherAmbience(data, uiState.prefs.ambience, night = night)
        WeatherCoreSignalField(night)
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
                                    color = ZhishengOrange,
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
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-6).sp,
                                )
                                Column(
                                    modifier = Modifier.padding(start = 12.dp, bottom = 11.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("SEC", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
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
                                Modifier.size(iconSize),
                            )
                            Spacer(Modifier.width(12.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    Fmt.temp(current?.temperature, uiState.tempUnit) ?: "--",
                                    style = MaterialTheme.typography.displayMedium,
                                    color = ZhishengText,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text("°", style = MaterialTheme.typography.headlineMedium, color = ZhishengOrange)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    current?.weatherText ?: current?.condition?.label ?: "等待天气数据",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = ZhishengCyan,
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
                        alert = data?.alerts?.firstOrNull()?.title,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().height(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "SRC ${data?.dataSource ?: "--"}  ·  UPD ${data?.updateTime?.let { Fmt.clock(it, offset) } ?: "--:--"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextTertiary,
                        maxLines = 1,
                    )
                    Text(
                        if (night) "NIGHT OPTICS / ACTIVE" else "DAYLIGHT VECTOR / ACTIVE",
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
            Box(Modifier.size(7.dp).background(ZhishengOrange))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(city, style = MaterialTheme.typography.titleMedium, color = ZhishengText, fontWeight = FontWeight.Bold)
                Text(
                    "ZHISHENG WEATHER CORE / ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZhishengTextTertiary,
                    letterSpacing = 1.5.sp,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "● ${if (loading) "SYNC" else "LIVE"}",
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
                modifier = Modifier.size(38.dp).border(1.dp, ZhishengCardBorder),
            ) {
                Icon(Icons.Default.Settings, contentDescription = uiText("设置"), tint = ZhishengOrange, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun WeatherCoreSunTrack(today: DailyWeather?, nowMinutes: Int) {
    val progress = sunTrackProgress(today?.sunrise, today?.sunset, nowMinutes)
    val borderColor = ZhishengCardBorder
    val orange = ZhishengOrange
    val cyan = ZhishengCyan
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("SUNRISE ${today?.sunrise ?: "--:--"}", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            Text("SOLAR TRACK", style = MaterialTheme.typography.labelSmall, color = ZhishengOrange, letterSpacing = 1.2.sp)
            Text("SUNSET ${today?.sunset ?: "--:--"}", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
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
    alert: String?,
) {
    Column(
        modifier.background(ZhishengSurface.copy(alpha = 0.64f))
            .border(1.dp, ZhishengCardBorder)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("06H WEATHER VECTOR", style = MaterialTheme.typography.labelMedium, color = ZhishengOrange, letterSpacing = 1.3.sp)
            Text("TEMP / PRECIP", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        }
        WeatherVectorGraph(hours, unit)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            hours.forEach { hour ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text(Fmt.hour(hour.timeMillis, offset), style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            WeatherCoreDatum("最高", high)
            WeatherCoreDatum("最低", low)
            WeatherCoreDatum("湿度", humidity)
        }
        Text(
            alert?.let { "! $it" } ?: "未来六小时趋势已就绪",
            style = MaterialTheme.typography.labelSmall,
            color = if (alert != null) ZhishengOrange else ZhishengMint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun WeatherVectorGraph(hours: List<HourlyWeather>, unit: String) {
    val values = hours.map { hour ->
        hour.temperature?.let { if (unit == "f") it * 9.0 / 5.0 + 32.0 else it }
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
        val step = if (hours.size > 1) size.width / (hours.size - 1) else size.width
        val path = Path()
        var started = false
        values.forEachIndexed { index, value ->
            if (value != null) {
                val x = index * step
                val y = 10f + ((max - value) / span).toFloat() * (size.height - 30f)
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
                drawCircle(cyan, if (index == 0) 4f else 2.5f, Offset(x, y))
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
        if (started) drawPath(path, cyan.copy(alpha = 0.78f), style = Stroke(width = 2f))
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
