package com.zhisheng.weather.widget

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt

data class WidgetPalette(
    val surface: Int,
    val ink: Int,
    val muted: Int,
    val accent: Int,
    val signal: Int,
    val isLight: Boolean,
)

fun widgetPalette(context: Context, style: WidgetStyle, config: WidgetConfig, previewBackground: Int? = null,
                  skyColors: List<Int> = emptyList()): WidgetPalette {
    val isLight = when (config.theme) {
        "light" -> true
        "dark" -> false
        else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
    }
    val wallpaper = previewBackground ?: runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 27) WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.let {
            Color.rgb(it.red(), it.green(), it.blue())
        }
        else null
    }.getOrNull()
    val base = when (style) {
        WidgetStyle.VISTA -> if (isLight) "#F6FBFF".toColorInt() else "#15293A".toColorInt()
        WidgetStyle.CLASSIC -> if (isLight) "#E9EEE8".toColorInt() else "#0B1112".toColorInt()
    }
    val alpha = (config.opacity.coerceIn(0, 100) * 255 / 100)
    val surface = ColorUtils.setAlphaComponent(base, alpha)
    val behind = wallpaper ?: if (isLight) Color.WHITE else Color.BLACK
    val composite = ColorUtils.compositeColors(surface, behind)
    val backgrounds = skyColors.takeIf { it.isNotEmpty() }?.map {
        ColorUtils.compositeColors(ColorUtils.setAlphaComponent(it, alpha), behind)
    } ?: listOf(composite)
    val pale = when (style) {
        WidgetStyle.VISTA -> "#F8FCFF".toColorInt()
        WidgetStyle.CLASSIC -> "#E8F1E9".toColorInt()
    }
    val deep = when (style) {
        WidgetStyle.VISTA -> "#112433".toColorInt()
        WidgetStyle.CLASSIC -> "#12291F".toColorInt()
    }
    val ink = when (config.text) {
        "light" -> pale
        "dark" -> deep
        // WallpaperColors is one color for the whole image. A dark-to-light
        // gradient can put a widget over a region opposite to that average.
        // On a fully transparent card, protected light text with a dark halo
        // survives the dark half; users can still choose dark text explicitly.
        else -> if (alpha == 0 && config.protectText) pale
            else if (backgrounds.minOf { ColorUtils.calculateContrast(pale, it) } >=
                backgrounds.minOf { ColorUtils.calculateContrast(deep, it) }) pale else deep
    }
    val lightInk = ColorUtils.calculateLuminance(ink) > .5
    val muted = ColorUtils.setAlphaComponent(if (lightInk) pale else deep, if (config.opacity < 45) 245 else 200)
    val accent = when (style) {
        WidgetStyle.VISTA -> if (lightInk) "#BFE9FF".toColorInt() else "#29677E".toColorInt()
        WidgetStyle.CLASSIC -> if (lightInk) "#91CBB5".toColorInt() else "#245C49".toColorInt()
    }
    val signal = when (style) {
        WidgetStyle.VISTA -> if (lightInk) "#FFB07A".toColorInt() else "#A34719".toColorInt()
        WidgetStyle.CLASSIC -> if (lightInk) "#FF9B55".toColorInt() else "#A34719".toColorInt()
    }
    return WidgetPalette(surface, ink, muted, accent, signal, isLight)
}
