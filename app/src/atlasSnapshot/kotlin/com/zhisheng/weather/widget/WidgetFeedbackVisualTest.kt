package com.zhisheng.weather.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.TextClock
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import com.zhisheng.weather.model.MinutePrecip
import com.zhisheng.weather.model.RainMeta
import java.io.File

/** Applies production RemoteViews, including reapply when the user changes display choices. */
class WidgetFeedbackVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1080, screenHeight = 2340),
        theme = "android:Theme.Material.Light.NoActionBar")
    private val json = Json { ignoreUnknownKeys = true }
    private val boundFailures = mutableListOf<String>()
    private fun dp(value: Int) = (value * paparazzi.context.resources.displayMetrics.density).toInt()

    /** Freeze clocks and measure the same bounded slot that a launcher gives us. */
    private fun layoutHost(frame: FrameLayout, width: Int, height: Int) {
        fun freeze(node: View) {
            if (node is TextClock) {
                val text = if (node.id == com.zhisheng.weather.R.id.widget_time) "'09:41'" else "'9月20日 周日'"
                node.format12Hour = text; node.format24Hour = text
            }
            if (node is ViewGroup) (0 until node.childCount).forEach { freeze(node.getChildAt(it)) }
        }
        freeze(frame)
        frame.measure(View.MeasureSpec.makeMeasureSpec(dp(width), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(height), View.MeasureSpec.EXACTLY))
        frame.layout(0, 0, dp(width), dp(height))
        assertEquals(dp(width), frame.width)
        assertEquals(dp(height), frame.height)
        assertEquals(dp(width), frame.getChildAt(0).width)
        assertEquals(dp(height), frame.getChildAt(0).height)
    }

    private fun checkVisibleBounds(frame: FrameLayout, name: String) {
        val failures = mutableListOf<String>()
        val sectionIds = setOf(com.zhisheng.weather.R.id.widget_scene_hero, com.zhisheng.weather.R.id.widget_forecast,
            com.zhisheng.weather.R.id.widget_precip_section, com.zhisheng.weather.R.id.widget_metrics,
            com.zhisheng.weather.R.id.widget_main_hero)
        fun check(node: View, parentX: Int, parentY: Int) {
            if (node.visibility != View.VISIBLE) return
            val x = parentX + node.left
            val y = parentY + node.top
            val key = runCatching { node.resources.getResourceEntryName(node.id) }.getOrDefault(node.id.toString())
            if ((node is TextView && node.text.isNotBlank()) || node.id in sectionIds) {
                if (x < 0 || y < 0 || x + node.width > frame.width || y + node.height > frame.height)
                    failures += "$key outside ${frame.width}x${frame.height}: ($x,$y) ${node.width}x${node.height}"
            }
            if (node is TextView && node.text.isNotBlank()) {
                val parent = node.parent as? View
                if (parent != null && (node.left < 0 || node.top < 0 || node.right > parent.width || node.bottom > parent.height))
                    failures += "$key outside its parent: ${node.text}"
                if (node.layout == null || node.layout.height > node.height)
                    failures += "$key cropped line height: ${node.text}"
                if (node.id in setOf(com.zhisheng.weather.R.id.widget_time, com.zhisheng.weather.R.id.widget_temp) &&
                    node.layout != null && (0 until node.layout.lineCount).any { node.layout.getEllipsisCount(it) > 0 })
                    failures += "$key cropped primary number: ${node.text}"
            }
            if (node is ViewGroup) (0 until node.childCount).forEach { check(node.getChildAt(it), x, y) }
        }
        check(frame.getChildAt(0), 0, 0)
        boundFailures += failures.map { "$name: $it" }
    }

    /** This bitmap is the evidence of real host bounds; Paparazzi records may use a full device canvas. */
    private fun recordHost(frame: FrameLayout, width: Int, height: Int, name: String) {
        layoutHost(frame, width, height)
        val bitmap = Bitmap.createBitmap(dp(width), dp(height), Bitmap.Config.ARGB_8888)
        frame.draw(Canvas(bitmap))
        val file = File("build/reports/widgets/feedback_$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        checkVisibleBounds(frame, name)
    }

    @Test fun panoramaHandlesActualShortHostAndTransparentRainStates() {
        val now = 1_800_000_000_000L
        for (style in WidgetStyle.entries) for (lightWallpaper in listOf(false, true))
        for ((width, height) in listOf(180 to 180, 344 to 300, 430 to 174)) {
            val context = paparazzi.context
            val wallpaper = if (lightWallpaper) Color.rgb(222, 232, 229) else Color.rgb(30, 47, 58)
            val frame = FrameLayout(context).apply { setBackgroundColor(wallpaper) }
            val sample = widgetPreviewSample(now)
            val config = WidgetConfig.defaults(style).copy(opacity = 0, showPrecipitation = true,
                text = if (lightWallpaper) "dark" else "light")
            val remote = WidgetBinder.bind(context, 1, WidgetInstance(1, style, WidgetKind.SCENE), config, sample,
                width, height, now, interactive = false, previewBackground = wallpaper)
            val view = remote.apply(context, frame)
            assertEquals(0, view.findViewById<android.widget.ImageView>(com.zhisheng.weather.R.id.widget_surface).imageAlpha)
            val section = view.findViewById<View>(com.zhisheng.weather.R.id.widget_precip_section)
            assertEquals(if (height >= 280) View.VISIBLE else View.GONE, section.visibility)
            frame.addView(view, FrameLayout.LayoutParams(dp(width), dp(height)))
            val name = "${style.key}_${width}x${height}_${if (lightWallpaper) "light" else "dark"}"
            recordHost(frame, width, height, "${name}_missing")
            if (height >= 280) {
                assertEquals("短时降水暂无有效数据", view.findViewById<TextView>(com.zhisheng.weather.R.id.widget_precip_title).text.toString())
                val wet = sample.copy(weather = sample.weather.copy(fetchedAt = now, rainMeta = RainMeta("QWEATHER", 5, now),
                    rainMinutes = listOf(MinutePrecip(now, 0f), MinutePrecip(now + 5 * 60_000, .2f))))
                WidgetBinder.bind(context, 1, WidgetInstance(1, style, WidgetKind.SCENE), config, wet,
                    width, height, now, interactive = false, previewBackground = wallpaper).reapply(context, view)
                assertEquals(View.VISIBLE, view.findViewById<View>(com.zhisheng.weather.R.id.widget_precip_chart).visibility)
                recordHost(frame, width, height, "${name}_rain")
            }
        }
        assertTrue(boundFailures.joinToString("\n"), boundFailures.isEmpty())
    }

    @Test fun hidingClockAndForecastReclaimsTheirSpaceOnExistingWidgets() {
        for (style in WidgetStyle.entries) for (kind in listOf(WidgetKind.WEEK, WidgetKind.SCENE)) {
            val context = paparazzi.context
            val wallpaper = Color.rgb(30, 47, 58)
            val frame = FrameLayout(context).apply { setBackgroundColor(wallpaper) }
            val sample = widgetPreviewSample()
            val base = WidgetConfig.defaults(style)
            fun bind(config: WidgetConfig) = WidgetBinder.bind(context, 1, WidgetInstance(1, style, kind), config, sample,
                kind.width, kind.height, interactive = false, previewBackground = wallpaper)
            val baseRemote = bind(base)
            var view = baseRemote.apply(context, frame)
            val hidden = json.decodeFromString<WidgetConfig>("""{"showClock":false,"showForecast":false,"textScale":2}""")
            val hiddenRemote = bind(hidden)
            if (hiddenRemote.layoutId == baseRemote.layoutId) hiddenRemote.reapply(context, view)
            else view = hiddenRemote.apply(context, frame)
            assertEquals(View.GONE, view.findViewById<View>(com.zhisheng.weather.R.id.widget_forecast).visibility)
            val clock = view.findViewById<View>(com.zhisheng.weather.R.id.widget_clock_hit)
            if (clock != null) assertEquals(View.GONE, clock.visibility)
            frame.addView(view, FrameLayout.LayoutParams(dp(kind.width), dp(kind.height)))
            recordHost(frame, kind.width, kind.height, "${style.key}_${kind.name.lowercase()}_hidden")
            if (hiddenRemote.layoutId == baseRemote.layoutId) bind(base).reapply(context, view)
            else {
                frame.removeAllViews()
                view = bind(base).apply(context, frame)
                frame.addView(view, FrameLayout.LayoutParams(dp(kind.width), dp(kind.height)))
            }
            assertEquals(View.VISIBLE, view.findViewById<View>(com.zhisheng.weather.R.id.widget_forecast).visibility)
            recordHost(frame, kind.width, kind.height, "${style.key}_${kind.name.lowercase()}_restored")
        }
        assertTrue(boundFailures.joinToString("\n"), boundFailures.isEmpty())
    }

    @Test fun largeNumbersAndLongLabelsStayInsideNarrowPanoramaAndForecastFreeWeek() {
        val now = 1_800_000_000_000L
        for (style in WidgetStyle.entries) for ((kind, width, height) in listOf(
            Triple(WidgetKind.SCENE, 180, 180), Triple(WidgetKind.WEEK, 250, 140))) {
            val context = paparazzi.context
            val wallpaper = Color.rgb(222, 232, 229)
            val frame = FrameLayout(context).apply { setBackgroundColor(wallpaper) }
            val sample = widgetPreviewSample(now).let { it.copy(city = it.city.copy(name = "内蒙古自治区鄂尔多斯市康巴什区"),
                weather = it.weather.copy(current = it.weather.current?.copy(temperature = -18.0, weatherText = "小雨转雨夹雪伴有强阵风"),
                    daily = it.weather.daily.map { day -> day.copy(low = -18.0, high = 108.0) },
                    forecastSummary = "午后可能出现短时阵雨，晚间风力增大，请结合本地最新预警合理安排出行。")) }
            for (showClock in listOf(true, false)) {
                val config = WidgetConfig.defaults(style).copy(textScale = 2f, showForecast = false, showClock = showClock,
                    detailScale = 1.3f, boldText = true, opacity = 0, text = "dark")
                val view = WidgetBinder.bind(context, 1, WidgetInstance(1, style, kind), config, sample, width, height, now,
                    interactive = false, previewBackground = wallpaper).apply(context, frame)
                frame.removeAllViews()
                frame.addView(view, FrameLayout.LayoutParams(dp(width), dp(height)))
                assertEquals(View.GONE, view.findViewById<View>(com.zhisheng.weather.R.id.widget_forecast).visibility)
                recordHost(frame, width, height, "${style.key}_${kind.name.lowercase()}_${width}x${height}_200_clock_$showClock")
                val city = view.findViewById<TextView>(com.zhisheng.weather.R.id.widget_city)
                assertTrue("Long place labels must remain readable", city.textSize / context.resources.displayMetrics.density >= 8.99f)
            }
        }
        assertTrue(boundFailures.joinToString("\n"), boundFailures.isEmpty())
    }
}
