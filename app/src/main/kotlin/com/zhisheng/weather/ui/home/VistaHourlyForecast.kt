package com.zhisheng.weather.ui.home

import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.R
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.phaseAwareCondition
import com.zhisheng.weather.ui.Fmt
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.components.WeatherIcon
import com.zhisheng.weather.ui.theme.LocalHomeSurfaceStyle
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** A light weather timeline: one reading baseline, with wind available at a glance. */
@Composable
internal fun VistaHourlyForecast(
    items: List<HourlyDisplayItem>,
    data: WeatherData,
    unit: String,
    utcOffsetSeconds: Int?,
) {
    if (items.isEmpty()) return
    val palette = LocalZhishengPalette.current
    val haptic = LocalHapticFeedback.current
    val inspectionMode = LocalInspectionMode.current
    val edgeOptics = remember(inspectionMode) {
        if (!inspectionMode && Build.VERSION.SDK_INT >= 33) HourlyEdgeOptics() else null
    }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val showRain = vistaHourlyShowsPrecip(items.map { it.weather })
    val scrollState = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val visibleColumns = floor(maxWidth.value / (56f * fontScale)).toInt()
            .coerceIn(if (fontScale <= 1.5f) 3 else 2, 8)
        val cellWidth = maxWidth / visibleColumns
        val cellPx = with(LocalDensity.current) { cellWidth.toPx() }
        val surfaceStyle = LocalHomeSurfaceStyle.current
        val refract = surfaceStyle == HomeSurfaceStyle.FRAGRANCE_GLASS && edgeOptics != null

        LaunchedEffect(scrollState, cellPx, haptic) {
            var lastHour = -1
            snapshotFlow {
                if (scrollState.isScrollInProgress) (scrollState.value / cellPx).roundToInt() else -1
            }.distinctUntilChanged().collect { hour ->
                if (hour >= 0 && lastHour >= 0 && hour != lastHour) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                lastHour = hour
            }
        }
        LaunchedEffect(scrollState, cellPx, surfaceStyle) {
            snapshotFlow { scrollState.isScrollInProgress to scrollState.maxValue }.distinctUntilChanged()
                .collect { (inProgress, maxValue) ->
                    if (!inProgress && maxValue > 0) {
                        val target = ((scrollState.value / cellPx).roundToInt() * cellPx).roundToInt()
                            .coerceIn(0, maxValue)
                        if (abs(target - scrollState.value) > 1) scrollState.animateScrollTo(target)
                    }
                }
        }
        // Forecast text stays flat and readable; only the last pixels bend under the glass lip.
        Box(Modifier.fillMaxWidth().graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }.drawWithContent {
            drawContent()
            val edge = ((if (refract) 14.dp else 12.dp).toPx() / size.width.coerceAtLeast(1f)).coerceIn(0f, 0.16f)
            drawRect(Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = if (scrollState.value > 0) 0f else 1f),
                edge to Color.Black,
                (1f - edge) to Color.Black,
                1f to Color.Black.copy(alpha = if (scrollState.value < scrollState.maxValue) 0f else 1f),
            ), blendMode = BlendMode.DstIn)
        }) {
        Box(Modifier.fillMaxWidth().graphicsLayer {
            if (refract && Build.VERSION.SDK_INT >= 33) {
                renderEffect = edgeOptics?.effect(
                    width = size.width,
                    height = size.height,
                    edgeWidth = 20.dp.toPx(),
                    hasLeft = scrollState.value > 0,
                    hasRight = scrollState.value < scrollState.maxValue,
                )
            }
        }) {
        Row(Modifier.horizontalScroll(scrollState)) {
            items.forEachIndexed { index, item ->
                val h = item.weather
                val condition = phaseAwareCondition(h.condition, data, h.timeMillis)
                val temperature = Fmt.temp(h.temperature, unit)?.let { "$it°" } ?: "—"
                val numberSize = minOf(20f, cellWidth.value / (temperature.length * 0.58f * fontScale))
                Column(Modifier.width(cellWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Every reading occupies the full cell width and a shared row height.
                    // Different icon silhouettes and text lengths must not shift a column's axis.
                    Box(Modifier.fillMaxWidth().height(22.dp * fontScale), contentAlignment = Alignment.Center) {
                        Text(if (item.isNow) "现在" else Fmt.hour(h.timeMillis, utcOffsetSeconds),
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            color = if (item.isNow) palette.mint else palette.textSecondary,
                            fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, maxLines = 1)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                        WeatherIcon(condition, Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(26.dp * fontScale), contentAlignment = Alignment.Center) {
                        Text(temperature, color = if (item.isNow) palette.mint else palette.text,
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            fontSize = numberSize.sp, lineHeight = 25.sp,
                            fontWeight = FontWeight.Light, letterSpacing = (-0.4).sp, maxLines = 1)
                    }
                    Box(Modifier.height(18.dp * fontScale).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(condition?.takeUnless { it == WeatherCondition.UNKNOWN }?.label ?: "暂无",
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            color = palette.textSecondary, fontSize = 11.sp,
                            lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    if (showRain) Row(Modifier.fillMaxWidth().height(20.dp * fontScale),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center) {
                        val label = vistaHourlyPrecipLabel(h.precipProb)
                        if (label.isNotEmpty()) {
                            val tint = if ((h.precipProb ?: 0) > 0) palette.cyan else palette.textTertiary
                            PhosphorIcon(R.drawable.ph_drop, null, Modifier.size(10.dp), tint.copy(alpha = 0.7f))
                            Spacer(Modifier.width(3.dp))
                            Text(label, color = tint, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(20.dp * fontScale).padding(top = 2.dp), contentAlignment = Alignment.Center) {
                        Text(hourlyWindLabel(h) ?: "—", color = palette.textSecondary,
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                            fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1)
                    }
                }
            }
        }
        }
        }
    }
}
