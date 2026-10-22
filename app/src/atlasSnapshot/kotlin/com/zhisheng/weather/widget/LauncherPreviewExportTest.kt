package com.zhisheng.weather.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextClock
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.R
import org.junit.Rule
import org.junit.Test

/** Launcher artwork is rendered by the actual production widget, not a separate mock. */
class LauncherPreviewExportTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5.copy(softButtons = false),
        theme = "android:Theme.Material.Light.NoActionBar")
    @Test fun exportTsinghuaPreviews() {
        val now = 1_792_459_800_000L
        val sample = widgetPreviewSample(now).let { it.copy(city = it.city.copy(name = "清华大学", affiliation = "北京·海淀区", latitude = 40.002, longitude = 116.326)) }
        for (style in WidgetStyle.entries) for (kind in WidgetKind.entries) {
            val density = paparazzi.context.resources.displayMetrics.density
            val width = (kind.width * density).toInt()
            val height = (kind.height * density).toInt()
            val frame = FrameLayout(paparazzi.context)
            val remote = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, style, kind),
                WidgetConfig.defaults(style), sample, kind.width, kind.height, now, interactive = false)
            val view = remote.apply(paparazzi.context, frame)
            view.findViewById<TextClock>(R.id.widget_time)?.apply { format12Hour = "'09:30'"; format24Hour = "'09:30'" }
            view.findViewById<TextClock>(R.id.widget_date)?.apply { format12Hour = "'10月20日 周二'"; format24Hour = "'10月20日 周二'" }
            frame.addView(view, FrameLayout.LayoutParams(width, height))
            frame.layoutParams = ViewGroup.LayoutParams(width, height)
            paparazzi.snapshot(frame, "launcher_${style.key}_${kind.name.lowercase()}")
            org.junit.Assert.assertEquals(width, view.width)
            org.junit.Assert.assertEquals(height, view.height)
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val destination = java.io.File("build/launcher-previews/widget_preview_${style.key}_${kind.name.lowercase()}.png")
            checkNotNull(destination.parentFile).mkdirs()
            destination.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
