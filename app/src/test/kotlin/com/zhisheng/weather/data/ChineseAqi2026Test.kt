package com.zhisheng.weather.data

import org.junit.Assert.*
import org.junit.Test

class ChineseAqi2026Test {
    @Test fun newParticleBreakpointsAndCeilingMatchThe2026Standard() {
        assertEquals(listOf(0, 50, 100, 150, 200, 300, 400, 500),
            listOf(0.0, 35.0, 60.0, 115.0, 150.0, 250.0, 350.0, 500.0)
                .map { ChineseAqi2026.subIndex("PM2.5", it) })
        assertEquals(listOf(0, 50, 100, 150, 200, 300, 400, 500),
            listOf(0.0, 50.0, 120.0, 250.0, 350.0, 420.0, 500.0, 600.0)
                .map { ChineseAqi2026.subIndex("PM10", it) })
        assertEquals(101, ChineseAqi2026.subIndex("PM2.5", 61.0))
        assertEquals(26, ChineseAqi2026.subIndex("PM2.5", 18.0))
        assertEquals(114, ChineseAqi2026.subIndex("PM2.5", 75.0))
    }

    @Test fun gasBreakpointsUseTheOneHourColumnAndSo2StopsAtTwoHundred() {
        assertEquals(50, ChineseAqi2026.subIndex("CO", 5.0))
        assertEquals(100, ChineseAqi2026.subIndex("CO", 10.0))
        assertEquals(50, ChineseAqi2026.subIndex("O₃", 160.0))
        assertEquals(100, ChineseAqi2026.subIndex("O₃", 200.0))
        assertEquals(50, ChineseAqi2026.subIndex("NO₂", 100.0))
        assertEquals(100, ChineseAqi2026.subIndex("NO₂", 200.0))
        assertEquals(200, ChineseAqi2026.subIndex("SO₂", 800.0))
        assertEquals(200, ChineseAqi2026.subIndex("SO₂", 900.0))
        assertEquals(200, ChineseAqi2026.subIndex("SO₂", 10_000.0))
    }

    @Test fun concentrationsAreRoundedHalfEvenBeforeInterpolation() {
        assertEquals(100, ChineseAqi2026.subIndex("PM2.5", 60.5))
        assertEquals(102, ChineseAqi2026.subIndex("PM2.5", 61.5))
        assertEquals(50, ChineseAqi2026.subIndex("CO", 5.05))
        assertEquals(52, ChineseAqi2026.subIndex("CO", 5.15))
    }

    @Test fun exactDecimalSubIndicesNeverGainOneFromFloatingPointNoise() {
        assertEquals(7, ChineseAqi2026.subIndex("PM10", 7.0))
        assertEquals(7, ChineseAqi2026.subIndex("NO₂", 14.0))
        assertEquals(7, ChineseAqi2026.subIndex("SO₂", 21.0))
        assertEquals(11, ChineseAqi2026.subIndex("CO", 1.1))
        assertEquals(49, ChineseAqi2026.subIndex("CO", 4.9))
        // CO 60→IAQI 200, 90→300: 200 + (65.4 - 60) × 100 / 30 = 218.
        assertEquals(218, ChineseAqi2026.subIndex("CO", 65.4))
    }

    @Test fun everyPollutantMustBeValidBeforePublishingACompositeEstimate() {
        val clean = ChineseAqi2026.estimate(18.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        assertEquals(26, clean.value)
        assertNull(clean.primary)
        assertTrue(clean.missingPollutants.isEmpty())
        for (invalid in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0)) {
            val incomplete = ChineseAqi2026.estimate(250.0, invalid, 0.0, 0.0, 0.0, 0.0)
            assertNull(incomplete.value)
            assertNull(incomplete.primary)
            assertEquals(listOf("PM10"), incomplete.missingPollutants)
            assertEquals(300, incomplete.subIndices["PM2.5"])
            assertTrue(incomplete.note.contains("非监测1小时平均"))
            assertTrue(incomplete.note.contains("不计算综合AQI"))
        }
    }

    @Test fun allTiedMajorPollutantsAreReportedAndThePublicRangeStartsAtOne() {
        val tied = ChineseAqi2026.estimate(60.0, 120.0, 0.0, 0.0, 0.0, 0.0)
        assertEquals(100, tied.value)
        assertEquals("PM2.5、PM10", tied.primary)
        assertEquals(1, ChineseAqi2026.estimate(0.0, 0.0, 0.0, 0.0, 0.0, 0.0).value)
    }
}
