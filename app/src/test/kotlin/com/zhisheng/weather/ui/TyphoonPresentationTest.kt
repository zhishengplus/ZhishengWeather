package com.zhisheng.weather.ui

import com.zhisheng.weather.data.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TyphoonPresentationTest {
    private val observed = TyphoonTrackPoint("2026-10-26 11:00:00", 20.0, 130.0, windLevel = 14)
    private val forecastPoint = observed.copy(time = "2026-10-26 23:00:00")
    private val forecast = TyphoonForecastTrack("中国", listOf(forecastPoint))
    private fun detail(active: Boolean = true) = TyphoonDetail(TyphoonStorm("202631", "烟花", active = active),
        listOf(observed), listOf(forecast), 0L)

    @Test fun hidingForecastPreservesObservedSelection() {
        val all = typhoonDisplayPoints(detail(), forecast)
        val onlyObserved = typhoonDisplayPoints(detail(), null)
        assertEquals(2, all.size)
        assertEquals(1, onlyObserved.size)
        assertEquals(typhoonPointKey(all.first()), typhoonPointKey(onlyObserved.first()))
        assertFalse(onlyObserved.first().forecast)
    }
    @Test fun stationaryForecastRemainsDistinctFromObservation() {
        val observedSelection = TyphoonMapSelection(observed, false)
        val futureSelection = TyphoonMapSelection(forecastPoint, true)
        assertNotEquals(typhoonPointKey(observedSelection), typhoonPointKey(futureSelection))
    }
    @Test fun cacheAndOldObservationsNeverMasqueradeAsLive() {
        val now = Instant.parse("2026-10-26T10:00:00Z").toEpochMilli()
        assertTrue(typhoonObservationStatus(detail(), false, now).startsWith("实况较旧"))
        assertTrue(typhoonObservationStatus(detail(), true, now).startsWith("缓存资料"))
        assertTrue(typhoonObservationStatus(detail(false), false, now).startsWith("历史路径"))
        assertTrue(typhoonObservationStatus(detail(), false, now - 7 * 3600_000).startsWith("最新实况"))
    }
    @Test fun coordinatesKeepSouthernAndWesternHemispheres() {
        assertEquals("20°S", typhoonCoordinate(-20.0, true))
        assertEquals("130.5°W", typhoonCoordinate(-130.5, false))
        assertEquals("20°N", typhoonCoordinate(20.0, true))
    }
    @Test fun partialRadiiAndZeroRemainDistinct() {
        assertEquals("100 / — / 0 / 80", typhoonRadiiLabel(TyphoonWindRadii(100.0, null, 0.0, 80.0)))
        assertEquals("— / — / — / —", typhoonRadiiLabel(null))
    }
    @Test fun utcTimeIsPresentedInBeijingTime() {
        assertEquals("10月26日 11:00", formatTyphoonTime("2026-10-26T03:00:00Z"))
        assertEquals("时次未提供", formatTyphoonTime("not a date"))
    }
}
