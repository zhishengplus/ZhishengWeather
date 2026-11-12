package com.zhisheng.weather.data

import java.time.Instant
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MoonCalcTest {
    @Test fun waxingAndWaningKeepOppositeIlluminatedSidesIndependentlyOfTheme() {
        for (date in listOf("2024-08-08", "2024-08-16"))
            assertTrue(MoonCalc.isWaxing(Instant.parse("${date}T12:00:00Z").toEpochMilli()))
        for (date in listOf("2024-08-22", "2024-08-29"))
            assertTrue(!MoonCalc.isWaxing(Instant.parse("${date}T12:00:00Z").toEpochMilli()))
    }

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun rememberTimeZone() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun identifiesKnownAugust2024PhaseDates() {
        assertEquals("new-moon", phaseAtNoon("2024-08-04"))
        assertEquals("first-quarter", phaseAtNoon("2024-08-12"))
        assertEquals("full-moon", phaseAtNoon("2024-08-19"))
        assertEquals("last-quarter", phaseAtNoon("2024-08-26"))
    }

    @Test
    fun identifiesPeriodsBetweenMajorPhases() {
        assertEquals("waxing-crescent", phaseAtNoon("2024-08-08"))
        assertEquals("waxing-gibbous", phaseAtNoon("2024-08-16"))
        assertEquals("waning-gibbous", phaseAtNoon("2024-08-22"))
        assertEquals("waning-crescent", phaseAtNoon("2024-08-29"))
    }

    @Test
    fun usesTheDeviceLocalDayForPhaseEvents() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
        val afternoonUtc = Instant.parse("2024-08-04T12:00:00Z").toEpochMilli()

        assertEquals("new-moon", MoonCalc.phaseKey(afternoonUtc))
    }

    @Test
    fun calculatesMoonriseAndMoonsetForARegularLatitude() {
        // 2026-08-10 00:00 at Beijing (UTC+8).
        val beijingDayStart = Instant.parse("2026-08-09T16:00:00Z").toEpochMilli()
        val times = MoonCalc.riseSet(beijingDayStart, 39.90, 116.41)

        requireNotNull(times.rise)
        requireNotNull(times.set)
        assertTrue(times.rise.matches(Regex("[0-2]\\d:[0-5]\\d")))
        assertTrue(times.set.matches(Regex("[0-2]\\d:[0-5]\\d")))
        // 公历天文表约为 01:50 / 17:43；本地轻量算法控制在 20 分钟内。
        assertWithinMinutes("01:50", times.rise, 20)
        assertWithinMinutes("17:43", times.set, 20)
    }

    @Test
    fun rejectsInvalidCoordinatesWithoutCrashing() {
        val dayStart = Instant.parse("2026-08-10T00:00:00Z").toEpochMilli()
        assertEquals(MoonCalc.MoonTimes(null, null), MoonCalc.riseSet(dayStart, 91.0, 0.0))
        assertEquals(MoonCalc.MoonTimes(null, null), MoonCalc.riseSet(dayStart, Double.NaN, 0.0))
    }

    // —— v0.0.4 补充：基准与边界 ——

    @Test
    fun phaseKeyForDayStartMatchesKnownEventDays() {
        // 直接以城市本地 00:00 的真实 epoch 调用，不依赖设备时区
        assertEquals("new-moon", MoonCalc.phaseKeyForDayStart(Instant.parse("2024-08-04T00:00:00Z").toEpochMilli()))
        assertEquals("full-moon", MoonCalc.phaseKeyForDayStart(Instant.parse("2024-08-19T00:00:00Z").toEpochMilli()))
        assertEquals("first-quarter", MoonCalc.phaseKeyForDayStart(Instant.parse("2024-08-12T00:00:00Z").toEpochMilli()))
        assertEquals("last-quarter", MoonCalc.phaseKeyForDayStart(Instant.parse("2024-08-26T00:00:00Z").toEpochMilli()))
    }

    @Test
    fun enrichFillsMissingFieldsAndKeepsExisting() {
        val dayStart = Instant.parse("2026-08-10T00:00:00Z").toEpochMilli()
        val bare = com.zhisheng.weather.model.DailyWeather(dateMillis = dayStart, high = 30.0, low = 20.0)
        val filled = MoonCalc.enrich(bare, 39.90, 116.41)
        assertTrue(filled.moonPhase in setOf(
            "new-moon", "first-quarter", "full-moon", "last-quarter",
            "waxing-crescent", "waxing-gibbous", "waning-gibbous", "waning-crescent",
        ))
        assertTrue(filled.moonrise?.matches(Regex("[0-2]\\d:[0-5]\\d")) ?: true)
        assertTrue(filled.moonset?.matches(Regex("[0-2]\\d:[0-5]\\d")) ?: true)
        // 已有数据不被覆盖
        val preset = bare.copy(moonPhase = "new-moon", moonrise = "06:00", moonset = "18:00")
        val kept = MoonCalc.enrich(preset, 39.90, 116.41)
        assertEquals("new-moon", kept.moonPhase)
        assertEquals("06:00", kept.moonrise)
        assertEquals("18:00", kept.moonset)
    }

    @Test
    fun riseSetWorksForSouthernHemisphereAndPolarEdge() {
        // 悉尼（南半球）：2026-08-10 当地 00:00（AEST = UTC+10）
        val sydneyDayStart = Instant.parse("2026-08-09T14:00:00Z").toEpochMilli()
        val sydney = MoonCalc.riseSet(sydneyDayStart, -33.87, 151.21)
        assertTrue(sydney.rise == null || sydney.rise.matches(Regex("[0-2]\\d:[0-5]\\d")))
        assertTrue(sydney.set == null || sydney.set.matches(Regex("[0-2]\\d:[0-5]\\d")))
        // 极区边缘：允许 null（无升落事件），但不许崩溃
        val polar = MoonCalc.riseSet(sydneyDayStart, 89.0, 0.0)
        assertTrue(polar.rise == null || polar.rise.matches(Regex("[0-2]\\d:[0-5]\\d")))
    }

    private fun phaseAtNoon(date: String): String =
        MoonCalc.phaseKey(Instant.parse("${date}T12:00:00Z").toEpochMilli())

    private fun assertWithinMinutes(expected: String, actual: String, tolerance: Int) {
        fun minutes(value: String): Int =
            value.substringBefore(':').toInt() * 60 + value.substringAfter(':').toInt()
        assertTrue("Expected $actual to be within $tolerance minutes of $expected", kotlin.math.abs(minutes(actual) - minutes(expected)) <= tolerance)
    }
}
