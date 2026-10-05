package com.zhisheng.weather.ui

import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.WeatherCondition
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastDigestRegressionTest {
    private val nowMillis = Instant.parse("2026-09-30T09:30:00Z").toEpochMilli()
    private val today = LocalDate.of(2026, 9, 30)

    @Test fun positivePrecipitationAmountCountsWithoutAnIconOrProbability() {
        val digest = buildForecastDigest(
            listOf(day(0, precipMm = 5.0)), "c", 0, nowMillis = nowMillis,
        )

        assertEquals("1 日", digest.rainValue)
        assertTrue(digest.overview.contains("1 天可能下雨"))
    }

    @Test fun missingPrecipitationDataIsNotReportedAsZeroRainDays() {
        val digest = buildForecastDigest(listOf(day(0)), "c", 0, nowMillis = nowMillis)

        assertEquals("—", digest.rainValue)
        assertFalse(digest.overview.contains("没看到"))
        assertFalse(digest.overview.contains("没有明显降水"))
        assertFalse(digest.overview.contains("放心"))
    }

    @Test fun explicitZeroPrecipitationStillReportsADryDay() {
        val digest = buildForecastDigest(listOf(day(0, precipMm = 0.0)), "c", 0, nowMillis = nowMillis)

        assertEquals("0 日", digest.rainValue)
    }

    @Test fun absentTomorrowDoesNotTurnTheSecondAvailableDateIntoTomorrow() {
        val digest = buildForecastDigest(
            listOf(day(0, WeatherCondition.CLEAR), day(2, WeatherCondition.RAIN)), "c", 0, nowMillis = nowMillis,
        )

        assertFalse(digest.headline, digest.headline.contains("明天"))
    }

    @Test fun forecastStartingTomorrowRecognizesRainOnThatActualDate() {
        val digest = buildForecastDigest(
            listOf(day(1, WeatherCondition.RAIN), day(2, WeatherCondition.CLEAR)), "c", 0, nowMillis = nowMillis,
        )

        assertTrue(digest.headline, digest.headline.contains("明天"))
    }

    @Test fun separatedRainDatesAreNotDescribedAsConsecutiveRain() {
        val digest = buildForecastDigest(
            listOf(day(0, WeatherCondition.RAIN), day(2, WeatherCondition.RAIN), day(4, WeatherCondition.RAIN)),
            "c", 0, nowMillis = nowMillis,
        )

        assertFalse(digest.headline, listOf("连着", "连阴", "接二连三").any { it in digest.headline })
    }

    private fun day(
        daysFromToday: Long,
        condition: WeatherCondition? = null,
        precipMm: Double? = null,
    ) = DailyWeather(
        dateMillis = today.plusDays(daysFromToday).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        high = 25.0,
        low = 18.0,
        condition = condition,
        precipMm = precipMm,
    )
}
