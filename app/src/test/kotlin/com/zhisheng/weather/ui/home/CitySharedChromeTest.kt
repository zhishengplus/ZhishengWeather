package com.zhisheng.weather.ui.home

import org.junit.Assert.*
import org.junit.Test

class CitySharedChromeTest {
    @Test fun segmentIsContinuousAcrossMidPageAndChangesOnlyAtCityBoundaries() {
        assertEquals(CityPageSegment(0, 1), cityPageSegment(.49f, 4))
        assertEquals(CityPageSegment(0, 1), cityPageSegment(.51f, 4))
        assertEquals(CityPageSegment(1, 2), cityPageSegment(1f, 4))
        assertEquals(CityPageSegment(2, 3), cityPageSegment(2.8f, 4))
    }

    @Test fun endsAndEmptyCityListsStayBounded() {
        assertEquals(CityPageSegment(0, 0), cityPageSegment(4f, 0))
        assertEquals(CityPageSegment(0, 0), cityPageSegment(-4f, 1))
        assertEquals(CityPageSegment(0, 1), cityPageSegment(-4f, 4))
        assertEquals(CityPageSegment(3, 3), cityPageSegment(4f, 4))
        assertEquals(CityPageSegment(0, 1), cityPageSegment(Float.NaN, 4))
    }

    @Test fun heroCrossfadeDoesNotDoubleTheStrokesAndReversesWithoutAnotherClock() {
        assertEquals(1f, cityHeroOpacity(0, 0f), 0f)
        assertEquals(0f, cityHeroOpacity(1, 0f), 0f)
        assertEquals(.25f, cityHeroOpacity(0, .5f), 0f)
        assertEquals(.25f, cityHeroOpacity(1, .5f), 0f)
        assertEquals(cityHeroOpacity(0, .2f), cityHeroOpacity(1, .8f), .0001f)
        assertEquals(0f, cityHeroOpacity(2, .2f), 0f)
    }
}
