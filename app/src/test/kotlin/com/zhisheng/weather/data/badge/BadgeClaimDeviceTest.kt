package com.zhisheng.weather.data.badge

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class BadgeClaimDeviceTest {
    @Test fun deviceKeyIsStableAcrossCaseAndUsesIndependentPurpose() {
        val id = "0123456789abcdef"
        val key = badgeClaimDeviceKey(id)!!
        assertEquals(key, badgeClaimDeviceKey(id.uppercase()))
        assertEquals(64, key.length); assertFalse(key.contains(id))
        val plain = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertNotEquals(plain, key); assertNotEquals(key, badgeClaimDeviceKey("0123456789abcdee"))
    }
    @Test fun missingInvalidAndKnownBrokenIdentifiersNeverFallBackToRandomIdentity() {
        for (value in listOf(null, "", "0", "0000000000000000", "9774d56d682e549c", "imei-123", "1234567890abcdef0", " abc")) assertNull(badgeClaimDeviceKey(value))
    }
}
