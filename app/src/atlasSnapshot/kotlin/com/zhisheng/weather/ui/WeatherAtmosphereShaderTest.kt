package com.zhisheng.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.ui.components.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Compile and bind the actual imported shaders, even though ordinary layout previews skip AGSL. */
class WeatherAtmosphereShaderTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5.copy(
        screenWidth = 180, screenHeight = 240, density = Density.MEDIUM),
        theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun rainVeilDrawsAndAllGlassWeatherShadersBindTheirRealTexturesAndUniforms() {
        val phase = mutableStateOf(.173)
        for ((kind, freezing) in listOf(NaturalWeather.RAIN to false,
            NaturalWeather.SNOW to false, NaturalWeather.RAIN to true)) {
            val state = NaturalLightState(kind, .7f, 0f, .78f, .85f, .2f, freezing)
            val frame = NaturalSkyFrame(state, naturalLightPalette(state, false))
            val environment = mutableStateOf<WeatherGlassEnvironment?>(WeatherGlassEnvironment(
                frame = { frame }, phase = phase, active = true,
                quality = SkyRenderQuality.HIGH, budget = SkyRenderQuality.HIGH,
                level = AmbienceLevel.INTENSE, paging = { false }))
            val glass = WeatherGlassAtmosphere(paparazzi.context.resources, environment)
            assertNotNull("Actual $kind/freezing=$freezing shader and uniforms must bind without fallback",
                glass.effect(180f, 240f, null))
        }
        val rain = RainVeil()
        val state = NaturalLightState(NaturalWeather.RAIN, .7f, 0f, .78f, .85f, .2f, false)
        val frame = NaturalSkyFrame(state, naturalLightPalette(state, false))
        var drew = false
        var succeeded = false
        paparazzi.snapshot(name = "actual_rain_veil_shader") {
            val layer = rememberGraphicsLayer()
            Canvas(Modifier.fillMaxSize().background(Color(0xFF243949))) {
                succeeded = rain.drawCached(this, layer, frame, .173, 1f,
                    size.height, SkyRenderQuality.HIGH, moving = true)
                drew = true
            }
        }
        assertTrue("The real distant rain shader must execute a native draw", drew)
        assertTrue("The real distant rain draw must not take its failure fallback", succeeded)
    }
}
