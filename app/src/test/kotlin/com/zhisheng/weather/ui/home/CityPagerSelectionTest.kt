package com.zhisheng.weather.ui.home

import org.junit.Assert.*
import org.junit.Test

class CityPagerSelectionTest {
    private val keys = listOf("a", "b", "c", "d")
    @Test fun initialAndExternalSelectionFollowTheSavedCity() {
        val state = CityPagerSelection()
        assertTrue(state.observe("b", keys))
        assertFalse(state.observe("b", keys))
        assertTrue(state.observe("d", keys))
        assertFalse(state.observe(null, emptyList()))
    }
    @Test fun lateAcknowledgementNeverRewindsASecondSwipe() {
        val state = CityPagerSelection()
        state.observe("a", keys)
        assertTrue(state.request("b", "a"))
        assertTrue(state.request("c", "a"))
        assertFalse(state.observe("b", keys))
        assertFalse(state.observe("c", keys))
        assertFalse(state.request("c", "c"))
        assertTrue(state.observe("d", keys))
    }
    @Test fun reversingBeforePersistenceIsStillARealSelection() {
        val state = CityPagerSelection()
        state.observe("a", keys)
        state.request("b", "a")
        assertTrue(state.request("a", "a"))
        assertFalse(state.observe("b", keys))
        assertFalse(state.observe("a", keys))
    }
    @Test fun conflatedAcknowledgementAndDeletedCitiesAreHandled() {
        val state = CityPagerSelection()
        state.observe("a", keys)
        state.request("b", "a")
        state.request("c", "a")
        assertFalse(state.observe("c", keys))
        assertTrue(state.observe("b", listOf("b", "d")))
        assertFalse(state.observe(null, emptyList()))
    }
    @Test fun repeatStopsDoNotCauseDuplicateRequests() {
        val state = CityPagerSelection()
        state.observe("a", keys)
        assertFalse(state.request("a", "a"))
        assertTrue(state.request("b", "a"))
        repeat(100) { assertFalse(state.request("b", "a")) }
    }
    @Test fun indicatorAlwaysFitsAndContainsBothSettledAndInterpolatedCities() {
        assertTrue(cityIndicatorWindow(0, 0f).isEmpty())
        for (count in 1..50) for (step in 0..(count - 1) * 4) {
            val position = step / 4f
            val range = cityIndicatorWindow(count, position)
            assertTrue(range.count() <= 7)
            assertTrue(range.first >= 0 && range.last < count)
            assertTrue(position >= range.first && position <= range.last)
        }
    }
}
