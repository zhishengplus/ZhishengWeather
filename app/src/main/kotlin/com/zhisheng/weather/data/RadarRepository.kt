package com.zhisheng.weather.data

import android.content.Context
import com.zhisheng.weather.model.RadarFrame
import com.zhisheng.weather.model.RadarTimeline
import com.zhisheng.weather.model.orderRadarFrames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object RadarRepository {
    private const val META_URL = "https://api.rainviewer.com/public/weather-maps.json"
    private const val MAX_FRAMES = 13

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private lateinit var directory: File

    fun init(context: Context) {
        directory = File(context.cacheDir, "radar").apply { mkdirs() }
    }

    suspend fun loadTimeline(): RadarTimeline = withContext(Dispatchers.IO) {
        check(::directory.isInitialized) { "RadarRepository.init must be called first" }
        val (meta, stale) = loadMetadata()
            ?: return@withContext RadarTimeline("", emptyList(), true)
        val frames = orderRadarFrames(meta.radar.past.takeLast(MAX_FRAMES)) { it.time }
            .map { RadarFrame(it.time * 1000L, it.path) }
        RadarTimeline(meta.host, frames, stale)
    }

    private fun loadMetadata(): Pair<RadarMetadata, Boolean>? {
        val cachedFile = File(directory, "metadata.json")
        return try {
            client.newCall(Request.Builder().url(META_URL).build()).execute().use { response ->
                val raw = response.body?.string()
                if (!response.isSuccessful || raw.isNullOrBlank()) error("metadata unavailable")
                val metadata = json.decodeFromString<RadarMetadata>(raw)
                cachedFile.writeText(raw)
                metadata to false
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            runCatching {
                if (!cachedFile.isFile) null
                else json.decodeFromString<RadarMetadata>(cachedFile.readText()) to true
            }.getOrNull()
        }
    }
}

@Serializable
private data class RadarMetadata(
    val version: String = "",
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
