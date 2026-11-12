package com.zhisheng.weather.data

import android.content.Context
import com.zhisheng.weather.model.HistoricalDateRange
import com.zhisheng.weather.model.historyDateRangeError
import com.zhisheng.weather.model.normalizedHistoryRange
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
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(city: City, date: LocalDate): HistoricalDay? = fetchRange(city, date, date).firstOrNull()

    suspend fun fetchRange(city: City, start: LocalDate, end: LocalDate): List<HistoricalDay> = withContext(Dispatchers.IO) {
        val url = "https://archive-api.open-meteo.com/v1/archive".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", city.latitude.toString())
            .addQueryParameter("longitude", city.longitude.toString())
            .addQueryParameter("start_date", start.toString())
            .addQueryParameter("end_date", end.toString())
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
            .apply {
                if (city.isPreciseLocation) addQueryParameter("cell_selection", "nearest")
            }
            .build()
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val raw = response.body?.string()
                if (!response.isSuccessful || raw.isNullOrBlank()) return@withContext emptyList()
                val daily = json.decodeFromString<ArchiveResponse>(raw).daily ?: return@withContext emptyList()
                normalizedHistoryRange(start, end, daily.time.indices.mapNotNull { index ->
                    daily.toHistoricalDay(index, daily.time[index])
                }).days
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            emptyList()
        }
    }
}

private object RecentWeatherSource {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
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
            .apply {
                if (city.isPreciseLocation) addQueryParameter("cell_selection", "nearest")
            }
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

    internal suspend fun loadDateRange(city: City, start: LocalDate, end: LocalDate, today: LocalDate): HistoricalDateRange {
        check(::directory.isInitialized)
        require(historyDateRangeError(start, end, today) == null)
        val dates = normalizedHistoryRange(start, end, emptyList()).dates
        val existing = dates.mapNotNull { date -> read(city, date)?.day }.associateBy { it.localDate }.toMutableMap()
        // Archive reanalysis is delayed; the existing recent archive covers the last seven complete days.
        val archiveEnd = minOf(end, today.minusDays(8))
        if (start <= archiveEnd && dates.any { it <= archiveEnd && it !in existing }) {
            HistoricalWeatherSource.fetchRange(city, start, archiveEnd).forEach { day ->
                existing[day.localDate] = day; write(city, day.localDate, day)
            }
        }
        if (end > archiveEnd && dates.any { it > archiveEnd && it !in existing }) {
            val recent = try { loadPastWeek(city, today).days }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
            recent.filter { it.localDate in start..end }.forEach { day -> existing[day.localDate] = day }
        }
        return normalizedHistoryRange(start, end, existing.values.toList())
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
