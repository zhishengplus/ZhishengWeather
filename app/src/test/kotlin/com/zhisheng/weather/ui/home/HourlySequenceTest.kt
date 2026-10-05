package com.zhisheng.weather.ui.home

import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.HourlyWeather
import org.junit.Assert.*
import org.junit.Test

class HourlySequenceTest {
    @Test fun exactHourShowsPreviousForecastThenOneRealNowWithoutDuplicateTimestamp() {
        val now = 1_800_000_000_000L
        val previous = HourlyWeather(now - 3_600_000L, 21.0)
        val sameHour = HourlyWeather(now, 22.0, precipProb = 30)
        val next = HourlyWeather(now + 3_600_000L, 24.0)
        val result = hourlyDisplayItems(CurrentWeather(temperature = 23.0), listOf(previous, sameHour, next), now)
        assertEquals(listOf(previous.timeMillis, now, next.timeMillis), result.map { it.weather.timeMillis })
        assertEquals(30, result[1].weather.precipProb)
        assertEquals(23.0, result[1].weather.temperature!!, 0.0)
    }
    private val now = 1_800_000_000_000L
    @Test fun missingHourlyAnchorStillDisplaysRealObservationBeforeFutureForecast() {
        val result = hourlyDisplayItems(CurrentWeather(temperature = 23.0), listOf(HourlyWeather(now + 7_200_000L, 25.0)), now)
        assertEquals(listOf(true, false), result.map { it.isNow })
        assertEquals(23.0, result.first().weather.temperature!!, 0.0)
    }
    @Test fun duplicateAndUnsortedForecastsProduceOneChronologicalSequence() {
        val past = HourlyWeather(now - 1_200_000L, 21.0)
        val next = HourlyWeather(now + 2_400_000L, 24.0)
        val result = hourlyDisplayItems(CurrentWeather(temperature = 23.0), listOf(next, past, next), now)
        assertEquals(listOf(past.timeMillis, now, next.timeMillis), result.map { it.weather.timeMillis })
        assertEquals(listOf(false, true, false), result.map { it.isNow })
    }
    @Test fun absentObservationNeverCreatesNowAndDropsObsoleteForecasts() {
        val result = hourlyDisplayItems(null, listOf(HourlyWeather(now - 7_200_000L, 10.0), HourlyWeather(now + 60_000L, 21.0)), now)
        assertEquals(1, result.size)
        assertFalse(result.single().isNow)
    }
    @Test fun emptyPayloadDoesNotInventObservation() {
        assertTrue(hourlyDisplayItems(null, emptyList(), now).isEmpty())
        assertEquals(1, hourlyDisplayItems(CurrentWeather(temperature = 23.0), emptyList(), now).size)
    }
}
