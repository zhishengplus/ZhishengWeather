package com.zhisheng.weather.ui.game

import org.junit.Assert.*
import org.junit.Test

class SkyBirdEngineTest {
    @Test fun onlyExplicitStartOrFlapMovesTheBird() {
        val ready = SkyBirdEngine.start()
        assertEquals(ready, SkyBirdEngine.step(ready, 1f))
        val flying = SkyBirdEngine.flap(ready)
        assertEquals(SkyBirdPhase.FLYING, flying.phase)
        assertTrue(SkyBirdEngine.step(flying, .05f).birdY < flying.birdY)
    }
    @Test fun pauseFreezesEverythingAndResumeDoesNotRestartScore() {
        val flying = SkyBirdEngine.flap(SkyBirdEngine.start()).copy(score = 4)
        val paused = SkyBirdEngine.pause(flying)
        assertEquals(paused, SkyBirdEngine.step(paused, .1f))
        assertEquals(paused, SkyBirdEngine.flap(paused))
        val resumed = SkyBirdEngine.resume(paused)
        assertEquals(4, resumed.score)
        assertEquals(paused.gates, resumed.gates)
        assertEquals(paused.velocity, resumed.velocity, 0f)
        assertEquals(paused.randomState, resumed.randomState)
    }
    @Test fun refreshRatesHaveEquivalentMotionWithoutEulerDrift() {
        val start = SkyBirdEngine.flap(SkyBirdEngine.start())
        fun simulate(hz: Int): SkyBirdState {
            var s = start
            repeat(hz / 2) { s = SkyBirdEngine.step(s, 1f / hz) }
            return s
        }
        val a = simulate(30); val b = simulate(120)
        assertEquals(a.birdY, b.birdY, .02f)
        assertEquals(a.distance, b.distance, .02f)
        assertEquals(a.phase, b.phase)
    }
    @Test fun largeOrInvalidFrameDelaysCannotTeleportThroughClouds() {
        val flying = SkyBirdEngine.flap(SkyBirdEngine.start())
        assertEquals(SkyBirdEngine.step(flying, .1f), SkyBirdEngine.step(flying, 60f))
        assertEquals(flying, SkyBirdEngine.step(flying, Float.NaN))
        assertEquals(flying, SkyBirdEngine.step(flying, -1f))
    }
    @Test fun clearingOneGateAwardsExactlyOnePoint() {
        val flying = SkyBirdEngine.start().copy(phase = SkyBirdPhase.FLYING,
            gates = listOf(CloudGate(1, 25f, 239.2f)))
        var s = SkyBirdEngine.step(flying, .05f)
        assertEquals(1, s.score)
        s = SkyBirdEngine.step(s, .05f)
        assertEquals(1, s.score)
    }
    @Test fun touchingCloudStopsGameAndNeverAwardsACollisionPoint() {
        val s = SkyBirdEngine.start().copy(phase = SkyBirdPhase.FLYING,
            birdY = 20f, gates = listOf(CloudGate(0, 80f, 240f)))
        val ended = SkyBirdEngine.step(s, .02f)
        assertEquals(SkyBirdPhase.LANDED, ended.phase)
        assertEquals(0, ended.score)
        assertEquals(ended, SkyBirdEngine.step(ended, .1f))
        assertEquals(ended, SkyBirdEngine.flap(ended))
    }
    @Test fun passingThroughGapDoesNotCollideWithDecorativeCloudEdges() {
        val s = SkyBirdEngine.start().copy(phase = SkyBirdPhase.FLYING,
            gates = listOf(CloudGate(0, 70f, 239.2f)))
        assertEquals(SkyBirdPhase.FLYING, SkyBirdEngine.step(s, .02f).phase)
    }
    @Test fun floorAndCeilingBothEndFlight() {
        for (y in listOf(9f, 480f)) {
            val s = SkyBirdEngine.start().copy(phase = SkyBirdPhase.FLYING, birdY = y)
            assertEquals(SkyBirdPhase.LANDED, SkyBirdEngine.step(s, .01f).phase)
        }
    }
    @Test fun generatedGapsRemainWithinFieldAtAllSupportedHeights() {
        for (height in listOf(260f, 520f, 700f)) {
            val s = SkyBirdEngine.start(height).copy(phase = SkyBirdPhase.FLYING,
                gates = listOf(CloudGate(9, 200f, height * .46f)))
            val next = SkyBirdEngine.step(s, .01f)
            val gate = next.gates.last()
            val half = SkyBirdEngine.gap(height) / 2f
            assertTrue(gate.centerY - half > SkyBirdEngine.RADIUS)
            assertTrue(gate.centerY + half < height - SkyBirdEngine.FLOOR - SkyBirdEngine.RADIUS)
        }
    }
    @Test fun explicitSeedReplaysButDifferentRunsHaveDifferentLayouts() {
        val a = SkyBirdEngine.start(seed = 87123451)
        assertEquals(a, SkyBirdEngine.start(seed = 87123451))
        val b = SkyBirdEngine.start(seed = 198376234)
        assertNotEquals(a.gates, b.gates)
        val openings = (1..40).map { SkyBirdEngine.start(seed = it * 4301513).gates.first() }
        assertTrue(openings.map { it.centerY }.distinct().size > 35)
        assertTrue(openings.map { it.x }.distinct().size > 35)
        assertTrue(openings.map { it.gap }.distinct().size > 35)
    }
    @Test fun randomChainsHaveBoundedReachableJumpsAndVariedSpacing() {
        for (height in listOf(260f, 520f, 700f)) for (score in listOf(0, 10, 50)) {
            var previous = SkyBirdEngine.start(height, seed = 598324).gates.single()
            var seed = 8721341
            val heights = mutableSetOf<Float>(); val spacings = mutableSetOf<Float>()
            repeat(150) {
                val (next, rng) = SkyBirdEngine.nextGate(previous, height, score, seed)
                val spacing = next.x - previous.x
                assertTrue(spacing in 201.99f..248.01f)
                assertTrue(kotlin.math.abs(next.centerY - previous.centerY) <= 112.01f)
                assertTrue(next.centerY - next.gap / 2 >= 29.99f)
                assertTrue(next.centerY + next.gap / 2 <= height - SkyBirdEngine.FLOOR - 29.99f)
                assertTrue(next.gap >= 80f)
                heights.add(next.centerY); spacings.add(spacing)
                previous = next; seed = rng
            }
            assertTrue(heights.size > 120); assertTrue(spacings.size > 120)
        }
    }
    @Test fun spawnDoesNotChangeTheOpeningAlreadyApproachingTheBird() {
        val gate = CloudGate(1, 200f, 240f, gap = 103f)
        val s = SkyBirdEngine.start(seed = 57).copy(phase = SkyBirdPhase.FLYING, score = 30, gates = listOf(gate))
        val next = SkyBirdEngine.step(s, .01f)
        assertEquals(103f, next.gates.first().gap, 0f)
        assertEquals(gate.centerY, next.gates.first().centerY, 0f)
        assertTrue(next.gates.last().gap in 101f..111f)
    }
    @Test fun difficultyRequiresTimingAndNarrowsFutureOpeningsGradually() {
        val ready = SkyBirdEngine.start(seed = 93715)
        val s = SkyBirdEngine.step(SkyBirdEngine.flap(ready), .1f)
        assertEquals(15.5f, s.distance, .01f)
        assertEquals(ready.birdY - 27.5f, s.birdY, .01f)
        assertTrue(SkyBirdEngine.gap(520f) < 125f)
        assertTrue(SkyBirdEngine.gap(520f, 20) < SkyBirdEngine.gap(520f))
        var unattended = SkyBirdEngine.flap(ready)
        repeat(20) { unattended = SkyBirdEngine.step(unattended, .1f) }
        assertEquals(SkyBirdPhase.LANDED, unattended.phase)
    }
    @Test fun everySupportedGapCanBeCrossedWithAControlledFlap() {
        for (height in listOf(260f, 520f, 700f)) for (score in listOf(0, 40)) {
            val center = (height - SkyBirdEngine.FLOOR) / 2
            val opening = SkyBirdEngine.gap(height, score)
            var s = SkyBirdEngine.start(height, seed = 91).copy(phase = SkyBirdPhase.FLYING, score = score,
                birdY = center + 20f, velocity = -275f,
                gates = listOf(CloudGate(0, SkyBirdEngine.BIRD_X, center, gap = opening)))
            repeat(60) {
                s = SkyBirdEngine.step(s, 1f / 120f)
                assertEquals("Smallest opening must remain playable", SkyBirdPhase.FLYING, s.phase)
            }
            assertEquals(score + 1, s.score)
        }
    }
    @Test fun cappedFallIsRefreshRateIndependent() {
        val start = SkyBirdEngine.start(seed = 29873).copy(phase = SkyBirdPhase.FLYING,
            birdY = 200f, velocity = 400f)
        val slow = SkyBirdEngine.step(start, .1f)
        var fast = start
        repeat(12) { fast = SkyBirdEngine.step(fast, 1f / 120f) }
        assertEquals(SkyBirdEngine.MAX_FALL, fast.velocity, 0f)
        assertEquals(slow.birdY, fast.birdY, .02f)
    }
}
