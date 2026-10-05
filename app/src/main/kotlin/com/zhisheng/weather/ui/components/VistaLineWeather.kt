package com.zhisheng.weather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import kotlin.math.cos
import kotlin.math.sin

// One 48-unit optical grid and round stroke family across all weather phenomena.
@Composable
internal fun VistaLineWeather(condition: WeatherCondition?, modifier: Modifier) {
    if (condition == null || condition == WeatherCondition.UNKNOWN) return
    val palette = LocalZhishengPalette.current
    val sun = if (palette.isLight) Color(0xFFC18435) else Color(0xFFE9BA76)
    val ink = palette.textSecondary
    val water = palette.cyan
    Canvas(modifier.semantics { contentDescription = condition.label }) {
        val side = size.minDimension
        translate((size.width - side) / 2f, (size.height - side) / 2f) {
            scale(side / 48f, side / 48f, pivot = Offset.Zero) {
                fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color = ink) =
                    drawLine(color, Offset(x1,y1), Offset(x2,y2), 2.2f, StrokeCap.Round)
                fun sun(cx: Float, cy: Float, radius: Float) {
                    drawCircle(sun.copy(alpha = 0.10f), radius, Offset(cx,cy))
                    drawCircle(sun, radius, Offset(cx,cy), style = Stroke(2.2f))
                    repeat(8) { i ->
                        val a = i * Math.PI / 4
                        line(cx + cos(a).toFloat()*(radius+4), cy+sin(a).toFloat()*(radius+4),
                            cx+cos(a).toFloat()*(radius+7),cy+sin(a).toFloat()*(radius+7),sun)
                    }
                }
                fun moon() {
                    val p=Path().apply {
                        moveTo(29f,5f); cubicTo(13f,4f,4f,18f,10f,31f)
                        cubicTo(16f,45f,35f,44f,42f,31f)
                        cubicTo(24f,35f,17f,18f,29f,5f); close()
                    }
                    drawPath(p,water.copy(alpha=0.08f)); drawPath(p,ink,style=Stroke(2.2f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                }
                fun cloud(y: Float = 0f) {
                    val p=Path().apply {
                        moveTo(13f,33f+y); cubicTo(3f,33f+y,3f,20f+y,13f,20f+y)
                        cubicTo(13f,7f+y,32f,7f+y,34f,21f+y)
                        cubicTo(45f,19f+y,47f,33f+y,36f,33f+y); close()
                    }
                    drawPath(p,palette.surface.copy(alpha=0.86f))
                    drawPath(p,ink,style=Stroke(2.2f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                }
                fun snow(cx: Float, cy: Float) {
                    repeat(3) { i -> val a=i*Math.PI/3; val dx=cos(a).toFloat()*3f; val dy=sin(a).toFloat()*3f
                        line(cx-dx,cy-dy,cx+dx,cy+dy,water) }
                }
                when(condition) {
                    WeatherCondition.CLEAR -> sun(24f,24f,9f)
                    WeatherCondition.CLEAR_NIGHT -> moon()
                    WeatherCondition.PARTLY_CLOUDY -> { sun(32f,14f,6f); cloud(6f) }
                    WeatherCondition.PARTLY_CLOUDY_NIGHT -> { scale(0.7f,0.7f,pivot=Offset(42f,3f)){moon()}; cloud(6f) }
                    WeatherCondition.CLOUDY -> cloud()
                    WeatherCondition.OVERCAST -> { line(14f,9f,28f,9f,ink.copy(alpha=0.5f)); cloud(3f) }
                    WeatherCondition.WIND -> {
                        val p=Path().apply{moveTo(5f,17f);lineTo(30f,17f);cubicTo(40f,17f,39f,5f,32f,7f);moveTo(8f,25f);lineTo(39f,25f);moveTo(5f,33f);lineTo(28f,33f);cubicTo(37f,33f,35f,44f,28f,41f)}
                        drawPath(p,water,style=Stroke(2.2f,cap=StrokeCap.Round))
                    }
                    WeatherCondition.FOG, WeatherCondition.HAZE, WeatherCondition.SAND -> {
                        cloud(-5f)
                        val tint=if(condition==WeatherCondition.SAND) sun else ink
                        line(8f,34f,37f,34f,tint);line(13f,40f,41f,40f,tint)
                        if(condition==WeatherCondition.HAZE) drawCircle(sun,1.5f,Offset(7f,40f))
                    }
                    else -> {
                        cloud(-5f)
                        when(condition) {
                            WeatherCondition.THUNDERSTORM -> {
                                val bolt=Path().apply{moveTo(25f,29f);lineTo(19f,37f);lineTo(26f,37f);lineTo(22f,45f)}
                                drawPath(bolt,sun,style=Stroke(2.4f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                            }
                            WeatherCondition.SNOW -> {snow(15f,37f);snow(32f,39f)}
                            WeatherCondition.SLEET -> {line(15f,33f,12f,39f,water);snow(31f,38f)}
                            WeatherCondition.HAIL -> {drawCircle(water,2.3f,Offset(15f,36f),style=Stroke(1.8f));drawCircle(water,2.3f,Offset(31f,40f),style=Stroke(1.8f))}
                            WeatherCondition.FREEZING_RAIN,WeatherCondition.FREEZING_DRIZZLE -> {line(15f,33f,12f,40f,water);snow(31f,38f);line(9f,44f,35f,44f,water)}
                            WeatherCondition.DRIZZLE -> {line(17f,35f,15f,39f,water);line(31f,33f,29f,37f,water)}
                            else -> {line(14f,33f,11f,40f,water);line(25f,35f,22f,43f,water);line(36f,33f,33f,40f,water)}
                        }
                    }
                }
            }
        }
    }
}
