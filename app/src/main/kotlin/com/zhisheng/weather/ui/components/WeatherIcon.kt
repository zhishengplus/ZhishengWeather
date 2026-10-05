package com.zhisheng.weather.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.ui.theme.isPhosphorVista
import com.zhisheng.weather.ui.theme.LocalZhishengPalette

// 天气语义沿用 model/ConditionIcons.kt 的映射；桌面小组件继续使用原 weather_* 资源。
// 视界主题采用柔和明暗的原生天气形体，小尺寸仍保持天气辨识度。
// 经典终端使用 classic_* 一套（同一形状、终端调色板）；经典浅色沿用 SrcIn 钢青线条处理。
@Composable
fun WeatherIcon(
    condition: WeatherCondition?,
    modifier: Modifier = Modifier,
) {
    if (condition == null || condition == WeatherCondition.UNKNOWN) {
        WeatherUnavailableIcon(modifier)
        return
    }
    val palette = LocalZhishengPalette.current
    val vista = isPhosphorVista
    if (vista) {
        VistaWeatherArtwork(condition, modifier)
        return
    }
    val res = if (vista) vistaConditionIconRes(condition, palette.isLight) else classicConditionIconRes(condition)
    if (res != null) {
        Box(modifier = modifier) {
            Image(
                painter = painterResource(res),
                contentDescription = condition?.label,
                modifier = Modifier.fillMaxSize(),
                colorFilter = when {
                    vista -> null
                    palette.isLight -> ColorFilter.tint(palette.cyan, BlendMode.SrcIn)
                    else -> null
                },
            )
            if (condition == WeatherCondition.HAIL || condition == WeatherCondition.FREEZING_RAIN ||
                condition == WeatherCondition.FREEZING_DRIZZLE
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val color = if (condition == WeatherCondition.HAIL) palette.orange else palette.cyan
                    val cx = size.width * 0.78f
                    val cy = size.height * 0.24f
                    val r = size.minDimension * 0.10f
                    if (condition == WeatherCondition.HAIL) {
                        val diamond = Path().apply {
                            moveTo(cx, cy - r)
                            lineTo(cx + r, cy)
                            lineTo(cx, cy + r)
                            lineTo(cx - r, cy)
                            close()
                        }
                        drawPath(diamond, color, style = Stroke(width = size.minDimension * 0.018f))
                    } else {
                        drawLine(color, androidx.compose.ui.geometry.Offset(cx - r, cy), androidx.compose.ui.geometry.Offset(cx + r, cy), size.minDimension * 0.018f)
                        drawLine(color, androidx.compose.ui.geometry.Offset(cx, cy - r), androidx.compose.ui.geometry.Offset(cx, cy + r), size.minDimension * 0.018f)
                    }
                }
            }
        }
    }
}

/** An explicit missing-data marker, never a fabricated cloud or sunny forecast. */
@Composable
internal fun WeatherUnavailableIcon(modifier: Modifier) {
    val color = LocalZhishengPalette.current.textSecondary
    Canvas(modifier.semantics { contentDescription = "天气状况暂无数据" }) {
        val side = size.minDimension
        drawCircle(color.copy(alpha = .85f), radius = side * .29f,
            style = Stroke(width = side * .045f))
        drawLine(color, Offset(center.x - side * .11f, center.y),
            Offset(center.x + side * .11f, center.y), side * .045f, StrokeCap.Round)
    }
}
