package com.zhisheng.weather.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.Fmt
import com.zhisheng.weather.ui.weatherPresentationTime
import com.zhisheng.weather.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

/** Small cards share one cloud texture. Per frame: two bitmap transforms, never volume ray marching. */
@Composable
internal fun CityAtmosphere(weather: WeatherData?, level: AmbienceLevel, night: Boolean,
    modifier: Modifier, active: Boolean, content: @Composable BoxScope.() -> Unit) {
    val now = weatherPresentationTime()
    val local = java.time.Instant.ofEpochMilli(now).atZone(Fmt.zoneId(weather?.utcOffsetSeconds))
    val state = naturalLightState(weather, night, local.hour * 60 + local.minute, now)
    val palette = naturalLightPalette(state, LocalZhishengPalette.current.isLight)
    val lightTheme = LocalZhishengPalette.current.isLight
    val phase = LocalVistaGlowPhase.current
    val texture by produceState<ImageBitmap?>(null, state.weather) {
        value = withContext(Dispatchers.Default) { CityCloudTexture.image(state.weather) }
    }
    val held = remember { doubleArrayOf(0.0) }
    val gain = when (level) { AmbienceLevel.OFF -> 0f; AmbienceLevel.SUBTLE -> .5f; AmbienceLevel.VIVID -> .76f; AmbienceLevel.INTENSE -> 1f }
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            if (active && level != AmbienceLevel.OFF) held[0] = phase.value.toDouble()
            val time = held[0]
            drawRect(Brush.verticalGradient(listOf(palette.top, palette.middle, palette.base)))
            val glow = Offset(size.width * .83f, size.height * .20f)
            drawCircle(Brush.radialGradient(listOf(palette.light.copy(alpha = .13f * (1f-state.cloud) * gain), Color.Transparent), glow, size.width*.5f), size.width*.5f, glow)
            texture?.let { cloud ->
                repeat(2) { layer ->
                    val drift = sin(time * PI * 2 + layer * 2.1).toFloat()
                    drawImage(cloud, dstOffset = IntOffset((-size.width*.12f + drift*size.width*.035f).roundToInt(), (-size.height*.25f + layer*size.height*.13f).roundToInt()),
                        dstSize = IntSize((size.width*1.28f).roundToInt(), (size.height*1.3f).roundToInt()),
                        alpha = state.cloud * gain * (if (lightTheme) .68f else .28f) * (if(layer==0) 1f else .48f),
                        colorFilter = ColorFilter.tint(palette.cloud), filterQuality = FilterQuality.Low)
                }
            }
            if (gain > 0f && state.weather == NaturalWeather.CLEAR && state.daylight < .5f) repeat(9) { i ->
                drawCircle(palette.particle.copy(alpha=gain * if (lightTheme) .38f else .24f), (.65f + rainSeed(i,2)*.5f).dp.toPx(), Offset(rainSeed(i,1)*size.width,rainSeed(i,9)*size.height*.65f))
            }
            if (state.weather in listOf(NaturalWeather.FOG,NaturalWeather.HAZE,NaturalWeather.DUST)) {
                val opacity=gain * if(state.weather==NaturalWeather.FOG) .25f else .15f
                drawRect(Brush.verticalGradient(listOf(palette.cloud.copy(alpha=opacity),Color.Transparent,palette.cloud.copy(alpha=opacity*.4f))))
            }
            if (gain > 0f && state.weather == NaturalWeather.DUST) repeat(16) { i ->
                val x=((time*3+rainSeed(i,4))%1).toFloat()*size.width
                drawCircle(palette.particle.copy(alpha=.23f * gain), .9f,Offset(x,rainSeed(i,6)*size.height))
            }
            val rain = state.weather in listOf(NaturalWeather.RAIN, NaturalWeather.STORM, NaturalWeather.MIXED)
            val snow = state.weather in listOf(NaturalWeather.SNOW, NaturalWeather.MIXED, NaturalWeather.ICE)
            if (gain > 0f && (rain || snow)) repeat(if(state.drizzle) 9 else if(state.intensity > .6f) 24 else 14) { i ->
                val travel = ((time * (if (snow && i%2==0) 9 else if (state.drizzle) 24 else 48) + rainSeed(i, 7)) % 1.0).toFloat()
                val x = rainSeed(i, 13) * size.width
                val y = travel * size.height
                val alpha = gain * (if (lightTheme) .42f else .30f) * (1f - travel*.6f)
                if (snow && (!rain || i%2==0)) drawCircle(palette.particle.copy(alpha=alpha), (.65f + rainSeed(i, 3)*.65f).dp.toPx(), Offset(x,y))
                else {
                    val length = (if (state.drizzle) 4f else 8f).dp.toPx()
                    drawLine(Brush.linearGradient(listOf(Color.Transparent,palette.particle.copy(alpha=alpha)),Offset(x,y-length),Offset(x-2.dp.toPx(),y)),Offset(x,y-length),Offset(x-2.dp.toPx(),y),(if (state.drizzle) .4f else .55f).dp.toPx())
                }
            }
            if (state.freezing) drawNaturalFrostEdge(palette, time, gain, Offset(size.width*.65f, size.height),
                Offset(size.width, size.height), inward = Offset(0f, -1f))
            drawNaturalThermal(state, palette, time, gain,
                Brush.radialGradient(listOf(Color.White, Color.Transparent), Offset.Zero, 1f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent,palette.base.copy(alpha=.32f))))
        }
        content()
    }
}

private object CityCloudTexture {
    private val cache = java.util.concurrent.ConcurrentHashMap<NaturalWeather, ImageBitmap>()
    fun image(weather: NaturalWeather): ImageBitmap = cache.getOrPut(weather) {
        fun noise(x: Float, y: Float): Float {
            val ix = floor(x).toInt(); val iy = floor(y).toInt()
            val fx = x-ix; val fy = y-iy
            val u=fx*fx*(3-2*fx); val v=fy*fy*(3-2*fy)
            fun hash(a: Int,b: Int) = rainSeed(a*151+b, 41)
            val a=hash(ix,iy)*(1-u)+hash(ix+1,iy)*u
            val b=hash(ix,iy+1)*(1-u)+hash(ix+1,iy+1)*u
            return a*(1-v)+b*v
        }
        val w=256; val h=128
        val pixels=IntArray(w*h) { p ->
            val scales = when(weather) {
                NaturalWeather.OVERCAST, NaturalWeather.SNOW -> 85f to 50f
                NaturalWeather.RAIN, NaturalWeather.MIXED, NaturalWeather.STORM -> 38f to 17f
                NaturalWeather.FOG, NaturalWeather.HAZE -> 140f to 85f
                NaturalWeather.DUST -> 38f to 90f
                NaturalWeather.WIND -> 90f to 14f
                else -> 48f to 28f
            }
            val x=p%w/scales.first + weather.ordinal*3.7f; val y=p/w/scales.second
            val n=noise(x,y)*.55f+noise(x*2.1f+7,y*2.1f)*.28f+noise(x*4.2f,y*4.2f+11)*.17f
            val alpha=(((n-.34f)/.42f).coerceIn(0f,1f) * (1f-(p/w).toFloat()/h).pow(.6f)*255).toInt()
            (alpha shl 24) or 0xFFFFFF
        }
        Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
