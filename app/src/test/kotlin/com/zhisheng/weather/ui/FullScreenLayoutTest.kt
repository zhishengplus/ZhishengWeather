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
        assertTrue(home.contains("val tickLabels = listOf(\"现在\""))
        assertTrue(home.contains("precipCardClearWindow"))
        assertTrue(home.contains("WeatherConsistency.currentHourIndex"))
        assertTrue(home.contains("Fmt.stamp"))
        assertTrue(home.contains("Fmt.zoneId(data.utcOffsetSeconds)"))
        assertTrue(home.contains("%.1f mm/h"))
        assertTrue(home.contains("未来雨势暂缺"))
        assertTrue(home.contains("if (dry) sourceLine else peakLabel"))
        assertTrue(home.contains("Fmt.coordinates(it.latitude, it.longitude)"))
        assertTrue(home.contains("textAlign = TextAlign.End"))
        assertTrue(home.contains("SRC \${dataSourceShortLabel(data.dataSource)}"))
        assertTrue(home.contains("widthIn(max = 720.dp)"))
        assertFalse(home.contains("\"\$updText // SRC"))
    }
}
