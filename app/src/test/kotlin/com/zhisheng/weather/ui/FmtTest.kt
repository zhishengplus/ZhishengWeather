package com.zhisheng.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FmtTest {

    @Test
    fun temperatureHandlesUnitsRoundingAndMissingValues() {
        assertEquals("24", Fmt.temp(23.6, "c"))
        assertEquals("74", Fmt.temp(23.6, "f"))
        assertEquals("°C", Fmt.unitSuffix("c"))
        assertEquals("°F", Fmt.unitSuffix("f"))
        assertNull(Fmt.temp(null, "c"))
    }

    @Test
    fun probabilityRejectsOutOfRangeValuesBeforeTheyReachLayout() {
        assertEquals("60%", Fmt.probability(60))
        assertEquals("100%", Fmt.probability(100))
        assertNull(Fmt.probability(0))
        assertNull(Fmt.probability(6000))
        assertNull(Fmt.probability(null))
    }

    @Test
    fun windConvertsToMetersPerSecond() {
        assertEquals("1.0 m/s", Fmt.wind(3.6, "ms"))
        assertEquals("1.0", Fmt.windValue(3.6, "ms"))
        assertNull(Fmt.wind(null, "ms"))
    }

    @Test
    fun beaufortBoundariesDoNotSlipByOneLevel() {
        val cases = listOf(
            0.0 to "0",
            0.99 to "0",
            1.0 to "1",
            5.99 to "1",
            6.0 to "2",
            11.99 to "2",
            12.0 to "3",
            28.99 to "4",
            29.0 to "5",
            49.99 to "6",
            50.0 to "7",
            74.99 to "8",
            75.0 to "9",
            102.99 to "10",
            103.0 to "11",
            117.99 to "11",
            118.0 to "12",
        )

        cases.forEach { (kmh, expected) ->
            assertEquals("Unexpected Beaufort value at $kmh km/h", expected, Fmt.windValue(kmh, "bft"))
        }
    }

    @Test
    fun pressureUsesStableDisplayPrecision() {
        assertEquals("1013 hPa", Fmt.pressure(1013.25, "hpa"))
        assertEquals("760 mmHg", Fmt.pressure(1013.25, "mmhg"))
        assertEquals("29.92 inHg", Fmt.pressure(1013.25, "inhg"))
        assertNull(Fmt.pressure(null, "hpa"))
    }

    @Test
    fun moonPhaseNamesAcceptProviderFormattingVariants() {
        assertEquals("满月", Fmt.moonPhaseZh("FULL MOON"))
        assertEquals("上弦月", Fmt.moonPhaseZh("first-quarter"))
        assertEquals("未知相位", Fmt.moonPhaseZh("未知相位"))
        assertNull(Fmt.moonPhaseZh(null))
    }

    @Test
    fun cityTimeLabelsUseTheWeatherLocationOffset() {
        val instant = java.time.Instant.parse("2026-08-26T00:30:00Z").toEpochMilli()

        assertEquals("9时", Fmt.hour(instant, 9 * 3_600))
        assertEquals("19时", Fmt.hour(instant, -5 * 3_600))
        assertEquals("周三", Fmt.weekday(instant, 1, 9 * 3_600))
        assertEquals("周二", Fmt.weekday(instant, 1, -5 * 3_600))
        assertEquals("8月26日 周三", Fmt.date(instant, 9 * 3_600))
        assertEquals("8月25日 周二", Fmt.date(instant, -5 * 3_600))
        assertEquals("08-26 09:30", Fmt.stamp(instant, 9 * 3_600))
        assertEquals("08-25 19:30", Fmt.stamp(instant, -5 * 3_600))
    }

    @Test
    fun dailyLabelsUseCityDateInsteadOfListIndex() {
        val now = java.time.Instant.parse("2026-08-31T16:30:00Z").toEpochMilli()
        val sameCityDay = java.time.Instant.parse("2026-09-01T03:00:00Z").toEpochMilli()
        val previousCityDay = java.time.Instant.parse("2026-08-31T03:00:00Z").toEpochMilli()

        assertEquals("今天", Fmt.dailyDayLabel(sameCityDay, now, 8 * 3_600))
        assertEquals("周一", Fmt.dailyDayLabel(previousCityDay, now, 8 * 3_600))
        assertEquals("1日", Fmt.dayOfMonth(sameCityDay, 8 * 3_600))
        assertEquals("9月", Fmt.month(sameCityDay, 8 * 3_600))
        assertEquals(true, Fmt.isDifferentMonth(previousCityDay, sameCityDay, 8 * 3_600))
    }

    @Test
    fun coordinatesUseAbsoluteValuesAndCorrectHemispheres() {
        assertEquals("39.90N  116.41E", Fmt.coordinates(39.90, 116.41))
        assertEquals("33.87S  151.21E", Fmt.coordinates(-33.87, 151.21))
        assertEquals("34.60S  58.38W", Fmt.coordinates(-34.60, -58.38))
        assertEquals("39.90N  116.41E", Fmt.coordinates(39.9042, 116.4074, precise = true))
    }

    @Test
    fun aqiStandardUsesAStandardLabelInsteadOfLookingLikeALocation() {
        assertEquals("国标", Fmt.aqiStandardLabel("中国"))
        assertEquals("美标", Fmt.aqiStandardLabel("美国"))
        assertEquals("QAQI", Fmt.aqiStandardLabel("QWeather"))
        assertNull(Fmt.aqiStandardLabel(null))
    }
}
