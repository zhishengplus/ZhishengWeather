package com.zhisheng.weather.data

import org.junit.Assert.*
import org.junit.Test

class LocationFixPolicyTest {
    private val now = 1_800_000_000_000L
    private val elapsed = 5_000_000_000_000L
    private fun fix(ageMillis: Long = 1_000, accuracy: Float? = 30f) =
        LocationFix(31.23, 121.47, now - ageMillis, elapsed - ageMillis * 1_000_000, accuracy)

    @Test fun staleProviderCallbackIsNotANewFix() {
        assertFalse(LocationFixPolicy.acceptsCallback(fix(600_000), now, elapsed))
    }

    @Test fun rebootOrFutureMonotonicTimestampIsRejected() {
        assertFalse(LocationFixPolicy.acceptsCallback(fix().copy(elapsedRealtimeNanos = elapsed + 1_000_000), now, elapsed))
    }

    @Test fun invalidCoordinatesAreRejected() {
        assertFalse(LocationFixPolicy.acceptsCallback(fix().copy(latitude = Double.NaN), now, elapsed))
        assertFalse(LocationFixPolicy.acceptsCallback(fix().copy(longitude = 190.0), now, elapsed))
    }

    @Test fun unusableAccuracyIsRejected() {
        for (accuracy in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 25_000f)) {
            assertFalse(LocationFixPolicy.acceptsCallback(fix(accuracy = accuracy), now, elapsed))
        }
    }

    @Test fun missingAccuracyCannotClaimStreetPrecision() {
        assertFalse(LocationFixPolicy.canResolveStreet(fix(accuracy = null)))
    }

    @Test fun preciseFreshFixCanResolveStreet() {
        assertTrue(LocationFixPolicy.acceptsCallback(fix(), now, elapsed))
        assertTrue(LocationFixPolicy.canResolveStreet(fix()))
        assertFalse(LocationFixPolicy.canResolveStreet(fix(accuracy = 900f)))
    }

    @Test fun cacheUsesMonotonicAgeDespiteWallClockChange() {
        val old = fix(600_000).copy(wallTimeMillis = now - 1_000)
        val recent = fix(2_000).copy(wallTimeMillis = now - 3_600_000)
        assertEquals(recent, LocationFixPolicy.cachedFix(listOf(old, recent), now, elapsed))
    }

    @Test fun poorNewestCacheDoesNotHideUsableRecentFix() {
        val usable = fix(2_000)
        assertEquals(usable, LocationFixPolicy.cachedFix(listOf(usable, fix(500, 30_000f)), now, elapsed))
    }

    @Test fun fifteenMinuteCacheCannotStandInForCurrentLocation() {
        assertNull(LocationFixPolicy.cachedFix(listOf(fix(14 * 60_000)), now, elapsed))
    }

    @Test fun automaticResultCannotOverrideUserCitySwitch() {
        assertFalse(shouldApplyLocation("gps-city", "saved-city"))
        assertTrue(shouldApplyLocation("gps-city", "gps-city"))
    }
}
