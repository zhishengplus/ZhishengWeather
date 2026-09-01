package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Test

// RainViewer 当前公开接口：只解析过去两小时回波，并严格限制离线目录时效。
class RadarRepositoryTest {

    @Test
    fun countsPastFramesAndIgnoresRetiredNowcastField() {
        val payload = """
            {"version":"2.0","host":"https://tilecache.rainviewer.com","radar":{
              "past":[{"time":1700000000,"path":"/v2/radar/1"},{"time":1700000600,"path":"/v2/radar/2"}],
              "nowcast":[{"time":1700001200,"path":"/v2/radar/f1"},{"time":1700001800,"path":"/v2/radar/f2"},{"time":1700002400,"path":"/v2/radar/f3"}]
            }}
        """.trimIndent()
        assertEquals(2, rainviewerPastFrameCount(payload))
    }

    @Test
    fun currentPayloadNeedsNoNowcastField() {
        val payload = """
            {"version":"2.0","host":"https://tilecache.rainviewer.com","radar":{
              "past":[{"time":1700000000,"path":"/v2/radar/1"}]
            }}
        """.trimIndent()
        assertEquals(1, rainviewerPastFrameCount(payload))
    }

    @Test
    fun unparseablePayloadCountsAsMissing() {
        assertEquals(-1, rainviewerPastFrameCount("not json"))
        assertEquals(-1, rainviewerPastFrameCount(""))
        assertEquals(-1, rainviewerPastFrameCount("{\"radar\":{\"past\":\"bad\"}}"))
    }

    @Test
    fun shortOfflineFallbackIsAllowedButOldRadarIsRejected() {
        val now = 1_700_003_000_000L
        assertEquals(
            true,
            isRadarMetadataCacheUsable(1_700_002_400L, 1_700_002_200L, now, maxStaleMs = 45 * 60_000L),
        )
        assertEquals(
            false,
            isRadarMetadataCacheUsable(1_699_990_000L, 1_699_989_400L, now, maxStaleMs = 45 * 60_000L),
        )
        assertEquals(false, isRadarMetadataCacheUsable(0L, null, now))
    }
}
