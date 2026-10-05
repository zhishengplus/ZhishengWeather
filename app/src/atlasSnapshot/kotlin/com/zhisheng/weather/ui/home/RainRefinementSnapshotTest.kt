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

/** Native visual review fixtures; synthetic data, never included in the APK. */
class RainRefinementSnapshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = 1125, screenHeight = 2436),
        theme = "android:Theme.Material.Light.NoActionBar")
    @Test fun rainAndCompactCard() {
        val scenario = atmosphereScenarios.first { it.code == "RAN" }
        val rain = simulatedWeather(scenario, WeatherIntensity.MODERATE)
        val now = requireNotNull(rain.updateTime)
        val data = rain.copy(rainHistory = (0..7).map { MinutePrecip(now - (8 - it) * 900_000L, .2f + it * .1f) },
            rainHistorySource = "OPEN-METEO")
        listOf(false, true).forEach { light ->
            listOf(.173, .177).forEach { phase ->
                val host = ComposeView(paparazzi.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setContent {
                        CompositionLocalProvider(LocalInspectionMode provides true, LocalWeatherPreviewTime provides now,
                            LocalNaturalPreviewPhase provides phase) {
                            ZhishengWeatherTheme(isLight = light) {
                                NaturalWeatherSurface(data, AmbienceLevel.VIVID, false, Modifier.fillMaxSize()) {
                                    Column(Modifier.fillMaxSize()) {
                                        Spacer(Modifier.height(160.dp))
                                        Text("19°", Modifier.padding(24.dp), color = ZhishengText,
                                            style = androidx.compose.material3.MaterialTheme.typography.displayLarge)
                                        Spacer(Modifier.height(150.dp))
                                        RainLandingTarget(true) { PrecipCard(data, Modifier) }
                                    }
                                }
                            }
                        }
                    }
                }
                paparazzi.snapshot(host, name = "rain_${light}_${phase}", offsetMillis = 1000)
            }
        }
    }
}
