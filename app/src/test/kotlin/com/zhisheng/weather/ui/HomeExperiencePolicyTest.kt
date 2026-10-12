package com.zhisheng.weather.ui

import com.zhisheng.weather.ui.home.citySwipeTarget
import com.zhisheng.weather.ui.home.isHorizontalCitySwipe
import org.junit.Assert.*
import org.junit.Test

class HomeExperiencePolicyTest {
    @Test fun verticalAndDiagonalScrollingNeverSelectsCity() {
        assertFalse(isHorizontalCitySwipe(40f, 80f, 24f))
        assertFalse(isHorizontalCitySwipe(60f, 60f, 24f))
        assertFalse(isHorizontalCitySwipe(12f, 0f, 24f))
        assertTrue(isHorizontalCitySwipe(-90f, 18f, 24f))
    }
    @Test fun swipeUsesCurrentKeysAndStopsAtListEdges() {
        val cities = listOf("located", "favorite", "search")
        assertEquals("favorite", citySwipeTarget(cities, "located", -90f))
        assertEquals("located", citySwipeTarget(cities, "favorite", 90f))
        assertNull(citySwipeTarget(cities, "located", 90f))
        assertNull(citySwipeTarget(cities, "search", -90f))
        assertNull(citySwipeTarget(cities, "removed", -90f))
        assertNull(citySwipeTarget(emptyList(), "located", -90f))
        assertEquals(listOf("located", "favorite", "search"), cities)
    }
}
