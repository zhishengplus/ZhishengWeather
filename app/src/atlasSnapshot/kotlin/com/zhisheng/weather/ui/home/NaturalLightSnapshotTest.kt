package com.zhisheng.weather.ui.home

import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.*
import com.zhisheng.weather.ui.components.*
import com.zhisheng.weather.ui.theme.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Production surface with explicitly labelled offline scenarios. */
@RunWith(Parameterized::class)
class NaturalLightSnapshotTest(private val scenarioCode: String, private val light: Boolean) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}_{1}")
        fun configurations() = listOf("CLR", "NIT", "PCL", "PCN", "OVC", "OVCN", "DRZ", "RAN", "STM", "SNW", "SLT",
            "HAL", "FZR", "FZD", "FOG", "HAZ", "SND", "WND", "DAWN", "DUSK", "UNKNOWN", "OFF", "SCROLLED")
            .flatMap { code -> listOf(arrayOf<Any>(code, true), arrayOf<Any>(code, false)) }
    }
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1125, screenHeight = 2436),
        theme = "android:Theme.Material.Light.NoActionBar")
    @Test fun atmosphere() {
        val scenario = atmosphereScenarios.first { it.code == if (scenarioCode in setOf("OFF", "SCROLLED")) "SNW" else scenarioCode }
        val data = simulatedWeather(scenario, if (scenarioCode in setOf("SNW", "RAN", "STM")) WeatherIntensity.HEAVY else WeatherIntensity.LIGHT)
        val city = City("金昌市", "离线场景预览", 38.5, 102.18, "natural-light-fixture")
        val level = if (scenarioCode == "OFF") AmbienceLevel.OFF else AmbienceLevel.VIVID
        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setContent {
                CompositionLocalProvider(LocalInspectionMode provides true, LocalWeatherPreviewTime provides data.updateTime,
                    LocalNaturalPreviewPhase provides 0.173) {
                    ZhishengWeatherTheme(isLight = light) {
                        if (scenarioCode == "SCROLLED") NaturalWeatherSurface(data, level, scenario.night, Modifier.fillMaxSize(), parallax = { 1600f }) {
                            Column {
                                Text("空气质量", Modifier.padding(24.dp), color = LocalZhishengPalette.current.text)
                                AqiCard(data.aqi!!, Modifier)
                            }
                        } else SimulatedWeatherSurface(data, city,
                            DisplayPrefs(ambience = level, scanlines = false, bootAnim = false, showSpacetime = false,
                                showSkyPhotography = false, showCoastWeather = false),
                            night = scenario.night, referenceTimeMillis = data.updateTime,
                            header = { TopBar(city.name, false, {}, {}, {}) })
                    }
                }
            }
        }
        paparazzi.snapshot(host)
    }
}
