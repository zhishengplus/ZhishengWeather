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
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Exercise the actual RemoteViews, including editor-style reapply on existing views. */
class WidgetTextScaleVisualTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1080, screenHeight = 2340),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private fun dp(value: Int) = (value * paparazzi.context.resources.displayMetrics.density).toInt()

    @Test fun sliderScaleChangesAllVisibleNumbersOnExistingWidgets() = checkScale("normal_font")

    @Test fun sliderStillWorksWithLargeSystemFonts() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1080, screenHeight = 2340, fontScale = 1.3f))
        checkScale("large_font")
    }

    @Test fun smallHostsKeepNegativeAndThreeDigitForecastsReadable() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1080, screenHeight = 2340, fontScale = 1.3f))
        val context = paparazzi.context
        val failures = mutableListOf<String>()
        for (style in WidgetStyle.entries) for (kind in WidgetKind.entries) for (unit in listOf("c", "f")) {
            val width = if (kind == WidgetKind.NOW) 110 else 250
            val height = when (kind) { WidgetKind.NOW -> 110; WidgetKind.PULSE -> 60;
                WidgetKind.BAND -> 120; WidgetKind.WEEK -> 140; WidgetKind.SCENE -> 320 }
            val sample = widgetPreviewSample().let { it.copy(weather = it.weather.copy(
                current = it.weather.current?.copy(temperature = if (unit == "c") -18.0 else 48.0),
                hourly = it.weather.hourly.map { h -> h.copy(temperature = if (unit == "c") -18.0 else 48.0) },
                daily = it.weather.daily.map { d -> d.copy(low = if (unit == "c") -18.0 else 40.0,
                    high = if (unit == "c") -8.0 else 48.0) })) }
            for (scale in listOf(.85f, 1f, 1.25f)) {
                val frame = FrameLayout(context)
                val config = WidgetConfig.defaults(style).copy(textScale = scale, tempUnit = unit)
                val view = WidgetBinder.bind(context, 1, WidgetInstance(1, style, kind), config, sample,
                    width, height, interactive = false).apply(context, frame)
                frame.addView(view, FrameLayout.LayoutParams(dp(width), dp(height)))
                frame.measure(View.MeasureSpec.makeMeasureSpec(dp(width), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dp(height), View.MeasureSpec.EXACTLY))
                frame.layout(0, 0, dp(width), dp(height))
                val name = "dense_${style.key}_${kind.name.lowercase()}_${unit}_${(scale * 100).toInt()}"
                fun check(node: View) {
                    if (node.visibility != View.VISIBLE) return
                    if (node is TextView && node.id in setOf(R.id.widget_temp, R.id.widget_range,
                            R.id.widget_cell_temp, R.id.widget_cell_extra) && node.text.isNotBlank()) {
                        if (node.textSize / context.resources.displayMetrics.density < 8.99f)
                            failures += "$name: unreadably small ${node.text}"
                        if (node.layout.height > node.height || (0 until node.layout.lineCount).any {
                                node.layout.getEllipsisCount(it) > 0 }) failures += "$name: cropped ${node.text}"
                    }
                    if (node is ViewGroup) (0 until node.childCount).forEach { check(node.getChildAt(it)) }
                }
                check(view)
                if (kind != WidgetKind.WEEK || style != WidgetStyle.CLASSIC) {
                    assertEquals(WidgetBinder.temperature(sample.weather.current?.temperature, config),
                        view.findViewById<TextView>(R.id.widget_temp).text.toString())
                }
                val bitmap = Bitmap.createBitmap(dp(width), dp(height), Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val file = File("build/reports/widgets/$name.png")
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun checkScale(tag: String) {
        val failures = mutableListOf<String>()
        val sizes = mutableListOf<String>("style,kind,height,id,small,normal,large")
        for (style in WidgetStyle.entries) for (kind in WidgetKind.entries)
        for (height in if (kind == WidgetKind.SCENE) listOf(300, 380, 440) else listOf(kind.height)) {
            val context = paparazzi.context
            val width = kind.width
            val frame = FrameLayout(context)
            val sample = widgetPreviewSample()
            fun bind(scale: Float) = WidgetBinder.bind(context, 1, WidgetInstance(1, style, kind),
                WidgetConfig.defaults(style).copy(textScale = scale), sample, width, height, interactive = false)
            val view = bind(.85f).apply(context, frame)
            frame.addView(view, FrameLayout.LayoutParams(dp(width), dp(height)))
            fun readings(node: View): List<TextView> {
                if (node.visibility != View.VISIBLE) return emptyList()
                val ids = setOf(R.id.widget_time, R.id.widget_temp, R.id.widget_range,
                    R.id.widget_metric_1, R.id.widget_metric_2, R.id.widget_metric_3,
                    R.id.widget_metric_4, R.id.widget_metric_5, R.id.widget_metric_6,
                    R.id.widget_cell_temp, R.id.widget_cell_extra)
                return if (node is TextView) listOf(node).filter {
                    (it.id in ids || (it.id == R.id.widget_cell_label && ':' in it.text)) && it.text.isNotBlank()
                }
                    else if (node is ViewGroup) (0 until node.childCount).flatMap { readings(node.getChildAt(it)) }
                    else emptyList()
            }
            val values = listOf(.85f, 1f, 1.25f).map { scale ->
                bind(scale).reapply(context, view)
                view.findViewById<android.widget.TextClock>(R.id.widget_time)?.apply {
                    format12Hour = "'09:41'"; format24Hour = "'09:41'"
                }
                view.findViewById<android.widget.TextClock>(R.id.widget_date)?.apply {
                    format12Hour = "'9月20日 周日'"; format24Hour = "'9月20日 周日'"
                }
                view.measure(View.MeasureSpec.makeMeasureSpec(dp(width), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dp(height), View.MeasureSpec.EXACTLY))
                view.layout(0, 0, dp(width), dp(height))
                val visibleReadings = readings(view)
                visibleReadings.forEach { number ->
                    if (number.textSize / context.resources.displayMetrics.density < 8.99f) {
                        failures += "$tag/${style.key}/$kind/$scale/${number.text}: below the 9dp readable size"
                    }
                    val layout = number.layout
                    if (layout == null || layout.height > number.height ||
                        (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }) {
                        failures += "$tag/${style.key}/$kind/$scale/${number.text}: cropped text"
                    }
                    val parent = number.parent as View
                    if (number.left < 0 || number.right > parent.width || number.top < 0 || number.bottom > parent.height) {
                        failures += "$tag/${style.key}/$kind/$scale/${number.text}: outside its row"
                    }
                }
                val result = visibleReadings.map { it.id to it.textSize }
                val bitmap = Bitmap.createBitmap(dp(width), dp(height), Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val file = File("build/reports/widgets/text_scale_${tag}_${style.key}_${kind.name.lowercase()}_${height}dp_${(scale * 100).toInt()}.png")
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                result
            }
            assertEquals(values[0].map { it.first }, values[1].map { it.first })
            assertEquals(values[1].map { it.first }, values[2].map { it.first })
            values[0].indices.forEach { i ->
                val (id, small) = values[0][i]
                val normal = values[1][i].second
                val large = values[2][i].second
                sizes += "${style.key},$kind,$height,${context.resources.getResourceEntryName(id)},$small,$normal,$large"
                val primary = id == R.id.widget_temp || id == R.id.widget_time
                if (!(small < normal && normal < large && large / small > if (primary) 1.4f else 1.01f)) {
                    failures += "${style.key}/$kind/${context.resources.getResourceEntryName(id)}: $small -> $normal -> $large"
                }
            }
        }
        File("build/reports/widgets/text-scale-sizes-$tag.csv").writeText(sizes.joinToString("\n"))
        assertTrue("The slider must change rendered numbers, including weather readings:\n${failures.joinToString("\n")}", failures.isEmpty())
    }
}
