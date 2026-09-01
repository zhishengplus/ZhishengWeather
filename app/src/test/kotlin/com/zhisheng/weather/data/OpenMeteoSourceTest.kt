package com.zhisheng.weather.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoSourceTest {
    @Test
    fun carbonMonoxideIsConvertedFromMicrogramsToMilligrams() {
        assertEquals("0.14", OpenMeteoSource.fmtCoMg(142.0))
        assertEquals("0.3", OpenMeteoSource.fmtCoMg(300.0))
    }

    @Test
    fun currentResponseAcceptsSurfacePressure() {
        val decoded = Json.decodeFromString<OmFull>(
            """{"current":{"time":"2026-08-31T01:15","surface_pressure":849.2,"pressure_msl":1015.4,"uv_index":0.0}}""",
        )

        assertEquals(849.2, decoded.current?.surface_pressure)
        assertEquals(1015.4, decoded.current?.pressure_msl)
        assertEquals(0.0, decoded.current?.uv_index ?: -1.0, 0.0001)
        assertEquals("2026-08-31T01:15", decoded.current?.time)
    }

    @Test
    fun fifteenMinuteRainUsesIntervalStartAndKeepsTwoHourCoverage() {
        val source = java.io.File(
            "src/main/kotlin/com/zhisheng/weather/data/OpenMeteoSource.kt",
        ).readText()
        assertTrue(source.contains("forecast_minutely_15=9"))
        assertTrue(source.contains("intervalEnd - 15 * 60_000L"))
    }
}
