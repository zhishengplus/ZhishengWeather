package com.zhisheng.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class Beta7VisualPolishTest {
    private fun source(path: String): String = sequenceOf(File(path), File("app/$path"))
        .first { it.exists() }.readText()

    @Test fun launcherShortcutsKeepSeparateEntries() {
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(source("src/main/res/xml/shortcuts.xml").byteInputStream())
        val shortcuts = document.getElementsByTagName("shortcut")
        val android = "http://schemas.android.com/apk/res/android"
        val actions = mutableSetOf<String>()
        val ids = mutableSetOf<String>()
        for (i in 0 until shortcuts.length) {
            val shortcut = shortcuts.item(i) as org.w3c.dom.Element
            ids += shortcut.getAttributeNS(android, "shortcutId")
            val icon = shortcut.getAttributeNS(android, "icon").removePrefix("@drawable/")
            assertTrue(sequenceOf(File("src/main/res/drawable/$icon.xml"), File("app/src/main/res/drawable/$icon.xml"))
                .any(File::exists))
            val intent = shortcut.getElementsByTagName("intent").item(0) as org.w3c.dom.Element
            actions += intent.getAttributeNS(android, "action")
            assertEquals("com.zhisheng.weather.MainActivity", intent.getAttributeNS(android, "targetClass"))
        }
        assertEquals(setOf("refresh_weather", "search_city", "open_settings"), ids)
        assertEquals(setOf("com.zhisheng.weather.action.REFRESH", "com.zhisheng.weather.action.SEARCH",
            "com.zhisheng.weather.action.SETTINGS"), actions)
    }
}
