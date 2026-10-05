package com.zhisheng.weather.ui

import org.junit.Assert.*
import org.junit.Test

/** The bottom island itself is covered by the offline Compose layout fixtures. */
class VistaSettingsNavigationTest {
    @Test fun everyStopIsStableAtRestAndWithinReach() {
        for (index in 0..3) {
            assertEquals(index, magneticTabTarget(index.toFloat(), 0f, 3))
            assertEquals(index.toFloat(), resistedTabPosition(index.toFloat(), 3), 0f)
        }
    }
    @Test fun extremeReleaseCanNeverSelectANonexistentCategory() {
        for (position in -20..20) for (velocity in -20..20) {
            assertTrue(magneticTabTarget(position / 4f, velocity.toFloat(), 3) in 0..3)
        }
    }
}
