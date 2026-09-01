package com.zhisheng.weather.data

import android.content.Context
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.HistoricalDay
import com.zhisheng.weather.model.HistoricalReview
import com.zhisheng.weather.model.RecentWeatherWeek
import com.zhisheng.weather.model.historicalTargetDates
import com.zhisheng.weather.model.normalized
import com.zhisheng.weather.model.normalizeRecentWeatherDays
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
            .addQueryParameter("temperature_unit", "celsius")
            .addQueryParameter("wind_speed_unit", "kmh")
            .addQueryParameter("precipitation_unit", "mm")
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
                // 历史页的核心任务是做温度对照。只有现象码、没有任何温度的空壳记录
                // 不进入列表，避免再次出现整屏「未知 / --」。
                ).normalized(date)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }
}

private object RecentWeatherSource {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(city: City, expectedEndDate: LocalDate): RecentWeatherWeek? = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", city.latitude.toString())
            .addQueryParameter("longitude", city.longitude.toString())
            .addQueryParameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min,temperature_2m_mean," +
                    "precipitation_sum,wind_speed_10m_max,wind_gusts_10m_max",
            )
            // 只取已经结束的七个自然日，避免把今天尚未完成的高低温混进回顾。
            .addQueryParameter("past_days", "7")
            .addQueryParameter("forecast_days", "0")
            .addQueryParameter("temperature_unit", "celsius")
            .addQueryParameter("wind_speed_unit", "kmh")
            .addQueryParameter("precipitation_unit", "mm")
            .addQueryParameter("timezone", "auto")
            .build()
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val raw = response.body?.string()
                if (!response.isSuccessful || raw.isNullOrBlank()) return@withContext null
                val daily = json.decodeFromString<ArchiveResponse>(raw).daily ?: return@withContext null
                val rawDays = daily.time.indices.mapNotNull { index ->
                    daily.toHistoricalDay(index, daily.time[index], model = "RECENT-ARCHIVE")
                }
                // 即便服务端忽略 forecast_days=0 或跨时区多回了一天，也只允许
                // 用户所选城市“昨天”结束的七个完整自然日进入页面。
                val days = normalizeRecentWeatherDays(rawDays, expectedEndDate)
                RecentWeatherWeek(days).takeIf { it.days.isNotEmpty() }
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

    suspend fun loadPastWeek(city: City, today: LocalDate): RecentWeatherWeek {
        check(::directory.isInitialized) { "HistoricalWeatherRepository.init must be called first" }
        val expectedEndDate = today.minusDays(1)
        readPastWeek(city, expectedEndDate)?.let { return it }
        val week = RecentWeatherSource.fetch(city, expectedEndDate) ?: error("Recent weather is unavailable")
        writePastWeek(city, expectedEndDate, week)
        return week
    }

    private suspend fun loadDay(city: City, date: LocalDate): HistoricalDay? {
        read(city, date)?.let { return it.day }
        return HistoricalWeatherSource.fetch(city, date)?.also { write(city, date, it) }
    }

    private suspend fun read(city: City, date: LocalDate): HistoryCacheEntry? = withContext(Dispatchers.IO) {
        runCatching {
            val file = cacheFile(city, date)
            if (!file.isFile) null else json.decodeFromString<HistoryCacheEntry>(file.readText()).let { entry ->
                entry.day.normalized(date)?.let { entry.copy(day = it) }
            }
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

    private suspend fun readPastWeek(city: City, endDate: LocalDate): RecentWeatherWeek? = withContext(Dispatchers.IO) {
        runCatching {
            val file = pastWeekCacheFile(city, endDate)
            if (!file.isFile) null
            else json.decodeFromString<PastWeekCacheEntry>(file.readText()).let {
                normalizeRecentWeatherDays(it.days, endDate).takeIf { days -> days.isNotEmpty() }
                    ?.let(::RecentWeatherWeek)
            }
        }.getOrNull()
    }

    private suspend fun writePastWeek(
        city: City,
        endDate: LocalDate,
        week: RecentWeatherWeek,
    ) = withContext(Dispatchers.IO) {
        runCatching {
            directory.mkdirs()
            val target = pastWeekCacheFile(city, endDate)
            val temp = File(directory, target.name + ".tmp")
            temp.writeText(json.encodeToString(PastWeekCacheEntry(week.days, System.currentTimeMillis())))
            if (!temp.renameTo(target)) {
                target.writeText(temp.readText())
                temp.delete()
            }
        }
        Unit
    }

    private fun cacheFile(city: City, date: LocalDate): File {
        // v4 加入异常值/日期一致性闸门，避免沿用早期被污染的多年平均缓存。
        val raw = String.format(Locale.US, "best-match-v4|%.4f|%.4f|%s", city.latitude, city.longitude, date)
        return File(directory, sha256(raw) + ".json")
    }

    private fun pastWeekCacheFile(city: City, endDate: LocalDate): File {
        val raw = String.format(Locale.US, "past-week-v2|%.4f|%.4f|%s", city.latitude, city.longitude, endDate)
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
private data class PastWeekCacheEntry(
    val days: List<HistoricalDay>,
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

private fun ArchiveDaily.toHistoricalDay(index: Int, fallbackDate: String, model: String = "BEST-MATCH"): HistoricalDay? =
    HistoricalDay(
        date = time.getOrNull(index) ?: fallbackDate,
        weatherCode = weather_code?.getOrNull(index),
        high = temperature_2m_max?.getOrNull(index),
        low = temperature_2m_min?.getOrNull(index),
        mean = temperature_2m_mean?.getOrNull(index),
        precipitationMm = precipitation_sum?.getOrNull(index),
        windMaxKmh = wind_speed_10m_max?.getOrNull(index),
        gustMaxKmh = wind_gusts_10m_max?.getOrNull(index),
        model = model,
    ).normalized()
