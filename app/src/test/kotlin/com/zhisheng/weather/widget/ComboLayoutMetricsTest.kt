package com.zhisheng.weather.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComboLayoutMetricsTest {
    private fun metrics(height: Int, compact: Boolean = height < 224): ComboLayoutMetrics {
        val vertical = if (height < 224) 16 else 16
        return comboLayoutMetrics(344, height, compact, 48, vertical * 2)
    }

    @Test fun sectionsFillContentAndStaySquareAcrossLauncherSlots() {
        val samples = listOf(
            180 to true,   // shortest supported resize (~4×2 is below minResizeHeight 180)
            250 to false,  // 4×3
            333 to false,  // 4×4
            417 to false,  // 4×5
        )
        var previousHero = 0f
        var previousTemp = 0f
        var previousDaily = 0f
        for ((height, compact) in samples) {
            val padV = 32
            val m = comboLayoutMetrics(344, height, compact, 48, padV)
            val content = (height - padV).toFloat()
            assertEquals("sections must fill $height dp", content, m.headerHeight + m.hourlyHeight + m.dailyHeight, 0.6f)
            assertEquals(m.dailyRowHeight * 2f, m.dailyHeight, 0.05f)
            assertTrue("hero $height", m.heroIcon >= 28f && m.heroIcon <= m.headerHeight)
            assertTrue("hourly icon $height", m.hourlyIcon >= 22f && m.hourlyIcon <= (344 - 48) / 6f)
            assertTrue("daily icon $height", m.dailyIcon >= 24f && m.dailyIcon <= m.dailyRowHeight)
            assertTrue("weekday larger than date at $height", m.dailyWeekdaySize > m.dailyDateSize)
            assertTrue("daily rows should hug content at $height: row=${m.dailyRowHeight} icon=${m.dailyIcon}",
                m.dailyRowHeight <= m.dailyIcon + 14f)
            assertEquals(ComboSizeBucket.entries.size, 4)
            if (!compact) {
                assertTrue("taller slot enlarges hero", m.heroIcon + 0.5f >= previousHero)
                assertTrue("taller slot enlarges temp", m.tempSize + 0.5f >= previousTemp)
                assertTrue("taller slot enlarges daily icon", m.dailyIcon + 0.5f >= previousDaily)
                previousHero = m.heroIcon
                previousTemp = m.tempSize
                previousDaily = m.dailyIcon
            }
        }
        val regular = metrics(250, false)
        val tall = metrics(333, false)
        assertTrue(tall.tempSize > regular.tempSize)
        assertTrue(tall.heroIcon > regular.heroIcon)
        assertTrue(tall.dailyIcon > regular.dailyIcon)
        assertTrue(tall.dailyWeekdaySize > regular.dailyWeekdaySize)
        assertEquals(ComboSizeBucket.SHORT, comboSizeBucket(180))
        assertEquals(ComboSizeBucket.REGULAR, comboSizeBucket(250))
        assertEquals(ComboSizeBucket.TALL, comboSizeBucket(333))
        assertEquals(ComboSizeBucket.EXTRA_TALL, comboSizeBucket(417))
    }
}
