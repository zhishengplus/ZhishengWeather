package com.zhisheng.weather.ui

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

class CommunityDirectoryTest {
    private fun payload(change: (JsonObjectBuilder.() -> Unit)? = null): String = buildJsonObject {
        put("protocol", 1); put("revision", 2); put("updatedAt", 1000)
        put("sponsors", JsonArray(SponsorBoard.map(::JsonPrimitive)))
        put("sections", JsonArray(CommunityContributorSections.map { section -> buildJsonObject {
            put("key", section.key); put("title", section.title); put("subtitle", section.subtitle)
            put("contributors", JsonArray(section.contributors.map(::JsonPrimitive)))
        } }))
        change?.invoke(this)
    }.toString()
    @Test fun bundledRosterRoundTripKeepsEveryNameGroupAndRank() {
        val result = parseCommunityDirectory(payload())
        assertEquals(SponsorBoard, result.sponsors)
        assertEquals(CommunityContributorSections, result.sections)
        assertEquals(CommunityContributors, result.contributors)
    }
    @Test fun invalidProtocolAndRevisionAreRejected() {
        for (raw in listOf(payload { put("protocol", 2) }, payload { put("revision", 0) })) {
            assertTrue(runCatching { parseCommunityDirectory(raw) }.isFailure)
        }
    }
    @Test fun emptyOrTruncatedRemoteListCannotEraseBundledFallback() {
        for (raw in listOf("{}", payload { put("sponsors", JsonArray(emptyList())) }, payload { put("sections", JsonArray(emptyList())) })) {
            assertTrue(runCatching { parseCommunityDirectory(raw) }.isFailure)
        }
    }
    @Test fun pinnedRankAndDuplicateNamesAreValidated() {
        val moved = SponsorBoard.toMutableList().apply { add(removeAt(5)) }
        assertTrue(runCatching { parseCommunityDirectory(payload { put("sponsors", JsonArray(moved.map(::JsonPrimitive))) }) }.isFailure)
        assertTrue(runCatching { parseCommunityDirectory(payload { put("sponsors", JsonArray((SponsorBoard + SponsorBoard[0]).map(::JsonPrimitive))) }) }.isFailure)
    }
    @Test fun validServerAppendPreservesOriginalNamesAndOrdering() {
        val result = parseCommunityDirectory(payload { put("sponsors", JsonArray((SponsorBoard + "测试公开昵称").map(::JsonPrimitive))) })
        assertEquals(SponsorBoard, result.sponsors.dropLast(1))
        assertEquals("测试公开昵称", result.sponsors.last())
    }
}
