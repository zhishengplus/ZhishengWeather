package com.zhisheng.weather.data

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class TyphoonDataQualityTest {
    @Test fun annualCatalogKeepsEveryStormAndDeduplicatesIds() {
        val rows = (1..35).map {
            """{"tfid":"2026${it.toString().padStart(2, '0')}","name":"测试","starttime":"2026-08-01 08:00:00"}"""
        }
        assertEquals(35, parseCatalogPayload((rows + rows.first()).joinToString(",", "[", "]")).size)
    }

    @Test fun emptySeasonIsValidButErrorObjectsAreNotEmptySeasons() {
        assertTrue(parseCatalogPayload("[]").isEmpty())
        assertTrue(parseCatalogPayload("""{"data":[]}""").isEmpty())
        assertThrows(IllegalStateException::class.java) { parseCatalogPayload("""{"message":"service unavailable"}""") }
        assertThrows(IllegalArgumentException::class.java) { parseCatalogPayload("""[{"tfid":"../path"}]""") }
    }

    @Test fun explicitUtcAndOffsetAreNotReinterpretedAsBeijingTime() {
        val expected = Instant.parse("2026-10-26T03:00:00Z").toEpochMilli()
        assertEquals(expected, parseTyphoonTime("2026-10-26 11:00:00"))
        assertEquals(expected, parseTyphoonTime("2026-10-26T03:00:00Z"))
        assertEquals(expected, parseTyphoonTime("2026-10-26T11:00:00+08:00"))
        assertEquals(expected, parseTyphoonTime("2026-10-25T23:00:00-04:00"))
        assertNull(parseTyphoonTime("2026-02-30 11:00:00"))
    }

    @Test fun invalidTimeAndMeasurementsNeverBecomeTimelineReadings() {
        val detail = parseDetailPayload("""{"points":[
          {"time":"broken","lat":15,"lng":120},
          {"time":"2026-10-26 11:00:00","lat":15,"lng":120,"power":-1,"speed":0,
           "pressure":0,"movespeed":-999,"strong":" ","radius7":"100|200","radius10":"Infinity"}
        ]}""", TyphoonStorm("202631", "测试"), 1L)
        val point = detail.observed.single()
        assertNull(point.windLevel)
        assertNull(point.windSpeedMs)
        assertNull(point.pressureHpa)
        assertNull(point.moveSpeedKmh)
        assertNull(point.intensity)
        assertNull(point.radius7)
        assertNull(point.radius10)
    }

    @Test fun detailRejectsAnotherStormAndUpdatesEndTimeFromDetail() {
        assertThrows(IllegalArgumentException::class.java) {
            parseDetailPayload("""{"tfid":"202630","points":[]}""", TyphoonStorm("202631", "测试"), 1L)
        }
        val detail = parseDetailPayload("""{"tfid":"202631","isactive":"0","endtime":"2026-10-26 11:00:00",
          "points":[{"time":"2026-10-26 11:00:00","lat":15,"lng":120}]}""",
            TyphoonStorm("202631", "测试", active = true), 1L)
        assertFalse(detail.storm.active)
        assertEquals("2026-10-26 11:00:00", detail.storm.endTime)
    }

    @Test fun forecastCannotGoBackwardsOrUseInvalidLatestObservation() {
        val detail = parseDetailPayload("""{"points":[
          {"time":"2026-10-26 11:00:00","lat":15,"lng":120,"forecast":[{"tm":"中国","forecastpoints":[
            {"time":"2026-10-26 05:00:00","lat":14,"lng":119},
            {"time":"2026-10-26 11:00:00","lat":15,"lng":120},
            {"time":"2026-10-26 17:00:00","lat":15,"lng":120},
            {"time":"2026-10-26 17:00:00","lat":15,"lng":120}
          ]}]},
          {"time":"2026-10-26 20:00:00","lat":999,"lng":120}
        ]}""", TyphoonStorm("202631", "测试"), 1L)
        assertEquals(1, detail.observed.size)
        assertEquals(listOf("2026-10-26 17:00:00"), detail.forecasts.single().points.map { it.time })
    }

    @Test fun sourceCompatibleLatestObservationPreservesUnitsAndQuadrants() {
        // Field shape and units checked against the official 202631 response; this is a fixed parser fixture.
        val detail = parseDetailPayload("""[{"tfid":"202631","name":"烟花","isactive":"1","points":[
          {"time":"2026-10-26 11:00:00","lat":"14.00","lng":"163.20","strong":"强台风",
           "power":"14","speed":"45","pressure":"950","radius7":"180|200|240|180"}
        ]}]""", TyphoonStorm("202631", "烟花"), 1L)
        val point = detail.observed.single()
        assertEquals(45.0, point.windSpeedMs!!, 0.0)
        assertEquals(950, point.pressureHpa)
        assertEquals(200.0, point.radius7!!.southEastKm!!, 0.0)
        assertEquals(240.0, point.radius7.southWestKm!!, 0.0)
    }

    @Test fun cachedDetailMustMatchSelectedStormStatusAndLatestCatalogTime() {
        val storm = TyphoonStorm("202631", "测试", active = true, endTime = "2026-10-26 11:00:00")
        val detail = TyphoonDetail(storm, listOf(TyphoonTrackPoint("2026-10-26 11:00:00", 14.0, 163.2)), emptyList(), 1L)
        assertTrue(cachedTyphoonDetailIsCurrent(detail, storm))
        assertFalse(cachedTyphoonDetailIsCurrent(detail, storm.copy(id = "202630")))
        assertFalse(cachedTyphoonDetailIsCurrent(detail, storm.copy(active = false)))
        assertFalse(cachedTyphoonDetailIsCurrent(detail, storm.copy(endTime = "2026-10-26 14:00:00")))
        assertFalse(cachedTyphoonDetailIsCurrent(detail.copy(observed = emptyList()), storm))
    }

    @Test fun forecastLeadHoursCannotBeMistakenForAbsoluteTargetTime() {
        val detail = parseDetailPayload("""{"points":[{"time":"2026-10-26 11:00:00","lat":14,"lng":163.2,
          "forecast":[{"tm":"中国","forecastpoints":[
            {"forecasttime":"12","lat":14.1,"lng":162.8},
            {"time":"2026-10-26 23:00:00","forecasttime":"12","ybsj":"2026-10-25T23:00:00-04:00","lat":14.1,"lng":162.8}
          ]}]}]}""", TyphoonStorm("202631", "测试"), 1L)
        assertEquals(listOf("2026-10-26 23:00:00"), detail.forecasts.single().points.map { it.time })
    }

    @Test fun stationaryZeroDirectionUsesReadableMeaningAndPreservesZeroSpeed() {
        val point = parseDetailPayload("""{"points":[{"time":"2026-10-26 11:00:00","lat":14,"lng":163.2,
          "movespeed":"0","movedirection":"0"}]}""", TyphoonStorm("202631", "测试"), 1L).observed.single()
        assertEquals("原地少动", point.moveDirection)
        assertEquals(0.0, point.moveSpeedKmh!!, 0.0)
    }
}
