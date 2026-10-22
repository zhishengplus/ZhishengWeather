package com.zhisheng.weather.ui

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.data.LandscapeStandbyStyle
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Window rotation is checked on Android; this exercises every actual clock presentation. */
class SystemRotationVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun everyClockKeepsSettingsAccessibleWithoutAForcePortraitAction() {
        for (interfaceStyle in InterfaceStyle.entries) for (clockStyle in LandscapeStandbyStyle.entries)
            for (light in listOf(true, false)) {
                paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
                    screenWidth = 640, screenHeight = 360, density = Density.MEDIUM,
                    orientation = ScreenOrientation.LANDSCAPE))
                var checked = false
                var settingsCalls = 0
                var refreshCalls = 0
                var failure: Throwable? = null
                val host = ComposeView(paparazzi.context).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    setContent {
                        CompositionLocalProvider(LocalInspectionMode provides true) {
                            ZhishengWeatherTheme(isLight = light, interfaceStyle = interfaceStyle,
                                softGlow = false, homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS) {
                                LandscapeStandbyScreen(
                                    HomeUiState(selectedCity = City("金昌市", "离线夹具", 38.5, 102.2, "rotation-fixture"),
                                        weather = WeatherData(current = CurrentWeather(temperature = 24.0,
                                            condition = WeatherCondition.CLEAR), utcOffsetSeconds = 28_800)),
                                    clockStyle, { refreshCalls++ }, { settingsCalls++ })
                            }
                        }
                    }
                    viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            if (!checked) try {
                                val root = descendants(this@apply).firstNotNullOfOrNull { view ->
                                    view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                        ?.invoke(view) as? SemanticsOwner
                                }?.unmergedRootSemanticsNode ?: error("Missing clock semantics")
                                val all = nodes(root)
                                val labels = all.flatMap { node ->
                                    node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                                        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
                                        listOfNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.label)
                                }
                                assertFalse("Clock must not offer a local orientation lock",
                                    labels.any { it.contains("竖屏") || it.contains("恢复自动旋转") })
                                val clocks = all.filter { node ->
                                    node.config.getOrNull(SemanticsProperties.Text).orEmpty()
                                        .any { it.text.matches(Regex("\\d{2}:\\d{2}")) }
                                }
                                assertTrue("The actual clock must render", clocks.isNotEmpty())
                                assertTrue("Clock digits must not be an exit button",
                                    clocks.all { it.config.getOrNull(SemanticsActions.OnClick) == null })
                                val settings = all.first { node ->
                                    node.boundsInRoot.width > 0 && node.boundsInRoot.height > 0 &&
                                        node.config.getOrNull(SemanticsActions.OnClick) != null &&
                                        nodes(node).any { "设置" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }
                                }
                                assertTrue(settings.config[SemanticsActions.OnClick].action!!.invoke())
                                assertEquals(1, settingsCalls)
                                assertEquals(0, refreshCalls)
                                checked = true
                            } catch (error: Throwable) { failure = error; checked = true }
                            return true
                        }
                    })
                }
                paparazzi.snapshot(host, "system_clock_${interfaceStyle.key}_${clockStyle.key}_$light")
                failure?.let { throw AssertionError("Clock presentation $interfaceStyle / $clockStyle / $light", it) }
                assertTrue("Native clock check must execute", checked)
            }
    }

    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
