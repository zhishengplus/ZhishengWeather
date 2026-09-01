package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindCompassLayoutTest {
    @Test
    fun windCompassStaysInlineWithOtherTelemetryCards() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val compass = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt")
            .readText()
            .substringAfter("private fun WindCompass")
            .substringBefore("private fun TeleLabel")

        assertTrue(compass.contains(".size(22.dp)"))
        assertFalse(compass.contains("38.dp"))
        assertFalse(compass.contains("drawOval"))
        assertTrue(compass.contains("drawPath(dart, needle)"))
        assertTrue(compass.contains("北=0°向上"))
    }
}
