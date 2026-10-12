package com.zhisheng.weather.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.android.resources.ScreenOrientation
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.data.*
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.theme.*
import org.junit.Rule
import org.junit.Test

/** Renders the real source window, including scrolling constraints and larger system text. */
class WeatherSourceManagerVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")
    private val policy = ModuleSourcePolicy().with(WeatherModule.CURRENT, SourcePref.CAIYUN)
    private val complete = WeatherData(utcOffsetSeconds = 28800,
        moduleEvidence = WeatherModule.entries.associate { it.key to WeatherModuleEvidence("CAIYUN", 1791527680000, 1791527680000) })

    private fun capture(name: String, style: InterfaceStyle = InterfaceStyle.PHOSPHOR_VISTA,
        light: Boolean = false, font: Float = 1f, width: Int = 360, height: Int = 800,
        surface: HomeSurfaceStyle = HomeSurfaceStyle.CURRENT,
        navigation: WeatherSourceNavigation = WeatherSourceNavigation(), available: Set<SourcePref> = SourcePref.entries.toSet()) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = width * 3, screenHeight = height * 3, fontScale = font,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false, homeSurfaceStyle = surface) {
                        Box(Modifier.fillMaxSize().background(LocalZhishengPalette.current.bg).padding(16.dp),
                            contentAlignment = if (height > width) Alignment.BottomCenter else Alignment.Center) {
                            WeatherSourceManagerPanel(SourcePref.CAIYUN, policy, complete, SourcePref.visible(true).toSet(),
                                available, navigation, onNavigate = {}, onSelect = {}, onBack = {}, onDone = {},
                                onCompare = {}, onManageProviders = {},
                                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = (height - 48).dp.coerceAtMost(700.dp)))
                        }
                    }
                }
            }
        }
        paparazzi.snapshot(host, name)
    }

    @Test fun sourceWindowKeepsItsHierarchyAcrossThemesAndScreenSizes() {
        for (style in InterfaceStyle.entries) for (light in listOf(true, false)) {
            capture("source_${style.key}_${if (light) "light" else "dark"}", style, light)
        }
        capture("source_modules", navigation = WeatherSourceNavigation(true))
        capture("source_current", navigation = WeatherSourceNavigation(true, WeatherModule.CURRENT))
        capture("source_daily_capability", navigation = WeatherSourceNavigation(true, WeatherModule.FORECAST))
        capture("source_large", font = 1.5f, width = 320, navigation = WeatherSourceNavigation(true, WeatherModule.CURRENT))
        capture("source_landscape", font = 1.3f, width = 800, height = 360)
        capture("source_unavailable", navigation = WeatherSourceNavigation(true, WeatherModule.CURRENT),
            available = SourcePref.visible(false).toSet())
        capture("source_glass_light", light = true, surface = HomeSurfaceStyle.FRAGRANCE_GLASS)
        capture("source_glass_dark", surface = HomeSurfaceStyle.FRAGRANCE_GLASS)
        capture("source_glass_modules", light = true, surface = HomeSurfaceStyle.FRAGRANCE_GLASS,
            navigation = WeatherSourceNavigation(true))
        capture("source_glass_large", light = true, font = 1.5f, width = 320, surface = HomeSurfaceStyle.FRAGRANCE_GLASS,
            navigation = WeatherSourceNavigation(true, WeatherModule.CURRENT))
        capture("source_glass_landscape", light = true, font = 1.3f, width = 800, height = 360,
            surface = HomeSurfaceStyle.FRAGRANCE_GLASS)
    }
}
