package com.zhisheng.weather.ui

import org.junit.Assert.*
import org.junit.Test

class ContributorsTest {
    @Test fun sourceDistributionDoesNotBundlePersonalRosters() {
        assertTrue(CommunityContributorSections.isEmpty())
        assertTrue(CommunityContributors.isEmpty())
    }
}
