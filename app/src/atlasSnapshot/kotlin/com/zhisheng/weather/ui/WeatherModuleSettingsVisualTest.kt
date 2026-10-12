package com.zhisheng.weather.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.data.*
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.theme.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Offline rendering of the actual module control, including missing evidence and large text. */
class WeatherModuleSettingsVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")
    private val now = Instant.parse("2026-10-09T06:34:40Z").toEpochMilli()
    private val complete = WeatherData(utcOffsetSeconds = 28800,
        moduleEvidence = WeatherModule.entries.associate { it.key to WeatherModuleEvidence("CAIYUN", now, now) })

    private fun capture(name: String, style: InterfaceStyle, light: Boolean, font: Float = 1f,
        width: Int = 360, policy: ModuleSourcePolicy = ModuleSourcePolicy(), weather: WeatherData? = complete) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(
            screenWidth = width * 3, screenHeight = 900 * 3, fontScale = font))
        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false) {
                        Column(Modifier.fillMaxSize().background(LocalZhishengPalette.current.bg)
                            .verticalScroll(rememberScrollState()).padding(18.dp)) {
                            VistaSettingsCard {
                                WeatherModuleSettingsContent(policy, SourcePref.visible(false).toSet(), weather, {}, { _ -> })
                            }
                        }
                    }
                }
            }
        }
        paparazzi.snapshot(host, name)
    }

    @Test fun moduleRowsMatchSettingsAtNormalAndLargeText() {
        for (style in InterfaceStyle.entries) for (light in listOf(true, false)) {
            capture("modules_${style.key}_${if (light) "light" else "dark"}", style, light)
        }
        for (style in InterfaceStyle.entries) {
            capture("modules_large_${style.key}", style, false, font = 1.5f, width = 320,
                policy = ModuleSourcePolicy().with(WeatherModule.CURRENT, SourcePref.XIAOMI)
                    .with(WeatherModule.FORECAST, SourcePref.OPEN_METEO))
        }
        capture("modules_missing", InterfaceStyle.PHOSPHOR_VISTA, true, weather = WeatherData())
        capture("modules_unavailable", InterfaceStyle.PHOSPHOR_VISTA, false,
            policy = ModuleSourcePolicy().with(WeatherModule.CURRENT, SourcePref.QWEATHER),
            weather = complete.copy(sourceDiagnostics = mapOf("current" to "指定来源不可用，保留主源实况")))
    }
}
