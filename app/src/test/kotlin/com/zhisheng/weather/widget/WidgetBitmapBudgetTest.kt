package com.zhisheng.weather.widget

import org.junit.Assert.*
import org.junit.Test

class WidgetBitmapBudgetTest {
    @Test fun leavesHeadroomBelowAndroid17ParcelLimit() {
        assertEquals(11_664_000L, widgetBitmapLimit(1080, 2400))
        assertEquals(widgetBitmapLimit(2400, 1080), widgetBitmapLimit(1080, 2400))
        assertTrue(widgetBitmapLimit(3840, 2160) < 3840L * 2160 * 6)
    }

    @Test fun countsSharedImagesOnceAndDifferentImagesIndividually() {
        val budget = WidgetBitmapBudget(1000)
        val shared = Any()
        budget.record(shared, 400)
        repeat(16) { budget.record(shared, 400) }
        assertEquals(400L, budget.usedBytes)
        // Equal content need not mean shared pixel storage.
        budget.record(String(charArrayOf('a')), 300)
        budget.record(String(charArrayOf('a')), 300)
        assertEquals(1000L, budget.usedBytes)
        assertTrue(budget.fits)
        budget.record(Any(), 1)
        assertFalse(budget.fits)
    }

    @Test fun ordinaryParcelRetainsFullResolution() {
        val budget = WidgetBitmapBudget(widgetBitmapLimit(1080, 2400))
        budget.record(Any(), 2_000_000)
        assertTrue(budget.fits)
        assertEquals(208, budget.dimension(208, 16, 512))
        assertEquals(512, budget.dimension(1000, 16, 512))
    }

    @Test fun oversizeParcelShrinksPixelsWithoutChangingLayoutUnits() {
        val budget = WidgetBitmapBudget(1_000_000)
        budget.record(Any(), 4_000_000)
        val scale = budget.reducedScale()
        val reduced = WidgetBitmapBudget(1_000_000, scale)
        assertEquals(.45f, scale, .0001f)
        assertEquals(90, reduced.dimension(200, 16, 512))
        assertEquals(16, reduced.dimension(16, 16, 512))
        assertTrue(4_000_000 * scale * scale < 1_000_000)
    }
}
