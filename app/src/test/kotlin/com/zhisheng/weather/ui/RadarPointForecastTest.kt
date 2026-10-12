package com.zhisheng.weather.ui

import com.zhisheng.weather.model.*
import org.junit.Assert.*
import org.junit.Test

class RadarPointForecastTest {
    private val now = 1_800_000_000_000L
    @Test fun hourlyWeatherDoesNotTurnIntoAFakeMinuteForecast() {
        assertTrue(radarPointForecast(WeatherData(hourly = listOf(HourlyWeather(now, precipMm = 1.0))), now).points.isEmpty())
    }
    @Test fun nativeFifteenMinuteBucketRemainsActiveUntilItsEnd() {
        val data = WeatherData(rainMeta = RainMeta("OPEN-METEO", 15, now - 600_000), rainMinutes = listOf(MinutePrecip(now - 600_000, .5f)))
        val result = radarPointForecast(data, now)
        assertEquals(15, result.intervalMinutes)
        assertEquals(1, result.points.size)
        assertEquals(now + 300_000, result.endTime)
    }
    @Test fun aPastMinutePointCannotRemainActiveThroughANearbyTimestampTolerance() {
        val result = radarPointForecast(WeatherData(rainMinutes = listOf(MinutePrecip(now - 90_000, .5f))), now)
        assertTrue(result.points.isEmpty())
        assertTrue(result.expired)
    }
    @Test fun partialHorizonIsPreservedAndDoesNotBecomeTwoHours() {
        val result = radarPointForecast(WeatherData(rainMeta = RainMeta("QWEATHER", 5, now),
            rainMinutes = listOf(MinutePrecip(now, 0f), MinutePrecip(now + 300_000, .4f))), now)
        assertEquals(now + 600_000, result.endTime)
        assertEquals(2, result.points.size)
    }
    @Test fun invalidValuesDuplicatesAndBeyondTwoHoursAreRemoved() {
        val result = radarPointForecast(WeatherData(rainMinutes = listOf(MinutePrecip(now, Float.NaN),
            MinutePrecip(now, .4f), MinutePrecip(now, .8f), MinutePrecip(now + 60_000, -1f),
            MinutePrecip(now + 7_200_000, 1f))), now)
        assertEquals(listOf(MinutePrecip(now, .4f)), result.points)
    }
    @Test fun expiredOrFutureDatedPublicationDoesNotShowAFreshCurve() {
        for (published in listOf(now - 1_800_001, now + 300_001)) {
            val result = radarPointForecast(WeatherData(rainMeta = RainMeta("QWEATHER", 5, published), rainMinutes = listOf(MinutePrecip(now, 0f))), now)
            assertTrue(result.expired); assertTrue(result.points.isEmpty()); assertNull(result.endTime)
        }
    }
    @Test fun gapRemainsNativeAndTheHorizonIsCappedWithoutSyntheticSamples() {
        val result = radarPointForecast(WeatherData(rainMeta = RainMeta("OPEN-METEO", 15, now),
            rainMinutes = listOf(MinutePrecip(now, 0f), MinutePrecip(now + 7_000_000, .5f))), now)
        assertEquals(2, result.points.size)
        assertEquals(now + 7_200_000, result.endTime)
        assertEquals(7_000_000, result.points[1].timeMillis - result.points[0].timeMillis)
    }
    @Test fun oldFetchTimeIsUsedWhenPublicationTimestampIsAbsent() {
        val result = radarPointForecast(WeatherData(fetchedAt = now - 1_900_000, rainMinutes = listOf(MinutePrecip(now, .2f))), now)
        assertTrue(result.expired); assertTrue(result.points.isEmpty())
    }
}
