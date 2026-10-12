package com.zhisheng.weather.ui

import com.zhisheng.weather.BuildConfig
import com.zhisheng.weather.R

internal data class CommunityGroup(
    val index: Int,
    val number: String,
    val joinUrl: String,
    val qrResource: Int,
)

// Match the public website's group numbers, decoded QR links and recommendation order.
internal val CommunityGroups = listOf(
    CommunityGroup(2, BuildConfig.COMMUNITY_QQ_GROUP, "https://qm.qq.com/q/Plez46Gzuy", R.drawable.qq_group_2_qr),
    CommunityGroup(1, BuildConfig.COMMUNITY_QQ_GROUP_1, "https://qm.qq.com/q/nfpxlC4Pyo", R.drawable.qq_group_1_qr),
)

internal val CommunityQqGroup: String
    get() = CommunityGroups.first().number

internal fun openCommunityGroupLink(group: CommunityGroup, launch: (String, String?) -> Boolean): Boolean =
    launch(group.joinUrl, "com.tencent.mobileqq") || launch(group.joinUrl, null)
