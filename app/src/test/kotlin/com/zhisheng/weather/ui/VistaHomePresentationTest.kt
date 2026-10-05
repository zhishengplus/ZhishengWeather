package com.zhisheng.weather.ui

import com.zhisheng.weather.model.AlertInfo
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.ui.home.vistaAlertHeading
import com.zhisheng.weather.ui.home.vistaHourlyPrecipLabel
import com.zhisheng.weather.ui.home.vistaHourlyShowsPrecip
import com.zhisheng.weather.ui.home.vistaLocationHeading
import com.zhisheng.weather.ui.home.vistaUpdateTime
import com.zhisheng.weather.ui.home.vistaVisibleAlerts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class VistaHomePresentationTest {
    @Test fun longLocationSeparatesCityFromStreetWithoutLosingAddress() {
        assertEquals("金川区" to "金川路街道·金川路", vistaLocationHeading("金川区·金川路街道·金川路"))
        assertEquals("金昌市" to "", vistaLocationHeading("金昌市"))
        assertEquals("Washington, D.C." to "", vistaLocationHeading("Washington, D.C."))
    }
    @Test fun officialPrefixCanCollapseWithoutLosingWarningTypeOrLevel() {
        assertEquals("地质灾害气象风险黄色预警", vistaAlertHeading(AlertInfo("金川发布地质灾害气象风险黄色预警")))
        assertEquals("暴雨红色预警信号", vistaAlertHeading(AlertInfo("某市气象台2026年09月07日08时继续发布暴雨红色预警信号")))
    }
    @Test fun unrecognizedWarningsKeepTheirFullMeaning() {
        listOf("解除暴雨红色预警", "气象风险提示：注意防范", "发布暴雨预警，局地可能伴随冰雹").forEach {
            assertEquals(it, vistaAlertHeading(AlertInfo(it)))
        }
    }
    @Test fun blankWarningsDoNotKeepAnEmptyHomeSlot() {
        assertTrue(vistaVisibleAlerts(emptyList()).isEmpty())
        assertTrue(vistaVisibleAlerts(listOf(AlertInfo(""), AlertInfo("   "))).isEmpty())
        assertEquals(1, vistaVisibleAlerts(listOf(AlertInfo(""), AlertInfo("大风蓝色预警"))).size)
    }
    @Test fun hourlyPrecipKeepsZeroAsAReadableValue() {
        assertEquals("0%", vistaHourlyPrecipLabel(0))
        assertEquals("40%", vistaHourlyPrecipLabel(40))
        assertEquals("", vistaHourlyPrecipLabel(null))
        assertEquals("", vistaHourlyPrecipLabel(6000))
        assertTrue(vistaHourlyShowsPrecip(listOf(HourlyWeather(0L, precipProb = 0))))
        assertFalse(vistaHourlyShowsPrecip(listOf(HourlyWeather(0L, precipProb = null))))
        assertFalse(vistaHourlyShowsPrecip(listOf(HourlyWeather(0L, precipProb = -1))))
    }
    @Test fun updateTimeUsesWeatherCityDateAndNeverMakesYesterdayLookCurrent() {
        val now = Instant.parse("2026-09-07T00:10:00Z").toEpochMilli()
        assertEquals("08:05", vistaUpdateTime(Instant.parse("2026-09-07T00:05:00Z").toEpochMilli(), now, 28800))
        assertEquals("09-06 23:59", vistaUpdateTime(Instant.parse("2026-09-06T15:59:00Z").toEpochMilli(), now, 28800))
        assertEquals("2025-09-07 08:05", vistaUpdateTime(Instant.parse("2025-09-07T00:05:00Z").toEpochMilli(), now, 28800))
        assertEquals("16:05", vistaUpdateTime(Instant.parse("2026-09-07T00:05:00Z").toEpochMilli(), now, -28800))
    }
}
