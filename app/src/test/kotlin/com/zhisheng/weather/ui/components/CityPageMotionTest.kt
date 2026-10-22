package com.zhisheng.weather.ui.components

import androidx.compose.runtime.State
import org.junit.Assert.assertEquals
import org.junit.Test

class CityPageMotionTest {
    @Test fun settledPageAloneRunsAndItsNeighboursArePrepared() {
        assertEquals(
            listOf(CityPageActivity.STOPPED, CityPageActivity.PREPARED, CityPageActivity.ACTIVE,
                CityPageActivity.PREPARED, CityPageActivity.STOPPED),
            (0..4).map { cityPageActivity(it, 0, 1, 2, moving = false, enabled = true) },
        )
    }

    @Test fun dragPreparesVisiblePagesAndNeighboursWithoutStartingAnimation() {
        assertEquals(
            listOf(CityPageActivity.STOPPED, CityPageActivity.PREPARED, CityPageActivity.PREPARED,
                CityPageActivity.PREPARED, CityPageActivity.PREPARED, CityPageActivity.STOPPED),
            (0..5).map { cityPageActivity(it, 3, 2, 0, moving = true, enabled = true) },
        )
    }

    @Test fun disabledAndInvalidPagesNeverRun() {
        for (index in -1..5) {
            assertEquals(CityPageActivity.STOPPED,
                cityPageActivity(index, 1, 2, 1, moving = true, enabled = false))
        }
        assertEquals(CityPageActivity.STOPPED,
            cityPageActivity(-1, 0, 1, 0, moving = true, enabled = true))
        assertEquals(CityPageActivity.STOPPED,
            cityPageActivity(0, -1, -1, -1, moving = false, enabled = true))
        assertEquals(CityPageActivity.PREPARED,
            cityPageActivity(0, -1, 0, 0, moving = true, enabled = true))
        assertEquals(CityPageActivity.STOPPED,
            cityPageActivity(0, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE,
                moving = false, enabled = true))
    }

    @Test fun hiddenPageDoesNotReadTheGlobalClock() {
        val source = CountingPhase(.2)
        val continuity = CityGlowContinuity()
        val active = CityPageGlowPhase(source, true, continuity)
        assertPhase(.2, active.value)
        source.phase = .3
        assertPhase(.3, active.value)
        val paused = CityPageGlowPhase(source, false, continuity)
        source.phase = .8
        repeat(10) { assertPhase(.3, paused.value) }
        assertEquals(2, source.reads)
    }

    @Test fun resumeContinuesFromLastVisiblePhaseAcrossClockWrap() {
        val source = CountingPhase(.9)
        val continuity = CityGlowContinuity()
        val first = CityPageGlowPhase(source, true, continuity)
        assertPhase(.9, first.value)
        source.phase = .95
        assertPhase(.95, first.value)
        val paused = CityPageGlowPhase(source, false, continuity)
        source.phase = .4
        assertPhase(.95, paused.value)
        val resumed = CityPageGlowPhase(source, true, continuity)
        assertPhase(.95, resumed.value)
        source.phase = .5
        assertPhase(.05, resumed.value)
        source.phase = .95
        assertPhase(.5, resumed.value)
        source.phase = .05
        assertPhase(.6, resumed.value)
    }

    @Test fun initiallyPreparedPageSamplesOnlyWhenFirstShown() {
        val source = CountingPhase(.66)
        val continuity = CityGlowContinuity()
        val prepared = CityPageGlowPhase(source, false, continuity)
        assertPhase(0.0, prepared.value)
        assertEquals(0, source.reads)
        val visible = CityPageGlowPhase(source, true, continuity)
        assertPhase(.66, visible.value)
        source.phase = Double.NaN
        assertPhase(.66, visible.value)
    }

    private fun assertPhase(expected: Double, actual: Double) = assertEquals(expected, actual, .000001)

    private class CountingPhase(var phase: Double) : State<Double> {
        var reads = 0
        override val value: Double get() { reads++; return phase }
    }
}
