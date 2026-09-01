package com.zhisheng.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// Open-Meteo 兜底：小米缺的能见度/露点/云量/阵风（短超时，失败静默）
object OpenMeteoApi {

    private val json = Json { ignoreUnknownKeys = true }
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(lat: Double, lon: Double): OpenMeteoResult? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=visibility,dew_point_2m,cloud_cover,wind_gusts_10m" +
                "&timezone=auto"
            val request = Request.Builder().url(url).build()
            okHttp.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                json.decodeFromString<OpenMeteoResult>(resp.body?.string() ?: return@withContext null)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    // 逐时兜底（全球覆盖、免 key）：和风 hourly 单路失败/不支持时（海外城市 4xx 等）
    // 逐时预报区曾整块空白（v0.0.1 修复），与逐日补齐同一套路
    suspend fun fetchHourly(lat: Double, lon: Double): OpenMeteoHourlyResponse? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&hourly=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code," +
                "wind_speed_10m,wind_direction_10m,wind_gusts_10m,precipitation_probability," +
                "precipitation,surface_pressure,visibility,dew_point_2m,cloud_cover,uv_index" +
                "&forecast_hours=24&timezone=auto"
            val request = Request.Builder().url(url).build()
            okHttp.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                json.decodeFromString<OpenMeteoHourlyResponse>(resp.body?.string() ?: return@withContext null)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    // 16 天逐日（全球覆盖、免 key）：和风逐日上限 10 天、小米海外仅约 5 天，
    // 用此接口把逐日补齐到 15 天（v0.0.1 东京丢 15 天预报的修复）。timezone=auto 按城市本地日界
    suspend fun fetchDaily(lat: Double, lon: Double): OpenMeteoDailyResult? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&daily=temperature_2m_max,temperature_2m_min,weather_code,wind_speed_10m_max," +
                "wind_gusts_10m_max,wind_direction_10m_dominant,precipitation_probability_max," +
                "precipitation_sum,sunrise,sunset,relative_humidity_2m_mean,cloud_cover_mean" +
                "&forecast_days=16&timezone=auto"
            val request = Request.Builder().url(url).build()
            okHttp.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                json.decodeFromString<OpenMeteoDailyResult>(resp.body?.string() ?: return@withContext null)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }
}

@Serializable
data class OpenMeteoResult(val current: OpenMeteoCurrent? = null)

@Serializable
data class OpenMeteoCurrent(
    val visibility: Double? = null,
    val dew_point_2m: Double? = null,
    val cloud_cover: Double? = null,
    val wind_gusts_10m: Double? = null,
)

@Serializable
data class OpenMeteoDailyResult(
    val daily: OpenMeteoDaily? = null,
    val utc_offset_seconds: Int = 0,
)

@Serializable
data class OpenMeteoDaily(
    val time: List<String>? = null,
    val temperature_2m_max: List<Double?>? = null,
    val temperature_2m_min: List<Double?>? = null,
    val weather_code: List<Int?>? = null,
    val wind_speed_10m_max: List<Double?>? = null,
    val wind_gusts_10m_max: List<Double?>? = null,
    val wind_direction_10m_dominant: List<Double?>? = null,
    val precipitation_probability_max: List<Double?>? = null,
    val precipitation_sum: List<Double?>? = null,
    val sunrise: List<String>? = null,
    val sunset: List<String>? = null,
    val relative_humidity_2m_mean: List<Double?>? = null,
    val cloud_cover_mean: List<Double?>? = null,
)

@Serializable
data class OpenMeteoHourlyResponse(
    val hourly: OpenMeteoHourly? = null,
    val utc_offset_seconds: Int = 0,
)

@Serializable
data class OpenMeteoHourly(
    val time: List<String>? = null,
    val temperature_2m: List<Double?>? = null,
    val apparent_temperature: List<Double?>? = null,
    val relative_humidity_2m: List<Double?>? = null,
    val weather_code: List<Int?>? = null,
    val wind_speed_10m: List<Double?>? = null,
    val wind_direction_10m: List<Double?>? = null,
    val wind_gusts_10m: List<Double?>? = null,
    val precipitation_probability: List<Double?>? = null,
    val precipitation: List<Double?>? = null,
    val surface_pressure: List<Double?>? = null,
    val visibility: List<Double?>? = null,
    val dew_point_2m: List<Double?>? = null,
    val cloud_cover: List<Double?>? = null,
    val uv_index: List<Double?>? = null,
)
