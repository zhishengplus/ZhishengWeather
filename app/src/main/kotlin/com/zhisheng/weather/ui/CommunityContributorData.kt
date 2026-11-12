package com.zhisheng.weather.ui

internal data class ContributorSection(
    val key: String,
    val title: String,
    val subtitle: String,
    val contributors: List<String>,
)

// Public community data can be loaded at runtime; personal rosters are not bundled.
internal val CommunityContributorSections: List<ContributorSection> = emptyList()
internal val CommunityContributors: List<String> = CommunityContributorSections
    .flatMap(ContributorSection::contributors)
    .distinct()
