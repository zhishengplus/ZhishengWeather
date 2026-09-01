package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 彩云雷达图接口解析：帧列表、边界、鉴权失败与 URL 补全
class CaiyunRadarRepositoryTest {

    private val payload = """
        {
          "status": "ok",
          "station": "CN03",
          "images": [
            [
              "http://cdn.caiyunapp.com/weather/radar/o/cnmap_a.png?auth_key=x",
              1640787600,
              [3.9079, 71.9282, 57.9079, 150.6026]
            ],
            [
              "/weather/radar/o/cnmap_b.png?auth_key=y",
              1640787900,
              [3.9, 71.9, 57.9, 150.6]
            ]
          ]
        }
    """.trimIndent()

    @Test
    fun parsesFrameUrlTimestampAndBounds() {
        val frames = CaiyunRadarRepository.parseCaiyunFrames(payload)

        assertEquals(2, frames?.size)
        val first = frames!![0]
        assertEquals(1_640_787_600_000L, first.timeMillis)
        assertEquals("http://cdn.caiyunapp.com/weather/radar/o/cnmap_a.png?auth_key=x", first.imageUrl)
        assertEquals(3.9079, first.southLat!!, 1e-9)
        assertEquals(71.9282, first.westLng!!, 1e-9)
        assertEquals(57.9079, first.northLat!!, 1e-9)
        assertEquals(150.6026, first.eastLng!!, 1e-9)
        assertTrue(first.isImageFrame)
    }

    @Test
    fun relativeImageUrlGetsCdnHost() {
        val frames = CaiyunRadarRepository.parseCaiyunFrames(payload)
        assertTrue(frames!![1].imageUrl!!.startsWith("https://cdn.caiyunapp.com/"))
    }

    @Test
    fun unauthorizedStatusYieldsNull() {
        val denied = payload.replace("\"status\": \"ok\"", "\"status\": \"no_permission\"")
        assertNull(CaiyunRadarRepository.parseCaiyunFrames(denied))
    }

    @Test
    fun missingImagesOrGarbageYieldsNull() {
        assertNull(CaiyunRadarRepository.parseCaiyunFrames("{\"status\":\"ok\"}"))
        assertNull(CaiyunRadarRepository.parseCaiyunFrames("not json"))
        assertNull(CaiyunRadarRepository.parseCaiyunFrames(""))
    }

    @Test
    fun statusReaderSurvivesBadPayload() {
        assertEquals("ok", CaiyunRadarRepository.caiyunStatusOf(payload))
        assertEquals("", CaiyunRadarRepository.caiyunStatusOf("garbage"))
    }

    @Test
    fun malformedFrameEntriesAreSkipped() {
        val bad = """
            {"status":"ok","images":[
              ["http://cdn.caiyunapp.com/ok.png", 1640787600, [3.9, 71.9, 57.9, 150.6]],
              ["no-url", 1640787900, [3.9, 71.9, 57.9, 150.6]],
              ["http://cdn.caiyunapp.com/no-bounds.png", 1640788200, [3.9]]
            ]}
        """.trimIndent()
        val frames = CaiyunRadarRepository.parseCaiyunFrames(bad)
        assertEquals(1, frames?.size)
    }
}
