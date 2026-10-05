package com.zhisheng.weather.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme
import org.junit.Rule
import org.junit.Test

/** Native Compose content, not an HTML approximation or a generated mockup. */
class WhatsNewVisualTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1125, screenHeight = 2436),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private fun capture(name: String, light: Boolean, style: InterfaceStyle = InterfaceStyle.PHOSPHOR_VISTA,
                        category: ReleaseNotesCategory = ReleaseNotesCategory.ALL,
                        width: Int = 375, height: Int = 812, fontScale: Float = 1f) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = width * 3, screenHeight = height * 3, fontScale = fontScale,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        val view = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setContent {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false) {
                    Box(Modifier.fillMaxSize().background(if (light) Color(0xFFCDD6DD) else Color(0xFF080D14))) {
                        WhatsNewDialogContent({}, initialCategory = category, animateEntrance = false)
                    }
                }
            }
        }
        paparazzi.snapshot(view, name)
    }

    @Test fun overviewMatchesBothStylesAndThemes() {
        for (style in InterfaceStyle.entries) for (light in listOf(true, false))
            capture("overview_${style.name.lowercase()}_${if (light) "light" else "dark"}", light, style)
    }

    @Test fun eachReadingCategoryHasANativePreview() {
        for (category in ReleaseNotesCategory.entries.filter { it != ReleaseNotesCategory.ALL })
        for (light in listOf(true, false))
            capture("category_${category.name.lowercase()}_${if (light) "light" else "dark"}", light, category = category)
    }

    @Test fun smallScreensLargeFontsLandscapeAndTablet() {
        for (light in listOf(true, false)) {
            capture("small_font_150_${if (light) "light" else "dark"}", light, width = 320, height = 640, fontScale = 1.5f)
            capture("small_font_200_${if (light) "light" else "dark"}", light, width = 320, height = 640, fontScale = 2f)
            capture("landscape_${if (light) "light" else "dark"}", light, width = 800, height = 360, fontScale = 1.3f)
        }
        capture("tablet", true, width = 768, height = 1024)
    }
}
