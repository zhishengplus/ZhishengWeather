package com.zhisheng.weather.data

import org.junit.Assert.*
import org.junit.Test

class SharedPlaceResponseTest {
    private val response = """{"protocol":1,"source":"amap","retentionDays":365,"address":
        {"province":"甘肃省","city":"金昌市","district":"金川区","street":"某街道","latitude":0,"longitude":0}}"""
    @Test fun serverLabelCannotReplaceDeviceCoordinateOrInventWeatherIdentity() {
        val label = requireNotNull(parseSharedPlaceResponse(response, 38.51, 102.18, true))
        assertEquals("金川区", label.name)
        assertEquals(38.51, label.latitude, 0.0)
        assertEquals(102.18, label.longitude, 0.0)
        assertEquals("zhisheng-amap", label.addressSource)
        assertNull(label.weatherLocationKey)
    }
    @Test fun approximateModeNeverKeepsServerStreet() {
        assertNull(parseSharedPlaceResponse(response, 38.51, 102.18, false)?.street)
    }
    @Test fun differentRetentionOrProtocolMustNotSilentlyChangeConsent() {
        for (raw in listOf(response.replace("365", "730"), response.replace("\"protocol\":1", "\"protocol\":2"))) {
            try { parseSharedPlaceResponse(raw, 38.51, 102.18, true); fail("Accepted undisclosed contract") }
            catch (_: PlaceServiceException) { }
        }
    }
    @Test fun absentAndOldConsentNeverEnableUploadAndIdentityIsRedacted() {
        assertFalse(SharedPlaceConfiguration().enabled)
        assertFalse(SharedPlaceConfiguration(1,"installation","secret").enabled)
        assertFalse(SharedPlaceConfiguration(2,"installation","").enabled)
        assertTrue(SharedPlaceConfiguration(2,"installation","secret").enabled)
        assertFalse(SharedPlaceConfiguration(2,"installation","secret").toString().contains("secret"))
        assertFalse(PlaceCredentials(sharedPlace = SharedPlaceConfiguration(2,"installation","secret")).toString().contains("installation"))
    }
}
