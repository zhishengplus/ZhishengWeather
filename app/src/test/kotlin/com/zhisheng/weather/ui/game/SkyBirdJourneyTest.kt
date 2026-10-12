package com.zhisheng.weather.ui.game

import org.junit.Assert.*
import org.junit.Test

class SkyBirdJourneyTest {
    @Test fun everyTenGatesChangesSceneryWithoutChangingPhysics() {
        assertEquals(SkyBirdScenery.VALLEY, SkyBirdJourney.scenery(9))
        assertEquals(SkyBirdScenery.PRAIRIE, SkyBirdJourney.scenery(10))
        assertEquals(SkyBirdScenery.PEAKS, SkyBirdJourney.scenery(20))
        assertEquals(SkyBirdScenery.COAST, SkyBirdJourney.scenery(30))
        assertEquals(SkyBirdScenery.COSMOS, SkyBirdJourney.scenery(40))
        for (milestone in 1..30) assertNotEquals(SkyBirdJourney.scenery(milestone * 10 - 1), SkyBirdJourney.scenery(milestone * 10))
    }
    @Test fun medalsAndLegendRanksContinueProgressing() {
        assertEquals(SkyBirdMedal.NONE, SkyBirdJourney.medal(9))
        assertEquals(SkyBirdMedal.BRONZE, SkyBirdJourney.medal(10))
        assertEquals(SkyBirdMedal.SILVER, SkyBirdJourney.medal(20))
        assertEquals(SkyBirdMedal.GOLD, SkyBirdJourney.medal(30))
        assertEquals(SkyBirdMedal.CRYSTAL, SkyBirdJourney.medal(40))
        assertEquals("传奇1阶", SkyBirdJourney.medalTitle(50))
        assertEquals("传奇2阶", SkyBirdJourney.medalTitle(100))
        assertEquals("传奇20阶", SkyBirdJourney.medalTitle(1000))
    }
    @Test fun celebrationIntensityGrowsButKeepsABoundedRenderingCost() {
        assertTrue(SkyBirdJourney.particleCount(20) > SkyBirdJourney.particleCount(10))
        assertTrue(SkyBirdJourney.particleCount(50) > SkyBirdJourney.particleCount(20))
        assertTrue(SkyBirdJourney.particleCount(80) > SkyBirdJourney.particleCount(50))
        assertTrue(SkyBirdJourney.particleCount(Int.MAX_VALUE) <= 60)
    }
    @Test fun pillarsCanReachActualSkyAndFloorInTallAndWideViewports() {
        for ((w, h) in listOf(360f to 700f, 320f to 250f, 1054f to 1721f, 620f to 188f))
            for (worldHeight in listOf(260f, 520f, 700f)) {
                val v = SkyBirdViewport.fit(w, h, worldHeight)
                assertEquals(0f, v.top + v.minY * v.scale, .001f)
                assertEquals(h, v.top + v.maxY * v.scale, .001f)
                assertTrue(v.top + (v.minY - 20f) * v.scale < 0f)
                assertTrue(v.top + (v.maxY + 20f) * v.scale > h)
                assertTrue(v.top >= -.001f && v.top + worldHeight * v.scale <= h + .001f)
                assertTrue(v.left >= -.001f && v.left + SkyBirdEngine.WIDTH * v.scale <= w + .001f)
            }
    }
    @Test fun aMilestoneAwardIsIssuedOnceAndPausePreservesIt() {
        val s = SkyBirdEngine.start(seed = 431).copy(phase = SkyBirdPhase.FLYING, score = 9,
            gates = listOf(CloudGate(1, 20f, 239.2f)))
        val cleared = SkyBirdEngine.step(s, .01f)
        assertEquals(10, cleared.score)
        assertEquals(10, cleared.celebration!!.threshold)
        val later = SkyBirdEngine.step(cleared, .05f)
        assertEquals(10, later.celebration!!.threshold)
        assertEquals(cleared.celebration.originY, later.celebration.originY, 0f)
        assertTrue(later.celebration.age > cleared.celebration.age)
        val paused = SkyBirdEngine.pause(later)
        assertEquals(paused, SkyBirdEngine.step(paused, .1f))
        assertEquals(paused.celebration, SkyBirdEngine.resume(paused).celebration)
    }
    @Test fun celebrationExpiresAndRestartClearsIt() {
        val s = SkyBirdEngine.start(seed = 41).copy(phase = SkyBirdPhase.FLYING,
            celebration = SkyBirdCelebration(20, 200f, SkyBirdJourney.CELEBRATION_SECONDS - .002f))
        assertNull(SkyBirdEngine.step(s, .01f).celebration)
        assertNull(SkyBirdEngine.start(seed = 41).celebration)
    }
    @Test fun aCollisionCannotGrantAMilestoneOrAlterTheCurrentOpening() {
        val gate = CloudGate(1, 25f, 239.2f, gap = 118f)
        val s = SkyBirdEngine.start(seed = 31).copy(phase = SkyBirdPhase.FLYING, score = 9, birdY = 20f,
            gates = listOf(gate))
        val hit = SkyBirdEngine.step(s, .01f)
        assertEquals(SkyBirdPhase.LANDED, hit.phase)
        assertEquals(9, hit.score); assertNull(hit.celebration)
        assertEquals(gate.gap, hit.gates.first().gap, 0f)
        assertEquals(gate.centerY, hit.gates.first().centerY, 0f)
    }
}
