package com.zhisheng.weather.data

import org.junit.Assert.*
import org.junit.Test

class PlaceRetryAfterTest {
    @Test fun retryAfterAcceptsSecondsOrHttpDateAndBoundsExtremeValues() {
        assertEquals(30_000L, placeRetryAfterMillis("30", 0))
        assertEquals(60_000L, placeRetryAfterMillis("Thu, 01 Jan 1970 00:01:00 GMT", 0))
        assertEquals(86_400_000L, placeRetryAfterMillis("9999999", 0))
        assertNull(placeRetryAfterMillis("broken", 0))
        assertNull(placeRetryAfterMillis(null, 0))
    }
}
