package com.zhisheng.weather.data

internal fun updateNoticeKey(info: AppUpdateInfo): String? = info.versionCode?.takeIf { it > 0 }
    ?.let { "code:$it" } ?: info.versionName.trim().lowercase().takeIf { it.isNotEmpty() }?.let { "name:$it" }
internal fun shouldOfferUpdate(info: AppUpdateInfo?, seen: Set<String>): Boolean =
    info?.let(::updateNoticeKey)?.let { it !in seen } ?: false
internal fun shouldOfferUpdate(info: AppUpdateInfo?, seen: Set<String>, snoozedUntil: Long, now: Long): Boolean =
    now >= snoozedUntil && shouldOfferUpdate(info, seen)

internal class UpdateNotices(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("update_notices", android.content.Context.MODE_PRIVATE)
    fun offer(info: AppUpdateInfo, now: Long = System.currentTimeMillis()): Boolean {
        val key = updateNoticeKey(info) ?: return false
        if (!shouldOfferUpdate(info, prefs.getStringSet("dismissed", emptySet()).orEmpty(), prefs.getLong(key, 0), now)) return false
        // Closing, process death, or choosing Later defers this release for three days.
        return prefs.edit().putLong(key, now + 3 * 24 * 60 * 60_000L).commit()
    }
    fun dismiss(info: AppUpdateInfo) {
        val key = updateNoticeKey(info) ?: return
        prefs.edit().putStringSet("dismissed", prefs.getStringSet("dismissed", emptySet()).orEmpty() + key).commit()
    }
}
