package com.zhisheng.weather.data.badge
internal fun badgeDisplayName(grant: BadgeGrant): String = when (grant.badgeId) {
    FIRST_LIGHT_BADGE -> "初光"
    "clear-sky-crown" -> "晴空冠冕"
    "together-with-you" -> "与君同行"
    else -> grant.edition
}
/** Verified grant and signed cache manifest only. Names alone never select a variant. */
internal fun badgeModelKey(grant: BadgeGrant, model: CachedBadgeModel? = null): String? {
    if (grant.badgeId == FIRST_LIGHT_BADGE) return "first-light"
    if (model == null) return null
    return runCatching { validateModelManifest(model.manifest, grant); model.assetKey }.getOrNull()
}
