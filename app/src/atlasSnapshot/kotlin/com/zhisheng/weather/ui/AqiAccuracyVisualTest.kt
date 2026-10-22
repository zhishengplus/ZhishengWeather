package com.zhisheng.weather.ui

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.home.AqiCard
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real production cards and charts, with only fabricated offline weather. */
class AqiAccuracyVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")

    private fun capture(name: String, style: InterfaceStyle, light: Boolean, width: Int, scale: Float,
        height: Int = 900, check: (List<String>) -> Unit, content: @Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width,
            screenHeight = height, density = Density.MEDIUM, fontScale = scale))
        var checked = false
        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false,
                    homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS) { content() }
            } }
            viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!checked) {
                        val root = descendants(this@apply).firstNotNullOfOrNull { view ->
                            view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                ?.invoke(view) as? SemanticsOwner
                        }?.unmergedRootSemanticsNode ?: error("Missing production semantics")
                        val all = nodes(root)
                        val labels = all.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
                        check(labels)
                        all.forEach { node ->
                            val layouts = mutableListOf<TextLayoutResult>()
                            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                            layouts.forEach { layout ->
                                val lines = (0 until layout.lineCount).joinToString { line ->
                                    "line=$line left=${layout.getLineLeft(line)} right=${layout.getLineRight(line)} " +
                                        "top=${layout.getLineTop(line)} bottom=${layout.getLineBottom(line)} baseline=${layout.getLineBaseline(line)}"
                                }
                                val input = layout.layoutInput
                                if (name.startsWith("hourly_aqi_") && input.text.text == "空气质量") {
                                    assertEquals("AQI title must remain horizontal, with its value on the separate full-width line: $name",
                                        1, layout.lineCount)
                                }
                                assertFalse("Text must fit its production measured area: ${input.text.text}; ${layout.size}; " +
                                    "case=$name style=${style.key} light=$light width=$width scale=$scale node=${node.boundsInRoot}; " +
                                    "overflowWidth=${layout.didOverflowWidth} overflowHeight=${layout.didOverflowHeight} " +
                                    "constraints=${input.constraints} density=${input.density} textStyle=${input.style}; $lines",
                                    layout.didOverflowHeight || (0 until layout.lineCount).any { line ->
                                        layout.getLineLeft(line) < -1f || layout.getLineRight(line) > layout.size.width + 1f ||
                                            layout.getLineBottom(line) > layout.size.height + 1f
                                    })
                            }
                        }
                        checked = true
                    }
                    return true
                }
            })
        }
        paparazzi.snapshot(host, name)
        assertTrue("Native assertions must execute on the actual composed UI", checked)
    }

    @Test fun providerEstimatedAndMissingAqiStayDistinctAcrossThemesAndLargeType() {
        val cases = listOf(
            "provider" to AqiInfo(value = 60, level = "良", standard = "中国", primary = "PM2.5", pm25 = "20"),
            "estimated" to AqiInfo(value = 120, level = "轻度污染", standard = "美国", primary = "PM2.5",
                pm25 = "35", pm10 = "48", o3 = "70", no2 = "18", so2 = "8", co = "0.4", estimated = true),
            "missing" to AqiInfo(value = null, pm25 = "20", estimated = true),
        )
        for (style in InterfaceStyle.entries) for (light in listOf(false, true))
            for ((width, scale) in listOf(360 to 1f, 320 to 2f)) for ((kind, air) in cases) {
                capture("aqi_${kind}_${style.key}_${light}_${width}", style, light, width, scale, check = { labels ->
                    if (air.estimated) assertTrue(Fmt.AQI_ESTIMATION_LABEL in labels)
                    else assertFalse(labels.any { it.contains("估算") })
                    if (air.value == null) {
                        assertTrue("Missing AQI must remain visibly unavailable", "AQI暂无数据" in labels)
                        assertFalse(labels.any { it in setOf("0", "优", "良") })
                    } else assertTrue(air.value.toString() in labels)
                }) { Column(Modifier.fillMaxWidth()) { AqiCard(air, Modifier) } }
            }
    }

    @Test fun mixedHourlyAirValuesRetainTheirOwnEstimationLabels() {
        val now = System.currentTimeMillis()
        val data = WeatherData(current = CurrentWeather(temperature = 22.0), utcOffsetSeconds = 0,
            aqi = AqiInfo(value = 120, level = "轻度污染", estimated = true), hourly = listOf(
                HourlyWeather(now + 1_800_000L, temperature = 23.0, aqi = 40),
                HourlyWeather(now + 5_400_000L, temperature = 24.0, aqi = 130, aqiEstimated = true),
            ))
        for (style in InterfaceStyle.entries) for (light in listOf(false, true)) {
            capture("hourly_aqi_${style.key}_$light", style, light, 320, 1.5f, height = 1400,
                check = { labels ->
                    assertTrue("The actual AQI title must be present", "空气质量" in labels)
                    assertTrue("Selected estimated observation must carry its label", labels.any { it.contains("120（估算）") })
                    assertTrue(labels.any { it.contains(Fmt.AQI_ESTIMATION_LABEL) })
                }) { HourlyDetailScreen(data, "布局示例", "c", "kmh", {}) }
        }
    }

    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
