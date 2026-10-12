package com.zhisheng.weather.ui

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.*
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SourceRecoveryVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")
    private val city = City("北京", "北京", 39.9, 116.4, "beijing")
    private var viewportHeight = 0

    @Test fun radarPanelsKeepCloseAndLongContentReachableAtLargeType() {
        for (style in InterfaceStyle.entries) for (light in listOf(false, true)) {
            capture("radar_info_${style.key}_$light", style, light, 320, 300, { root -> checkPanel(root, "关闭") }) {
                RadarInfoPanel(RadarSource.CAIYUN, true, {}, Modifier.fillMaxWidth().heightIn(max = 300.dp))
            }
            capture("radar_source_${style.key}_$light", style, light, 320, 300, { root ->
                checkPanel(root, "关闭")
                val choices = nodes(root).filter { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }
                assertEquals(2, choices.size)
                assertTrue(choices.last().config.contains(SemanticsProperties.Disabled))
            }) { RadarSourcePanel(RadarSource.RAINVIEWER, false, {}, {}, Modifier.fillMaxWidth().heightIn(max = 300.dp)) }
        }
    }

    @Test fun failedComparisonKeepsRealRetryAndCloseActionsInsideAShortPanel() {
        for (style in InterfaceStyle.entries) {
            var retried = false
            var closed = false
            capture("comparison_failed_${style.key}", style, true, 640, 240, { root ->
                checkPanel(root, "关闭")
                val retry = action(root, "重试")
                assertFalse(retry.config.contains(SemanticsProperties.Disabled))
                assertTrue(retry.config[SemanticsActions.OnClick].action?.invoke() == true)
                assertTrue(action(root, "关闭").config[SemanticsActions.OnClick].action?.invoke() == true)
            }) { WeatherSourceComparisonPanel(city, null, "暂时无法完成比较，请检查网络后重试",
                { retried = true }, { closed = true }, Modifier.fillMaxWidth().heightIn(max = 240.dp)) }
            assertTrue(retried)
            assertTrue(closed)
        }
    }

    @Test fun loadingComparisonCannotStartAnOverlappingRetry() {
        capture("comparison_loading", InterfaceStyle.PHOSPHOR_VISTA, false, 320, 480, { root ->
            assertTrue(action(root, "重新比较").config.contains(SemanticsProperties.Disabled))
            checkPanel(root, "关闭")
        }) { WeatherSourceComparisonPanel(city, null, null, {}, {}, Modifier.fillMaxWidth().heightIn(max = 480.dp)) }
    }

    @Test fun aSingleForecastHourOrOnlyTheObservationStillShowsItsActualReading() {
        val oneHour = WeatherData(hourly = listOf(HourlyWeather(System.currentTimeMillis() + 3_600_000, 26.0)), utcOffsetSeconds = 0)
        val observation = WeatherData(current = CurrentWeather(temperature = 26.0, uvIndex = 4), utcOffsetSeconds = 0)
        for (style in InterfaceStyle.entries) for ((kind, data) in listOf("last_hour" to oneHour, "observation" to observation)) {
            capture("hourly_single_${style.key}_$kind", style, true, 320, 800, { root ->
                val labels = nodes(root).flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
                assertTrue("A real single reading must not be hidden", "26°" in labels)
                assertFalse("A nonempty date must not be called empty", "暂无逐时数据" in labels)
            }) { HourlyDetailScreen(data, "北京", "c", "kmh", {}) }
        }
    }

    private fun checkPanel(root: SemanticsNode, actionText: String) {
        val button = action(root, actionText)
        assertTrue("Fixed action must remain measurable", button.size.height >= 48 * 3 - 1)
        assertTrue("Fixed action must be inside the viewport", button.boundsInRoot.bottom <= viewportHeight + 1)
        nodes(root).forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            layouts.forEach { layout ->
                // A wrap-content Text retains its paragraph's available width, even
                // when its measured size is the shorter intrinsic width. Compare
                // actual glyph lines: the container alone reports false overflow.
                val clippedLine = (0 until layout.lineCount).any { line ->
                    layout.getLineLeft(line) < -1f || layout.getLineRight(line) > layout.size.width + 1f ||
                        layout.getLineBottom(line) > layout.size.height + 1f
                }
                assertFalse("Panel text must wrap: text=${layout.layoutInput.text.text}, " +
                    "width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, " +
                    "size=${layout.size}, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
                    "intrinsic=${layout.multiParagraph.maxIntrinsicWidth}, constraints=${layout.layoutInput.constraints}, " +
                    "style=${layout.layoutInput.style}, bounds=${node.boundsInRoot}",
                    layout.didOverflowHeight || clippedLine)
            }
        }
    }
    private fun action(root: SemanticsNode, label: String) = nodes(root).first { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null && nodes(node).any {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
        }
    }
    private fun capture(name: String, style: InterfaceStyle, light: Boolean, width: Int, height: Int,
        check: (SemanticsNode) -> Unit, content: @androidx.compose.runtime.Composable () -> Unit) {
        viewportHeight = height * 3
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width * 3,
            screenHeight = height * 3, fontScale = 1.5f,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
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
                        val owner = descendants(this@apply).firstNotNullOfOrNull { view ->
                            view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                ?.invoke(view) as? SemanticsOwner
                        } ?: error("Missing production semantics")
                        check(owner.unmergedRootSemanticsNode)
                        checked = true
                    }
                    return true
                }
            })
        }
        paparazzi.snapshot(host, name)
        assertTrue(checked)
    }
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
