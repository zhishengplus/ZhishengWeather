package com.zhisheng.weather.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneId

class WidgetRedrawAlarmTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun at(text: String) = OffsetDateTime.parse(text).toInstant().toEpochMilli()

    @Test fun nextTriggerLandsJustAfterTheNextHalfHourBoundary() {
        assertEquals(at("2026-11-11T07:00:30+08:00"), WidgetRedrawAlarm.nextTrigger(at("2026-11-11T07:00:05+08:00"), zone))
        assertEquals(at("2026-11-11T07:30:30+08:00"), WidgetRedrawAlarm.nextTrigger(at("2026-11-11T07:05:00+08:00"), zone))
        assertEquals(at("2026-11-11T08:00:30+08:00"), WidgetRedrawAlarm.nextTrigger(at("2026-11-11T07:31:00+08:00"), zone))
        // Exactly on the trigger instant moves to the following boundary.
        assertEquals(at("2026-11-11T08:00:30+08:00"), WidgetRedrawAlarm.nextTrigger(at("2026-11-11T07:30:30+08:00"), zone))
        // Midnight rollover (daily rows advance to the next two days).
        assertEquals(at("2026-11-12T00:00:30+08:00"), WidgetRedrawAlarm.nextTrigger(at("2026-11-11T23:45:00+08:00"), zone))
    }
}
