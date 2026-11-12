package com.zhisheng.weather.ui

import com.zhisheng.weather.BuildConfig
import com.zhisheng.weather.R

internal data class CommunityGroup(
    val index: Int,
    val number: String,
    val joinUrl: String,
    val qrResource: Int,
)

// Contact details are supplied by each developer through local configuration.
internal val CommunityGroups = listOf(
    CommunityGroup(2, BuildConfig.COMMUNITY_QQ_GROUP, BuildConfig.COMMUNITY_QQ_JOIN_URL, R.drawable.qq_group_2_qr),
    CommunityGroup(1, BuildConfig.COMMUNITY_QQ_GROUP_1, BuildConfig.COMMUNITY_QQ_JOIN_URL_1, R.drawable.qq_group_1_qr),
)

internal val CommunityQqGroup: String
    get() = CommunityGroups.first().number

internal fun openCommunityGroupLink(group: CommunityGroup, launch: (String, String?) -> Boolean): Boolean =
    group.joinUrl.startsWith("https://") &&
        (launch(group.joinUrl, "com.tencent.mobileqq") || launch(group.joinUrl, null))
