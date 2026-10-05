package com.zhisheng.weather.ui

import com.zhisheng.weather.model.RadarFrame
import com.zhisheng.weather.model.RadarFeed
import com.zhisheng.weather.ui.home.vistaNowMinutes
import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherClockAuditTest {
    @Test fun luminaryWithoutCachedTimezoneUsesTheSameLocalClockAsThePage() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            val now = Instant.parse("2026-09-30T04:00:00Z").toEpochMilli()
            assertEquals("12:00", Fmt.clock(now, null))
            assertEquals(12 * 60, vistaNowMinutes(now, null))
            assertEquals(4 * 60, vistaNowMinutes(now, 0))
            assertEquals(9 * 60 + 45, vistaNowMinutes(now, 20700))
        } finally { TimeZone.setDefault(previous) }
    }

    @Test fun radarNowTimestampUsesTheLastObservationAndNeverAFutureFrame() {
        val observed = RadarFrame(Instant.parse("2026-09-30T09:00:00Z").toEpochMilli())
        val forecast = RadarFrame(Instant.parse("2026-09-30T09:05:00Z").toEpochMilli())
        val feed = RadarFeed(past = listOf(observed), future = listOf(forecast))
        assertEquals(observed, radarNowFrame(feed.playbackFrames, feed.nowBoundaryIndex))
        assertEquals(observed, radarNowFrame(listOf(observed), 0))
        assertNull(radarNowFrame(emptyList(), -1))
    }
}
