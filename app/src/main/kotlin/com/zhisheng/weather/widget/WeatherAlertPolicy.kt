package com.zhisheng.weather.widget

import com.zhisheng.weather.model.AlertInfo
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.*
import java.time.format.DateTimeFormatter

@Serializable
internal data class AlertReceipt(val fingerprint: String, val firstSeen: Long, val lastSeen: Long)
internal data class AlertDelivery(val key: String, val alert: AlertInfo, val expiresAt: Long)
internal data class AlertPlan(val deliveries: List<AlertDelivery>, val activeKeys: Set<String>, val receipts: Map<String, AlertReceipt>)

private val withdrawalTitle = Regex("解除|取消|撤销|cancel", RegexOption.IGNORE_CASE)

internal fun planWeatherAlerts(cityKey: String, alerts: List<AlertInfo>, previous: Map<String, AlertReceipt>, now: Long,
                               utcOffsetSeconds: Int? = null): AlertPlan {
    val receipts = previous.filterValues { now - it.lastSeen in 0..7 * 86_400_000L }.toMutableMap()
    val deliveries = mutableListOf<AlertDelivery>()
    val active = mutableSetOf<String>()
    alerts.filter { it.title.isNotBlank() }.forEach { alert ->
        // The provider ID identifies an evolving warning; publication/content identifies its revision.
        val key = digest(listOf(cityKey, alert.id?.takeIf(String::isNotBlank) ?: alert.title, alert.type.orEmpty()))
        if (withdrawalTitle.containsMatchIn(alert.title)) return@forEach
        val fingerprint = digest(listOf(alert.title, alert.detail.orEmpty(), alert.pubTime.orEmpty(), alert.level.orEmpty(), alert.severity.name))
        val old = receipts[key]
        val issued = alert.pubTime?.let { alertTime(it, utcOffsetSeconds) }
        if (issued != null && issued > now + 300_000) return@forEach
        val firstSeen = old?.takeIf { it.fingerprint == fingerprint }?.firstSeen ?: now
        // Undated or unbounded messages get a conservative local lifetime, never extended on each refresh.
        val expiry = alert.expiresAt ?: ((issued ?: firstSeen) + 86_400_000L)
        if (expiry <= now) return@forEach
        if (!active.add(key)) return@forEach
        if (old?.fingerprint != fingerprint) deliveries += AlertDelivery(key, alert, expiry)
        receipts[key] = AlertReceipt(fingerprint, firstSeen, now)
    }
    return AlertPlan(deliveries, active, receipts)
}

private fun digest(parts: List<String>) = MessageDigest.getInstance("SHA-256")
    .digest(parts.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

private fun alertTime(value: String, offsetSeconds: Int?): Long? {
    val text = value.trim()
    text.toLongOrNull()?.let { return if (it in 1_000_000_000..9_999_999_999L) it * 1000 else it.takeIf { t -> t >= 1_000_000_000_000L } }
    return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(text.replace(' ', 'T'), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .toInstant(ZoneOffset.ofTotalSeconds(offsetSeconds?.takeIf { it in -64800..64800 } ?: 8 * 3600)).toEpochMilli() }.getOrNull()
}

internal enum class NotificationPermissionAction { SAVE, REQUEST, SETTINGS }
internal fun notificationPermissionAction(wantsNotification: Boolean, runtimeRequired: Boolean, granted: Boolean,
    askedBefore: Boolean, showRationale: Boolean, appAllowed: Boolean, channelAllowed: Boolean): NotificationPermissionAction =
    when {
        !wantsNotification -> NotificationPermissionAction.SAVE
        runtimeRequired && !granted -> if (askedBefore && !showRationale) NotificationPermissionAction.SETTINGS else NotificationPermissionAction.REQUEST
        !appAllowed || !channelAllowed -> NotificationPermissionAction.SETTINGS
        else -> NotificationPermissionAction.SAVE
    }
