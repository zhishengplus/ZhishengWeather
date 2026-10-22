package com.zhisheng.weather.data
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

class AnnouncementsTest {
    private val id = "12345678-1234-1234-1234-123456789abc"
    private fun message(change: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
        put("id", id); put("title", "服务公告"); put("body", "第一行\n第二行 <b>也是普通文字</b>")
        put("publishedAt", 1_000_000); put("expiresAt", 87_400_000); put("minVersionCode", 20261086); change()
    }
    private fun feed(rows: List<JsonObject> = listOf(message()), change: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
        put("protocol", 1); put("revision", 2); put("etag", "\"announcements-2-0123456789abcdef\"")
        put("items", JsonArray(rows)); change()
    }.toString()
    @Test fun validFeedKeepsPlainTextNewlinesAndAcceptsEmptyWithdrawnFeed() {
        assertEquals("第一行\n第二行 <b>也是普通文字</b>", parseAnnouncements(feed()).items.single().body)
        assertTrue(parseAnnouncements(feed(emptyList())).items.isEmpty())
    }
    @Test fun malformedOrOversizedFeedCannotBecomeAnAnnouncement() {
        val invalid = listOf(feed { put("protocol", 2) }, feed { put("revision", 0) }, feed { put("etag", "unsafe\r\nheader") },
            feed(listOf(message(), message())), feed(List(11) { message { put("id", "%08x-1234-1234-1234-123456789abc".format(it)) } }),
            feed(listOf(message { put("body", "x".repeat(2001)) })), feed(listOf(message { put("title", 123) })),
            feed(listOf(message { put("title", "line\nbreak") })), feed(listOf(message { put("expiresAt", 1_000_000) })))
        invalid.forEach { assertTrue(it.take(70), runCatching { parseAnnouncements(it) }.isFailure) }
    }
    @Test fun unsupportedExpiredOrFutureNoticesStayHiddenAtExactBoundaries() {
        val parsed = parseAnnouncements(feed())
        assertTrue(eligibleAnnouncements(parsed, 999_999, 20261086).isEmpty())
        assertTrue(eligibleAnnouncements(parsed, 1_000_000, 20261085).isEmpty())
        assertEquals(1, eligibleAnnouncements(parsed, 1_000_000, 20261086).size)
        assertTrue(eligibleAnnouncements(parsed, 87_400_000, 20261086).isEmpty())
    }
    @Test fun pendingOrderIsStableAndNewerVersionCanReceiveOldUnreadNotice() {
        val later = message { put("id", "22345678-1234-1234-1234-123456789abc"); put("publishedAt", 2_000_000) }
        assertEquals(listOf(id, "22345678-1234-1234-1234-123456789abc"), eligibleAnnouncements(parseAnnouncements(feed(listOf(later, message()))), 3_000_000, 20261087).map { it.id })
    }
    @Test fun receiptIdentityIsStablePerNoticeAndNeverReusesLocationOrCrossNoticeIdentity() {
        val seed = "a".repeat(64)
        val first = announcementReceiptIdentity(seed, id)
        assertEquals(first, announcementReceiptIdentity(seed, id))
        assertNotEquals(first.first, first.second)
        assertNotEquals(first.first, announcementReceiptIdentity(seed, "22345678-1234-1234-1234-123456789abc").first)
        assertNotEquals(first.first, announcementReceiptIdentity("b".repeat(64), id).first)
        assertFalse(first.first.contains(seed)); assertFalse(first.first.contains(id))
    }
}
