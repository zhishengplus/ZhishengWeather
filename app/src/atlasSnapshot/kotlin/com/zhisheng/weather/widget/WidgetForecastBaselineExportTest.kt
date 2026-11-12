package com.zhisheng.weather.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Writes raw pixel exports of 时日预报 / 逐时天光 for regression comparison
 * (e.g. static forecast slots vs. the old addView cells). Output only;
 * the comparison itself is done outside the test.
 */
class WidgetForecastBaselineExportTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1080, screenHeight = 2340, softButtons = false),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private fun dp(value: Int) = (value * paparazzi.context.resources.displayMetrics.density).toInt()

    @Test fun exportForecastBaselines() {
        val now = java.time.OffsetDateTime.parse("2026-11-10T12:00:00+02:00").toInstant().toEpochMilli()
        val full = widgetPreviewSample(now)
        val few = full.copy(weather = full.weather.copy(hourly = full.weather.hourly.take(3)))
        val empty = full.copy(weather = com.zhisheng.weather.model.WeatherData())
        val samples = listOf("full" to full, "few" to few, "empty" to empty)
        val cases = buildList {
            listOf(344 to 180, 344 to 250, 344 to 333, 344 to 417).forEach { add(Triple(WidgetKind.COMBO, it.first, it.second)) }
            listOf(344 to 120, 344 to 160, 344 to 200, 260 to 160).forEach { add(Triple(WidgetKind.BAND, it.first, it.second)) }
        }
        val outDir = File("build/preview-compare/current").apply { deleteRecursively(); mkdirs() }
        var count = 0
        for ((kind, w, h) in cases) for (style in WidgetStyle.entries) for (theme in listOf("light", "dark")) {
            for ((sampleName, sample) in samples) {
                if (sampleName != "full" && theme == "dark") continue
                val config = WidgetConfig.defaults(style).copy(theme = theme)
                val remote = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, style, kind), config, sample,
                    w, h, now, interactive = false, previewBackground = 0xFF9BB83A.toInt())
                val bitmap = draw(remote, w, h)
                File(outDir, "${kind.name.lowercase()}-${style.key}-${w}x$h-$theme-$sampleName.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                count++
            }
        }
        assertTrue(count > 0)
    }

    private fun draw(remote: android.widget.RemoteViews, widthDp: Int, heightDp: Int): Bitmap {
        val context = paparazzi.context
        val inflater = LayoutInflater.from(context)
        val cls = LayoutInflater::class.java
        fun field(name: String) = cls.getDeclaredField(name).apply { isAccessible = true }
        val f1 = field("mFactory"); val f2 = field("mFactory2"); val fs = field("mFactorySet")
        val previous = Triple(f1.get(inflater), f2.get(inflater), fs.getBoolean(inflater))
        f1.set(inflater, null); f2.set(inflater, null); fs.setBoolean(inflater, false)
        val view = try { remote.apply(context, FrameLayout(context)) } finally {
            f1.set(inflater, previous.first); f2.set(inflater, previous.second); fs.setBoolean(inflater, previous.third)
        }
        val host = FrameLayout(context)
        host.addView(view, FrameLayout.LayoutParams(dp(widthDp), dp(heightDp)))
        host.measure(View.MeasureSpec.makeMeasureSpec(dp(widthDp), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(heightDp), View.MeasureSpec.EXACTLY))
        host.layout(0, 0, dp(widthDp), dp(heightDp))
        val bitmap = Bitmap.createBitmap(dp(widthDp), dp(heightDp), Bitmap.Config.ARGB_8888)
        host.draw(Canvas(bitmap))
        return bitmap
    }
}
