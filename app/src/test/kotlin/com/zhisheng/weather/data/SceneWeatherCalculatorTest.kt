package com.zhisheng.weather.data

import com.zhisheng.weather.model.GlowKind
import com.zhisheng.weather.model.MarinePoint
import com.zhisheng.weather.model.TideTrend
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneWeatherCalculatorTest {
    @Test
    fun goodLayeredCloudsProduceWorthWaitingGlow() {
        val event = Instant.parse("2026-09-04T10:00:00Z").toEpochMilli()
        val hours = (-1L..1L).map { offset ->
            hour(event + offset * 3_600_000L, low = 12.0, mid = 48.0, high = 42.0, cloud = 55.0, visibility = 24.0)
        }
        val result = SceneWeatherCalculator.glow(hours, listOf(event to GlowKind.DUSK), event - 2 * 3_600_000L)

        assertNotNull(result)
        assertEquals("很值得等", result?.grade)
        assertEquals(3, result?.reasons?.size)
    }

    @Test
    fun rainFogAndClosedLowCloudHardDowngradeGlow() {
        val event = Instant.parse("2026-09-04T10:00:00Z").toEpochMilli()
        val hours = listOf(hour(event, low = 96.0, mid = 50.0, high = 50.0, cloud = 100.0, visibility = 1.5, rain = 1.2))
        val result = SceneWeatherCalculator.glow(hours, listOf(event to GlowKind.DUSK), event - 1_000L)

        assertEquals("不建议专程去", result?.grade)
    }

    @Test
    fun starWindowRequiresAstronomicalNightAndTwoContinuousHours() {
        val midnight = Instant.parse("2026-09-04T16:00:00Z").toEpochMilli() // 北京本地午夜附近
        val clear = (0L..4L).map { hour(midnight + it * 3_600_000L, low = 3.0, mid = 3.0, high = 4.0, cloud = 5.0, visibility = 30.0) }
        val result = SceneWeatherCalculator.stars(clear, 39.9, 116.4, midnight)

        assertNotNull(result)
        assertTrue(result!!.score >= 60)
        assertEquals(2 * 3_600_000L, result.endMillis - result.startMillis)
        val noon = Instant.parse("2026-09-05T04:00:00Z").toEpochMilli()
        assertNull(SceneWeatherCalculator.stars((0L..2L).map { hour(noon + it * 3_600_000L) }, 39.9, 116.4, noon))
    }

    @Test
    fun tideFindsTrendAndNextHighLowAcrossSequence() {
        val start = Instant.parse("2026-09-04T00:00:00Z").toEpochMilli()
        val levels = listOf(0.0, 0.4, 0.8, 0.3, -0.2, 0.1, 0.6)
        val points = levels.mapIndexed { index, value -> MarinePoint(start + index * 3_600_000L, value) }
        val result = SceneWeatherCalculator.tide(points, start + 30 * 60_000L)

        assertEquals(TideTrend.RISING, result.first)
        assertEquals(start + 2 * 3_600_000L, result.second?.timeMillis)
        assertEquals(start + 4 * 3_600_000L, result.third?.timeMillis)
    }

    @Test
    fun coastGridDistanceUsesRealGreatCircleDistance() {
        assertTrue(SceneWeatherRepository.haversineKm(22.55, 114.10, 22.55, 114.20) in 9.0..12.0)
        assertTrue(SceneWeatherRepository.haversineKm(39.90, 116.40, 39.90, 117.80) > SceneWeatherRepository.MAX_COAST_GRID_DISTANCE_KM)
    }

    @Test
    fun solarAndLunarAnchorsStayPhysicallyPlausible() {
        val equinoxNoon = Instant.parse("2026-03-20T12:00:00Z").toEpochMilli()
        val equinoxMidnight = Instant.parse("2026-03-20T00:00:00Z").toEpochMilli()
        assertTrue(Astronomy.sunAltitudeDegrees(equinoxNoon, 0.0, 0.0) > 85.0)
        assertTrue(Astronomy.sunAltitudeDegrees(equinoxMidnight, 0.0, 0.0) < -85.0)
        val newMoon = Instant.parse("2024-08-04T11:13:00Z").toEpochMilli()
        val fullMoon = Instant.parse("2024-08-19T18:26:00Z").toEpochMilli()
        assertTrue(MoonCalc.illuminationFraction(newMoon) < 0.05)
        assertTrue(MoonCalc.illuminationFraction(fullMoon) > 0.95)
    }

    private fun hour(
        time: Long,
        low: Double = 10.0,
        mid: Double = 10.0,
        high: Double = 10.0,
        cloud: Double = 15.0,
        visibility: Double = 20.0,
        rain: Double = 0.0,
    ) = SceneHour(
        timeMillis = time,
        temperature = 15.0,
        humidity = 55.0,
        dewPoint = 6.0,
        precipitation = rain,
        precipitationProbability = if (rain > 0) 80.0 else 5.0,
        visibilityKm = visibility,
        cloud = cloud,
        lowCloud = low,
        midCloud = mid,
        highCloud = high,
        windKmh = 8.0,
        gustKmh = 12.0,
    )
}
