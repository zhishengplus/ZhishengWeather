package com.zhisheng.weather.ui

import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.ui.home.hourlyDisplayItems
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class HourlyTimeLabelsTest {
    @Test fun previousForecastKeepsItsClockLabelAndOnlyTheObservationSaysNow() {
        val now = epoch("2026-09-30T09:30:00Z")
        val display = hourlyDisplayItems(
            CurrentWeather(temperature = 23.0),
            listOf(HourlyWeather(epoch("2026-09-30T09:00:00Z")), HourlyWeather(epoch("2026-09-30T10:00:00Z"))),
            now,
        )

        assertEquals(listOf("09:00", "现在", "10:00"), hourlyAxisLabels(display, 0, now).map { it.second })
    }

    @Test fun futureForecastWithoutAnObservationDoesNotSayNow() {
        val now = epoch("2026-09-30T09:30:00Z")
        val display = hourlyDisplayItems(
            null,
            listOf(HourlyWeather(epoch("2026-09-30T10:00:00Z")), HourlyWeather(epoch("2026-09-30T11:00:00Z"))),
            now,
        )

        assertEquals(listOf("10:00", "11:00"), hourlyAxisLabels(display, 0, now).map { it.second })
    }

    @Test fun midnightAxisComparesForecastDatesWithTheCurrentCityDay() {
        val now = epoch("2026-09-30T16:30:00Z") // Oct 1, 00:30 in China.
        val display = hourlyDisplayItems(
            CurrentWeather(temperature = 23.0),
            listOf(HourlyWeather(epoch("2026-09-30T16:00:00Z")), HourlyWeather(epoch("2026-09-30T17:00:00Z"))),
            now,
        )

        assertEquals(listOf("00:00", "现在", "01:00"), hourlyAxisLabels(display, 28_800, now).map { it.second })
    }

    @Test fun midnightBubblesDoNotCallThePreviousForecastDayToday() {
        val now = epoch("2026-09-30T16:10:00Z") // Oct 1, 00:10 in China.
        val display = hourlyDisplayItems(
            CurrentWeather(temperature = 23.0),
            listOf(HourlyWeather(epoch("2026-09-30T15:30:00Z")), HourlyWeather(epoch("2026-09-30T17:00:00Z"))),
            now,
        )

        assertEquals(listOf("9/30 23:30", "今天 00:10", "今天 01:00"), hourlyBubbleLabels(display, 28_800, now))
    }

    private fun epoch(value: String) = Instant.parse(value).toEpochMilli()
}
