package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreciseAddressSearchTest {

    @Test
    fun addressSearchKeepsFullCoordinatesAndCreatesPreciseIdentity() {
        val result = requireNotNull(
            preciseAddressCity(
                district = "金川区",
                city = "金昌市",
                province = "甘肃省",
                featureName = "金川公园",
                thoroughfare = "公园路",
                premises = null,
                latitude = 38.5201234,
                longitude = 102.1887654,
            ),
        )

        assertEquals("金川区", result.name)
        assertEquals("甘肃省·金昌市", result.affiliation)
        assertEquals("金川公园·公园路", result.street)
        assertEquals(38.5201234, result.latitude, 0.0)
        assertEquals(102.1887654, result.longitude, 0.0)
        assertEquals("geo:38.520,102.189", result.locationKey)
        assertTrue(result.isPreciseLocation)
        assertNull(result.weatherLocationKey)
    }

    @Test
    fun invalidCoordinatesAreRejected() {
        assertNull(
            preciseAddressCity(null, "测试市", null, "测试点", null, null, Double.NaN, 120.0),
        )
        assertNull(
            preciseAddressCity(null, "测试市", null, "测试点", null, null, 32.0, 200.0),
        )
    }
}
