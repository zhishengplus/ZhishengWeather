package com.zhisheng.weather.ui

import android.content.Intent
import android.util.Xml
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.BuildConfig
import com.zhisheng.weather.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

/** Run on previewPublic too: its installation package differs from the namespace. */
class ShortcutIntentTest {
    @get:Rule val paparazzi = Paparazzi(theme = "android:Theme.Material.Light.NoActionBar")

    @Test fun allStaticShortcutsTargetTheirInstalledVariant() {
        val intents = linkedMapOf<String, Intent>()
        val resources = paparazzi.context.resources
        resources.getXml(R.xml.shortcuts).use { parser ->
            var shortcutId = ""
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "shortcut" -> shortcutId = parser.getAttributeValue(
                            "http://schemas.android.com/apk/res/android", "shortcutId")
                        "intent" -> intents[shortcutId] = Intent.parseIntent(
                            resources, parser, Xml.asAttributeSet(parser))
                    }
                }
                parser.next()
            }
        }
        val actions = mapOf(
            "refresh_weather" to "com.zhisheng.weather.action.REFRESH",
            "search_city" to "com.zhisheng.weather.action.SEARCH",
            "open_settings" to "com.zhisheng.weather.action.SETTINGS",
        )
        assertEquals(actions.keys, intents.keys)
        for ((id, intent) in intents) {
            assertEquals("Shortcut $id must open its own installed variant",
                BuildConfig.APPLICATION_ID, intent.component?.packageName)
            assertEquals("com.zhisheng.weather.MainActivity", intent.component?.className)
            assertEquals(actions.getValue(id), intent.action)
        }
    }
}
