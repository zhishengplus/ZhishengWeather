package com.zhisheng.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import android.hardware.SensorManager
import android.view.Surface

class DeviceHeadingTest {
    @Test
    fun shortestDeltaCrossesNorthWithoutSpinningTheLongWay() {
        assertEquals(20f, shortestAngleDelta(350f, 10f), 0.01f)
        assertEquals(-20f, shortestAngleDelta(10f, 350f), 0.01f)
        assertEquals(0f, shortestAngleDelta(0f, 360f), 0.01f)
    }

    @Test
    fun lerpDoesNotJumpAcrossTheCompassFace() {
        assertEquals(0f, lerpAngleDegrees(350f, 10f, 0.5f), 0.01f)
        assertEquals(10f, lerpAngleDegrees(350f, 10f, 1f), 0.01f)
    }

    @Test
    fun needleStaysLockedToWindWhenPhoneTurns() {
        assertEquals(90f, windNeedleScreenRotation(90f, 0f), 0.01f)
        assertEquals(0f, windNeedleScreenRotation(90f, 90f), 0.01f)
        assertEquals(315f, windNeedleScreenRotation(45f, 90f), 0.01f)
    }

    @Test
    fun headingFilterSettlesTowardNewSample() {
        val filter = HeadingFilter(alpha = 0.5f)
        filter.push(0f)
        val next = filter.push(90f)
        assertEquals(45f, next, 0.01f)
    }

    @Test
    fun uprightPoseUsesScreenTopAsForward() {
        assertEquals(SensorManager.AXIS_X to SensorManager.AXIS_Z, compassWorldAxes(Surface.ROTATION_0, upright = true))
        assertEquals(SensorManager.AXIS_X to SensorManager.AXIS_Y, compassWorldAxes(Surface.ROTATION_0, upright = false))
    }

    @Test
    fun flatMatrixIsNotTreatedAsUpright() {
        val flat = FloatArray(9).also { it[8] = 1f }
        val upright = FloatArray(9).also { it[8] = 0.1f }
        assertEquals(false, deviceHeldUpright(flat))
        assertEquals(true, deviceHeldUpright(upright))
    }
}
