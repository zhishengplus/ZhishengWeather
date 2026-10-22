package com.zhisheng.weather.ui

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.data.SoftGlowLevel
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherIntensity
import com.zhisheng.weather.ui.components.LocalNaturalPreviewPhase
import com.zhisheng.weather.ui.home.SimulatedWeatherSurface
import com.zhisheng.weather.ui.home.TopBar
import com.zhisheng.weather.ui.theme.LocalVistaGlowPhase
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual NaturalWeatherSurface + hero + glass modules, with fixed offline weather and time.
 * Layoutlib renders the Canvas compatibility sky; AGSL atmosphere needs device verification. */
class FullAtmosphereVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5.copy(
        screenWidth = 390, screenHeight = 1000, density = Density.MEDIUM),
        theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun sunnyRainyAndNightHomesKeepTheirWeatherArtworkGlowAndGlass() {
        for (light in listOf(true, false)) for (code in listOf("CLR", "RAN", "NIT")) {
            val scenario = atmosphereScenarios.first { it.code == code }
            val sample = simulatedWeather(scenario, WeatherIntensity.MODERATE)
            val fixedTime = java.time.LocalDate.of(2026, 10, 18)
                .atTime(scenario.minuteOfDay / 60, scenario.minuteOfDay % 60)
                .toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli()
            val delta = fixedTime - requireNotNull(sample.updateTime)
            val data = sample.copy(updateTime = fixedTime, fetchedAt = fixedTime,
                hourly = sample.hourly.map { it.copy(timeMillis = it.timeMillis + delta) },
                daily = sample.daily.map { it.copy(dateMillis = it.dateMillis + delta) },
                rainMinutes = sample.rainMinutes.map { it.copy(timeMillis = it.timeMillis + delta) },
                rainMeta = sample.rainMeta?.copy(updateTime = fixedTime))
            val city = City("金昌市", "离线视觉夹具", 38.5, 102.18, "full-atmosphere-$code")
            var checked = false
            var failure: Throwable? = null
            val host = ComposeView(paparazzi.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                setContent {
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        ZhishengWeatherTheme(isLight = light, interfaceStyle = InterfaceStyle.PHOSPHOR_VISTA,
                            homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS,
                            softGlow = true, softGlowLevel = SoftGlowLevel.FLOWING) {
                            CompositionLocalProvider(LocalNaturalPreviewPhase provides .173,
                                LocalVistaGlowPhase provides mutableStateOf(.173)) {
                                SimulatedWeatherSurface(data, city, DisplayPrefs(
                                    ambience = AmbienceLevel.INTENSE, bootAnim = false,
                                    showSpacetime = false, showSkyPhotography = false, showCoastWeather = false),
                                    night = scenario.night, referenceTimeMillis = fixedTime, livingSkyOverride = true,
                                    header = { TopBar(city.displayName, false, {}, {}, {}) })
                            }
                        }
                    }
                }
                viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        if (!checked) try {
                            val root = descendants(this@apply).firstNotNullOfOrNull { view ->
                                view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                    ?.invoke(view) as? SemanticsOwner
                            }?.unmergedRootSemanticsNode ?: error("Missing complete weather semantics")
                            val visible = nodes(root).filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
                            val text = visible.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.map { it.text }
                            assertTrue("The real city header must render", text.any { "金昌" in it })
                            assertTrue("The real hero reading must render", text.any { it == "19" })
                            assertTrue("The weather label must survive the renderer changes", scenario.name in text)
                            assertTrue("Actual forecast glass content must render", text.any { "逐时预报" in it })
                            checked = true
                        } catch (error: Throwable) { failure = error; checked = true }
                        return true
                    }
                })
            }
            paparazzi.snapshot(host, "full_atmosphere_${code}_$light", offsetMillis = 1_000)
            failure?.let { throw AssertionError("Complete offline home $code / light=$light", it) }
            assertTrue("Complete native weather scene must execute", checked)
        }
    }

    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
