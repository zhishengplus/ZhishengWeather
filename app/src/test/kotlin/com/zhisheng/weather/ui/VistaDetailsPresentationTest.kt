package com.zhisheng.weather.ui

import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.GlowForecast
import com.zhisheng.weather.model.GlowKind
import com.zhisheng.weather.ui.home.vistaAstroAltitude
import com.zhisheng.weather.ui.home.vistaAstroClockFraction
import com.zhisheng.weather.ui.home.vistaAstroProgressInWindow
import com.zhisheng.weather.ui.home.vistaAstroUpDuration
import com.zhisheng.weather.ui.home.vistaAstroVisible
import com.zhisheng.weather.ui.home.vistaAstroWindow
import com.zhisheng.weather.ui.home.vistaCloudCaption
import com.zhisheng.weather.ui.home.vistaDaylightActive
import com.zhisheng.weather.ui.home.vistaEnergySegments
import com.zhisheng.weather.ui.home.vistaGlowBriefReason
import com.zhisheng.weather.ui.home.vistaGlowHeadline
import com.zhisheng.weather.ui.home.vistaGlowNowInWindow
import com.zhisheng.weather.ui.home.vistaGlowNowStatus
import com.zhisheng.weather.ui.home.vistaGlowWhenLabel
import com.zhisheng.weather.ui.home.vistaGlowWindowLabel
import com.zhisheng.weather.ui.home.vistaHumidityCaption
import com.zhisheng.weather.ui.home.vistaLuminaryProgress
import com.zhisheng.weather.ui.home.vistaMeterFraction
import com.zhisheng.weather.ui.home.vistaNowMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VistaDetailsPresentationTest {
    @Test fun luminaryTangentsStayContinuousAcrossRiseSetAndMidnight() {
        for ((rise, set) in listOf(405 to 1173, 115 to 1048, 1351 to 797)) {
            for (point in listOf(rise, set, 1440)) {
                val before = vistaAstroAltitude(point, rise, set) - vistaAstroAltitude(point - 1, rise, set)
                val after = vistaAstroAltitude(point + 1, rise, set) - vistaAstroAltitude(point, rise, set)
                assertEquals(before, after, 0.0001f)
            }
            for (minute in 0..1440) assertTrue(vistaAstroAltitude(minute, rise, set) in -1.0001f..1.0001f)
        }
    }
    @Test fun humidityAndCloudCaptionsStayQualitative() {
        assertEquals("空气偏干", vistaHumidityCaption(18.0))
        assertEquals("湿度适宜", vistaHumidityCaption(44.0))
        assertEquals("云层较厚", vistaCloudCaption(100.0))
    }
    @Test fun metersIgnoreOutOfRangeTelemetry() {
        assertEquals(0.44, vistaMeterFraction("HUMIDITY", CurrentWeather(humidity = 44.0))!!, 0.0001)
        assertEquals(null, vistaMeterFraction("HUMIDITY", CurrentWeather(humidity = 140.0)))
        assertEquals(null, vistaMeterFraction("WIND", CurrentWeather(humidity = 44.0)))
        assertEquals(7, vistaEnergySegments(0.44, 16))
        assertEquals(0, vistaEnergySegments(0.0))
        assertEquals(16, vistaEnergySegments(1.0))
    }
    @Test fun sunArcProgressFollowsSunriseToSunset() {
        assertEquals(0f, vistaLuminaryProgress("06:00", "18:00", 6 * 60), 0.001f)
        assertEquals(0.5f, vistaLuminaryProgress("06:00", "18:00", 12 * 60), 0.001f)
        assertEquals(1f, vistaLuminaryProgress("06:00", "18:00", 18 * 60), 0.001f)
        assertFalse(vistaDaylightActive("06:00", "18:00", 5 * 60))
        assertTrue(vistaDaylightActive("06:00", "18:00", 12 * 60))
    }
    @Test fun cityClockUsesWeatherOffsetNotPhoneZone() {
        val noonUtc = java.time.Instant.parse("2026-09-07T04:00:00Z").toEpochMilli()
        assertEquals(12 * 60, vistaNowMinutes(noonUtc, 28800))
        assertEquals(20 * 60, vistaNowMinutes(noonUtc, -28800))
    }
    @Test fun moonWaveHandlesRiseAfterMidnightWrap() {
        val rise = 22 * 60 + 31
        val set = 13 * 60 + 17
        assertEquals(14 * 60 + 46, vistaAstroUpDuration(rise, set))
        assertTrue(vistaAstroVisible(23 * 60, rise, set))
        assertTrue(vistaAstroVisible(8 * 60, rise, set))
        assertFalse(vistaAstroVisible(16 * 60, rise, set))
        assertEquals(0f, vistaAstroAltitude(rise, rise, set), 0.001f)
        assertEquals(0f, vistaAstroAltitude(set, rise, set), 0.001f)
        assertEquals(1f, vistaAstroAltitude(rise + (14 * 60 + 46) / 2, rise, set), 0.001f)
        val window = vistaAstroWindow(rise, set)!!
        assertTrue(vistaAstroProgressInWindow(rise, window) < 0.25f)
        assertTrue(vistaAstroProgressInWindow(set, window) > 0.75f)
        assertEquals(0.25f, vistaAstroClockFraction(6 * 60), 0.001f)
        assertEquals(0.5f, vistaAstroClockFraction(12 * 60), 0.001f)
        assertEquals((1 * 60 + 54).toFloat() / (24 * 60), vistaAstroClockFraction(1 * 60 + 54), 0.001f)
    }
    @Test fun glowCopyLeadsWithConclusionAndClockRange() {
        val zone = "Asia/Shanghai"
        val start = java.time.Instant.parse("2026-09-07T10:00:00Z").toEpochMilli()
        val end = java.time.Instant.parse("2026-09-07T11:20:00Z").toEpochMilli()
        val glow = GlowForecast(
            GlowKind.DUSK, "可以碰碰运气", start, end,
            listOf("有中高云可被染色，云量组合尚可", "本地点低云较少；远处地平线仍需现场观察"),
        )
        val afternoon = java.time.Instant.parse("2026-09-07T06:00:00Z").toEpochMilli()
        assertEquals("今晚晚霞 · 可以碰碰运气", vistaGlowHeadline(glow, afternoon, zone))
        assertEquals("今晚不值得专程出门", vistaGlowHeadline(glow.copy(grade = "不建议专程去"), afternoon, zone))
        assertEquals("太阳落下前后 18:00–19:20", vistaGlowWhenLabel(glow, zone))
        assertEquals("18:00–19:20", vistaGlowWindowLabel(start, end, zone))
        assertEquals("有中高云可被染色，云量组合尚可", vistaGlowBriefReason(glow))
        assertEquals("现在还早", vistaGlowNowStatus(afternoon, glow))
        assertEquals("就在这会儿", vistaGlowNowStatus(start + 1_000L, glow))
        assertEquals("已经过了", vistaGlowNowStatus(end + 1_000L, glow))
        assertTrue(vistaGlowNowInWindow(start + 1_000L, glow))
        assertFalse(vistaGlowNowInWindow(end + 1_000L, glow))
        val dawn = GlowForecast(
            GlowKind.DAWN, "很值得等",
            java.time.Instant.parse("2026-09-07T22:10:00Z").toEpochMilli(),
            java.time.Instant.parse("2026-09-07T23:20:00Z").toEpochMilli(),
            emptyList(),
        )
        assertEquals("明早朝霞 · 很值得等", vistaGlowHeadline(dawn, afternoon, zone))
        assertEquals("太阳升起前后 06:10–07:20", vistaGlowWhenLabel(dawn, zone))
    }
}
