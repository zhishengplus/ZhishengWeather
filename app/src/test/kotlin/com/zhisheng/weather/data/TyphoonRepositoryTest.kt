package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TyphoonRepositoryTest {
    @Test
    fun catalog_sortsActiveFirstAndPreservesOfficialIdentity() {
        val result = parseCatalogPayload(
            """[
              {"tfid":"202621","name":"艾莎尼","enname":"ATSANI","starttime":"2026-08-24 08:00:00","isactive":"0"},
              {"tfid":"202623","name":"班朗","enname":"BANG-LANG","starttime":"2026-08-28 14:00:00","isactive":"1"}
            ]""",
        )

        assertEquals("202623", result.first().id)
        assertTrue(result.first().active)
        assertEquals("BANG-LANG", result.first().englishName)
        assertFalse(result.last().active)
    }

    @Test
    fun detail_readsObservedAndChinaForecast_withoutDuplicatingCurrentPoint() {
        val payload = """{
          "tfid":"202623","name":"班朗","enname":"BANG-LANG","isactive":"1",
          "points":[
            {"time":"2026-08-28 14:00:00","lat":"18.0","lng":"155.0","power":"8","speed":"20","pressure":"995","strong":"热带风暴"},
            {"time":"2026-08-28 17:00:00","lat":"18.5","lng":"154.2","power":"9","speed":"23","pressure":"990","strong":"热带风暴","radius7":"180|160|150|170","forecast":[
              {"tm":"中国","forecastpoints":[
                {"time":"2026-08-28 17:00:00","lat":"18.5","lng":"154.2","power":"9"},
                {"time":"2026-08-29 05:00:00","lat":"20.1","lng":"151.8","power":"10","pressure":"985"}
              ]}
            ]}
          ]
        }"""

        val detail = parseDetailPayload(payload, TyphoonStorm("202623", "班朗"), 123L)

        assertEquals(2, detail.observed.size)
        val wind7 = requireNotNull(detail.observed.last().radius7)
        assertEquals(180.0, wind7.northEastKm!!, 0.0)
        assertEquals(160.0, wind7.southEastKm!!, 0.0)
        assertEquals(150.0, wind7.southWestKm!!, 0.0)
        assertEquals(170.0, wind7.northWestKm!!, 0.0)
        assertEquals("中国", detail.forecasts.single().agency)
        assertEquals(1, detail.forecasts.single().points.size)
        assertEquals(151.8, detail.forecasts.single().points.single().longitude, 0.0)
    }

    @Test
    fun detail_keepsOnlyChinaMeteorologicalForecast() {
        val payload = """{
          "points":[{
            "time":"2026-09-01 09:00:00","lat":"19.4","lng":"113.3",
            "forecast":[
              {"tm":"日本","forecastpoints":[{"time":"2026-09-01 21:00:00","lat":"20.0","lng":"112.0"}]},
              {"tm":"中国","forecastpoints":[
                {"time":"2026-09-01 09:00:00","lat":"19.4","lng":"113.3"},
                {"time":"2026-09-01 21:00:00","lat":"20.8","lng":"111.5"}
              ]},
              {"tm":"中国香港","forecastpoints":[{"time":"2026-09-01 21:00:00","lat":"19.9","lng":"112.2"}]}
            ]
          }]
        }"""
        val detail = parseDetailPayload(payload, TyphoonStorm("202618", "沙德尔"), 1L)
        assertEquals(listOf("中国"), detail.forecasts.map { it.agency })
        assertEquals(1, detail.forecasts.single().points.size)
        assertEquals(111.5, detail.forecasts.single().points.single().longitude, 0.0)
        assertTrue(isCmaForecastAgency("中国"))
        assertTrue(isCmaForecastAgency("中央气象台"))
        assertFalse(isCmaForecastAgency("中国香港"))
        assertFalse(isCmaForecastAgency("日本"))
    }

    @Test
    fun invalidCoordinates_areRejected() {
        val payload = """{"points":[
          {"time":"2026-08-28 14:00:00","lat":"181","lng":"20"},
          {"time":"2026-08-28 17:00:00","lat":"18","lng":"154"}
        ]}"""
        val detail = parseDetailPayload(payload, TyphoonStorm("202623", "班朗"), 1L)
        assertEquals(1, detail.observed.size)
    }

    @Test
    fun detail_preservesObjectQuadrantsForEveryWindCircle() {
        val payload = """{"points":[{
          "time":"2026-08-28 17:00:00","lat":18.5,"lng":154.2,
          "radius7_quad":{"ne":300,"se":260,"sw":220,"nw":280},
          "radius10":{"ne":100,"se":80,"sw":60,"nw":90},
          "radius12":"50|40|30|45"
        }]}"""

        val point = parseDetailPayload(payload, TyphoonStorm("202623", "班朗"), 1L).observed.single()
        assertEquals(220.0, point.radius7?.southWestKm!!, 0.0)
        assertEquals(90.0, point.radius10?.northWestKm!!, 0.0)
        assertEquals(40.0, point.radius12?.southEastKm!!, 0.0)
    }
}
