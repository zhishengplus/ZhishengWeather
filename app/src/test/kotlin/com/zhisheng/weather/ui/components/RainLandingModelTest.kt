package com.zhisheng.weather.ui.components

import androidx.compose.ui.geometry.Rect
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import org.junit.Assert.*
import org.junit.Test

class RainLandingModelTest {
    @Test fun adjacentClearAndMissingCitiesCannotBorrowARainingContactPlane() {
        val all = listOf(
            RainLandingPlane("rain", WeatherCondition.RAIN, -180f, 140f, 420f),
            RainLandingPlane("clear", WeatherCondition.CLEAR, 140f, 460f, 360f),
            RainLandingPlane("missing", null, 460f, 780f, 390f),
        )
        val visible = visibleRainLandingPlanes(all, 320f, 700f, 140f)
        assertEquals(1, visible.size)
        assertEquals(0f, visible.single().left, 0f)
        assertEquals("rain", rainLandingContact(visible, 139.99f)?.cityKey)
        assertNull(rainLandingContact(visible, 140f))
        assertNull(rainLandingContact(visible, 300f))
        assertNull(rainLandingContact(visible, Float.NaN))
    }

    @Test fun eachVisibleRainCityRetainsItsOwnScrolledHeightAndTheNearestEdgeWins() {
        val planes = visibleRainLandingPlanes(listOf(
            RainLandingPlane("a", WeatherCondition.DRIZZLE, -100f, 170f, 220f),
            RainLandingPlane("b", WeatherCondition.THUNDERSTORM, 170f, 490f, 480f),
            RainLandingPlane("overlap", WeatherCondition.RAIN, 20f, 80f, 330f),
        ), 320f, 700f, 140f)
        assertEquals("a", rainLandingContact(planes, 50f)?.cityKey)
        assertEquals(220f, rainLandingContact(planes, 50f)!!.top, 0f)
        assertEquals("b", rainLandingContact(planes, 170f)?.cityKey)
        assertEquals(480f, rainLandingContact(planes, 250f)!!.top, 0f)
    }

    @Test fun offscreenInvalidDryAndSnowEdgesNeverBecomeCollisionSurfaces() {
        val original = RainLandingPlane("wet", WeatherCondition.RAIN, 0f, 320f, 400f)
        val rejected = listOf(original.copy(left = 400f, right = 720f),
            original.copy(left = -400f, right = -10f), original.copy(top = 139f),
            original.copy(top = 701f), original.copy(left = Float.NaN),
            original.copy(condition = WeatherCondition.SNOW), original.copy(condition = WeatherCondition.UNKNOWN))
        assertTrue(visibleRainLandingPlanes(rejected, 320f, 700f, 140f).isEmpty())
        assertTrue(visibleRainLandingPlanes(listOf(original), 0f, 700f, 140f).isEmpty())
        assertTrue(hasLiquidRain(WeatherCondition.SLEET))
        assertTrue(original.copy(condition = WeatherCondition.FREEZING_RAIN).freezing)
    }

    @Test fun disposingOneCityOnlyRemovesItsRegistrationAndWeatherUpdatesKeepGeometry() {
        val registry = RainLandingRegistry()
        val first = Any(); val second = Any()
        val weather = WeatherData(current = CurrentWeather(condition = WeatherCondition.RAIN))
        val bounds = Rect(0f, 300f, 320f, 500f)
        registry.update(first, "a", weather, bounds)
        registry.update(second, "b", null, bounds.translate(320f, 0f))
        registry.remove(second)
        assertEquals("a", registry.snapshot().single().cityKey)
        registry.updateWeather(first, "a", null)
        assertEquals(bounds, registry.snapshot().single().bounds)
        assertNull(registry.snapshot().single().weather)
        registry.remove(first)
        assertTrue(registry.snapshot().isEmpty())
    }

    @Test fun splashIsShortBoundedBallisticMotionWithAContinuousFade() {
        for (index in 0..60) for (bead in 0..2) {
            val a = rainSplashBead(index, bead, .02f, .5f)!!
            val b = rainSplashBead(index, bead, .06f, .5f)!!
            val c = rainSplashBead(index, bead, .10f, .5f)!!
            assertEquals(2f * 420f * .04f * .04f, c.yDp - 2f * b.yDp + a.yDp, .0001f)
            assertEquals(b.xDp - a.xDp, c.xDp - b.xDp, .0001f)
            assertTrue(a.opacity > b.opacity && b.opacity > c.opacity)
            for (drop in listOf(a, b, c)) {
                assertTrue(drop.yDp in -5f..0f)
                assertTrue(kotlin.math.abs(drop.xDp) < 12f)
                assertTrue(drop.radiusDp in .42f..0.88f)
            }
        }
        assertNull(rainSplashBead(0, 0, -.01f, 0f))
        assertNull(rainSplashBead(0, 0, RAIN_SPLASH_SECONDS, 0f))
        assertNull(rainSplashBead(0, 0, Float.NaN, 0f))
        assertNull(rainSplashBead(0, 0, .1f, Float.NaN))
    }

    @Test fun budgetRemainsGlobalBoundedAndLowerTiersReduceWork() {
        val high = rainLandingParticleBudget(1f, false, 1f, 1f)
        assertEquals(106, high)
        assertTrue(rainLandingParticleBudget(1f, false, 1f, .5f) < high)
        assertTrue(rainLandingParticleBudget(1f, true, 1f, 1f) < high)
        assertTrue(rainLandingParticleBudget(.1f, false, 1f, 1f) < high)
        assertEquals(0, rainLandingParticleBudget(1f, false, 1f, 0f))
        assertEquals(high, rainLandingParticleBudget(200f, false, 20f, 100f))
        assertEquals(0, rainLandingParticleBudget(Float.NaN, false, 1f, 1f))
    }

    @Test fun completeSharedClockCycleKeepsFlightAndImpactPositionsSeamless() {
        for (index in 0..60) for (drizzle in listOf(true, false)) {
            val first = rainFlight(0.0, index, .7f, drizzle)
            val seam = rainFlight(1.0, index, .7f, drizzle)
            assertEquals(first.progress, seam.progress, .00001f)
            assertEquals(first.x, seam.x, .00001f)
            assertEquals(first.seconds, seam.seconds, 0f)
            assertEquals(first.depth, seam.depth, 0f)
        }
    }
}
