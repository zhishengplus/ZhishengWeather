package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmapStreetLabelTest {
    @Test
    fun townshipAndRoadRemainDistinctAndReadable() {
        assertEquals("酒仙桥街道·酒仙桥路", amapStreetLabel("酒仙桥街道", "酒仙桥路", "北京市"))
    }

    @Test
    fun emptyOrAdministrativeOnlyResponseDoesNotPretendToBeStreet() {
        assertNull(amapStreetLabel("北京市", null, "北京市"))
        assertNull(amapStreetLabel("", " ", "北京"))
    }
}
