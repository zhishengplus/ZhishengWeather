package com.zhisheng.weather.ui.home

import com.zhisheng.weather.model.HourlyWeather
import org.junit.Assert.*
import org.junit.Test

class HourlyTemperatureTrendTest {
    private fun items(vararg temperatures: Double?) = temperatures.mapIndexed { index, value ->
        HourlyDisplayItem(HourlyWeather(1_800_000_000_000L + index * 3_600_000L, value), index == 0)
    }

    @Test fun smallChangesStaySubtleAndUseTheSameScaleAcrossAllHours() {
        val points = hourlyTemperatureTrend(items(20.0, 21.0, 22.0)).map { it!! }
        assertEquals(0.625f, points[0].fraction, 0.0001f)
        assertEquals(0.5f, points[1].fraction, 0.0001f)
        assertEquals(0.375f, points[2].fraction, 0.0001f)
        assertFalse(points[0].connectsToPrevious)
        assertTrue(points.drop(1).all { it.connectsToPrevious })
    }

    @Test fun wideAndNegativeRangesRetainTheirRealOrderAndExtrema() {
        val points = hourlyTemperatureTrend(items(-20.0, -5.0, 0.0, 20.0)).map { it!! }
        assertEquals(1f, points.first().fraction, 0f)
        assertEquals(0f, points.last().fraction, 0f)
        assertTrue(points.zipWithNext().all { (a, b) -> a.fraction > b.fraction })
    }

    @Test fun constantAndSingleReadingsAreCenteredWithoutFabricatingNeighbours() {
        assertTrue(hourlyTemperatureTrend(items(11.0, 11.0, 11.0)).all { it!!.fraction == 0.5f })
        val single = hourlyTemperatureTrend(items(-7.0)).single()!!
        assertEquals(0.5f, single.fraction, 0f)
        assertFalse(single.connectsToPrevious)
        assertTrue(hourlyTemperatureTrend(emptyList()).isEmpty())
    }

    @Test fun missingAndNonFiniteReadingsKeepTheirHourAndBreakTheLine() {
        val points = hourlyTemperatureTrend(items(20.0, null, 22.0, Double.NaN, Double.POSITIVE_INFINITY, 24.0))
        assertEquals(6, points.size)
        assertNull(points[1])
        assertNull(points[3])
        assertNull(points[4])
        assertFalse(points[2]!!.connectsToPrevious)
        assertFalse(points[5]!!.connectsToPrevious)
        assertTrue(hourlyTemperatureTrend(items(null, Double.NEGATIVE_INFINITY)).all { it == null })
    }

    @Test fun forecastGapsNeverJoinEvenWhenBothEndpointsHaveTemperatures() {
        val hours = items(20.0, 21.0, 22.0, 23.0).mapIndexed { index, item ->
            item.copy(weather = item.weather.copy(timeMillis = 1_800_000_000_000L + listOf(0L, 1_800_000L, 7_200_000L, 12_600_001L)[index]))
        }
        assertEquals(listOf(false, true, true, false), hourlyTemperatureTrend(hours).map { it!!.connectsToPrevious })
    }

    @Test fun duplicateAndBackwardsTimesDoNotImplyAContinuousForecast() {
        val hours = items(20.0, 21.0, 22.0).mapIndexed { index, item ->
            item.copy(weather = item.weather.copy(timeMillis = listOf(100L, 100L, 90L)[index]))
        }
        assertTrue(hourlyTemperatureTrend(hours).all { !it!!.connectsToPrevious })
    }

    @Test fun aSteadyRiseFlowsToBothEdgesWithoutThePreviousHorizontalShelf() {
        val segments = hourlyTemperatureCurves(hourlyTemperatureTrend(items(20.0, 21.0, 22.0))).single().segments
        assertEquals(0f, segments.first().start.x, 0f)
        assertEquals(3f, segments.last().end.x, 0f)
        assertTrue(segments.first().start.fraction > segments.first().end.fraction)
        assertTrue(segments.last().start.fraction > segments.last().end.fraction)
        segments.forEach { segment ->
            assertEquals(-0.125f, slope(segment.start, segment.firstControl), 0.0001f)
            assertEquals(-0.125f, slope(segment.secondControl, segment.end), 0.0001f)
        }
    }

    @Test fun everyRealReadingStaysOnTheCurveAndAdjacentSegmentsShareTheirTangent() {
        val trend = hourlyTemperatureTrend(items(18.0, 20.0, 19.0, 24.0, 23.0, 17.0))
        val segments = hourlyTemperatureCurves(trend).single().segments
        trend.forEachIndexed { index, point ->
            val knot = segments.first { it.end.x == index + 0.5f }.end
            assertEquals(point!!.fraction, knot.fraction, 0f)
        }
        segments.zipWithNext().forEach { (left, right) ->
            assertEquals(left.end, right.start)
            assertEquals(slope(left.secondControl, left.end), slope(right.start, right.firstControl), 0.0001f)
        }
    }

    @Test fun extremeTurnsFlatSectionsAndHalfCellEdgesNeverOvershootTheirEndpoints() {
        val cases = listOf(
            listOf(0f, 1f), listOf(0f, 1f, 0f), listOf(1f, 0f, 1f),
            listOf(.5f, .5f, .5f), listOf(.5f, .5f, .6f, .6f),
            listOf(.1f, .1001f, .9f, .91f), listOf(.8f, .2f, .1999f, .9f),
        )
        for (fractions in cases) {
            val trend = fractions.mapIndexed { index, value -> HourlyTemperaturePoint(value, index > 0) }
            hourlyTemperatureCurves(trend).single().segments.forEach { segment ->
                val low = minOf(segment.start.fraction, segment.end.fraction)
                val high = maxOf(segment.start.fraction, segment.end.fraction)
                for (sample in 0..100) {
                    val value = fractionAt(segment, sample / 100f)
                    assertTrue("Curve must preserve local extrema: $fractions at $sample = $value",
                        value >= low - .00001f && value <= high + .00001f)
                    assertTrue(value in -0.00001f..1.00001f)
                }
            }
        }
    }

    @Test fun edgeContinuationsNeverReachAcrossMissingHoursOrTimeGaps() {
        val trend = hourlyTemperatureTrend(items(20.0, 21.0, null, 23.0, 24.0, 25.0))
        val curves = hourlyTemperatureCurves(trend)
        assertEquals(2, curves.size)
        assertEquals(0f, curves.first().segments.first().start.x, 0f)
        assertEquals(1.5f, curves.first().segments.last().end.x, 0f)
        assertEquals(3.5f, curves.last().segments.first().start.x, 0f)
        assertEquals(6f, curves.last().segments.last().end.x, 0f)

        val gap = hourlyTemperatureTrend(items(20.0, 21.0, 22.0, 23.0)).mapIndexed { index, point ->
            if (index == 2) point!!.copy(connectsToPrevious = false) else point
        }
        val separated = hourlyTemperatureCurves(gap)
        assertEquals(1.5f, separated.first().segments.last().end.x, 0f)
        assertEquals(2.5f, separated.last().segments.first().start.x, 0f)
    }

    @Test fun noTemperatureOrIsolatedReadingsNeverCreateDrawingOnlyNeighbours() {
        assertTrue(hourlyTemperatureCurves(hourlyTemperatureTrend(items())).isEmpty())
        assertTrue(hourlyTemperatureCurves(hourlyTemperatureTrend(items(20.0))).isEmpty())
        assertTrue(hourlyTemperatureCurves(hourlyTemperatureTrend(items(20.0, null, 21.0))).isEmpty())
        val inset = hourlyTemperatureCurves(hourlyTemperatureTrend(items(null, 20.0, 21.0, null))).single()
        assertEquals(1.5f, inset.segments.first().start.x, 0f)
        assertEquals(2.5f, inset.segments.last().end.x, 0f)
    }

    private fun slope(left: HourlyTemperatureKnot, right: HourlyTemperatureKnot): Float =
        (right.fraction - left.fraction) / (right.x - left.x)

    private fun fractionAt(segment: HourlyTemperatureSegment, t: Float): Float {
        val u = 1f - t
        return u * u * u * segment.start.fraction + 3f * u * u * t * segment.firstControl.fraction +
            3f * u * t * t * segment.secondControl.fraction + t * t * t * segment.end.fraction
    }
}
