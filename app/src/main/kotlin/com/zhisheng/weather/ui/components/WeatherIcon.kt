package com.zhisheng.weather.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.conditionIconRes
import com.zhisheng.weather.ui.theme.LocalZhishengPalette

// In-app and widgets share the same static Makin-Things artwork (see ConditionIcons.kt).
@Composable
fun WeatherIcon(
    condition: WeatherCondition?,
    modifier: Modifier = Modifier,
) {
    val res = conditionIconRes(condition)
    if (condition == null || condition == WeatherCondition.UNKNOWN || res == null) {
        WeatherUnavailableIcon(modifier)
        return
    }
    Image(
        painter = painterResource(res),
        contentDescription = condition.label,
        modifier = modifier,
    )
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
