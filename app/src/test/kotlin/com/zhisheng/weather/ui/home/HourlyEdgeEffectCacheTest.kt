package com.zhisheng.weather.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class HourlyEdgeEffectCacheTest {
    private val middle = HourlyEdgeEffectKey(960f, 680f, 65f, true, true)

    @Test fun scrollingInsideTheSameViewportReusesTheEffect() {
        var created = 0
        val cache = HourlyEdgeEffectCache { _: HourlyEdgeEffectKey -> created++; Any() }
        val first = cache.get(middle)
        repeat(240) { assertSame(first, cache.get(middle.copy())) }
        assertEquals(1, created)
    }

    @Test fun everyOpticalInputInvalidatesTheEffect() {
        val changes = listOf(
            middle.copy(width = 961f),
            middle.copy(height = 681f),
            middle.copy(edgeWidth = 70f),
            middle.copy(hasLeft = false),
            middle.copy(hasRight = false),
        )
        changes.forEach { changed ->
            val cache = HourlyEdgeEffectCache { _: HourlyEdgeEffectKey -> Any() }
            val first = cache.get(middle)
            val next = cache.get(changed)
            assertNotSame(first, next)
            assertSame(next, cache.get(changed))
        }
    }

    @Test fun returningToAnOldViewportRebuildsInsteadOfKeepingOldShaderState() {
        var created = 0
        val cache = HourlyEdgeEffectCache { _: HourlyEdgeEffectKey -> created++; Any() }
        val first = cache.get(middle)
        cache.get(middle.copy(hasLeft = false))
        assertNotSame(first, cache.get(middle))
        assertEquals(3, created)
    }
}
