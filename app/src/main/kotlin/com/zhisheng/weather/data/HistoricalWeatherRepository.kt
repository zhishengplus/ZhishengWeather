package com.zhisheng.weather.data

import android.content.Context
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.HistoricalDay
import com.zhisheng.weather.model.HistoricalReview
import com.zhisheng.weather.model.historicalTargetDates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private object HistoricalWeatherSource {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(city: City, date: LocalDate): HistoricalDay? = withContext(Dispatchers.IO) {
        val url = "https://archive-api.open-meteo.com/v1/archive".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", city.latitude.toString())
            .addQueryParameter("longitude", city.longitude.toString())
            .addQueryParameter("start_date", date.toString())
            .addQueryParameter("end_date", date.toString())
            .addQueryParameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min,temperature_2m_mean," +
                    "precipitation_sum,wind_speed_10m_max,wind_gusts_10m_max",
            )
            // ERA5-Land 单模型不提供 weather_code/风速等多项日变量，
            // 强制它会让页面只剩「未知 / --」；Best Match 按年份选择完整数据集。
            .addQueryParameter("models", "best_match")
            .addQueryParameter("timezone", "auto")
            .build()
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val raw = response.body?.string()
                if (!response.isSuccessful || raw.isNullOrBlank()) return@withContext null
                val daily = json.decodeFromString<ArchiveResponse>(raw).daily ?: return@withContext null
                val returnedDate = daily.time.firstOrNull() ?: date.toString()
                HistoricalDay(
                    date = returnedDate,
                    weatherCode = daily.weather_code?.firstOrNull(),
                    high = daily.temperature_2m_max?.firstOrNull(),
                    low = daily.temperature_2m_min?.firstOrNull(),
                    mean = daily.temperature_2m_mean?.firstOrNull(),
                    precipitationMm = daily.precipitation_sum?.firstOrNull(),
                    windMaxKmh = daily.wind_speed_10m_max?.firstOrNull(),
                    gustMaxKmh = daily.wind_gusts_10m_max?.firstOrNull(),
                ).takeIf(HistoricalDay::hasUsableWeather)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }
}

object HistoricalWeatherRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var directory: File

    fun init(context: Context) {
        directory = File(context.cacheDir, "history").apply { mkdirs() }
    }

    suspend fun loadReview(
        city: City,
        referenceDate: LocalDate,
        count: Int = 5,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): HistoricalReview = coroutineScope {
        check(::directory.isInitialized) { "HistoricalWeatherRepository.init must be called first" }
        val targets = historicalTargetDates(referenceDate, count)
        val completed = AtomicInteger(0)
        val gate = Semaphore(3)
        val days = targets.map { date ->
            async {
                val day = gate.withPermit { loadDay(city, date) }
                onProgress(completed.incrementAndGet(), targets.size)
                day
            }
        }.awaitAll().filterNotNull().sortedByDescending { it.date }
        HistoricalReview(referenceDate, days, targets.size)
    }

    private suspend fun loadDay(city: City, date: LocalDate): HistoricalDay? {
        read(city, date)?.let { return it.day }
        return HistoricalWeatherSource.fetch(city, date)?.also { write(city, date, it) }
    }

    private suspend fun read(city: City, date: LocalDate): HistoryCacheEntry? = withContext(Dispatchers.IO) {
        runCatching {
            val file = cacheFile(city, date)
            if (!file.isFile) null else json.decodeFromString<HistoryCacheEntry>(file.readText())
        }.getOrNull()
    }

    private suspend fun write(city: City, date: LocalDate, day: HistoricalDay) = withContext(Dispatchers.IO) {
        runCatching {
            directory.mkdirs()
            val target = cacheFile(city, date)
            val temp = File(directory, target.name + ".tmp")
            temp.writeText(json.encodeToString(HistoryCacheEntry(day, System.currentTimeMillis())))
            if (!temp.renameTo(target)) {
                target.writeText(temp.readText())
                temp.delete()
            }
        }
        Unit
    }

    private fun cacheFile(city: City, date: LocalDate): File {
        // v2 使旧 ERA5-Land 空字段缓存自动失效。
        val raw = String.format(Locale.US, "best-match-v2|%.4f|%.4f|%s", city.latitude, city.longitude, date)
        return File(directory, sha256(raw) + ".json")
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

@Serializable
private data class HistoryCacheEntry(
    val day: HistoricalDay,
    val fetchedAt: Long,
)

@Serializable
private data class ArchiveResponse(val daily: ArchiveDaily? = null)

@Serializable
private data class ArchiveDaily(
    val time: List<String> = emptyList(),
    val weather_code: List<Int?>? = null,
    val temperature_2m_max: List<Double?>? = null,
    val temperature_2m_min: List<Double?>? = null,
    val temperature_2m_mean: List<Double?>? = null,
    val precipitation_sum: List<Double?>? = null,
    val wind_speed_10m_max: List<Double?>? = null,
    val wind_gusts_10m_max: List<Double?>? = null,
)
