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
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.*
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.components.*
import com.zhisheng.weather.ui.theme.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** Native production content with deterministic offline data; no generated screenshot substitutes. */
class BetaFeedbackUiVisualTest {
    private val resultOwner = object : androidx.activity.result.ActivityResultRegistryOwner {
        override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
                input: I, options: androidx.core.app.ActivityOptionsCompat?) {}
        }
    }
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")
    private val now = Instant.parse("2026-10-09T03:00:00Z").toEpochMilli()
    private fun capture(name: String, style: InterfaceStyle, width: Int = 360, height: Int = 800,
        font: Float = 1f, content: @androidx.compose.runtime.Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width * 3,
            screenHeight = height * 3, fontScale = font,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent {
                CompositionLocalProvider(LocalInspectionMode provides true, LocalWeatherPreviewTime provides now,
                    LocalNaturalPreviewPhase provides .173,
                    androidx.activity.compose.LocalActivityResultRegistryOwner provides resultOwner) {
                    ZhishengWeatherTheme(isLight = false, interfaceStyle = style, softGlow = false) { content() }
                }
            }
        }
        paparazzi.snapshot(host, name)
    }
    @Test fun realHoursAndMissingHoursRenderAtNarrowAndShortSizes() {
        val city = City("金昌市", "离线验收数据", 38.5, 102.18, "feedback-native")
        val daily = listOf(DailyWeather(now, 22.0, 9.0, WeatherCondition.RAIN))
        val hours = (0..7).map { HourlyWeather(now + it * 3600_000L, if (it == 3) null else 14.0 + it) }
        capture("hours_gaps", InterfaceStyle.PHOSPHOR_VISTA) { VistaForecastScreen(city, daily, null, "c", "kmh", 28800, {}, hours) }
        capture("hours_missing_large_landscape", InterfaceStyle.PHOSPHOR_VISTA, 800, 360, 1.3f) { VistaForecastScreen(city, daily, null, "c", "kmh", 28800, {}, emptyList()) }
    }
    @Test fun classicAtmosphereAndRefreshSettingsRemainReadable() {
        for (style in InterfaceStyle.entries) capture("refresh_${style.key}", style, font = 1.15f) {
            Column(Modifier.fillMaxSize().background(LocalZhishengPalette.current.bg).verticalScroll(rememberScrollState())) { AutomationSettingsPanel() }
        }
        for (style in InterfaceStyle.entries) capture("refresh_large_${style.key}", style, width = 320, font = 1.5f) {
            Column(Modifier.fillMaxSize().background(LocalZhishengPalette.current.bg).verticalScroll(rememberScrollState())) { AutomationSettingsPanel() }
        }
        val weather = WeatherData(current = CurrentWeather(temperature = -5.0, condition = WeatherCondition.SNOW), fetchedAt = now, updateTime = now, utcOffsetSeconds = 28800)
        capture("classic_snow", InterfaceStyle.CLASSIC_TERMINAL) {
            NaturalWeatherSurface(weather, AmbienceLevel.VIVID, false, Modifier.fillMaxSize()) {
                Column(Modifier.padding(24.dp)) { Text("经典终端 · 雪", color = LocalZhishengPalette.current.text); Text("−5°", color = LocalZhishengPalette.current.text) }
            }
        }
    }
}
