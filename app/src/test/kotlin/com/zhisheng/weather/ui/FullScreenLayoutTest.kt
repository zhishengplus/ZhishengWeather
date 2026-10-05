package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullScreenLayoutTest {
    @Test
    fun homeSurfaceExtendsBehindGestureNavigationArea() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val activity = File(projectDir, "src/main/kotlin/com/zhisheng/weather/MainActivity.kt").readText()
        val theme = File(projectDir, "src/main/res/values/themes.xml").readText()
        val home = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt").readText()

        assertTrue(activity.contains("WindowCompat.setDecorFitsSystemWindows(window, false)"))
        assertTrue(activity.contains("window.navigationBarColor = android.graphics.Color.TRANSPARENT"))
        assertTrue(activity.contains("window.isNavigationBarContrastEnforced = false"))
        assertTrue(theme.contains("<item name=\"android:navigationBarColor\">@android:color/transparent</item>"))
        assertFalse(home.contains("CLEAR SIGNAL"))
        assertFalse(home.contains("CLEAR WINDOW"))
        val current = com.zhisheng.weather.model.CurrentWeather(condition = com.zhisheng.weather.model.WeatherCondition.RAIN)
        val precipitation = precipitationPresentation(com.zhisheng.weather.model.WeatherData(current = current), 1_800_000_000_000L)
        assertFalse(precipitation.dry)
        assertTrue(precipitation.points.isEmpty())
        assertTrue(precipitation.summary.contains("当前"))
        assertTrue(home.contains("Fmt.stamp"))
        assertTrue(home.contains("Fmt.zoneId(data.utcOffsetSeconds)"))
        // Number formatting and the displayed unit may live in separate string fragments.
        assertTrue(home.contains("当前雨强"))
        assertTrue(home.contains(" mm/h"))
        assertTrue(home.contains("Fmt.coordinates(it.latitude, it.longitude, precise = it.isPreciseLocation)"))
        assertTrue(home.contains("textAlign = TextAlign.End"))
        assertTrue(home.contains("SRC \${dataSourceShortLabel(data.dataSource)}"))
        assertTrue(home.contains("data.locationMatch?.preciseGps == true"))
        assertTrue(home.contains("widthIn(max = chrome.contentMaxWidth)"))
        assertFalse(home.contains("\"\$updText // SRC"))
    }
}
