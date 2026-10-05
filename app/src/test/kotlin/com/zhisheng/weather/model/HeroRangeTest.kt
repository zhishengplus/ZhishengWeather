package com.zhisheng.weather.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HeroRangeTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = DailyWeather(dateMillis = at(0), high = 24.0, low = 8.0)
    private val tomorrow = DailyWeather(
        dateMillis = LocalDateTime.of(2026, 8, 22, 0, 0).atZone(zone).toInstant().toEpochMilli(),
        high = 18.0,
        low = 6.0,
    )
    private val yesterday = YesterdayInfo(high = 22.0, low = 5.0)

    private fun at(hour: Int): Long =
        LocalDateTime.of(2026, 8, 21, hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun morningUsesYesterdayLowAndTodayHigh() {
        val r = HeroTemps.range(listOf(today, tomorrow), yesterday, at(7), zone)
        assertEquals("昨晚最低", r.leftLabel)
        assertEquals(5.0, r.left)
        assertEquals("今天最高", r.rightLabel)
        assertEquals(24.0, r.right)
    }

    @Test
    fun missingYesterdayLeavesMorningLowEmpty() {
        val r = HeroTemps.range(listOf(today, tomorrow), null, at(7), zone)
        assertEquals("昨晚最低", r.leftLabel)
        assertEquals(null, r.left)
        assertEquals(24.0, r.right)
    }

    @Test
    fun daytimeUsesTodayHighLow() {
        val r = HeroTemps.range(listOf(today, tomorrow), yesterday, at(14), zone)
        assertEquals("高", r.leftLabel)
        assertEquals(24.0, r.left)
        assertEquals("低", r.rightLabel)
        assertEquals(8.0, r.lowOrRight())
    }

    @Test
    fun eveningWithoutHourlyUsesExplicitTomorrowLabel() {
        // Daily-only sources must not call a calendar-day minimum tonight.
        val r = HeroTemps.range(listOf(today, tomorrow), yesterday, at(21), zone)
        assertEquals("明日最低", r.leftLabel)
        assertEquals(6.0, r.left)
        assertEquals("明天最高", r.rightLabel)
        assertEquals(18.0, r.right)
    }

    @Test
    fun eveningFallsBackToTodayLowWhenTomorrowMissing() {
        val r = HeroTemps.range(listOf(today), yesterday, at(21), zone)
        assertEquals("今日最低", r.leftLabel)
        assertEquals("今天最高", r.rightLabel)
        assertEquals(8.0, r.left)
        assertEquals(24.0, r.right)
    }

    @Test
    fun feelsLikeHidesWhenClose() {
        assertFalse(HeroTemps.showFeelsLike(20.0, 20.4))
        assertTrue(HeroTemps.showFeelsLike(20.0, 22.0))
        assertFalse(HeroTemps.showFeelsLike(20.0, null))
    }

    @Test
    fun eveningUsesCityTimezoneNotThePhoneDefault() {
        val ny = ZoneId.of("America/New_York")
        val now = LocalDateTime.of(2026, 8, 21, 21, 0).atZone(ny).toInstant().toEpochMilli()
        val todayMs = LocalDateTime.of(2026, 8, 21, 0, 0).atZone(ny).toInstant().toEpochMilli()
        val tomorrowMs = LocalDateTime.of(2026, 8, 22, 0, 0).atZone(ny).toInstant().toEpochMilli()
        val r = HeroTemps.range(
            listOf(
                DailyWeather(dateMillis = todayMs, high = 24.0, low = 8.0),
                DailyWeather(dateMillis = tomorrowMs, high = 18.0, low = 6.0),
            ),
            yesterday,
            now,
            ny,
        )
        assertEquals("明日最低", r.leftLabel)
        assertEquals(6.0, r.left)
        assertEquals("明天最高", r.rightLabel)
        assertEquals(18.0, r.right)
    }

    @Test
    fun daytimePicksCalendarTodayWhenDailyStartsOnYesterday() {
        val yesterdayMs = LocalDateTime.of(2026, 8, 20, 22, 0).atZone(zone).toInstant().toEpochMilli()
        val todayMs = LocalDateTime.of(2026, 8, 21, 0, 0).atZone(zone).toInstant().toEpochMilli()
        val tomorrowMs = LocalDateTime.of(2026, 8, 22, 0, 0).atZone(zone).toInstant().toEpochMilli()
        val r = HeroTemps.range(
            listOf(
                DailyWeather(dateMillis = yesterdayMs, high = 30.0, low = 1.0),
                DailyWeather(dateMillis = todayMs, high = 24.0, low = 8.0),
                DailyWeather(dateMillis = tomorrowMs, high = 18.0, low = 6.0),
            ),
            yesterday,
            at(14),
            zone,
        )
        assertEquals("高", r.leftLabel)
        assertEquals(24.0, r.left)
        assertEquals(8.0, r.right)
    }

    @Test
    fun staleDailyRowsDoNotMasqueradeAsToday() {
        val stale = DailyWeather(
            dateMillis = LocalDateTime.of(2026, 8, 20, 0, 0).atZone(zone).toInstant().toEpochMilli(),
            high = 31.0,
            low = 19.0,
        )
        val r = HeroTemps.range(listOf(stale), yesterday, at(14), zone)

        assertEquals(null, r.left)
        assertEquals(null, r.right)
    }

    @Test
    fun completeNightUsesHourlyLowEvenWhenDailyMinimumDisagrees() {
        val hours = (0..11).map { offset ->
            HourlyWeather(at(21) + offset * 3_600_000L, temperature = if (offset == 8) 18.0 else 22.0)
        }
        val r = HeroTemps.range(listOf(today, tomorrow.copy(low = 20.0)), yesterday, at(21), zone, hours)
        assertEquals("今夜最低", r.leftLabel)
        assertEquals(18.0, r.left)
    }

    @Test
    fun ShortNightForecastDoesNotClaimToKnowTheNightMinimum() {
        val hours = listOf(HourlyWeather(at(21), temperature = 22.0), HourlyWeather(at(22), temperature = 21.0))
        val r = HeroTemps.range(listOf(today, tomorrow), yesterday, at(21), zone, hours)
        assertEquals("明日最低", r.leftLabel)
        assertEquals(6.0, r.left)
    }

    @Test
    fun MissingMiddleOfNightDoesNotClaimCompleteCoverage() {
        val hours = listOf(HourlyWeather(at(21), temperature = 22.0), HourlyWeather(at(21) + 10 * 3_600_000L, temperature = 18.0))
        val r = HeroTemps.range(listOf(today, tomorrow), yesterday, at(21), zone, hours)
        assertEquals("明日最低", r.leftLabel)
    }

    private fun HeroRange.lowOrRight() = right
}
