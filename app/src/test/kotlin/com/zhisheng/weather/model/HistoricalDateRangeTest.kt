package com.zhisheng.weather.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HistoricalDateRangeTest {
    private val today = LocalDate.of(2026,10,29)
    @Test fun directLeapDayKeepsItsOwnYear() {
        val date = LocalDate.of(2024,2,29)
        assertNull(historyDateRangeError(date,date,today))
        val range=normalizedHistoryRange(date,date,listOf(HistoricalDay("2024-02-29",mean=12.0),HistoricalDay("2023-02-28",high=20.0)))
        assertEquals(listOf(date),range.dates);assertEquals("2024-02-29",range.days.single().date)
    }
    @Test fun julyIncludesBothEndsAndKeepsMissingDays() {
        val from=LocalDate.of(2023,7,1);val to=LocalDate.of(2023,7,20)
        val range=normalizedHistoryRange(from,to,listOf(HistoricalDay("2023-07-01",mean=20.0)))
        assertEquals(20,range.dates.size);assertEquals(from,range.dates.first());assertEquals(to,range.dates.last());assertEquals(19,range.missingCount)
    }
    @Test fun leapMonthHas29DatesAndOrdinaryMonthHas28() {
        for((year,count) in listOf(2024 to 29,2025 to 28)) {
            val first=LocalDate.of(year,2,1)
            assertEquals(count,normalizedHistoryRange(first,first.withDayOfMonth(first.lengthOfMonth()),emptyList()).dates.size)
        }
    }
    @Test fun futureReverseOldAndUnboundedRangesAreRefused() {
        assertNotNull(historyDateRangeError(today,today,today))
        assertNotNull(historyDateRangeError(today.minusDays(1),today.minusDays(2),today))
        assertNotNull(historyDateRangeError(LocalDate.of(1939,1,1),today.minusDays(1),today))
        assertNotNull(historyDateRangeError(LocalDate.of(2023,1,1),LocalDate.of(2024,1,2),today))
        assertNull(historyDateRangeError(LocalDate.of(2024,1,1),LocalDate.of(2024,12,31),today))
    }
    @Test fun badAndDuplicateDatesDoNotInventTemperatures() {
        val date=LocalDate.of(2024,2,29)
        val range=normalizedHistoryRange(date,date,listOf(HistoricalDay("bad",high=12.0),HistoricalDay("2024-02-29",high=Double.NaN),
            HistoricalDay("2024-02-29",mean=12.0),HistoricalDay("2024-02-29",high=20.0)))
        assertEquals(1,range.days.size);assertEquals(12.0,range.days.single().mean!!,0.0)
        assertNull(range.days.single().high);assertNull(range.days.single().low)
    }
}
