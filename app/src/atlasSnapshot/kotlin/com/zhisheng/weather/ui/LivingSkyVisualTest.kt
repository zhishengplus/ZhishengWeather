package com.zhisheng.weather.ui

import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.ui.components.LocalNaturalPreviewPhase
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme
import org.junit.Rule
import org.junit.Test

class LivingSkyVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1125, screenHeight = 2436),
        theme = "android:Theme.Material.Light.NoActionBar")

    private fun capture(name: String, light: Boolean, moment: Int = 3, condition: Int = 1,
        width: Int = 375, height: Int = 812, fontScale: Float = 1f) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width * 3,
            screenHeight = height * 3, fontScale = fontScale,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        val view = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setContent {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = InterfaceStyle.PHOSPHOR_VISTA, softGlow = false) {
                    CompositionLocalProvider(LocalInspectionMode provides true, LocalNaturalPreviewPhase provides .173) {
                        LivingSkyPreviewContent({}, Modifier.fillMaxSize(), moment, condition)
                    }
                }
            }
        }
        paparazzi.snapshot(view, name)
    }

    @Test fun allSixTimesInBothThemes() {
        for (light in listOf(true, false)) for (moment in skyPreviewMoments.indices)
            capture("moment_${moment}_${if (light) "light" else "dark"}", light, moment)
    }

    @Test fun rainFogAndLargeFontRemainReadable() {
        for (light in listOf(true, false)) {
            capture("rain_${if (light) "light" else "dark"}", light, condition = 2)
            capture("fog_${if (light) "light" else "dark"}", light, condition = 3)
            capture("font_200_${if (light) "light" else "dark"}", light, width = 320, height = 640, fontScale = 2f)
            capture("landscape_${if (light) "light" else "dark"}", light, width = 800, height = 360, fontScale = 1.3f)
        }
    }
}
