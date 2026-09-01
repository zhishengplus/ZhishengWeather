package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapeStandbyScreenTest {
    @Test
    fun landscapeUsesDedicatedStandbyScreenAndCanBeDisabled() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val activity = File(projectDir, "src/main/kotlin/com/zhisheng/weather/MainActivity.kt").readText()
        val screen = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/LandscapeStandbyScreen.kt").readText()
        val weatherCore = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/LandscapeWeatherCoreScreen.kt").readText()
        val settings = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/SettingsScreen.kt").readText()

        assertTrue(activity.contains("LandscapeStandbyScreen"))
        assertTrue(activity.contains("val standbyVisible = standbyActive && screen == AppScreen.HOME"))
        assertTrue(activity.contains("onSettings = { screen = AppScreen.SETTINGS }"))
        assertTrue(activity.contains("SCREEN_ORIENTATION_PORTRAIT"))
        assertTrue(activity.contains("SCREEN_ORIENTATION_SENSOR"))
        assertTrue(activity.contains("portraitSession"))
        assertTrue(activity.contains("PortraitSessionNotice(onRestore = { portraitSession = false })"))
        assertTrue(settings.contains("landscapePortraitLocked"))
        assertTrue(settings.contains("> 恢复自动旋转"))
        assertTrue(activity.contains("onExitLandscape"))
        assertTrue(screen.contains("ZHISHENG AMBIENT TERMINAL"))
        assertTrue(screen.contains("BuildConfig.VERSION_NAME"))
        assertTrue(screen.contains("ClassicLandscapeStandbyScreen"))
        assertTrue(screen.contains("LandscapeStandbyStyle.WEATHER_CORE"))
        assertTrue(weatherCore.contains("ZHISHENG WEATHER CORE / ${'$'}{BuildConfig.VERSION_NAME}"))
        assertTrue(weatherCore.contains("WeatherCoreSunTrack"))
        assertTrue(weatherCore.contains("WeatherVectorGraph"))
        assertTrue(weatherCore.contains("sunTrackProgress"))
        assertTrue(screen.contains("todayDaily(nowMillis)"))
        assertTrue(screen.contains("onSettings: () -> Unit"))
        assertTrue(screen.contains("Icons.Default.Settings"))
        assertTrue(screen.contains("StandbyPortraitButton"))
        assertTrue(weatherCore.contains("StandbyPortraitButton"))
        assertTrue(settings.contains("横屏待机界面"))
        assertTrue(settings.contains("Configuration.ORIENTATION_LANDSCAPE"))
        assertTrue(settings.contains("LandscapeSettingsRail"))
        assertTrue(settings.contains("LazyColumn"))
        assertTrue(settings.contains("itemsIndexed(sections)"))
        assertTrue(settings.contains("Modifier.fillMaxWidth().height(44.dp)"))
        assertTrue(settings.contains("modifier = Modifier.fillMaxWidth().weight(1f)"))
        assertTrue(settings.contains("经典终端"))
        assertTrue(settings.contains("气象中枢"))
    }
}
