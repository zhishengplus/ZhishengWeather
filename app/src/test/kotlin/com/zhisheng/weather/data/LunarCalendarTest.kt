package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 农历转换测试。全部期望值由 solarlunar(MIT) 参考实现生成并逐条核对，
 * 覆盖：春节/中秋/端午、闰月（含 2033 闰十一月著名特例）、跨年边界。
 */
class LunarCalendarTest {

    private fun check(date: String, expected: String) {
        assertEquals(expected, LunarCalendar.dayLabel(LocalDate.parse(date)))
    }

    @Test
    fun springFestivalsMapToFirstDayOfFirstMonth() {
        check("2024-02-10", "正月初一")
        check("2025-01-29", "正月初一")
        check("2026-02-17", "正月初一")
    }

    @Test
    fun festivalsAndOrdinaryDays() {
        check("2026-06-19", "五月初五") // 端午
        check("2024-09-17", "八月十五") // 中秋
        check("2026-09-25", "八月十五") // 中秋（与 MIUI 锁屏对照过 2026-09-15=八月初五）
        check("2026-09-15", "八月初五")
        check("2026-10-22", "九月十三")
    }

    @Test
    fun leapMonthsUseLeapPrefix() {
        check("2023-03-22", "闰二月初一")
        check("2025-06-25", "六月初一")
        check("2025-07-25", "闰六月初一")
        check("2025-08-23", "七月初一") // 闰六月结束后的回归
        check("2028-06-25", "闰五月初三")
        // 2033 年问题：闰十一月必须正确解析（多数简易实现会在此翻车）
        check("2033-12-22", "闰冬月初一")
    }

    @Test
    fun eleventhAndTwelfthMonthsUseTraditionalNames() {
        check("2026-01-01", "冬月十三")
        check("2026-12-31", "冬月廿三")
        check("2024-12-31", "腊月初一")
        check("2025-01-01", "腊月初二")
        check("2026-02-16", "腊月廿九") // 除夕（该年无三十）
    }

    @Test
    fun outOfRangeReturnsNull() {
        assertNull(LunarCalendar.dayLabel(LocalDate.of(1899, 12, 31)))
        assertNull(LunarCalendar.dayLabel(LocalDate.of(2101, 1, 1)))
    }
}
