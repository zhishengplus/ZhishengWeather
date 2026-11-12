package com.zhisheng.weather.ui

import org.junit.Assert.*
import org.junit.Test

class SponsorBoardTest {
    @Test fun onlyFirstPlaceKeepsBadge() {
        assertEquals("👑", sponsorBadge(1))
        for (rank in 2..100) assertNull(sponsorBadge(rank))
    }
    @Test fun sourceDistributionDoesNotBundlePersonalRosters() {
        assertTrue(SponsorBoard.isEmpty())
    }
}
