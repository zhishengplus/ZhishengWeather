package com.zhisheng.weather.ui.home

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class VistaLuminaryMotionTest {
    private val track = LuminaryMorphTrack(320f, 29f, 118f, 15f, 142f, 88f, 8f)

    @Test fun wavePreservesTheRealClockAndExistingAltitudeForBothBodies() {
        for ((rise, set) in listOf(439 to 1099, 1016 to 304, 0 to 720, 720 to 0)) {
            val up = vistaAstroUpDuration(rise, set)!!
            for (now in 0 until VISTA_DAY_MINUTES step 7) {
                val elapsed = Math.floorMod(now - rise, VISTA_DAY_MINUTES)
                val above = elapsed < up
                val orbit = if (above) elapsed.toFloat() / up else (elapsed - up).toFloat() / (VISTA_DAY_MINUTES - up)
                val point = track.point(now.toFloat(), orbit, above, up, 0f)
                assertEquals(8f + 304f * now / VISTA_DAY_MINUTES, point.x, 0.001f)
                assertEquals(29f - 15f * vistaAstroAltitude(now, rise, set), point.y, 0.001f)
            }
        }
    }

    @Test fun midnightMoonSegmentsConvergeIntoOneArcWithoutAnArtificialClockLine() {
        val pieces = luminaryClockSpans(1016, 304, true).sortedBy { it.from }
        assertEquals(2, pieces.size)
        assertEquals(pieces[0].to, pieces[1].from, 0.0001f)
        val q = pieces[0].to
        for (step in 0..20) {
            val p = step / 20f
            val right = track.point(1440f, q, true, 728, p)
            val left = track.point(0f, q, true, 728, p)
            assertEquals(304f * (1f - p), right.x - left.x, 0.001f)
            assertEquals(right.y, left.y, 0.001f)
        }
    }

    @Test fun visibleClockPiecesCoverEveryAboveAndBelowInstantExactlyOnce() {
        for ((rise, set) in listOf(439 to 1099, 1016 to 304, 0 to 720, 720 to 0, 1439 to 1)) {
            val up = vistaAstroUpDuration(rise, set)!!
            for (above in listOf(true, false)) {
                val spans = luminaryClockSpans(rise, set, above)
                assertEquals(1f, spans.sumOf { (it.to - it.from).toDouble() }.toFloat(), 0.0001f)
                for (span in spans) {
                    assertTrue(span.clockStart + span.duration * span.from >= -0.001f)
                    assertTrue(span.clockStart + span.duration * span.to <= 1440.001f)
                    assertEquals(if (above) up else 1440 - up, span.duration)
                }
            }
        }
    }

    @Test fun theMovingPointStaysOnTheSameInterpolatedTrackAtEveryPhase() {
        val rise = 439
        val set = 1099
        val up = 660
        val now = 743
        val q = (now - rise).toFloat() / up
        val span = luminaryClockSpans(rise, set, true).single()
        for (step in 0..40) {
            val p = step / 40f
            val fromCurve = track.point(span.clockStart + span.duration * q, q, true, up, p)
            val marker = track.point(now.toFloat(), q, true, up, p)
            assertEquals(marker.x, fromCurve.x, 0.001f)
            assertEquals(marker.y, fromCurve.y, 0.001f)
        }
    }

    @Test fun theFullSunGlowFitsAtTheCrestAndBothHorizonEndsThroughoutTheMorph() {
        for (step in 0..40) for (q in listOf(0f, 0.5f, 1f)) {
            val p = step / 40f
            val radius = 7f + 7f * p
            val point = track.point(439f + 660f * q, q, true, 660, p)
            assertTrue(point.y - radius >= -0.001f)
            assertTrue(point.y + radius <= track.arcHorizon + 14.001f)
            assertTrue(point.x - radius >= -0.001f && point.x + radius <= 320.001f)
        }
    }

    @Test fun missingOrEqualRiseSetNeverCreatesAnInventedOrbit() {
        assertTrue(luminaryClockSpans(720, 720, true).isEmpty())
        assertTrue(luminaryClockSpans(720, 720, false).isEmpty())
        assertNull(vistaArcProgress(720, 720, 720))
    }

    @Test fun aHiddenMoonRemainsHiddenAtTheArcEndpointEvenWhenItsRiseCrossesMidnight() {
        assertNull(vistaArcProgress(12 * 60, 1016, 304))
        assertEquals((23 * 60 - 1016) / 728f, vistaArcProgress(23 * 60, 1016, 304)!!, 0.0001f)
        assertEquals((1440 + 2 * 60 - 1016) / 728f, vistaArcProgress(2 * 60, 1016, 304)!!, 0.0001f)
    }

    @Test fun detailsOnlyAppearTowardsTheArcAndReverseWithoutAThresholdJump() {
        assertEquals(0f, luminaryDetailsAlpha(0f), 0f)
        assertEquals(0f, luminaryDetailsAlpha(0.45f), 0f)
        assertEquals(1f, luminaryDetailsAlpha(1f), 0.0001f)
        for (i in 1..1000) assertTrue(abs(luminaryDetailsAlpha(i / 1000f) - luminaryDetailsAlpha((i - 1) / 1000f)) < 0.002f)
    }
}
