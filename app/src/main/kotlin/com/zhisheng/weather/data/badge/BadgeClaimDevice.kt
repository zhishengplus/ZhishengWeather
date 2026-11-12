package com.zhisheng.weather.data.badge

import java.security.MessageDigest
import java.util.Locale

/** App-signing-key/user/device scope on Android 8+. Never use this digest to restore ownership. */
internal fun badgeClaimDeviceKey(androidId: String?): String? {
    val normalized = androidId?.lowercase(Locale.ROOT) ?: return null
    if (!normalized.matches(Regex("[0-9a-f]{1,16}")) || normalized.all { it == '0' } || normalized == "9774d56d682e549c") return null
    return MessageDigest.getInstance("SHA-256").digest(("zhisheng-weather-badge-auto-v1:" + normalized).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
