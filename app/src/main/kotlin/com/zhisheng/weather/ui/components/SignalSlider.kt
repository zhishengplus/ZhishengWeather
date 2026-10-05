@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.zhisheng.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.isPhosphorVista

/** Thin visual track; native Slider retains talkback, keyboard and drag behaviour. */
@Composable
internal fun SignalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    colors: SliderColors = SliderDefaults.colors(),
    onValueChangeFinished: (() -> Unit)? = null,
) {
    if (!isPhosphorVista) {
        Slider(value, onValueChange, modifier, enabled, valueRange, steps, onValueChangeFinished, colors)
        return
    }
    val palette = LocalZhishengPalette.current
    Slider(
        value = value, onValueChange = onValueChange, modifier = modifier,
        enabled = enabled, valueRange = valueRange, steps = steps,
        onValueChangeFinished = onValueChangeFinished, colors = colors,
        thumb = {
            Canvas(Modifier.size(20.dp)) {
                drawCircle(palette.surface, radius = 7.dp.toPx())
                drawCircle(palette.mint, radius = 7.dp.toPx(), style = Stroke(2.dp.toPx()))
                drawCircle(palette.mint, radius = 2.dp.toPx())
            }
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state, modifier = Modifier.height(3.dp),
                colors = colors, enabled = enabled,
                thumbTrackGapSize = 0.dp, drawStopIndicator = null,
            )
        },
    )
}
