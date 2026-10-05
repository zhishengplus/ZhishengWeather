package com.zhisheng.weather.data

import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.WeatherData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 积分板纯函数测试（打分 / 调权 / 快照 / JSON）。DataStore 壳子不在此层测。 */
class FusionScoreboardTest {

    private val now = 1789358400000L // 2026-09-14 12:00 北京
    private val nowHour = FusionEngine.hourBucket(now)

    private fun src(
        id: String,
        currentTemp: Double? = null,
        hourlyTemps: Map<Long, Double> = emptyMap(),
    ) = FusionEngine.FusionSource(
        id, id.substringBefore(':'), 1.0,
        WeatherData(
            current = currentTemp?.let { CurrentWeather(temperature = it) },
            hourly = hourlyTemps.map { (t, temp) -> HourlyWeather(timeMillis = t, temperature = temp) },
            utcOffsetSeconds = 28800,
        ),
    )

    @Test
    fun pickTruthPrefersObservationPriority() {
        val truth = FusionScoreboard.pickTruth(
            listOf(
                src("OPEN-METEO:ecmwf_ifs025", currentTemp = 10.0),
                src("TENCENT", currentTemp = 20.0),
                src("XIAOMI", currentTemp = 21.0),
            )
        )!!
        assertEquals("XIAOMI", truth.sourceId)
        assertEquals(21.0, truth.temperature, 0.001)
    }

    @Test
    fun scoringUsesCurrentObservationAgainstDuePendingHours() {
        val board = FusionBoard(
            pending = listOf(
                PendingHour(nowHour - 3_600_000L, mapOf("XIAOMI" to 20.0, "NMC" to 22.0)), // 到时 → 打分
                PendingHour(nowHour + 3_600_000L, mapOf("XIAOMI" to 99.0)), // 未到时 → 不打分（也不删除）
                PendingHour(nowHour - 10 * 3_600_000L, mapOf("XIAOMI" to 5.0)), // 过期 → 直接丢弃
            )
        )
        val scored = FusionScoreboard.scorePending(board, FusionScoreboard.TruthSample("XIAOMI", 21.0), now)
        assertEquals(listOf(nowHour + 3_600_000L), scored.pending.map { it.hourMillis })
        val xiaomi = scored.stats.getValue("XIAOMI")
        assertEquals(1, xiaomi.n) // 未到时的不计
        assertEquals(1.0, xiaomi.errSum, 0.001) // |20-21|
        assertEquals(1.0, scored.stats.getValue("NMC").errSum, 0.001) // |22-21|
        assertNull(scored.stats["TENCENT"])
    }

    @Test
    fun coldStartKeepsPriorFactors() {
        val stats = mapOf("XIAOMI" to SourceErrorStat(n = 3, errSum = 3.0, lastUpdate = now))
        assertTrue(FusionScoreboard.adaptiveFactors(stats, now).isEmpty())
    }

    @Test
    fun adaptiveFactorClampsAndNmcFloored() {
        val stats = mapOf(
            "XIAOMI" to SourceErrorStat(n = 10, errSum = 10.0, lastUpdate = now), // MAE 1.0（最好）→ 1.0
            "TENCENT" to SourceErrorStat(n = 10, errSum = 50.0, lastUpdate = now), // MAE 5.0 → exp(-2)≈0.14 → 夹到 0.6
            "NMC" to SourceErrorStat(n = 10, errSum = 50.0, lastUpdate = now), // 0.6 → 官方下限 0.75
        )
        val factors = FusionScoreboard.adaptiveFactors(stats, now)
        assertEquals(1.0, factors.getValue("XIAOMI"), 0.001)
        assertEquals(0.6, factors.getValue("TENCENT"), 0.001)
        assertEquals(0.75, factors.getValue("NMC"), 0.001)
    }

    @Test
    fun adaptiveFactorStillSeparatesSourcesAfterManySamples() {
        val effectiveSamples = (1.0 - Math.pow(FusionScoreboard.DECAY, 200.0)) /
            (1.0 - FusionScoreboard.DECAY)
        val stats = mapOf(
            "XIAOMI" to SourceErrorStat(n = 200, errSum = effectiveSamples, lastUpdate = now),
            "TENCENT" to SourceErrorStat(n = 200, errSum = effectiveSamples * 4.0, lastUpdate = now),
        )
        val factors = FusionScoreboard.adaptiveFactors(stats, now)
        assertEquals(1.0, factors.getValue("XIAOMI"), 0.001)
        assertEquals(FusionScoreboard.FACTOR_FLOOR, factors.getValue("TENCENT"), 0.001)
    }

    @Test
    fun staleStatsResetAfterSevenDays() {
        val stale = SourceErrorStat(n = 100, errSum = 500.0, lastUpdate = now - 8L * 24 * 3_600_000L)
        assertTrue(FusionScoreboard.adaptiveFactors(mapOf("XIAOMI" to stale), now).isEmpty())
        val board = FusionBoard(stats = mapOf("XIAOMI" to stale))
        val after = FusionScoreboard.scorePending(board, FusionScoreboard.TruthSample("XIAOMI", 20.0), now)
        assertEquals(0, after.stats.getValue("XIAOMI").n)
    }

    @Test
    fun staleStatsAreDiscardedBeforeScoringNewObservation() {
        val old = SourceErrorStat(n = 100, errSum = 500.0, lastUpdate = now - 8L * 24 * 3_600_000L)
        val board = FusionBoard(
            pending = listOf(PendingHour(nowHour - 3_600_000L, mapOf("XIAOMI" to 20.0))),
            stats = mapOf("XIAOMI" to old),
        )
        val after = FusionScoreboard.scorePending(board, FusionScoreboard.TruthSample("XIAOMI", 21.0), now)
        assertEquals(1, after.stats.getValue("XIAOMI").n)
        assertEquals(1.0, after.stats.getValue("XIAOMI").errSum, 0.001)
    }

    @Test
    fun appendSnapshotKeepsWindowAndLimit() {
        val existing = (1..30L).map { PendingHour(nowHour - it * 3_600_000L, mapOf("XIAOMI" to 20.0)) }
        val sources = listOf(
            src("XIAOMI", hourlyTemps = (1..6L).associate { nowHour + it * 3_600_000L to 20.0 + it }),
            src("NMC", hourlyTemps = mapOf(nowHour + 2 * 3_600_000L to 10.0)),
        )
        val board = FusionScoreboard.appendSnapshot(FusionBoard(pending = existing), sources, now)
        assertEquals(FusionScoreboard.PENDING_LIMIT, board.pending.size)
        val at2h = board.pending.first { it.hourMillis == nowHour + 2 * 3_600_000L }
        assertEquals(2, at2h.perSourceTemp.size) // 多源并集
        assertEquals(10.0, at2h.perSourceTemp.getValue("NMC"), 0.001)
        assertEquals(board.pending.map { it.hourMillis }.sorted(), board.pending.map { it.hourMillis })
        assertEquals(board.pending.size, board.pending.map { it.hourMillis }.distinct().size)
    }

    @Test
    fun boardJsonRoundTripsWithDefaults() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val board = FusionBoard(
            pending = listOf(PendingHour(nowHour, mapOf("XIAOMI" to 20.0))),
            stats = mapOf("XIAOMI" to SourceErrorStat(1, 2.0, now)),
        )
        val text = json.encodeToString(FusionBoard.serializer(), board)
        assertEquals(board, json.decodeFromString<FusionBoard>(text))
        // 空对象可解（向前兼容）
        val empty = json.decodeFromString<FusionBoard>("{}")
        assertTrue(empty.pending.isEmpty())
        assertEquals(1, empty.version)
    }
}
