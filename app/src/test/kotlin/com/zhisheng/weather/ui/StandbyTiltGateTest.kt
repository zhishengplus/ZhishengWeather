package com.zhisheng.weather.ui

import org.junit.Assert.*
import org.junit.Test

class StandbyTiltGateTest {
    private class Rig {
        var landscape = false
        var scheduledDelay = 0L
        var pending: (() -> Unit)? = null
        val gate = StandbyTiltGate(
            scheduleEntry = { delay, action -> scheduledDelay = delay; pending = action },
            cancelEntry = { pending = null },
            onLandscapeChanged = { landscape = it },
        )
        fun elapse() { val action = pending; pending = null; action?.invoke() }
    }

    @Test fun stableHorizontalEntersWithoutAnotherSensorEvent() {
        for (angle in listOf(65, 90, 115, 245, 270, 295)) {
            val rig = Rig()
            rig.gate.updateAngle(angle)
            assertFalse(rig.landscape)
            assertEquals(500L, rig.scheduledDelay)
            rig.elapse()
            assertTrue("angle=$angle", rig.landscape)
        }
    }

    @Test fun smallHorizontalJitterDoesNotRestartTheDelay() {
        val rig = Rig()
        rig.gate.updateAngle(90)
        val first = rig.pending
        rig.gate.updateAngle(91)
        assertSame(first, rig.pending)
        rig.elapse()
        assertTrue(rig.landscape)
    }

    @Test fun LeavingEntryRangeCancelsAndRequiresANewFullDelay() {
        val rig = Rig()
        rig.gate.updateAngle(90)
        val stale = rig.pending!!
        rig.gate.updateAngle(60)
        assertNull(rig.pending)
        rig.gate.updateAngle(90)
        stale()
        assertFalse(rig.landscape)
        rig.elapse()
        assertTrue(rig.landscape)
    }

    @Test fun flatOrPortraitCancelsAnUnconfirmedTilt() {
        for (angle in listOf(-1, 0, 40, 320, 359)) {
            val rig = Rig()
            rig.gate.updateAngle(90)
            rig.gate.updateAngle(angle)
            rig.elapse()
            assertFalse("angle=$angle", rig.landscape)
        }
    }

    @Test fun confirmedLandscapeSurvivesHysteresisAndFlatButExitsAtPortrait() {
        val rig = Rig()
        rig.gate.updateAngle(270)
        rig.elapse()
        for (angle in listOf(60, 180, 300, -1)) {
            rig.gate.updateAngle(angle)
            assertTrue("angle=$angle", rig.landscape)
        }
        rig.gate.updateAngle(0)
        assertFalse(rig.landscape)
    }

    @Test fun resetRejectsALateCallbackAndClearsConfirmedLandscape() {
        val rig = Rig()
        rig.gate.updateAngle(90)
        val stale = rig.pending!!
        rig.gate.reset()
        stale()
        assertFalse(rig.landscape)
        rig.gate.updateAngle(90)
        rig.elapse()
        rig.gate.reset()
        assertFalse(rig.landscape)
    }
}
