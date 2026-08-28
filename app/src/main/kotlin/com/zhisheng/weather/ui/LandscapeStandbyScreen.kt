package com.zhisheng.weather.ui

import android.provider.Settings
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** 横放在桌面或充电座上使用的独立气象时钟；不复用竖屏滚动页。 */
@Composable
fun LandscapeStandbyScreen(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
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

    Box(Modifier.fillMaxSize().background(ZhishengBg)) {
        WeatherAmbience(data, uiState.prefs.ambience, night = night)
        StandbySignalField()
        BoxWithConstraints(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp, vertical = 18.dp),
        ) {
            val clockSize = (maxHeight.value * 0.38f).coerceIn(86f, 172f).sp
            val weatherIconSize = minOf(maxHeight * 0.29f, 118.dp)
            val secondsBaseline = maxHeight * 0.075f
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Column(
                    Modifier.weight(1.35f).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                uiState.selectedCity?.displayName ?: "枳生天气",
                                style = MaterialTheme.typography.headlineMedium,
                                color = ZhishengOrange,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "ZHISHENG AMBIENT TERMINAL / 0.1.3",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZhishengTextTertiary,
                                letterSpacing = 2.sp,
                            )
                        }
                        Text(
                            "● ${if (uiState.loading) "SYNC" else "LIVE"}",
                            modifier = Modifier.clickable(onClick = onRefresh).padding(8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.loading) ZhishengOrange else ZhishengMint,
                            letterSpacing = 1.5.sp,
                        )
                    }

                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            clock,
                            fontSize = clockSize,
                            lineHeight = clockSize,
                            color = ZhishengText,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-7).sp,
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            seconds,
                            modifier = Modifier.padding(bottom = secondsBaseline),
                            style = MaterialTheme.typography.headlineSmall,
                            color = ZhishengCyan,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(date, style = MaterialTheme.typography.titleLarge, color = ZhishengTextSecondary)
                            Spacer(Modifier.width(18.dp))
                            Text(
                                uiState.selectedCity?.let { Fmt.coordinates(it.latitude, it.longitude) }.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = ZhishengTextTertiary,
                                letterSpacing = 1.sp,
                            )
                        }
                        Spacer(Modifier.height(9.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(ZhishengCyan.copy(alpha = 0.5f)))
                    }
                }

                Column(
                    Modifier.weight(0.92f).fillMaxHeight()
                        .background(ZhishengSurface.copy(alpha = 0.78f))
                        .border(1.dp, ZhishengCardBorder)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        WeatherIcon(current?.condition, Modifier.size(weatherIconSize))
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    Fmt.temp(current?.temperature, uiState.tempUnit) ?: "--",
                                    fontSize = (clockSize.value * 0.43f).sp,
                                    lineHeight = (clockSize.value * 0.46f).sp,
                                    color = ZhishengText,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text("°", style = MaterialTheme.typography.headlineMedium, color = ZhishengOrange)
                            }
                            Text(
                                current?.weatherText ?: current?.condition?.label ?: "等待天气数据",
                                style = MaterialTheme.typography.titleMedium,
                                color = ZhishengCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StandbyDatum("体感", Fmt.temp(current?.feelsLike, uiState.tempUnit)?.plus("°") ?: "--")
                        StandbyDatum("最高", Fmt.temp(today?.high, uiState.tempUnit)?.plus("°") ?: "--")
                        StandbyDatum("最低", Fmt.temp(today?.low, uiState.tempUnit)?.plus("°") ?: "--")
                    }

                    val hours = data?.hourly.orEmpty().take(5)
                    if (hours.isNotEmpty()) {
                        Column {
                            Text(
                                "NEXT / ${hours.size} HOURS",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZhishengOrange,
                                letterSpacing = 1.5.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth()) {
                                hours.forEach { hour ->
                                    Column(
                                        Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            Fmt.hour(hour.timeMillis, offset),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ZhishengTextTertiary,
                                        )
                                        Text(
                                            Fmt.temp(hour.temperature, uiState.tempUnit)?.plus("°") ?: "--",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = ZhishengText,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val alert = data?.alerts?.firstOrNull()
                    Text(
                        alert?.let { "! ${it.title}" } ?: "SRC ${data?.dataSource ?: "--"}  ·  UPD ${data?.updateTime?.let { Fmt.clock(it, offset) } ?: "--:--"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = alert?.let { alertLevelColor(it.severity) } ?: ZhishengTextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun StandbyDatum(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        Text(value, style = MaterialTheme.typography.titleMedium, color = ZhishengMint, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StandbySignalField() {
    val context = LocalContext.current
    val animate = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
    val transition = rememberInfiniteTransition(label = "standby-scan")
    val animatedPhase by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing), RepeatMode.Restart),
        label = "scan-phase",
    )
    val phase = if (animate) animatedPhase else 0.62f
    val line = ZhishengCyan
    val orange = ZhishengOrange
    Canvas(Modifier.fillMaxSize()) {
        val y = size.height * 0.72f
        drawLine(line.copy(alpha = 0.08f), Offset(size.width * 0.04f, y), Offset(size.width * 0.96f, y), 1f)
        val x = size.width * phase
        drawLine(line.copy(alpha = 0.36f), Offset(x - 44f, y), Offset(x + 44f, y), 2f)
        drawRect(line.copy(alpha = 0.72f), Offset(x - 3f, y - 3f), Size(6f, 6f))
        val inset = 14f
        val arm = 42f
        drawLine(orange.copy(alpha = 0.30f), Offset(inset, inset), Offset(inset + arm, inset), 2f)
        drawLine(orange.copy(alpha = 0.30f), Offset(inset, inset), Offset(inset, inset + arm), 2f)
        drawRect(
            line.copy(alpha = 0.10f),
            Offset(size.width * 0.64f, size.height * 0.07f),
            Size(size.width * 0.32f, size.height * 0.86f),
            style = Stroke(width = 1f),
        )
    }
}
