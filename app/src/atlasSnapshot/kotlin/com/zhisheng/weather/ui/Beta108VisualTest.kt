package com.zhisheng.weather.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme
import org.junit.Rule
import org.junit.Test

class Beta108VisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")
    @Test fun welcomeLightAndDark() {
        for (light in listOf(true, false)) for (busy in listOf(false, true)) paparazzi.snapshot("welcome_${light}_busy_$busy") {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                ZhishengWeatherTheme(isLight = light, homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS) {
                    Box(Modifier.fillMaxSize().padding(16.dp)) {
                        LocationWelcomePanel({}, {}, {}, Modifier.fillMaxWidth().heightIn(max = 620.dp), busy = busy)
                    }
                }
            }
        }
    }
    @Test fun welcomeAtLargeTypeAndShortLandscape() {
        for ((width, height) in listOf(320 to 600, 640 to 240)) {
            paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
                screenWidth = width, screenHeight = height,
                density = com.android.resources.Density.MEDIUM, fontScale = 2f,
                orientation = if (width > height) com.android.resources.ScreenOrientation.LANDSCAPE else com.android.resources.ScreenOrientation.PORTRAIT))
            paparazzi.snapshot("welcome_large_${width}_$height") {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    ZhishengWeatherTheme(isLight = false, homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS) {
                        Box(Modifier.fillMaxSize().padding(12.dp)) {
                            LocationWelcomePanel({}, {}, {}, Modifier.fillMaxWidth().heightIn(max = (height - 24).dp))
                        }
                    }
                }
            }
        }
    }
    @Test fun manualCoordinateInput() { paparazzi.snapshot("manual_coordinates") {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            ZhishengWeatherTheme { ManualPlacePanel({}, {}, Modifier.fillMaxWidth().heightIn(max = 600.dp)) }
        }
    } }
    @Test fun historyRibbonEndPoints() {
        val end = java.time.LocalDate.of(2026, 10, 19)
        val week = com.zhisheng.weather.model.RecentWeatherWeek((0..6).map { index ->
            com.zhisheng.weather.model.HistoricalDay(end.minusDays((6 - index).toLong()).toString(),
                weatherCode = if (index % 2 == 0) 0 else 3, high = 23.0 + index,
                low = 12.0 + index, precipitationMm = 0.0, windMaxKmh = 12.0)
        })
        val host = androidx.compose.ui.platform.ComposeView(paparazzi.context).apply {
            setContent {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    ZhishengWeatherTheme(homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS, softGlow = false) {
                        VistaRecentHistory(week, "c", "kmh", end)
                    }
                }
            }
        }
        paparazzi.gif(host, "history_edges", end = 600, fps = 2)
    }
}
