package com.zhisheng.weather.ui

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

class CommunityDirectoryTest {
    private val sampleSponsors = (1..6).map { "Sample supporter $it" }
    private val sampleSections = listOf(ContributorSection("COMMUNITY", "Community", "Sample contributors", listOf("Sample contributor")))
    private val sampleContributors = sampleSections.flatMap { it.contributors }
    private fun payload(change: (JsonObjectBuilder.() -> Unit)? = null): String = buildJsonObject {
        put("protocol", 1); put("revision", 2); put("updatedAt", 1000)
        put("sponsors", JsonArray(sampleSponsors.map(::JsonPrimitive)))
        put("sections", JsonArray(sampleSections.map { section -> buildJsonObject {
            put("key", section.key); put("title", section.title); put("subtitle", section.subtitle)
            put("contributors", JsonArray(section.contributors.map(::JsonPrimitive)))
        } }))
        change?.invoke(this)
    }.toString()
    @Test fun rosterRoundTripKeepsEveryNameGroupAndRank() {
        val result = parseCommunityDirectory(payload())
        assertEquals(sampleSponsors, result.sponsors)
        assertEquals(sampleSections, result.sections)
        assertEquals(sampleContributors, result.contributors)
    }
    @Test fun invalidProtocolAndRevisionAreRejected() {
        for (raw in listOf(payload { put("protocol", 2) }, payload { put("revision", 0) })) {
            assertTrue(runCatching { parseCommunityDirectory(raw) }.isFailure)
        }
    }
    @Test fun emptyOrTruncatedRemoteListIsRejected() {
        for (raw in listOf("{}", payload { put("sponsors", JsonArray(emptyList())) }, payload { put("sections", JsonArray(emptyList())) })) {
            assertTrue(runCatching { parseCommunityDirectory(raw) }.isFailure)
        }
    }
    @Test fun duplicateNamesAreRejected() {
        assertTrue(runCatching { parseCommunityDirectory(payload { put("sponsors", JsonArray((sampleSponsors + sampleSponsors[0]).map(::JsonPrimitive))) }) }.isFailure)
    }
    @Test fun validServerAppendPreservesOriginalNamesAndOrdering() {
        val result = parseCommunityDirectory(payload { put("sponsors", JsonArray((sampleSponsors + "测试公开昵称").map(::JsonPrimitive))) })
        assertEquals(sampleSponsors, result.sponsors.dropLast(1))
        assertEquals("测试公开昵称", result.sponsors.last())
    }
}
