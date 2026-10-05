package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TyphoonStationaryForecastRegressionTest {
    @Test fun laterStationaryForecastIsPreservedWhileCurrentAnchorIsRemoved() {
        val payload = """{
            "tfid":"202624","name":"测试台风","isactive":"1",
            "points":[{
                "time":"2026-09-30 10:00:00","lat":18.5,"lng":154.2,
                "power":9,"speed":23,"pressure":990,"strong":"热带风暴",
                "forecast":[{"tm":"中国","forecastpoints":[
                    {"time":"2026-09-30 10:00:00","lat":18.5,"lng":154.2,
                     "power":9,"speed":23,"pressure":990,"strong":"热带风暴"},
                    {"time":"2026-09-30 16:00:00","lat":18.5,"lng":154.2,
                     "power":10,"speed":28,"pressure":985,"strong":"强热带风暴"}
                ]}]
            }]
        }"""

        val detail = parseDetailPayload(payload, TyphoonStorm("202624", "测试台风"), 1L)

        assertEquals(1, detail.forecasts.size)
        val forecast = detail.forecasts.single()
        assertEquals("中国", forecast.agency)
        assertEquals(listOf("2026-09-30 16:00:00"), forecast.points.map { it.time })
        assertEquals(10, forecast.points.single().windLevel)
        assertEquals(985, forecast.points.single().pressureHpa)
        assertEquals(18.5, forecast.points.single().latitude, 0.001)
        assertEquals(154.2, forecast.points.single().longitude, 0.001)
    }
}
