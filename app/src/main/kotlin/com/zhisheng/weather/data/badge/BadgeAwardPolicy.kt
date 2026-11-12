package com.zhisheng.weather.data.badge

/** Verified ownership permits collecting; optional model preparation/animation cannot hold it hostage. */
internal fun canCollectBadgeAward(awardId: String?, collection: List<BadgeGrant>, collectingId: String?): Boolean =
    collectingId == null && (awardId == null || collection.any { it.grantId == awardId })
