package com.zhisheng.weather.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.R
import com.zhisheng.weather.model.Nowcast
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WidgetSummaryVisualTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1080, screenHeight = 2340),
        theme = "android:Theme.Material.Light.NoActionBar",
    )
    private fun dp(value: Int) = (value * paparazzi.context.resources.displayMetrics.density).toInt()

    @Test fun longSummaryWrapsWithoutLosingTheSecondClause() = checkSummaries("normal")

    @Test fun largeSystemFontKeepsSummaryAndForecastApart() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1080, screenHeight = 2340, fontScale = 1.3f))
        checkSummaries("large_font")
    }

    @Test fun exceptionallyLongSummaryKeepsFullAccessibleTextAndCanRefreshToShortText() {
        val context = paparazzi.context
        val now = System.currentTimeMillis()
        val sample = widgetPreviewSample(now).let { it.copy(weather = it.weather.copy(
            daily = it.weather.daily.map { d -> d.copy(high = 27.0, low = 18.0) })) }
        for (style in WidgetStyle.entries) {
            val frame = FrameLayout(context)
            fun bind(text: String) = WidgetBinder.bind(context, 1, WidgetInstance(1, style, WidgetKind.SCENE),
                WidgetConfig.defaults(style).copy(showWeatherGirl = true, opacity = 45),
                sample.copy(weather = sample.weather.copy(forecastSummary = text)), 344, 380, now, interactive = false)
            val longText = "未来24小时：" + "午后有短时阵雨，出门记得带伞。".repeat(8)
            val view = bind(longText).apply(context, frame)
            frame.addView(view, FrameLayout.LayoutParams(dp(344), dp(380)))
            fun measure() {
                frame.measure(View.MeasureSpec.makeMeasureSpec(dp(344), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dp(380), View.MeasureSpec.EXACTLY))
                frame.layout(0, 0, dp(344), dp(380))
            }
            measure()
            val text = view.findViewById<TextView>(R.id.widget_scene_briefing_text)
            assertEquals(longText, text.text.toString())
            assertEquals(longText, text.contentDescription.toString())
            assertEquals(2, text.layout.lineCount)
            assertTrue(text.layout.getEllipsisCount(1) > 0)
            assertTrue(text.layout.height <= text.height)
            val shortText = "未来24小时：天气平稳。"
            bind(shortText).reapply(context, view)
            measure()
            assertEquals(shortText, text.text.toString())
            assertEquals(shortText, text.contentDescription.toString())
            assertEquals(1, text.layout.lineCount)
            assertEquals(0, text.layout.getEllipsisCount(0))
        }
    }

    private fun checkSummaries(tag: String) {
        val failures = mutableListOf<String>()
        val now = System.currentTimeMillis()
        // Provider forecast prose is longer than the old one-line preview sample.
        val sample = widgetPreviewSample(now).let { it.copy(weather = it.weather.copy(
            daily = it.weather.daily.map { day -> day.copy(high = 27.0, low = 18.0) },
            forecastSummary = "未来24小时：午后有短时阵雨，晚间出门记得带伞，回家注意路面湿滑。")) }
        val expected = Nowcast.briefing(sample.weather, "c", now)!!.text
        for (style in WidgetStyle.entries) for (height in listOf(300, 340, 380, 440))
        for (theme in listOf("light", "dark")) for (girl in listOf(false, true)) {
            val context = paparazzi.context
            val frame = FrameLayout(context)
            val config = WidgetConfig.defaults(style).copy(showWeatherGirl = girl, theme = theme,
                opacity = if (girl) 45 else 88)
            val view = WidgetBinder.bind(context, 1, WidgetInstance(1, style, WidgetKind.SCENE),
                config, sample, 344, height, now, interactive = false).apply(context, frame)
            frame.addView(view, FrameLayout.LayoutParams(dp(344), dp(height)))
            view.findViewById<android.widget.TextClock>(R.id.widget_time).apply {
                format12Hour = "'09:41'"; format24Hour = "'09:41'"
            }
            view.findViewById<android.widget.TextClock>(R.id.widget_date).apply {
                format12Hour = "'9月20日 周日'"; format24Hour = "'9月20日 周日'"
            }
            frame.measure(View.MeasureSpec.makeMeasureSpec(dp(344), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dp(height), View.MeasureSpec.EXACTLY))
            frame.layout(0, 0, dp(344), dp(height))
            val name = "summary_${tag}_${style.key}_${height}dp_${theme}_girl_$girl"
            val summary = view.findViewById<TextView>(R.id.widget_scene_briefing_text)
            if (summary.text.toString() != expected) failures += "$name: second clause discarded"
            val layout = summary.layout
            if (layout == null || layout.height > summary.height ||
                (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 })
                failures += "$name: normal forecast prose was clipped"
            if (layout != null && layout.lineCount != 2) failures += "$name: long sentence did not wrap"
            if (summary.textSize / context.resources.displayMetrics.density < 10.5f)
                failures += "$name: summary too small"
            fun checkBounds(node: View) {
                if (node.visibility != View.VISIBLE) return
                val parent = node.parent as? View
                if (parent != null && (node.top < 0 || node.bottom > parent.height ||
                        node.left < 0 || node.right > parent.width)) failures += "$name: view outside row"
                if (node is TextView && node.text.isNotBlank() && node.layout.height > node.height)
                    failures += "$name: text vertically clipped: ${node.text}"
                if (node is ViewGroup) (0 until node.childCount).forEach { checkBounds(node.getChildAt(it)) }
            }
            checkBounds(view)
            val bitmap = Bitmap.createBitmap(dp(344), dp(height), Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/widgets/$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
