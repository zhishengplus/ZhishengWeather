package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTransitionTest {
    @Test
    fun settingsSitsToTheRightOfHomeAndSearchToTheLeft() {
        assertEquals(0, AppScreen.SEARCH.navSlot())
        assertEquals(1, AppScreen.HOME.navSlot())
        assertEquals(2, AppScreen.HISTORY.navSlot())
        assertEquals(3, AppScreen.RADAR.navSlot())
        assertEquals(4, AppScreen.SETTINGS.navSlot())
        assertEquals(5, AppScreen.ATMOSPHERE_LAB.navSlot())
        assertTrue(AppScreen.SETTINGS.navSlot() > AppScreen.HOME.navSlot())
        assertTrue(AppScreen.SEARCH.navSlot() < AppScreen.HOME.navSlot())
        assertTrue(AppScreen.ATMOSPHERE_LAB.navSlot() > AppScreen.SETTINGS.navSlot())
    }

    @Test
    fun mainActivityKeepsHomeMountedUnderOverlay() {
        val activity = sequenceOf(
            File("src/main/kotlin/com/zhisheng/weather/MainActivity.kt"),
            File("app/src/main/kotlin/com/zhisheng/weather/MainActivity.kt"),
        ).first { it.isFile }.readText()
        assertTrue(activity.contains("overlayEnter(overlayScreen)"))
        assertTrue(activity.contains("overlayExit(overlayScreen)"))
        assertTrue(activity.contains("screenTransition(initialState, targetState)"))
        assertTrue(activity.contains("onSettingsClick = { screen = AppScreen.SETTINGS }"))
        assertTrue(activity.contains("主屏始终留在下层"))
        assertTrue(!activity.contains("AppScreen.HOME -> HomeScreen"))
        assertTrue(!activity.contains("targetState == Screen.SEARCH"))
        assertTrue(!activity.contains("slideInHorizontally { -it / 3 }"))
    }
}
