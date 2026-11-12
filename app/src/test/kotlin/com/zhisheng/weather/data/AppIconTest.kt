package com.zhisheng.weather.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class AppIconTest {
    @Test fun defaultAndOldIconPreferencesResolveToNewDefault() {
        for (value in listOf(null, "unknown", "minimal", "classic", "dark", "light", "default"))
            assertEquals(AppIconStyle.DEFAULT, AppIconStyle.from(value))
        assertEquals(AppIconStyle.CHARACTER, AppIconStyle.from("character"))
        assertEquals(listOf("默认" to "default", "天气娘" to "character"), AppIconStyle.entries.map { it.cn to it.key })
    }

    @Test fun previouslyPinnedLegacyEntryCanContinueAsTheNewDefault() {
        val prefix = "com.zhisheng.weather."
        assertEquals(prefix + "IconCharacter", AppIconManager.aliasFor(AppIconStyle.DEFAULT, emptySet()))
        for (name in listOf("IconMinimal", "IconClassic", "IconLight")) {
            val old = prefix + name
            assertEquals(old, AppIconManager.aliasFor(AppIconStyle.DEFAULT, setOf(old)))
            assertEquals(prefix + "IconWeatherGirl", AppIconManager.aliasFor(AppIconStyle.CHARACTER, setOf(old)))
        }
    }

    @Test fun onlyOneLauncherAliasIsEnabledForFreshInstallAndUpgradeKeepsLegacyEntries() {
        val dir = File(requireNotNull(System.getProperty("user.dir")))
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File(dir, "src/main/AndroidManifest.xml"))
        val ns = "http://schemas.android.com/apk/res/android"
        val aliases = document.getElementsByTagName("activity-alias")
        val elements = (0 until aliases.length).map { aliases.item(it) as Element }
        val byName = elements.associateBy { it.getAttributeNS(ns, "name") }
        assertEquals(setOf(".IconCharacter", ".IconWeatherGirl", ".IconMinimal", ".IconClassic", ".IconLight"), byName.keys)
        assertEquals(listOf(".IconCharacter"), elements.filter { it.getAttributeNS(ns, "enabled") == "true" }
            .map { it.getAttributeNS(ns, "name") })
        for (name in listOf(".IconCharacter", ".IconMinimal", ".IconClassic", ".IconLight")) {
            assertEquals("@mipmap/zw_launcher", byName.getValue(name).getAttributeNS(ns, "icon"))
            assertEquals("@mipmap/zw_launcher_round", byName.getValue(name).getAttributeNS(ns, "roundIcon"))
        }
        assertEquals("@mipmap/ic_launcher_character", byName.getValue(".IconWeatherGirl").getAttributeNS(ns, "icon"))
        val receivers = document.getElementsByTagName("receiver")
        val upgrade = (0 until receivers.length).map { receivers.item(it) as Element }
            .single { it.getAttributeNS(ns, "name") == ".data.AppIconUpgradeReceiver" }
        assertEquals("false", upgrade.getAttributeNS(ns, "exported"))
        assertTrue((0 until upgrade.getElementsByTagName("action").length).any {
            (upgrade.getElementsByTagName("action").item(it) as Element).getAttributeNS(ns, "name") == "android.intent.action.MY_PACKAGE_REPLACED"
        })
    }
}
