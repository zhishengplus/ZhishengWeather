package com.zhisheng.weather.widget
import org.junit.Assert.*
import org.junit.Test

class SystemClockLauncherTest {
    @Test fun chooserAndDisabledOrPrivateComponentsAreNotSystemClockSuccess() {
        assertFalse(isUsableSystemClock("android", 1, true, true))
        assertFalse(isUsableSystemClock("com.android.intentresolver", 1, true, true))
        assertFalse(isUsableSystemClock("com.android.deskclock", 1, false, true))
        assertFalse(isUsableSystemClock("com.android.deskclock", 1, true, false))
    }
    @Test fun preinstalledAndUpdatedSystemClocksWinOverDownloadedAlternatives() {
        assertTrue(isUsableSystemClock("com.android.deskclock", 1, true, true))
        assertTrue(isUsableSystemClock("com.sec.android.app.clockpackage", 128, true, true))
        assertTrue(isUsableSystemClock("vendor.new.clock", 1, true, true))
        assertFalse(isUsableSystemClock("com.google.android.deskclock", 0, true, true))
    }
    @Test fun romClockInstalledAsOrdinaryAppStillWorksWithoutAllowingOtherDownloadedClocks() {
        val xiaomi = romClockPackages("Xiaomi", "Redmi").toSet()
        assertTrue(isUsableSystemClock("com.android.deskclock", 0, true, true, xiaomi))
        assertFalse(isUsableSystemClock("com.google.android.deskclock", 0, true, true, xiaomi))
        assertFalse(isUsableSystemClock("com.android.deskclock", 0, true, false, xiaomi))
        assertFalse(isUsableSystemClock("android", 1, true, true, xiaomi))
        assertTrue(isUsableSystemClock("com.sec.android.app.clockpackage", 0, true, true, romClockPackages("samsung", "Samsung").toSet()))
        assertTrue(romClockPackages("unknown", "unknown").isEmpty())
    }
}
