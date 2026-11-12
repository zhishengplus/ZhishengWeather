package com.zhisheng.weather.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetProviderMatchingTest {
    private val klass = "com.zhisheng.weather.widget.VistaComboWidgetProvider"

    @Test fun acceptsFrameworkAndLauncherNamesWithinTheSameApplication() {
        for (applicationId in listOf("com.zhisheng.weather", "com.zhisheng.weather.preview", "com.zhisheng.weather.badgeexperience")) {
            for (name in listOf(klass, ".widget.VistaComboWidgetProvider", "VistaComboWidgetProvider")) {
                assertTrue("$applicationId: $name", widgetProviderClassMatches(applicationId, klass, applicationId, name))
            }
        }
    }

    @Test fun rejectsAnotherVariantAndUnrelatedProviderClasses() {
        assertFalse(widgetProviderClassMatches("com.zhisheng.weather", klass, "com.zhisheng.weather.preview", klass))
        assertFalse(widgetProviderClassMatches("com.zhisheng.weather", klass, "com.zhisheng.weather", "other.package.VistaComboWidgetProvider"))
        assertFalse(widgetProviderClassMatches("com.zhisheng.weather", klass, "com.zhisheng.weather", ".widget.ClassicComboWidgetProvider"))
        assertFalse(widgetProviderClassMatches("com.zhisheng.weather", klass, "com.zhisheng.weather", ""))
    }
}
