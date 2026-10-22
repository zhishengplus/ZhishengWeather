package com.zhisheng.weather.ui

import org.junit.Assert.*
import org.junit.Test

class StartupEntryPolicyTest {
    @Test fun anUnseenUpgradeIsShownEvenWhenTheInstallerRestoresThePreviousTask() {
        assertFalse(suppressStartupPrompts(false, "android.intent.action.MAIN", true))
        assertTrue(suppressStartupPrompts(false, "android.intent.action.MAIN", false))
        assertTrue(suppressStartupPrompts(false, "com.zhisheng.weather.action.WIDGET_WEATHER", true))
        assertTrue(suppressStartupPrompts(false, "android.intent.action.VIEW", true))
    }
    @Test fun widgetActionSkipsNoticesAndInstallerMainEntryShowsThem() {
        assertFalse(isStartupIconEntry("com.zhisheng.weather.action.WIDGET_WEATHER", false, true, false, false))
        assertTrue(isStartupIconEntry("android.intent.action.MAIN", false, true, false, false))
    }

    @Test fun genuineIconEntryAllowsNoticesButTaskRestorationDoesNot() {
        assertTrue(isStartupIconEntry("android.intent.action.MAIN", true, true, false, false))
        assertFalse(isStartupIconEntry("android.intent.action.MAIN", true, true, true, false))
        assertFalse(isStartupIconEntry("android.intent.action.MAIN", true, true, false, true))
        assertFalse(isStartupIconEntry("android.intent.action.MAIN", true, false, false, false))
    }

    @Test fun notificationSearchAndSettingsDoNotGetCovered() {
        for (action in listOf(null, "android.intent.action.VIEW", "com.zhisheng.weather.action.SEARCH", "com.zhisheng.weather.action.SETTINGS")) {
            assertFalse(isStartupIconEntry(action, true, true, false, false))
        }
    }
}
