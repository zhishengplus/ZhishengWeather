package com.zhisheng.weather.widget

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Increasing the detail slider may reach a size limit, but must never make text smaller. */
class WidgetDetailScaleVisualTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1080, screenHeight = 2340),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    @Test fun increasingDetailScaleNeverShrinksVisibleNativeDescriptions() = checkDetailScale()

    @Test fun increasingDetailScaleKeepsNativeDescriptionsReadableWithLargeSystemType() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1080, screenHeight = 2340, fontScale = 1.5f))
        checkDetailScale()
    }

    @Test fun compactNarrowHostsKeepNativeDescriptionsInsideTheirRows() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = 1080, screenHeight = 2340, fontScale = 1.5f))
        for (height in listOf(280, 339, 439)) checkDetailScale(listOf(WidgetKind.SCENE), 239, height)
    }

    private fun checkDetailScale(kinds: List<WidgetKind> = WidgetKind.entries,
        hostWidth: Int? = null, hostHeight: Int? = null) {
        val failures = mutableListOf<String>()
        val context = paparazzi.context
        val density = context.resources.displayMetrics.density
        val sample = widgetPreviewSample().let { it.copy(weather = it.weather.copy(forecastSummary = "天气平稳，适合出门。")) }
        for (style in WidgetStyle.entries) for (kind in kinds) {
            val width = hostWidth ?: kind.width
            val height = hostHeight ?: if (kind == WidgetKind.SCENE) 440 else kind.height
            val frame = FrameLayout(context)
            fun bind(scale: Float) = WidgetBinder.bind(context, 1, WidgetInstance(1, style, kind),
                WidgetConfig.defaults(style).copy(detailScale = scale), sample, width, height, interactive = false)
            val view = bind(.85f).apply(context, frame)
            frame.addView(view, FrameLayout.LayoutParams((width * density).toInt(), (height * density).toInt()))
            val sizes = listOf(.85f, 1f, 1.5f).map { scale ->
                bind(scale).reapply(context, view)
                view.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
                view.layout(0, 0, (width * density).toInt(), (height * density).toInt())
                details(view).map {
                    if (it.layout == null || it.layout.height > it.height)
                        failures += "${style.key}/$kind/${width}x${height}/$scale/${context.resources.getResourceEntryName(it.id)}: vertically clipped description"
                    it.id to it.textSize
                }
            }
            assertEquals(sizes[0].map { it.first }, sizes[1].map { it.first })
            assertEquals(sizes[1].map { it.first }, sizes[2].map { it.first })
            sizes[0].indices.forEach { index ->
                val id = sizes[0][index].first
                val a = sizes[0][index].second
                val b = sizes[1][index].second
                val c = sizes[2][index].second
                val line = "${style.key}/$kind/${width}x${height}/${context.resources.getResourceEntryName(id)}: $a -> $b -> $c px"
                println(line)
                if (b + .05f < a || c + .05f < b) failures += line
            }
        }
        assertTrue("Increasing description size shrank actual native text:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    private fun details(view: View): List<TextView> {
        if (view.visibility != View.VISIBLE) return emptyList()
        val ids = setOf(R.id.widget_city, R.id.widget_date, R.id.widget_lunar, R.id.widget_condition,
            R.id.widget_scene_briefing_text, R.id.widget_metric_label_1, R.id.widget_metric_label_2,
            R.id.widget_metric_label_3, R.id.widget_metric_label_4, R.id.widget_metric_label_5,
            R.id.widget_metric_label_6, R.id.widget_scene_hourly_title, R.id.widget_scene_day_title,
            R.id.widget_precip_title, R.id.widget_cell_label)
        return when (view) {
            is TextView -> listOf(view).filter { it.id in ids && it.text.isNotBlank() }
            is ViewGroup -> (0 until view.childCount).flatMap { details(view.getChildAt(it)) }
            else -> emptyList()
        }
    }
}
