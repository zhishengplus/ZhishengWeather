package com.zhisheng.weather.data

import android.content.Context
import android.graphics.BitmapFactory
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.RadarCoverageState
import com.zhisheng.weather.model.RadarFrame
import com.zhisheng.weather.model.RadarTimeline
import com.zhisheng.weather.model.classifyRadarCoveragePixels
import com.zhisheng.weather.model.orderRadarFrames
import com.zhisheng.weather.model.radarTileSample
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object RadarRepository {
    private const val META_URL = "https://api.rainviewer.com/public/weather-maps.json"
    private const val MAX_FRAMES = 13
    private const val COVERAGE_ZOOM = 6
    private const val COVERAGE_CACHE_MS = 24 * 60 * 60_000L
    private const val METADATA_MAX_STALE_MS = 45 * 60_000L

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private lateinit var directory: File
    private val coverageCache = mutableMapOf<String, Pair<Long, RadarCoverageState>>()

    fun init(context: Context) {
        directory = File(context.cacheDir, "radar").apply { mkdirs() }
    }

    suspend fun loadTimeline(city: City? = null): RadarTimeline = withContext(Dispatchers.IO) {
        check(::directory.isInitialized) { "RadarRepository.init must be called first" }
        val (meta, stale) = loadMetadata()
            ?: return@withContext RadarTimeline("", emptyList(), true)
        val frames = orderRadarFrames(meta.radar.past.takeLast(MAX_FRAMES)) { it.time }
            .map { RadarFrame(it.time * 1000L, it.path) }
        val coverage = city?.let { loadCoverage(meta.host, it) } ?: RadarCoverageState.UNKNOWN
        RadarTimeline(meta.host, frames, stale, coverage)
    }

    private fun loadCoverage(host: String, city: City): RadarCoverageState {
        val key = String.format(Locale.US, "%.2f,%.2f", city.latitude, city.longitude)
        synchronized(coverageCache) {
            coverageCache[key]?.takeIf { System.currentTimeMillis() - it.first < COVERAGE_CACHE_MS }?.let { return it.second }
        }
        val sample = radarTileSample(city.latitude, city.longitude, COVERAGE_ZOOM)
        val url = host.trimEnd('/') +
            "/v2/coverage/0/256/$COVERAGE_ZOOM/${sample.tileX}/${sample.tileY}/0/0_0.png"
        val state = try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val bytes = response.body?.bytes() ?: return@use RadarCoverageState.UNKNOWN
                if (!response.isSuccessful || bytes.isEmpty()) return@use RadarCoverageState.UNKNOWN
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use RadarCoverageState.UNKNOWN
                try {
                    val half = 4
                    val width = (half * 2 + 1).coerceAtMost(bitmap.width)
                    val height = (half * 2 + 1).coerceAtMost(bitmap.height)
                    val scaledX = (sample.pixelX.toDouble() / 256.0 * bitmap.width).roundToInt()
                        .coerceIn(0, bitmap.width - 1)
                    val scaledY = (sample.pixelY.toDouble() / 256.0 * bitmap.height).roundToInt()
                        .coerceIn(0, bitmap.height - 1)
                    val left = (scaledX - width / 2).coerceIn(0, bitmap.width - width)
                    val top = (scaledY - height / 2).coerceIn(0, bitmap.height - height)
                    val pixels = IntArray(width * height)
                    bitmap.getPixels(pixels, 0, width, left, top, width, height)
                    classifyRadarCoveragePixels(pixels)
                } finally {
                    bitmap.recycle()
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            RadarCoverageState.UNKNOWN
        }
        synchronized(coverageCache) { coverageCache[key] = System.currentTimeMillis() to state }
        return state
    }

    private fun loadMetadata(): Pair<RadarMetadata, Boolean>? {
        val cachedFile = File(directory, "metadata.json")
        return try {
            client.newCall(Request.Builder().url(META_URL).build()).execute().use { response ->
                val raw = response.body?.string()
                if (!response.isSuccessful || raw.isNullOrBlank()) error("metadata unavailable")
                val metadata = json.decodeFromString<RadarMetadata>(raw)
                if (metadata.host.isBlank() || metadata.radar.past.isEmpty()) error("metadata incomplete")
                cachedFile.writeText(raw)
                metadata to false
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            runCatching {
                if (!cachedFile.isFile) null
                else {
                    val cached = json.decodeFromString<RadarMetadata>(cachedFile.readText())
                    cached.takeIf {
                        it.host.isNotBlank() &&
                            it.radar.past.isNotEmpty() &&
                            isRadarMetadataCacheUsable(
                                generatedSeconds = it.generated,
                                latestFrameSeconds = it.radar.past.maxOfOrNull(RadarFrameDescriptor::time),
                                nowMillis = System.currentTimeMillis(),
                                maxStaleMs = METADATA_MAX_STALE_MS,
                            )
                    }?.let { it to true }
                }
            }.getOrNull()
        }
    }
}

@Serializable
private data class RadarMetadata(
    val version: String = "",
    val generated: Long = 0L,
    val host: String,
    val radar: RadarFrameCollection,
)

@Serializable
private data class RadarFrameCollection(
    val past: List<RadarFrameDescriptor> = emptyList(),
)

@Serializable
private data class RadarFrameDescriptor(
    val time: Long,
    val path: String,
)

private val radarMetaJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** RainViewer 当前公开接口只提供过去两小时回波；-1 表示响应不可识别。 */
internal fun rainviewerPastFrameCount(payload: String): Int {
    val text = payload.trim()
    if (text.isEmpty()) return -1
    return runCatching {
        val root = radarMetaJson.parseToJsonElement(text).jsonObject
        val radar = root["radar"]?.jsonObject ?: return -1
        radar["past"]?.jsonArray?.size ?: -1
    }.getOrDefault(-1)
}

/**
 * 只允许短时离线兜底。目录生成时间和最新帧时间取较新者，避免断网后继续播放
 * 数小时甚至数天前的回波；轻微的服务端时钟超前（5 分钟）可以接受。
 */
internal fun isRadarMetadataCacheUsable(
    generatedSeconds: Long,
    latestFrameSeconds: Long?,
    nowMillis: Long,
    maxStaleMs: Long = 45 * 60_000L,
): Boolean {
    val newestSeconds = maxOf(generatedSeconds, latestFrameSeconds ?: 0L)
    if (newestSeconds <= 0L) return false
    val ageMs = nowMillis - newestSeconds * 1000L
    return ageMs in -5 * 60_000L..maxStaleMs
}
