package com.zhisheng.weather.data

import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class AnnouncementStreamTest {
    @Test fun `only complete named events and heartbeats are delivered`() {
        val events = mutableListOf<String?>()
        readAnnouncementEvents(Buffer().writeUtf8(": connected\n\nevent: ignored\ndata: private\n\nevent: announcements\ndata: first\ndata: second\n\nevent: announcements\ndata: unfinished\n"), events::add)
        assertEquals(listOf(null, "first\nsecond"), events)
    }
    @Test fun `oversized lines and multi-line events are bounded`() {
        for (raw in listOf("data: " + "x".repeat(70_000) + "\n", "data: " + "x".repeat(40_000) + "\ndata: " + "x".repeat(40_000) + "\n\n")) {
            assertThrows(Exception::class.java) { readAnnouncementEvents(Buffer().writeUtf8(raw)) { fail("oversized event") } }
        }
    }
}
