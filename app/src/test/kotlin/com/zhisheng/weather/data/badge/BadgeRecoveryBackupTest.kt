package com.zhisheng.weather.data.badge

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class BadgeRecoveryBackupTest {
    private val code = "ZB1.12345678-1234-1234-1234-123456789abc." + "a".repeat(43)
    private fun backup() = BadgeRecoveryBackup(listOf(BadgeRecoveryBackup.Entry("澄空之戒", 23, "晨光", code)), 1000)
    @Test fun utf8FileContainsPerInstanceCodesNamesAndInstructionsWithoutAccountCredentials() {
        val out = ByteArrayOutputStream(); writeBadgeRecoveryBackup(backup()) { out }
        val text = out.toString("UTF-8")
        for (required in listOf("澄空之戒", "NO.000023", "晨光", code, "转入后旧码失效")) assertTrue(text.contains(required))
        assertFalse(text.contains("FL1.")); assertFalse(text.contains("token"))
        assertFalse(backup().toString().contains(code)); assertFalse(backup().entries.single().toString().contains(code))
    }
    @Test fun unavailableOrFailingDestinationNeverReportsSuccess() {
        assertThrows(IOException::class.java) { writeBadgeRecoveryBackup(backup()) { null } }
        val out = object : ByteArrayOutputStream() { override fun close() { throw IOException("fixture") } }
        assertThrows(IOException::class.java) { writeBadgeRecoveryBackup(backup()) { out } }
    }
}
