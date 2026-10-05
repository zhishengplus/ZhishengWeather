package com.zhisheng.weather.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconTest {
    @Test
    fun characterIconIsTheDefaultPreference() {
        assertEquals(AppIconStyle.CHARACTER, AppIconStyle.from(null))
        assertEquals(AppIconStyle.CHARACTER, AppIconStyle.from("unknown"))
        assertEquals(AppIconStyle.DARK, AppIconStyle.from("classic"))
        assertEquals(AppIconStyle.DARK, AppIconStyle.from("dark"))
        assertEquals(AppIconStyle.LIGHT, AppIconStyle.from("light"))
    }

    @Test
    fun launcherAliasesAreMutuallyConfiguredAndSettingsExposeBothChoices() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val manifest = File(projectDir, "src/main/AndroidManifest.xml").readText()
        val settings = File(
            projectDir,
            "src/main/kotlin/com/zhisheng/weather/ui/SettingsScreen.kt",
        ).readText()

        assertTrue(manifest.contains("android:name=\".IconCharacter\""))
        assertTrue(manifest.contains("android:name=\".IconClassic\""))
        assertTrue(manifest.contains("android:name=\".IconLight\""))
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher_character\""))
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher_light\""))
        assertTrue(manifest.contains("android:targetActivity=\".MainActivity\""))
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(projectDir, "src/main/AndroidManifest.xml"))
        val activities = document.getElementsByTagName("activity")
        val main = (0 until activities.length).map { activities.item(it) as org.w3c.dom.Element }
            .first { it.getAttribute("android:name") == ".MainActivity" }
        assertEquals(0, main.getElementsByTagName("category").length)
        assertTrue(settings.contains("\"应用图标\""))
        assertTrue(settings.contains("\"天气娘\" to \"character\""))
        assertTrue(settings.contains("\"深色\" to \"dark\""))
        assertTrue(settings.contains("\"浅色\" to \"light\""))
        assertTrue(settings.contains("AppIconManager.apply(context, selected)"))
    }

    @Test
    fun nonCharacterIconsFollowTheResolvedTheme() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val activity = File(
            projectDir,
            "src/main/kotlin/com/zhisheng/weather/MainActivity.kt",
        ).readText()

        assertTrue(activity.contains("selectedIcon != AppIconStyle.CHARACTER"))
        assertTrue(activity.contains("if (isLight) AppIconStyle.LIGHT else AppIconStyle.DARK"))
        assertTrue(activity.contains("SettingsRepository.setAppIconStyle(themedIcon)"))
    }
}
