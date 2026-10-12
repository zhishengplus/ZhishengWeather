package com.zhisheng.weather.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RadarObservationReloadTest {
    @Test fun expiredFeedOnResumeHidesOldFramesAndActuallyRequestsFreshObservation() = runBlocking {
        val events = mutableListOf<String>()
        val resumedAt = 1_800_000_000_000L
        val observedAt = resumedAt - 46 * 60_000L
        val result = reloadRadarObservation(observedAt, radarObservationUsable(observedAt, resumedAt),
            onLoading = { events += "loading" }, load = { events += "request"; resumedAt })
        assertEquals(listOf("loading", "request"), events)
        assertEquals(RadarReloadResult.Loaded(resumedAt, false), result)
    }
    @Test fun expiredFeedBecomesErrorOnlyWhenReloadFails() = runBlocking {
        val events = mutableListOf<String>()
        val result = reloadRadarObservation("expired", false,
            onLoading = { events += "loading" }, load = { events += "request"; error("offline") })
        assertEquals(listOf("loading", "request"), events)
        assertTrue(result is RadarReloadResult.Failed)
    }
    @Test fun stillUsableFeedCanRemainVisibleAsStaleAfterFailure() = runBlocking {
        val result = reloadRadarObservation("recent", true,
            onLoading = { fail("recent feed must stay visible") }, load = { error("offline") })
        assertEquals(RadarReloadResult.Loaded("recent", true), result)
    }
    @Test fun leavingPagePropagatesCancellationInsteadOfPublishingOldFeed() = runBlocking {
        try {
            reloadRadarObservation("recent", true, {}, { throw CancellationException() })
            fail("cancellation must escape")
        } catch (_: CancellationException) { }
    }
}
