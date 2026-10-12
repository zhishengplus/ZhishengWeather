package com.zhisheng.weather.widget

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WidgetCachePrivacyTest {
    @Test fun migrationOnlyMovesRebuildableWeatherAndRecentLocations() {
        val old = mapOf<String, Any>("snapshot_geo:1,2" to "weather", "recent_cities" to "cities",
            "config_123" to "config", "notification_v2" to "settings", "pin_token" to "pending",
            "weather_click_20260943_123" to true)
        assertEquals(mapOf("snapshot_geo:1,2" to "weather", "recent_cities" to "cities"), widgetWeatherCacheEntries(old))
    }
    @Test fun weatherCachesAreExcludedFromBothBackupAndTransferWhileConfigsRemain() {
        val root = File(requireNotNull(System.getProperty("user.dir")), "src/main/res/xml")
        for (file in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(root, file))
            val branches = if (file == "backup_rules.xml") listOf(doc.documentElement)
                else listOf(doc.getElementsByTagName("cloud-backup").item(0), doc.getElementsByTagName("device-transfer").item(0))
            for (branch in branches) {
                val xml = branch.childNodes
                val excluded = (0 until xml.length).mapNotNull { i ->
                    val node = xml.item(i) as? org.w3c.dom.Element ?: return@mapNotNull null
                    node.getAttribute("domain") to node.getAttribute("path")
                }.toSet()
                assertTrue(excluded.contains("file" to "datastore/weather_cache.preferences_pb"))
                assertTrue(excluded.contains("sharedpref" to "widget_weather_cache.xml"))
                assertFalse(excluded.contains("sharedpref" to "widget_beta9.xml"))
            }
        }
    }
}
