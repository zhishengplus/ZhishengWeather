package com.zhisheng.weather.widget

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.R
import org.junit.Rule
import org.junit.Test

/** These inflate production RemoteViews, including their reflection actions. */
class WidgetVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1080, screenHeight = 2340), theme = "android:Theme.Material.Light.NoActionBar")
    private fun dp(value: Int) = (value * paparazzi.context.resources.displayMetrics.density).toInt()
    @Test fun allWidgets() {
        for (style in WidgetStyle.entries) {
            for (kind in WidgetKind.entries) {
                val cfg = WidgetConfig.defaults(style)
                val frame = FrameLayout(paparazzi.context).apply {
                    setBackgroundColor(if (style == WidgetStyle.VISTA) Color.rgb(159, 187, 199) else Color.rgb(50, 64, 62))
                }
                val remote = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, style, kind), cfg, widgetPreviewSample(), kind.width, kind.height, interactive = false)
                val view = remote.apply(paparazzi.context, frame)
                view.findViewById<android.widget.TextClock>(R.id.widget_time)?.apply { format12Hour = "'09:41'"; format24Hour = "'09:41'" }
                view.findViewById<android.widget.TextClock>(R.id.widget_date)?.apply { format12Hour = "'9月20日 周日'"; format24Hour = "'9月20日 周日'" }
                frame.addView(view, FrameLayout.LayoutParams(dp(kind.width), dp(kind.height)))
                frame.layoutParams = ViewGroup.LayoutParams(dp(kind.width), dp(kind.height))
                paparazzi.snapshot(frame, "widget_${style.key}_${kind.name.lowercase()}")
                if (kind == WidgetKind.NOW) {
                    val surface = view.findViewById<View>(R.id.widget_surface)
                    org.junit.Assert.assertEquals("2×2 card should be square on a tall launcher host: ${style.key}", surface.width, surface.height)
                    val content = view.findViewById<View>(R.id.widget_content)
                    org.junit.Assert.assertTrue("2×2 text needs a 16dp interior inset: ${style.key}", content.paddingLeft >= dp(16))
                    org.junit.Assert.assertEquals(content.paddingLeft, content.paddingRight)
                    val readings = view.findViewById<View>(R.id.widget_metrics)
                    org.junit.Assert.assertNotNull("2×2 card should use its lower space: ${style.key}", readings)
                    org.junit.Assert.assertTrue("2×2 readings must reach the lower edge: ${style.key}",
                        readings.bottom + dp(12) >= dp(kind.height - 20))
                    org.junit.Assert.assertEquals("体感", view.findViewById<TextView>(R.id.widget_metric_label_1).text.toString())
                    org.junit.Assert.assertEquals("空气", view.findViewById<TextView>(R.id.widget_metric_label_2).text.toString())
                    org.junit.Assert.assertEquals("Legacy air text must not overlap the header: ${style.key}",
                        View.GONE, view.findViewById<View>(R.id.widget_air).visibility)
                }
                if (kind == WidgetKind.BAND) {
                    val forecast = view.findViewById<ViewGroup>(R.id.widget_forecast)
                    for (index in 0 until forecast.childCount) {
                        val cell = forecast.getChildAt(index) as ViewGroup
                        for (childIndex in 0 until cell.childCount) {
                            val child = cell.getChildAt(childIndex)
                            org.junit.Assert.assertTrue("Hourly forecast must fit above footer: ${style.key}", child.top >= 0 && child.bottom <= cell.height)
                        }
                    }
                }
                val bitmap = android.graphics.Bitmap.createBitmap(dp(kind.width), dp(kind.height), android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                val out = java.io.File("build/reports/widgets/beta9_preview_${style.key}_${kind.name.lowercase()}.png")
                out.parentFile?.mkdirs()
                out.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
    @Test fun tallHostAndIconChoices() {
        for (set in WidgetIconSet.entries) {
            val kind = WidgetKind.WEEK
            val cfg = WidgetConfig(iconSet = set.key)
            val frame = FrameLayout(paparazzi.context).apply { setBackgroundColor(Color.rgb(38, 69, 70)); layoutParams = ViewGroup.LayoutParams(dp(344), dp(286)) }
            val remote = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, WidgetStyle.VISTA, kind), cfg, widgetPreviewSample(), 344, 286, interactive = false)
            val view = remote.apply(paparazzi.context, frame)
            frame.addView(view, FrameLayout.LayoutParams(dp(344), dp(286)))
            paparazzi.snapshot(frame, "tall_week_${set.key}")
            org.junit.Assert.assertEquals(dp(286), view.findViewById<View>(R.id.widget_surface).height)
            val bitmap = android.graphics.Bitmap.createBitmap(dp(344), dp(286), android.graphics.Bitmap.Config.ARGB_8888)
            frame.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/reports/widgets/tall_week_${set.key}.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun fourByFourUsesTallLauncherSpaceWithoutClippingForecast() {
        for (style in WidgetStyle.entries) for (height in listOf(300, 440, 500)) {
            val width = 344
            val frame = FrameLayout(paparazzi.context).apply {
                layoutParams = ViewGroup.LayoutParams(dp(width), dp(height))
            }
            val view = WidgetBinder.bind(paparazzi.context, 1,
                WidgetInstance(1, style, WidgetKind.SCENE), WidgetConfig.defaults(style),
                widgetPreviewSample(), width, height, interactive = false).apply(paparazzi.context, frame)
            frame.addView(view, FrameLayout.LayoutParams(dp(width), dp(height)))
            paparazzi.snapshot(frame, "scene_${style.key}_${height}dp")
            val content = view.findViewById<ViewGroup>(R.id.widget_content)
            val daily = view.findViewById<View>(R.id.widget_forecast)
            val bitmap = android.graphics.Bitmap.createBitmap(dp(width), dp(height), android.graphics.Bitmap.Config.ARGB_8888)
            frame.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/reports/widgets/scene_${style.key}_${height}dp.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            org.junit.Assert.assertTrue("4×4 forecast clips at ${height}dp for ${style.key}: bottom=${daily.bottom}, limit=${content.height - content.paddingBottom}",
                daily.bottom <= content.height - content.paddingBottom)
        }
    }
    @Test fun fourByFourShowsSummaryAndOptionalWeatherGirl() {
        for (style in WidgetStyle.entries) for (girl in listOf(false, true)) {
            val frame = FrameLayout(paparazzi.context)
            val config = WidgetConfig.defaults(style).copy(showWeatherGirl = girl)
            val view = WidgetBinder.bind(paparazzi.context, 1,
                WidgetInstance(1, style, WidgetKind.SCENE), config,
                widgetPreviewSample(), 344, 440, interactive = false).apply(paparazzi.context, frame)
            frame.addView(view, FrameLayout.LayoutParams(dp(344), dp(440)))
            paparazzi.snapshot(frame, "scene_${style.key}_weather_girl_$girl")
            org.junit.Assert.assertTrue(view.findViewById<TextView>(R.id.widget_scene_briefing_text).text.isNotBlank())
            org.junit.Assert.assertEquals(if (girl) View.VISIBLE else View.GONE,
                view.findViewById<View>(R.id.widget_scene_girl).visibility)
            for (index in 1..6) {
                val id = paparazzi.context.resources.getIdentifier("widget_metric_$index", "id", paparazzi.context.packageName)
                org.junit.Assert.assertTrue("Missing 4×4 metric $index", view.findViewById<TextView>(id).text.isNotBlank())
            }
        }
    }
    @Test fun expandedSceneRestoresHourlyForecastWhenRemoteViewsAreReapplied() {
        val now = System.currentTimeMillis()
        val populated = widgetPreviewSample(now)
        val empty = populated.copy(weather = populated.weather.copy(hourly = emptyList()))
        for (style in WidgetStyle.entries) {
            val context = paparazzi.context
            val frame = FrameLayout(context)
            val config = WidgetConfig.defaults(style)
            fun bind(snapshot: WidgetSnapshot) = WidgetBinder.bind(context, 1,
                WidgetInstance(1, style, WidgetKind.SCENE), config, snapshot,
                344, 440, now, interactive = false)
            val initial = bind(empty)
            val view = initial.apply(context, frame)
            frame.addView(view, FrameLayout.LayoutParams(dp(344), dp(440)))
            val title = view.findViewById<View>(R.id.widget_scene_hourly_title)
            val forecast = view.findViewById<ViewGroup>(R.id.widget_hourly_forecast)
            org.junit.Assert.assertEquals(View.GONE, title.visibility)
            org.junit.Assert.assertEquals(View.GONE, forecast.visibility)
            val refreshed = bind(populated)
            org.junit.Assert.assertEquals("Exercise reuse of the same launcher layout", initial.layoutId, refreshed.layoutId)
            refreshed.reapply(context, view)
            org.junit.Assert.assertEquals("Hourly title must return after a refresh: ${style.key}", View.VISIBLE, title.visibility)
            org.junit.Assert.assertEquals("Hourly forecast must return after a refresh: ${style.key}", View.VISIBLE, forecast.visibility)
            org.junit.Assert.assertEquals(5, forecast.childCount)
            org.junit.Assert.assertEquals("26°", forecast.getChildAt(0).findViewById<TextView>(R.id.widget_cell_temp).text.toString())
            bind(empty).reapply(context, view)
            org.junit.Assert.assertEquals(View.GONE, title.visibility)
            org.junit.Assert.assertEquals(View.GONE, forecast.visibility)
            org.junit.Assert.assertEquals(0, forecast.childCount)
            bind(populated).reapply(context, view)
            org.junit.Assert.assertEquals(View.VISIBLE, title.visibility)
            org.junit.Assert.assertEquals(View.VISIBLE, forecast.visibility)
            org.junit.Assert.assertEquals(5, forecast.childCount)
        }
    }
    @Test fun weekUsesReadableIconsAtTypicalLauncherHeight() {
        val width = 344
        val height = 169
        val frame = FrameLayout(paparazzi.context).apply { layoutParams = ViewGroup.LayoutParams(dp(width), dp(height)) }
        val remote = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, WidgetStyle.VISTA, WidgetKind.WEEK),
            WidgetConfig.defaults(WidgetStyle.VISTA), widgetPreviewSample(), width, height, interactive = false)
        val view = remote.apply(paparazzi.context, frame)
        frame.addView(view, FrameLayout.LayoutParams(dp(width), dp(height)))
        paparazzi.snapshot(frame, "week_typical_launcher_height")
        org.junit.Assert.assertEquals(dp(46), view.findViewById<View>(R.id.widget_icon).height)
        val forecast = view.findViewById<ViewGroup>(R.id.widget_forecast)
        org.junit.Assert.assertEquals(dp(36), forecast.getChildAt(0).findViewById<View>(R.id.widget_cell_icon).height)
        val centers = (0 until forecast.childCount).map { index ->
            val cell = forecast.getChildAt(index) as ViewGroup
            val icon = cell.findViewById<View>(R.id.widget_cell_icon)
            cell.left + icon.left + icon.width / 2
        }
        val gaps = centers.zipWithNext { left, right -> right - left }
        org.junit.Assert.assertTrue("4×2 icons should use equal horizontal intervals: $gaps", gaps.maxOrNull()!! - gaps.minOrNull()!! <= dp(2))
        val bitmap = android.graphics.Bitmap.createBitmap(dp(width), dp(height), android.graphics.Bitmap.Config.ARGB_8888)
        frame.draw(android.graphics.Canvas(bitmap))
        val file = java.io.File("build/reports/widgets/week_typical_launcher_height.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun twoByTwoStaysSquareOnTallLauncherCells() {
        for (style in WidgetStyle.entries) {
            val frame = FrameLayout(paparazzi.context).apply { layoutParams = ViewGroup.LayoutParams(dp(164), dp(184)) }
            val remote = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, style, WidgetKind.NOW),
                WidgetConfig.defaults(style), widgetPreviewSample(), 164, 184, interactive = false)
            val view = remote.apply(paparazzi.context, frame)
            frame.addView(view, FrameLayout.LayoutParams(dp(164), dp(184)))
            paparazzi.snapshot(frame, "tall_now_${style.key}")
            val surface = view.findViewById<View>(R.id.widget_surface)
            org.junit.Assert.assertEquals(dp(164), surface.width)
            org.junit.Assert.assertEquals(dp(164), surface.height)
            org.junit.Assert.assertTrue(kotlin.math.abs(surface.top - dp(10)) <= dp(1))
        }
    }
    @Test fun twoByTwoKeepsLongAirQualityReadable() {
        val sample = widgetPreviewSample().let { it.copy(weather = it.weather.copy(
            aqi = com.zhisheng.weather.model.AqiInfo(value = 105, level = "轻度污染"))) }
        val frame = FrameLayout(paparazzi.context)
        val view = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, WidgetStyle.VISTA, WidgetKind.NOW),
            WidgetConfig(), sample, 150, 174, interactive = false).apply(paparazzi.context, frame)
        frame.addView(view, FrameLayout.LayoutParams(dp(150), dp(174)))
        paparazzi.snapshot(frame, "now_air_quality_105")
        val reading = view.findViewById<TextView>(R.id.widget_metric_2)
        org.junit.Assert.assertEquals("105", reading.text.toString())
        org.junit.Assert.assertTrue(reading.contentDescription.contains("轻度污染"))
    }
    @Test fun editors() {
        for (style in WidgetStyle.entries) for (kind in WidgetKind.entries) {
            val sample = widgetPreviewSample()
            val host = ComposeView(paparazzi.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                    WidgetStudio(style, null, kind, WidgetConfig.defaults(style), listOf(sample.city), sample.city, null,
                        NotificationSettings(), {}, {}, { _, _ -> }, sample)
                } }
            }
            paparazzi.snapshot(host, "editor_${style.key}_${kind.name.lowercase()}")
        }
    }
    @Test fun notificationLayouts() {
        for (expanded in listOf(false, true)) for (night in listOf(false, true)) {
            paparazzi.unsafeUpdateConfig(
                deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1080, screenHeight = 2340, nightMode = if (night) com.android.resources.NightMode.NIGHT else com.android.resources.NightMode.NOTNIGHT),
                theme = if (night) "android:Theme.Material.NoActionBar" else "android:Theme.Material.Light.NoActionBar",
            )
            val context = paparazzi.context
            val frame = FrameLayout(context).apply {
                setBackgroundColor(if (night) Color.rgb(38, 40, 43) else Color.rgb(249, 250, 252))
                layoutParams = ViewGroup.LayoutParams(dp(300), dp(if (expanded) 234 else 48))
            }
            val now = System.currentTimeMillis()
            val view = WidgetNotification.views(context, NotificationSettings(), widgetPreviewSample(now), expanded, now).apply(context, frame)
            frame.addView(view, FrameLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT))
            val name = "notification_${if (expanded) "expanded" else "compact"}_${if (night) "dark" else "light"}"
            paparazzi.snapshot(frame, name)
            org.junit.Assert.assertTrue("Notification must respect system height limits", view.height <= dp(if (expanded) 252 else 48))
            if (expanded) {
                org.junit.Assert.assertEquals("62%", view.findViewById<TextView>(R.id.notification_metric_2).text.toString())
                org.junit.Assert.assertEquals("优 42", view.findViewById<TextView>(R.id.notification_metric_4).text.toString())
                org.junit.Assert.assertEquals("最高 20%", view.findViewById<TextView>(R.id.notification_metric_5).text.toString())
            }
            val bitmap = android.graphics.Bitmap.createBitmap(dp(300), frame.height, android.graphics.Bitmap.Config.ARGB_8888)
            frame.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/reports/widgets/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun minimumHostSizesKeepCriticalTextWhole() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1080, screenHeight = 2340, fontScale = 1.3f))
        val failures = mutableListOf<String>()
        // 负摄氏与三位华氏是最宽的两种温度读数。华氏只跑最小宿主，那是空间最紧的一档。
        val variants = listOf(
            Triple("minimum", "c", -18.0),
            Triple("large_font", "c", -18.0),
            Triple("minimum_f", "f", 48.0),
        )
        for ((tag, unit, temperature) in variants) {
        val minimum = tag != "large_font"
        for (style in WidgetStyle.entries) for (kind in WidgetKind.entries) {
            val width = if (!minimum) kind.width else if (kind == WidgetKind.NOW) 110 else 250
            val height = if (!minimum) kind.height else when (kind) { WidgetKind.NOW -> 110; WidgetKind.PULSE -> 60; WidgetKind.BAND -> 120; WidgetKind.WEEK -> 140; WidgetKind.SCENE -> 320 }
            val frame = FrameLayout(paparazzi.context).apply { layoutParams = ViewGroup.LayoutParams(dp(width), dp(height)) }
            val sample = widgetPreviewSample().let { it.copy(city = it.city.copy(name = "嘉峪关市"), weather = it.weather.copy(current = it.weather.current?.copy(temperature = temperature))) }
            val view = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, style, kind), WidgetConfig.defaults(style).copy(textScale = 1.25f, tempUnit = unit), sample, width, height, interactive = false).apply(paparazzi.context, frame)
            frame.addView(view, FrameLayout.LayoutParams(dp(width), dp(height)))
            val name = "${tag}_${style.key}_${kind.name.lowercase()}"
            paparazzi.snapshot(frame, name)
            fun checkTree(node: View) {
                if (node.visibility != View.VISIBLE) return
                if (node is TextView && node.text.isNotBlank()) {
                    val layout = node.layout
                    org.junit.Assert.assertNotNull("$name ${node.text} has a layout", layout)
                    if (node.id != R.id.widget_secondary) for (line in 0 until layout.lineCount) {
                        if (layout.getEllipsisCount(line) != 0) failures += "$name ${node.text} ellipsized (${node.width})"
                    }
                    if (layout.height > node.height) failures += "$name ${node.text} vertical (${layout.height}/${node.height})"
                    val parent = node.parent as View
                    if (node.top < 0 || node.bottom > parent.height) failures += "$name ${node.text} outside row (${node.top},${node.bottom}/${parent.height})"
                }
                if (node is ViewGroup) for (i in 0 until node.childCount) checkTree(node.getChildAt(i))
            }
            checkTree(view)
            val bitmap = android.graphics.Bitmap.createBitmap(dp(width), dp(height), android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val file = java.io.File("build/reports/widgets/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        }
        org.junit.Assert.assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
    @Test fun notificationSettingsCanOpenWithoutWidget() {
        val sample = widgetPreviewSample()
        val host = ComposeView(paparazzi.context).apply {
            setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                NotificationStudio(NotificationSettings(), listOf(sample.city), sample.city, null, {}, {})
            } }
        }
        paparazzi.snapshot(host, "notification_settings")
    }
    @Test fun shortForecastsDoNotInventEmptyDays() {
        val now = System.currentTimeMillis()
        val sample = widgetPreviewSample(now).let { it.copy(weather = it.weather.copy(hourly = it.weather.hourly.take(2), daily = it.weather.daily.take(3))) }
        for (kind in listOf(WidgetKind.BAND, WidgetKind.WEEK)) {
            val frame = FrameLayout(paparazzi.context)
            val view = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, WidgetStyle.VISTA, kind), WidgetConfig(), sample, kind.width, kind.height, now, interactive = false).apply(paparazzi.context, frame)
            org.junit.Assert.assertEquals(if (kind == WidgetKind.BAND) 2 else 3, view.findViewById<ViewGroup>(R.id.widget_forecast).childCount)
        }
    }
    @Test fun narrowAndTransparent() {
        for (style in WidgetStyle.entries) for (opacity in listOf(0, 45, 100)) {
            val cfg = WidgetConfig.defaults(style).copy(opacity = opacity, textScale = 1.25f, text = "auto")
            if (opacity == 0) org.junit.Assert.assertTrue(
                "Protected automatic ink must survive a dark-to-light wallpaper: ${style.key}",
                androidx.core.graphics.ColorUtils.calculateLuminance(
                    widgetPalette(paparazzi.context, style, cfg, Color.rgb(40, 57, 66)).ink,
                ) > 0.5,
            )
            for (kind in WidgetKind.entries) {
                val width = if (kind == WidgetKind.NOW) 150 else 300
                val frame = FrameLayout(paparazzi.context).apply { setBackgroundColor(Color.rgb(40, 57, 66)); layoutParams = ViewGroup.LayoutParams(dp(width), dp(kind.height)) }
                val remote = WidgetBinder.bind(paparazzi.context, 1, WidgetInstance(1, style, kind), cfg, widgetPreviewSample(), width, kind.height, interactive = false)
                frame.addView(remote.apply(paparazzi.context, frame), FrameLayout.LayoutParams(dp(width), dp(kind.height)))
                paparazzi.snapshot(frame, "${style.key}_${kind.name.lowercase()}_${opacity}_narrow")
            }
        }
    }
}
