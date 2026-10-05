package com.zhisheng.weather.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.graphics.toArgb
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.ui.components.*

/** A still sample of the home's light field. No launcher animation or extra weather request. */
internal data class WidgetSky(val state: NaturalLightState, val palette: NaturalLightPalette) {
    val contrastColors: List<Int> get() = listOf(palette.top, palette.middle, palette.base,
        palette.cloudShadow, palette.cloudLit).map { it.toArgb() }
}

internal fun widgetSky(style: WidgetStyle, snapshot: WidgetSnapshot?, light: Boolean,
                       enabled: Boolean, level: AmbienceLevel, now: Long): WidgetSky? {
    if (style != WidgetStyle.VISTA || !enabled || level == AmbienceLevel.OFF) return null
    val city = snapshot?.city ?: return null
    val solar = solarSkyState(city.latitude, city.longitude, now) ?: return null
    val minutes = Math.floorMod(now / 60_000L + (snapshot.weather.utcOffsetSeconds ?: 0) / 60L, 1440L).toInt()
    val state = naturalLightState(snapshot.weather, solar.daylight < .5f, minutes, now, solar)
    if (state.solar == null) return null
    val original = naturalLightPalette(state, light)
    // Honour the existing atmosphere strength, while keeping all five cards quiet enough to read.
    val gain = when (level) { AmbienceLevel.OFF -> 0f; AmbienceLevel.SUBTLE -> .52f; AmbienceLevel.VIVID -> .76f; AmbienceLevel.INTENSE -> 1f }
    val palette = original.copy(
        top = androidx.compose.ui.graphics.lerp(original.base, original.top, gain),
        middle = androidx.compose.ui.graphics.lerp(original.base, original.middle, gain))
    return WidgetSky(state, palette)
}

internal object WidgetSkyBitmap {
    private val cache = object : LruCache<String, Bitmap>(512 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    @Synchronized fun of(sky: WidgetSky): Bitmap {
        val p = sky.palette
        val key = listOf(p.top.toArgb(), p.middle.toArgb(), p.base.toArgb(), p.cloud.toArgb(),
            (sky.state.cloud * 18).toInt()).joinToString(":")
        cache.get(key)?.let { return it }
        // This texture carries only soft colour, never text or a rasterized card edge.
        // The launcher clips its 26dp corners with a native XML outline at the actual host size.
        val bitmap = Bitmap.createBitmap(64, 256, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, 256f,
            intArrayOf(p.top.toArgb(), p.middle.toArgb(), p.base.toArgb()), floatArrayOf(0f, .43f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 64f, 256f, paint)
        val veil = androidx.core.graphics.ColorUtils.setAlphaComponent(p.cloud.toArgb(), (sky.state.cloud * 18).toInt())
        paint.shader = RadialGradient(51f, 72f, 64f, intArrayOf(veil, veil and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 64f, 256f, paint)
        cache.put(key, bitmap)
        return bitmap
    }
}
