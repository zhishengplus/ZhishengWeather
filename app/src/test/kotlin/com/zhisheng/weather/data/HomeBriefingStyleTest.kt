package com.zhisheng.weather.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeBriefingStyleTest {
    @Test
    fun weatherGirlRemainsDefaultAndTipsIsOptional() {
        assertEquals(HomeBriefingStyle.WEATHER_GIRL, HomeBriefingStyle.from(null))
        assertEquals(HomeBriefingStyle.WEATHER_GIRL, HomeBriefingStyle.from("unknown"))
        assertEquals(HomeBriefingStyle.TIPS, HomeBriefingStyle.from("tips"))
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
        assertTrue(settings.contains("\"简洁 Tips\" to \"tips\""))
        assertTrue(home.contains("HomeBriefingStyle.WEATHER_GIRL ->"))
        assertTrue(home.contains("HomeBriefingStyle.TIPS ->"))
        assertTrue(home.contains("text = \"TIPS //\""))
        assertTrue(home.contains("maxLines = if (copy.detail == null) 2 else 1"))
        assertTrue(home.contains("不能把关键动作截成省略号"))
    }
}
