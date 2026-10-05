package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.MinutePrecip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Cross-provider supplement, always identified as model retrospective rather than observation. */
internal object PrecipitationHistorySource {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS).build()
    private data class Entry(val fetchedAt: Long, val points: List<MinutePrecip>)
    private val cache = ConcurrentHashMap<String, Entry>()

    suspend fun fetch(city: City): List<MinutePrecip> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val key = "${city.latitude},${city.longitude}"
        cache[key]?.takeIf { now - it.fetchedAt in 0..600_000L }?.let { return@withContext it.points }
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${city.latitude}&longitude=${city.longitude}" +
                "&minutely_15=precipitation&past_minutely_15=8&forecast_minutely_15=1&timeformat=unixtime&timezone=GMT" +
                openMeteoCellSelection(city.isPreciseLocation)
            val points = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                response.body?.string()?.let { parse(it, now) }.orEmpty()
            }
            if (cache.size > 32) cache.entries.removeIf { now - it.value.fetchedAt > 600_000L }
            if (points.isNotEmpty()) cache[key] = Entry(now, points)
            points
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { emptyList() }
    }

    internal fun parse(raw: String, now: Long): List<MinutePrecip> {
        val result = json.decodeFromString<HistoryResponse>(raw).minutely_15 ?: return emptyList()
        return result.time.mapIndexedNotNull { index, seconds ->
            val time = seconds * 1_000L
            val amount = result.precipitation.getOrNull(index)?.takeIf { it.isFinite() && it >= 0.0 }
            // Timestamps mark the end of each modelled 15-minute accumulation window.
            if (time !in (now - 7_200_000L)..now || amount == null) null
            else (amount * 4).toFloat().takeIf { it.isFinite() }?.let { MinutePrecip(time, it) }
        }.distinctBy { it.timeMillis }.sortedBy { it.timeMillis }
    }
}

@Serializable private data class HistoryResponse(val minutely_15: HistorySeries? = null)
@Serializable private data class HistorySeries(
    val time: List<Long> = emptyList(), val precipitation: List<Double?> = emptyList(),
)
