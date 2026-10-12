package com.zhisheng.weather.ui

import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme
import org.junit.Rule
import org.junit.Test

/** Review frames of the native opening; each frame is the production stage at a fixed time. */
class UpdateOpeningFramesTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1080, screenHeight = 2340),
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private val spec = UpdateOpeningSpec(version = "10.4", previousVersion = "10.3", channel = "BETA",
        sectionCount = 6, noteCount = 30)

    private fun frame(name: String, t: Float, light: Boolean, style: InterfaceStyle = InterfaceStyle.PHOSPHOR_VISTA,
                      width: Int = 360, height: Int = 780) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = width * 3, screenHeight = height * 3,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        val view = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setContent {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false) {
                    UpdateOpeningStage(spec, { t })
                }
            }
        }
        paparazzi.snapshot(view, name)
    }

    @Test fun vistaLightTimeline() {
        for (t in 0..5000 step 100) frame("vista_light_${t.toString().padStart(4, '0')}", t.toFloat(), light = true)
    }

    @Test fun vistaDarkKeyFrames() {
        for (t in listOf(900, 1800, 2400, 3300, 4000)) frame("vista_dark_$t", t.toFloat(), light = false)
    }

    @Test fun classicKeyFrames() {
        for (t in listOf(900, 1800, 2400, 4000)) frame("classic_dark_$t", t.toFloat(), light = false,
            style = InterfaceStyle.CLASSIC_TERMINAL)
    }

    @Test fun landscapeAndSmallScreen() {
        frame("landscape_light_4000", 4000f, light = true, width = 800, height = 360)
        frame("small_light_4000", 4000f, light = true, width = 320, height = 568)
    }
}
