package com.zhisheng.weather.ui

import androidx.compose.ui.unit.IntOffset
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class StandbyBurnInTest {
    @Test fun startsCenteredWithoutAnEntranceJump() {
        assertEquals(IntOffset.Zero, standbyBurnInOffset(0L))
        assertEquals(IntOffset.Zero, standbyBurnInOffset(-1L))
    }

    @Test fun aDayOfIdleDisplayMovesInBothDirectionsWithinSixPhysicalPixels() {
        val offsets = (0L..86_400_000L step 15_000L).map(::standbyBurnInOffset)
        assertTrue(offsets.all { abs(it.x) <= 6 && abs(it.y) <= 6 })
        assertTrue(offsets.any { it.x > 0 } && offsets.any { it.x < 0 })
        assertTrue(offsets.any { it.y > 0 } && offsets.any { it.y < 0 })
        assertTrue(offsets.distinct().size > 30)
    }

    @Test fun eachFifteenSecondStepMovesAtMostOnePixelOnEitherAxis() {
        val offsets = (0L..86_400_000L step 15_000L).map(::standbyBurnInOffset)
        assertTrue(offsets.zipWithNext().all { (a, b) -> abs(a.x - b.x) <= 1 && abs(a.y - b.y) <= 1 })
    }
}
