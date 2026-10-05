package com.zhisheng.weather.widget

import android.graphics.Bitmap
import android.util.LruCache
import android.widget.RemoteViews
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.zhisheng.weather.R
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.ui.components.classicConditionIconRes
import com.zhisheng.weather.ui.components.drawVistaWeatherArtwork
import com.zhisheng.weather.ui.components.NaturalLightPalette
import androidx.compose.ui.graphics.toArgb

/** Render the very same artwork as the new home screen, without an animation clock. */
internal object WidgetIcons {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun bind(views: RemoteViews, id: Int, condition: WeatherCondition?, set: WidgetIconSet, light: Boolean, sizePx: Int = 144,
             ambient: NaturalLightPalette? = null) {
        if (condition == null || condition == WeatherCondition.UNKNOWN) {
            views.setImageViewResource(id, R.drawable.ph_cloud)
        } else if (set == WidgetIconSet.CLASSIC) {
            views.setImageViewResource(id, classicConditionIconRes(condition) ?: R.drawable.ph_cloud)
        } else {
            views.setImageViewBitmap(id, bitmap(condition, light, sizePx, ambient))
        }
        views.setContentDescription(id, condition?.label ?: "暂无天气")
    }

    @Synchronized
    internal fun bitmap(condition: WeatherCondition, light: Boolean, sizePx: Int = 144, ambient: NaturalLightPalette? = null): Bitmap {
        val pixels = sizePx.coerceIn(16, 512)
        val key = "${condition.name}:$light:$pixels:${ambient?.light?.toArgb()}:${ambient?.particle?.toArgb()}"
        cache.get(key)?.let { return it }
        val image = ImageBitmap(pixels, pixels)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(pixels.toFloat(), pixels.toFloat())) {
            drawVistaWeatherArtwork(condition, light, ambient = ambient)
        }
        return image.asAndroidBitmap().also {
            // The ImageView has explicit dp bounds; avoid another intrinsic-density scale.
            it.density = Bitmap.DENSITY_NONE
            cache.put(key, it)
        }
    }
}
