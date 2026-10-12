package com.zhisheng.weather.ui

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.theme.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ForecastCompletionVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 960,
        screenHeight = 3300, fontScale = 1.5f), theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun classicAndVistaDailyDetailsActuallyContainTheSelectedDaysHourlySection() {
        val day = Instant.parse("2026-10-10T00:00:00Z").toEpochMilli()
        for (style in InterfaceStyle.entries) for (light in listOf(false, true)) {
            var checked = false
            val host = ComposeView(paparazzi.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                    ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false) {
                        DailyForecastScreen(null, listOf(DailyWeather(day, high = 24.0, low = 12.0)),
                            null, "c", "kmh", 0, {}, hourly = (0..23).map {
                                HourlyWeather(day + it * 3_600_000, if (it == 5) null else 15.0 + it / 3.0)
                            })
                    }
                } }
                viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        if (!checked) {
                            val owner = descendants(this@apply).firstNotNullOfOrNull { view ->
                                view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                    ?.invoke(view) as? SemanticsOwner
                            } ?: error("Missing production semantics")
                            val labels = nodes(owner.unmergedRootSemanticsNode).flatMap {
                                it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text }
                            }
                            assertTrue("$style must expose actual hourly content, not only the daily workbench",
                                labels.any { it == "当日逐小时温度" })
                            checked = true
                        }
                        return true
                    }
                })
            }
            paparazzi.snapshot(host, "daily_hourly_${style.key}_${light}_320_150")
            assertTrue(checked)
        }
    }

    @Test fun selectingTheLastDateShowsItsSingleRealForecastHour() {
        val tomorrow = java.time.Instant.ofEpochMilli(System.currentTimeMillis()).atZone(java.time.ZoneOffset.UTC)
            .toLocalDate().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val data = WeatherData(hourly = (0..24).map {
            HourlyWeather(tomorrow + it * 3_600_000, if (it == 24) 26.0 else 12.0)
        }, utcOffsetSeconds = 0)
        for (style in InterfaceStyle.entries) {
            var clicked = false
            var checked = false
            val host = ComposeView(paparazzi.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                    ZhishengWeatherTheme(isLight = true, interfaceStyle = style, softGlow = false) {
                        HourlyDetailScreen(data, "北京", "c", "kmh", {})
                    }
                } }
                viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        val owner = descendants(this@apply).firstNotNullOfOrNull { view ->
                            view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                ?.invoke(view) as? SemanticsOwner
                        } ?: error("Missing production semantics")
                        val all = nodes(owner.unmergedRootSemanticsNode)
                        val tabs = all.filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
                        if (!clicked) {
                            assertEquals("Both real dates must be available", 2, tabs.size)
                            assertTrue(tabs.last().config[SemanticsActions.OnClick].action?.invoke() == true)
                            clicked = true
                        } else if (!checked && tabs.last().config.getOrNull(SemanticsProperties.Selected) == true) {
                            val labels = all.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
                            assertTrue("The last date's actual temperature must be visible", "26°" in labels)
                            assertFalse("A single real hour cannot be called empty", "暂无逐时数据" in labels)
                            checked = true
                        }
                        this@apply.postInvalidateOnAnimation()
                        return true
                    }
                })
            }
            paparazzi.gif(host, "hourly_last_date_${style.key}_320_150", end = 2000, fps = 8)
            assertTrue("The last date must actually become selected and show its reading", checked)
        }
    }

    @Test fun futureDateClockLabelsFitTheirActualAxisAtLargeType() {
        val tomorrow = java.time.Instant.ofEpochMilli(System.currentTimeMillis()).atZone(java.time.ZoneOffset.UTC)
            .toLocalDate().plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val data = WeatherData(hourly = (0..3).map { HourlyWeather(tomorrow + it * 3_600_000, 17.0 - it) }, utcOffsetSeconds = 0)
        for (style in InterfaceStyle.entries) {
            var checked = false
            val host = ComposeView(paparazzi.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                    ZhishengWeatherTheme(isLight = true, interfaceStyle = style, softGlow = false) {
                        HourlyDetailScreen(data, "北京", "c", "kmh", {})
                    }
                } }
                viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        if (!checked) {
                            val owner = descendants(this@apply).firstNotNullOfOrNull { view ->
                                view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                    ?.invoke(view) as? SemanticsOwner
                            } ?: error("Missing production semantics")
                            val axes = nodes(owner.unmergedRootSemanticsNode).filter { node ->
                                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.matches(Regex("0[0-3]:00")) }
                            }
                            assertTrue("Future-date clocks must be present", axes.size >= 2)
                            axes.forEach { node ->
                                assertTrue("An axis label left the panel", node.boundsInRoot.left >= 0 && node.boundsInRoot.right <= 960)
                                val layouts = mutableListOf<TextLayoutResult>()
                                assertTrue(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) == true)
                                layouts.forEach { layout ->
                                    assertEquals("Axis clocks must stay on one line", 1, layout.lineCount)
                                    assertTrue("Axis glyphs must fit", layout.getLineLeft(0) >= -1 &&
                                        layout.getLineRight(0) <= layout.size.width + 1 && !layout.didOverflowHeight)
                                }
                            }
                            checked = true
                        }
                        return true
                    }
                })
            }
            paparazzi.snapshot(host, "hourly_future_clocks_${style.key}_320_150")
            assertTrue(checked)
        }
    }

    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
