package com.zhisheng.weather.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBriefingStyleTest {
    @Test
    fun textTipsAreDefaultAndWeatherGirlRemainsOptIn() {
        assertEquals(HomeBriefingStyle.TIPS, HomeBriefingStyle.from(null))
        assertEquals(HomeBriefingStyle.TIPS, HomeBriefingStyle.from("unknown"))
        assertEquals(HomeBriefingStyle.WEATHER_GIRL, HomeBriefingStyle.from("weather_girl"))
        assertEquals(HomeBriefingStyle.TIPS, HomeBriefingStyle.from("tips"))
        assertEquals(HomeBriefingStyle.OFF, HomeBriefingStyle.from("off"))
    }

    @Test
    fun settingsAndHomeExposeBothCompanionLayouts() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val settings = File(
            projectDir,
            "src/main/kotlin/com/zhisheng/weather/ui/SettingsScreen.kt",
        ).readText()
        val home = File(
            projectDir,
            "src/main/kotlin/com/zhisheng/weather/ui/home/HomeScreen.kt",
        ).readText()

        assertTrue(settings.contains("\"天气娘\" to \"weather_girl\""))
        assertTrue(settings.contains("(if (isPhosphorVista) \"文字提示\" else \"简洁 Tips\") to \"tips\""))
        assertTrue(settings.contains("\"关闭\" to \"off\""))
        assertTrue(home.contains("HomeBriefingStyle.WEATHER_GIRL ->"))
        assertTrue(home.contains("HomeBriefingStyle.TIPS ->"))
        assertTrue(home.contains("HomeBriefingStyle.OFF -> Unit"))
        assertTrue(home.contains("text = if (isPhosphorVista) \"天气提示\" else \"TIPS //\""))
        assertTrue(home.contains("maxLines = if (copy.detail == null) 2 else 1"))
        assertTrue(home.contains("不能把关键动作截成省略号"))
    }
}
