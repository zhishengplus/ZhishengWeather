package com.zhisheng.weather.widget

import com.zhisheng.weather.model.AlertInfo
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WeatherAlertPolicyTest {
    private val now = Instant.parse("2026-09-22T02:00:00Z").toEpochMilli()
    private val warning = AlertInfo("暴雨黄色预警", "请注意防范", pubTime = "2026-09-22T09:00:00+08:00", id = "rain-1")

    @Test fun newWarningIsDeliveredOnceAndSurvivesReordering() {
        val first = planWeatherAlerts("city-a", listOf(warning, warning), emptyMap(), now)
        assertEquals(1, first.deliveries.size)
        val again = planWeatherAlerts("city-a", listOf(warning), first.receipts, now + 60_000)
        assertTrue(again.deliveries.isEmpty())
        assertEquals(first.activeKeys, again.activeKeys)
    }
    @Test fun changedOfficialTextOrPublicationProducesOneUpdatedNotice() {
        val first = planWeatherAlerts("city-a", listOf(warning), emptyMap(), now)
        val next = planWeatherAlerts("city-a", listOf(warning.copy(detail = "升级防御措施")), first.receipts, now + 60_000)
        assertEquals(1, next.deliveries.size)
        assertEquals(first.activeKeys, next.activeKeys)
    }
    @Test fun cityIsolationAndWithdrawalDoNotForgetDeduplication() {
        val first = planWeatherAlerts("city-a", listOf(warning), emptyMap(), now)
        val withdrawn = planWeatherAlerts("city-a", emptyList(), first.receipts, now + 60_000)
        assertTrue(withdrawn.activeKeys.isEmpty())
        assertTrue(planWeatherAlerts("city-a", listOf(warning), withdrawn.receipts, now + 120_000).deliveries.isEmpty())
        assertEquals(1, planWeatherAlerts("city-b", listOf(warning), first.receipts, now + 120_000).deliveries.size)
    }
    @Test fun cancelledExpiredFutureAndStaleWarningsNeverNotify() {
        listOf(warning.copy(title = "解除暴雨黄色预警"), warning.copy(expiresAt = now - 1),
            warning.copy(pubTime = "2026-09-19T09:00:00+08:00"), warning.copy(pubTime = "2026-09-23T09:00:00+08:00"))
            .forEach { assertTrue(planWeatherAlerts("a", listOf(it), emptyMap(), now).deliveries.isEmpty()) }
    }
    @Test fun explicitValidityAllowsLongLivedWarningsAndLimitsNotificationTimeout() {
        val old = warning.copy(pubTime = "2026-09-20T00:00:00Z", expiresAt = now + 3_600_000)
        assertEquals(now + 3_600_000, planWeatherAlerts("a", listOf(old), emptyMap(), now).deliveries.single().expiresAt)
    }
    @Test fun missingPublicationTimeDoesNotExtendValidityEveryRefresh() {
        val undated = warning.copy(pubTime = null)
        val first = planWeatherAlerts("a", listOf(undated), emptyMap(), now)
        assertEquals(1, first.deliveries.size)
        assertTrue(planWeatherAlerts("a", listOf(undated), first.receipts, now + 25 * 3_600_000).activeKeys.isEmpty())
    }
    @Test fun localAndEpochPublicationTimesAreParsedWithoutPhoneTimezone() {
        listOf("2026-09-22 09:00:00", "1790038800", "1790038800000").forEach { time ->
            assertEquals(time, 1, planWeatherAlerts("a", listOf(warning.copy(pubTime = time)), emptyMap(), now, 8 * 3600).deliveries.size)
        }
    }
    @Test fun deniedPermanentlyAndBlockedChannelsOfferSettingsButDisableAlwaysWorks() {
        assertEquals(NotificationPermissionAction.SETTINGS, notificationPermissionAction(true, true, false, true, false, false, true))
        assertEquals(NotificationPermissionAction.SETTINGS, notificationPermissionAction(true, false, true, false, false, false, true))
        assertEquals(NotificationPermissionAction.SETTINGS, notificationPermissionAction(true, true, true, true, false, true, false))
        assertEquals(NotificationPermissionAction.REQUEST, notificationPermissionAction(true, true, false, false, false, false, true))
        assertEquals(NotificationPermissionAction.SAVE, notificationPermissionAction(false, true, false, true, false, false, false))
    }
}
