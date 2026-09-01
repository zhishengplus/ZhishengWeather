package com.zhisheng.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributorsTest {
    @Test
    fun communityListContainsConfirmedScreenshotContributors() {
        val confirmed = listOf(
            "飞667",
            "一杯冰美式、、",
            "M1ralce",
            "紅星照耀中國",
            "我爱跑步",
            "河鱼天雁",
            "你的心里没点高数吗",
            "周月星斗",
            "无敌战神暴王龙",
            "control3",
            "明珠有泪",
            "Gstar_",
            "伍拾两HZ",
            "寡欲老公猪",
        )
        confirmed.forEach { id -> assertTrue("Missing contributor: $id", id in CommunityContributors) }
        assertFalse("Typo must not remain", "的飞667" in CommunityContributors)
        assertEquals("PPQ1028", CommunityContributors.first())
        assertEquals("r1file", CommunityContributors[5])
        assertEquals("库洛小黑", CommunityContributors[7])
        assertEquals(473, CommunityContributors.size)
        assertEquals(CommunityContributors.size, CommunityContributors.distinct().size)
    }

    @Test
    fun communityListKeepsReviewedSectionsAndRejectsHostileEntries() {
        assertEquals(
            listOf("FOUNDING", "COCREATION", "SUPPORT", "COMMUNITY"),
            CommunityContributorSections.map { it.key },
        )
        assertEquals(listOf(24, 128, 16, 305), CommunityContributorSections.map { it.contributors.size })
        assertEquals(
            CommunityContributors,
            CommunityContributorSections.flatMap { it.contributors }.distinct(),
        )
        listOf(
            "catt95",
            "喝不了的酸牛奶",
            "夕夕夕夕夕",
            "瓜子920",
            "スカーレット",
            "裤岸小犇",
            "慎思合一",
            "战帅101",
            "阿珍快来",
        ).forEach { id -> assertFalse("Excluded contributor leaked into public list: $id", id in CommunityContributors) }
        listOf("xGrok", "一道积分算一天", "裤1234567", "dyt5AAUI", "道達").forEach { id ->
            assertTrue("Owner-approved contributor is missing: $id", id in CommunityContributors)
        }
        assertTrue(CommunityContributors.indexOf("神秘票风口昌男") < CommunityContributors.indexOf("ExclusiveD"))
        assertTrue(CommunityContributors.none(String::isBlank))
    }
}
