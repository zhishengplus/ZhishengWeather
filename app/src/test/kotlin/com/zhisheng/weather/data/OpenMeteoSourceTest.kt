package com.zhisheng.weather.data

import kotlinx.serialization.json.Json
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.RainMeta
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.hasDrySkyWindow
import com.zhisheng.weather.ui.precipitationPresentation
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val now = Instant.parse("2026-10-13T02:05:00Z").toEpochMilli()
        val times = (0..8).map { LocalDateTime.of(2026, 10, 13, 10, 0).plusMinutes(it * 15L).toString() }
        val points = OpenMeteoSource.minutelyToMinutes(OmMinutely15(times, List(9) { 0.25 }), 28800, now)
        assertEquals(8, points.size)
        assertEquals(Instant.parse("2026-10-13T02:00:00Z").toEpochMilli(), points.first().timeMillis)
        assertEquals(120 * 60_000L, points.last().timeMillis + 15 * 60_000L - points.first().timeMillis)
        assertEquals(1f, points.first().precip, 0f)
    }

    @Test
    fun missingPrecipitationNeverCreatesDryBucketsOrTheGameWindow() {
        val now = Instant.parse("2026-10-13T02:00:00Z").toEpochMilli()
        val times = (1..8).map { LocalDateTime.of(2026, 10, 13, 10, 0).plusMinutes(it * 15L).toString() }
        for (amounts in listOf(null, emptyList(), List<Double?>(8) { null })) {
            val parsed = Json.decodeFromString<OmMinutely15>(Json.encodeToString(OmMinutely15.serializer(),
                OmMinutely15(times, amounts)))
            assertTrue(OpenMeteoSource.minutelyToMinutes(parsed, 28800, now).isEmpty())
        }
        val partial = OpenMeteoSource.minutelyToMinutes(OmMinutely15(times,
            listOf(0.0, 0.0, null, 0.0, 0.0, 0.0, 0.0, 0.0)), 28800, now)
        assertEquals(7, partial.size)
        assertEquals(now + 3 * 15 * 60_000L, partial[2].timeMillis)
        val data = WeatherData(current = CurrentWeather(condition = WeatherCondition.CLEAR),
            rainMinutes = partial, rainMeta = RainMeta("OPEN-METEO", 15, now))
        assertFalse(precipitationPresentation(data, now).hasDrySkyWindow())
    }

    @Test
    fun invalidAmountsAndTimesKeepTheirOriginalIndexAndNeverExtendCoverage() {
        val start = LocalDateTime.of(2026, 10, 13, 10, 0)
        val now = start.toInstant(ZoneOffset.ofHours(8)).toEpochMilli()
        val times = (1..10).map { start.plusMinutes(it * 15L).toString() }.toMutableList()
        times[1] = "bad-date"
        val points = OpenMeteoSource.minutelyToMinutes(OmMinutely15(times,
            listOf(0.0, 1.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 0.5, 0.0)), 28800, now)
        assertEquals(listOf(now, now + 75 * 60_000L, now + 90 * 60_000L), points.map { it.timeMillis })
        assertEquals(listOf(0f, 2f, 0f), points.map { it.precip })
    }
}
