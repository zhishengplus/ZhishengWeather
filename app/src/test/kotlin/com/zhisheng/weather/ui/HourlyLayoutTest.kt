package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HourlyLayoutTest {
    @Test
    fun missingWeatherIconStillKeepsFixedSlotForContinuousCurve() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val home = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt").readText()

        assertTrue(home.contains("Box(Modifier.size(24.dp), contentAlignment = Alignment.Center)"))
        assertTrue(home.contains("WeatherIcon(visualCondition, Modifier.fillMaxSize())"))
        assertTrue(home.contains("visualCondition = phaseAwareCondition(h.condition, data, h.timeMillis)"))
        assertTrue(home.contains("WeatherConsistency.currentHourIndex(hourly, nowMillis)"))
        assertTrue(home.contains("LocalDensity.current.fontScale.coerceIn(1f, 1.5f)"))
        assertTrue(home.contains("contentPadding = PaddingValues(horizontal = 4.dp)"))
        assertTrue(home.contains("hourlyOutlookText(data.current, data.hourly, nowMs)"))
        assertTrue(home.contains("hourlyDisplayItems(data.current, data.hourly, nowMs)"))
        assertTrue(home.contains("Fmt.windForce(h.windSpeed)"))
        assertFalse(home.contains("\u0024{hourly.size}小时"))
        assertFalse(home.contains("\u0024{hourly.size}H"))
        assertFalse(home.contains("viewModel.selectCity(target.locationKey)"))
        assertTrue(home.contains("statusText,"))
        assertTrue(home.contains("if (dry) sourceLine else peakLabel"))
    }
}
