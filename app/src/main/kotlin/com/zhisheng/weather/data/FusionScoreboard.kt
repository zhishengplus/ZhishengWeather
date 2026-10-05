package com.zhisheng.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

private val Context.fusionBoardStore: DataStore<Preferences> by preferencesDataStore(name = "fusion_scoreboard")

@Serializable
internal data class PendingHour(
    val hourMillis: Long,
    /** 源 id → 该小时的温度预测（积分板只评它对"国内更准"最关键的近时段温度）。 */
    val perSourceTemp: Map<String, Double>,
)

@Serializable
internal data class SourceErrorStat(
    val n: Int = 0,
    /** 温度绝对误差累计（每次计分对旧值半衰，适应天气型变化）。 */
    val errSum: Double = 0.0,
    val lastUpdate: Long = 0L,
)

@Serializable
internal data class FusionBoard(
    val version: Int = 1,
    val pending: List<PendingHour> = emptyList(),
    val stats: Map<String, SourceErrorStat> = emptyMap(),
)

/**
 * 逐城误差积分板：用本次实况给上一轮快照打分（预报 vs 后续实况），产出每源
 * 自适应系数（有效权重 = 先验 × factor）。核心打分与调权是纯函数、可单测；
 * DataStore 壳子全程静默——积分板绝不拖垮取数。
 */
object FusionScoreboard {

    internal const val MIN_SAMPLES = 8
    internal const val DECAY = 0.85
    internal const val PENDING_LIMIT = 24
    internal const val FACTOR_FLOOR = 0.6
    internal const val FACTOR_CEIL = 1.15
    internal const val NMC_FACTOR_FLOOR = 0.75
    internal const val STALE_MS = 7 * 24 * 60 * 60_000L
    internal const val SCORE_DELAY_MS = 30 * 60_000L
    internal val OBSERVER_PRIORITY = listOf("XIAOMI", "NMC", "WEATHERCN", "TENCENT", "CAIYUN", "QWEATHER")

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var appContext: Context? = null

    internal fun init(context: Context) {
        appContext = context.applicationContext
    }

    internal data class TruthSample(val sourceId: String, val temperature: Double)

    /** 编排入口：打分 + 写快照 + 产出系数。任何异常返回空表（系数即 1.0）。 */
    internal suspend fun settle(
        cityKey: String,
        sources: List<FusionEngine.FusionSource>,
        nowMillis: Long,
    ): Map<String, Double> = withContext(Dispatchers.IO) {
        runCatching {
            val context = appContext ?: return@runCatching emptyMap()
            val board = load(context, cityKey)
            val truth = pickTruth(sources)
            val scored = scorePending(board, truth, nowMillis)
            val withNew = appendSnapshot(scored, sources, nowMillis)
            save(context, cityKey, withNew)
            adaptiveFactors(withNew.stats, nowMillis)
        }.getOrDefault(emptyMap())
    }

    /** 真值：观测类源优先（实况温度）。 */
    internal fun pickTruth(sources: List<FusionEngine.FusionSource>): TruthSample? =
        OBSERVER_PRIORITY.firstNotNullOfOrNull { id ->
            sources.firstOrNull { it.id == id }?.data?.current?.temperature
                ?.takeIf { it.isFinite() && it in -90.0..60.0 }
                ?.let { TruthSample(id, it) }
        }

    /** 给"已到时"的快照打分；命中窗口 = 目标小时 ±2h；过期未命中直接丢弃。 */
    internal fun scorePending(board: FusionBoard, truth: TruthSample?, nowMillis: Long): FusionBoard {
        if (truth == null) return board
        val nowHour = FusionEngine.hourBucket(nowMillis)
        val consumed = mutableListOf<Long>()
        val stats = board.stats.toMutableMap()
        board.pending.forEach { pending ->
            if (pending.hourMillis > nowMillis - SCORE_DELAY_MS) return@forEach
            when {
                abs(pending.hourMillis - nowHour) <= 2 * FusionEngine.HOUR_MS -> {
                    pending.perSourceTemp.forEach { (id, pred) ->
                        if (pred.isFinite()) {
                            val stat = stats[id]?.takeIf { nowMillis - it.lastUpdate <= STALE_MS } ?: SourceErrorStat()
                            stats[id] = stat.copy(
                                n = stat.n + 1,
                                errSum = stat.errSum * DECAY + abs(pred - truth.temperature),
                                lastUpdate = nowMillis,
                            )
                        }
                    }
                    consumed += pending.hourMillis
                }
                pending.hourMillis < nowMillis - 6 * FusionEngine.HOUR_MS -> consumed += pending.hourMillis
            }
        }
        val pruned = stats.mapValues { (_, stat) ->
            if (nowMillis - stat.lastUpdate > STALE_MS) SourceErrorStat() else stat
        }
        return board.copy(pending = board.pending.filterNot { it.hourMillis in consumed }, stats = pruned)
    }

    /** 新快照：各源未来 1..6h 温度；并集保留最近 24 行。 */
    internal fun appendSnapshot(
        board: FusionBoard,
        sources: List<FusionEngine.FusionSource>,
        nowMillis: Long,
    ): FusionBoard {
        val nowHour = FusionEngine.hourBucket(nowMillis)
        val fresh = mutableListOf<PendingHour>()
        for (offset in 1..6L) {
            val hour = nowHour + offset * FusionEngine.HOUR_MS
            val perSource = buildMap {
                sources.forEach { s ->
                    s.data.hourly.firstOrNull { FusionEngine.hourBucket(it.timeMillis) == hour }
                        ?.temperature?.takeIf { it.isFinite() }?.let { put(s.id, it) }
                }
            }
            if (perSource.isNotEmpty()) fresh += PendingHour(hour, perSource)
        }
        return board.copy(
            pending = (board.pending + fresh)
                .distinctBy { it.hourMillis }
                .sortedBy { it.hourMillis }
                .takeLast(PENDING_LIMIT),
        )
    }

    /** 自适应系数：n<8 不参与；factor = clamp(exp(-(MAE - MAE_best)/2), 0.6, 1.15)；NMC 下限 0.75。 */
    internal fun adaptiveFactors(stats: Map<String, SourceErrorStat>, nowMillis: Long): Map<String, Double> {
        val eligible = stats.filter { (_, s) ->
            s.n >= MIN_SAMPLES && s.errSum.isFinite() && nowMillis - s.lastUpdate <= STALE_MS
        }
        if (eligible.isEmpty()) return emptyMap()
        // errSum is exponentially decayed; its divisor must decay the same way.
        // Dividing by the lifetime count makes every estimated error approach zero over time.
        val maes = eligible.mapValues { (_, s) ->
            val effectiveSamples = (1.0 - DECAY.pow(s.n)) / (1.0 - DECAY)
            s.errSum / effectiveSamples
        }
        val best = maes.values.min()
        return maes.mapValues { (id, mae) ->
            val raw = exp(-(mae - best) / 2.0).coerceIn(FACTOR_FLOOR, FACTOR_CEIL)
            if (id == FusionEngine.OFFICIAL_ID) raw.coerceAtLeast(NMC_FACTOR_FLOOR) else raw
        }
    }

    private fun key(cityKey: String) = stringPreferencesKey("sb_$cityKey")

    private suspend fun load(context: Context, cityKey: String): FusionBoard =
        context.fusionBoardStore.data.first()[key(cityKey)]
            ?.let { runCatching { json.decodeFromString<FusionBoard>(it) }.getOrNull() }
            ?: FusionBoard()

    private suspend fun save(context: Context, cityKey: String, board: FusionBoard) {
        context.fusionBoardStore.edit { prefs ->
            prefs[key(cityKey)] = json.encodeToString(FusionBoard.serializer(), board)
        }
    }
}
