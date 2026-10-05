package com.zhisheng.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.cityDate
import kotlin.math.roundToInt
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// Open-Meteo 兜底：小米缺的能见度/露点/云量/阵风（短超时，失败静默）
object OpenMeteoApi {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(
        lat: Double,
        lon: Double,
        preciseGps: Boolean = false,
    ): OpenMeteoResult? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=visibility,dew_point_2m,cloud_cover,wind_gusts_10m" +
                "&timezone=auto${openMeteoCellSelection(preciseGps)}"
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
    suspend fun fetchHourly(
        lat: Double,
        lon: Double,
        preciseGps: Boolean = false,
    ): OpenMeteoHourlyResponse? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&hourly=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code," +
                "wind_speed_10m,wind_direction_10m,wind_gusts_10m,precipitation_probability," +
                "precipitation,surface_pressure,visibility,dew_point_2m,cloud_cover,uv_index" +
                "&forecast_hours=24&timezone=auto${openMeteoCellSelection(preciseGps)}"
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
    suspend fun fetchDaily(
        lat: Double,
        lon: Double,
        preciseGps: Boolean = false,
    ): OpenMeteoDailyResult? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&daily=temperature_2m_max,temperature_2m_min,weather_code,wind_speed_10m_max," +
                "wind_gusts_10m_max,wind_direction_10m_dominant,precipitation_probability_max," +
                "precipitation_sum,sunrise,sunset,relative_humidity_2m_mean,cloud_cover_mean" +
                "&forecast_days=16&timezone=auto${openMeteoCellSelection(preciseGps)}"
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

    // —— 枳生天气源专用（免 key）：多模式 / 补充包 / 空气质量 ——
    // 中国区域适用性筛选后的模型名单：实测中国区无数据的（bom/kma）与
    // 中国区回退 ECMWF 的欧洲区域模式（dmi/knmi/metno）不入列。
    internal val MULTI_MODEL_TIER1 = listOf(
        "ecmwf_ifs025", "gfs_global", "icon_seamless", "cma_grapes_global", "jma_seamless",
    )
    internal val MULTI_MODEL_TIER2 = listOf(
        "ukmo_seamless", "meteofrance_seamless", "gem_global", "ecmwf_aifs025_single",
    )

    // Tier1 十变量（≤10 控制配额计数）；露点/体感由本地级数兜底（Magnus/热指数），不单列请求。
    private const val TIER1_HOURLY =
        "temperature_2m,relative_humidity_2m,precipitation_probability,precipitation," +
            "wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility,cloud_cover,surface_pressure"
    private const val TIER2_HOURLY = "temperature_2m,wind_speed_10m,wind_direction_10m,precipitation_probability"

    internal suspend fun fetchMultiModel(
        lat: Double,
        lon: Double,
        preciseGps: Boolean = false,
    ): OmMultiModel? = withContext(Dispatchers.IO) {
        try {
            val tier1 = getJsonObject(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&hourly=$TIER1_HOURLY&models=${MULTI_MODEL_TIER1.joinToString(",")}" +
                    "&forecast_days=3&timezone=auto${openMeteoCellSelection(preciseGps)}"
            ) ?: return@withContext null
            val tier2 = getJsonObject(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&hourly=$TIER2_HOURLY&models=${MULTI_MODEL_TIER2.joinToString(",")}" +
                    "&forecast_days=3&timezone=auto${openMeteoCellSelection(preciseGps)}"
            )
            val parsed = parseMultiModel(tier1, tier2 ?: JsonObject(emptyMap()))
            if (parsed.models.isEmpty()) return@withContext null
            parsed
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    /**
     * best_match 补充包：逐时紫外线 + 16 天逐日全套（含 uv_index_max/湿度/云量）+ 昨日
     * （past_days=1）+ 15 分钟降水。一次请求覆盖完全体契约的长尾字段。
     */
    internal suspend fun fetchSupplements(
        lat: Double,
        lon: Double,
        preciseGps: Boolean = false,
    ): OmSupplements? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&hourly=uv_index" +
                "&daily=temperature_2m_max,temperature_2m_min,weather_code,wind_speed_10m_max," +
                "wind_gusts_10m_max,wind_direction_10m_dominant,precipitation_probability_max," +
                "precipitation_sum,sunrise,sunset,uv_index_max,relative_humidity_2m_mean,cloud_cover_mean" +
                "&minutely_15=precipitation&past_days=1&forecast_days=16&timezone=auto" +
                openMeteoCellSelection(preciseGps)
            val obj = getJsonObject(url) ?: return@withContext null
            parseSupplements(obj)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    /** 空气质量：只取污染物浓度（物理量全球可比）；AQI 数值由国标源或本地 GB 公式负责。 */
    internal suspend fun fetchAirQuality(lat: Double, lon: Double): OmAirQuality? = withContext(Dispatchers.IO) {
        try {
            val obj = getJsonObject(
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
                    "&current=pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone" +
                    "&hourly=pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone" +
                    "&forecast_days=2&timezone=auto"
            ) ?: return@withContext null
            parseAirQuality(obj)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    private fun getJsonObject(url: String): JsonObject? = try {
        okHttp.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) null
            else (json.parseToJsonElement(resp.body?.string() ?: return@use null) as? JsonObject)
        }
    } catch (_: Exception) {
        null
    }

    /** "2026-09-14T00:00"（城市本地壁钟）→ 真实 epoch：按 UTC 解析后减去时区偏移。 */
    private fun localHourToEpoch(text: String, offsetSeconds: Int): Long? = try {
        java.time.LocalDateTime.parse(text)
            .toInstant(java.time.ZoneOffset.UTC).toEpochMilli() - offsetSeconds * 1000L
    } catch (_: Exception) {
        null
    }

    // —— JsonObject 手工解析（编译期引用，R8 安全；不用反射路径） ——

    internal fun parseMultiModel(tier1: JsonObject, tier2: JsonObject): OmMultiModel {
        val models = mutableListOf<OmModelSeries>()
        fun collect(root: JsonObject, names: List<String>, tier1Vars: Boolean) {
            val offset = root.primInt("utc_offset_seconds") ?: 0
            val h = root["hourly"] as? JsonObject ?: return
            val epochs = h.arrOf("time").strings().mapNotNull { t ->
                t?.let { localHourToEpoch(it, offset) }
            }
            if (epochs.isEmpty()) return
            for (name in names) {
                fun col(base: String) = h.arrOf("${base}_$name").doubles()
                val temp = col("temperature_2m")
                // 该模型本次无数据（超载/未覆盖区域）→ 直接缺席，不占席位
                if (temp.isEmpty()) continue
                models += OmModelSeries(
                    model = name,
                    timeZoneOffsetSeconds = offset,
                    timeMillis = epochs,
                    temperature = temp,
                    humidity = if (tier1Vars) col("relative_humidity_2m") else emptyList(),
                    precipProb = col("precipitation_probability"),
                    precipMm = if (tier1Vars) col("precipitation") else emptyList(),
                    windSpeedKmh = col("wind_speed_10m"),
                    windDirectionDeg = col("wind_direction_10m"),
                    windGustKmh = if (tier1Vars) col("wind_gusts_10m") else emptyList(),
                    visibilityMeters = if (tier1Vars) col("visibility") else emptyList(),
                    cloudCover = if (tier1Vars) col("cloud_cover") else emptyList(),
                    pressureHpa = if (tier1Vars) col("surface_pressure") else emptyList(),
                )
            }
        }
        collect(tier1, MULTI_MODEL_TIER1, tier1Vars = true)
        collect(tier2, MULTI_MODEL_TIER2, tier1Vars = false)
        return OmMultiModel(
            models = models,
            latitude = tier1.primDouble("latitude") ?: tier2.primDouble("latitude"),
            longitude = tier1.primDouble("longitude") ?: tier2.primDouble("longitude"),
        )
    }

    internal fun parseSupplements(root: JsonObject): OmSupplements {
        val offset = root.primInt("utc_offset_seconds") ?: 0
        val hourly = root["hourly"] as? JsonObject
        val daily = root["daily"] as? JsonObject
        val minutely = root["minutely_15"] as? JsonObject
        return OmSupplements(
            timeZoneOffsetSeconds = offset,
            latitude = root.primDouble("latitude"),
            longitude = root.primDouble("longitude"),
            hourlyTimeMillis = hourly?.arrOf("time").strings().mapNotNull { t ->
                t?.let { localHourToEpoch(it, offset) }
            },
            uvIndex = hourly?.arrOf("uv_index").doubles(),
            dailyTime = daily?.arrOf("time").strings().mapNotNull { it },
            dailyHigh = daily?.arrOf("temperature_2m_max").doubles(),
            dailyLow = daily?.arrOf("temperature_2m_min").doubles(),
            dailyWeatherCode = daily?.arrOf("weather_code").ints(),
            dailyWindMaxKmh = daily?.arrOf("wind_speed_10m_max").doubles(),
            dailyGustMaxKmh = daily?.arrOf("wind_gusts_10m_max").doubles(),
            dailyWindDirDeg = daily?.arrOf("wind_direction_10m_dominant").doubles(),
            dailyPrecipProbMax = daily?.arrOf("precipitation_probability_max").doubles(),
            dailyPrecipSumMm = daily?.arrOf("precipitation_sum").doubles(),
            dailySunrise = daily?.arrOf("sunrise").strings(),
            dailySunset = daily?.arrOf("sunset").strings(),
            dailyUvMax = daily?.arrOf("uv_index_max").doubles(),
            dailyHumidityMean = daily?.arrOf("relative_humidity_2m_mean").doubles(),
            dailyCloudMean = daily?.arrOf("cloud_cover_mean").doubles(),
            minutely15TimeMillis = minutely?.arrOf("time").strings().mapNotNull { t ->
                t?.let { localHourToEpoch(it, offset) }
            },
            minutely15PrecipMm = minutely?.arrOf("precipitation").doubles(),
        )
    }

    internal fun parseAirQuality(root: JsonObject): OmAirQuality {
        val offset = root.primInt("utc_offset_seconds") ?: 0
        val current = root["current"] as? JsonObject
        val hourly = root["hourly"] as? JsonObject
        return OmAirQuality(
            timeZoneOffsetSeconds = offset,
            currentPm25 = current?.primDouble("pm2_5"),
            currentPm10 = current?.primDouble("pm10"),
            currentCo = current?.primDouble("carbon_monoxide")?.div(1000.0),
            currentNo2 = current?.primDouble("nitrogen_dioxide"),
            currentSo2 = current?.primDouble("sulphur_dioxide"),
            currentO3 = current?.primDouble("ozone"),
            hourlyTimeMillis = hourly?.arrOf("time").strings().mapNotNull { t ->
                t?.let { localHourToEpoch(it, offset) }
            },
            hourlyPm25 = hourly?.arrOf("pm2_5").doubles(),
            hourlyPm10 = hourly?.arrOf("pm10").doubles(),
            hourlyCo = hourly?.arrOf("carbon_monoxide").doubles().map { it?.div(1000.0) },
            hourlyNo2 = hourly?.arrOf("nitrogen_dioxide").doubles(),
            hourlySo2 = hourly?.arrOf("sulphur_dioxide").doubles(),
            hourlyO3 = hourly?.arrOf("ozone").doubles(),
        )
    }

    private fun JsonObject.arrOf(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonArray?.doubles(): List<Double?> =
        this?.map { (it as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() } ?: emptyList()
    private fun JsonArray?.ints(): List<Int?> =
        this?.map { (it as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt() } ?: emptyList()
    private fun JsonArray?.strings(): List<String?> =
        this?.map { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    private fun JsonObject.primString(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.primDouble(key: String): Double? = primString(key)?.toDoubleOrNull()
    private fun JsonObject.primInt(key: String): Int? = primString(key)?.toDoubleOrNull()?.toInt()
}

/** 精确定位时要求服务端命中离 GPS 最近的可用网格；普通搜索城市沿用陆地优选。 */
internal fun openMeteoCellSelection(preciseGps: Boolean): String =
    if (preciseGps) "&cell_selection=nearest" else ""

@Serializable
data class OpenMeteoResult(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val current: OpenMeteoCurrent? = null,
)

@Serializable
data class OpenMeteoCurrent(
    val visibility: Double? = null,
    val dew_point_2m: Double? = null,
    val cloud_cover: Double? = null,
    val wind_gusts_10m: Double? = null,
)

@Serializable
data class OpenMeteoDailyResult(
    val latitude: Double? = null,
    val longitude: Double? = null,
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
    val latitude: Double? = null,
    val longitude: Double? = null,
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

// —— 枳生天气源数据类型 ——

/** 单个数值模式的中国区逐时序列；各列与 timeMillis 等长，缺列为空表（消费端按 getOrNull 取）。 */
internal data class OmModelSeries(
    val model: String,
    val timeMillis: List<Long>,
    val temperature: List<Double?>,
    val humidity: List<Double?> = emptyList(),
    val precipProb: List<Double?> = emptyList(),
    val precipMm: List<Double?> = emptyList(),
    val windSpeedKmh: List<Double?> = emptyList(),
    val windDirectionDeg: List<Double?> = emptyList(),
    val windGustKmh: List<Double?> = emptyList(),
    /** Open-Meteo 原始单位（米），使用时 /1000 转为公里。 */
    val visibilityMeters: List<Double?> = emptyList(),
    val cloudCover: List<Double?> = emptyList(),
    val pressureHpa: List<Double?> = emptyList(),
    val timeZoneOffsetSeconds: Int = 28800,
)

internal data class OmMultiModel(
    val models: List<OmModelSeries>,
    val latitude: Double?,
    val longitude: Double?,
)

/** best_match 补充包：逐时紫外线 + 16 天逐日全套（含昨日）+ 15 分钟降水。 */
internal data class OmSupplements(
    val timeZoneOffsetSeconds: Int,
    val latitude: Double?,
    val longitude: Double?,
    val hourlyTimeMillis: List<Long>,
    val uvIndex: List<Double?>,
    val dailyTime: List<String>,
    val dailyHigh: List<Double?>,
    val dailyLow: List<Double?>,
    val dailyWeatherCode: List<Int?>,
    val dailyWindMaxKmh: List<Double?>,
    val dailyGustMaxKmh: List<Double?>,
    val dailyWindDirDeg: List<Double?>,
    val dailyPrecipProbMax: List<Double?>,
    val dailyPrecipSumMm: List<Double?>,
    val dailySunrise: List<String?>,
    val dailySunset: List<String?>,
    val dailyUvMax: List<Double?>,
    val dailyHumidityMean: List<Double?>,
    val dailyCloudMean: List<Double?>,
    val minutely15TimeMillis: List<Long>,
    val minutely15PrecipMm: List<Double?>,
)

/** 空气质量（仅污染物浓度；AQI 数值不由此处提供——国标源或本地 GB 公式负责）。 */
internal data class OmAirQuality(
    val timeZoneOffsetSeconds: Int,
    val currentPm25: Double?,
    val currentPm10: Double?,
    /** CO alone is normalized to mg/m³; other pollutants retain µg/m³. */
    val currentCo: Double?,
    val currentNo2: Double?,
    val currentSo2: Double?,
    val currentO3: Double?,
    val hourlyTimeMillis: List<Long>,
    val hourlyPm25: List<Double?>,
    val hourlyPm10: List<Double?>,
    /** CO in mg/m³, matching the Chinese IAQI breakpoints and display unit. */
    val hourlyCo: List<Double?>,
    val hourlyNo2: List<Double?>,
    val hourlySo2: List<Double?>,
    val hourlyO3: List<Double?>,
)

// —— 枳生天气源：Om 序列 → 内部 WeatherData（纯转换） ——

/** 单个 OM 模型 → 一路融合输入（逐时 + 由"当前小时桶"合成的实况票）。 */
internal fun omModelToWeatherData(series: OmModelSeries, nowMillis: Long): WeatherData {
    val hourly = series.timeMillis.mapIndexedNotNull { i, t ->
        HourlyWeather(
            timeMillis = t,
            temperature = series.temperature.getOrNull(i),
            windSpeed = series.windSpeedKmh.getOrNull(i),
            windDirectionDeg = series.windDirectionDeg.getOrNull(i)?.takeIf { it in 0.0..360.0 },
            precipProb = series.precipProb.getOrNull(i)?.roundToInt()?.takeIf { it in 0..100 },
            precipMm = series.precipMm.getOrNull(i),
            humidity = series.humidity.getOrNull(i),
            windGust = series.windGustKmh.getOrNull(i),
            visibility = series.visibilityMeters.getOrNull(i)?.let { it / 1000.0 },
            cloudCover = series.cloudCover.getOrNull(i),
            pressure = series.pressureHpa.getOrNull(i),
        )
    }
    val nowBucket = FusionEngine.hourBucket(nowMillis, series.timeZoneOffsetSeconds)
    val nowEntry = hourly.firstOrNull { it.timeMillis == nowBucket }
        ?: hourly.firstOrNull { it.timeMillis >= nowBucket }
    val current = nowEntry?.let { h ->
        CurrentWeather(
            temperature = h.temperature,
            humidity = h.humidity,
            windSpeed = h.windSpeed,
            windDirectionDeg = h.windDirectionDeg,
            pressure = h.pressure,
            visibility = h.visibility,
            cloudCover = h.cloudCover,
            windGust = h.windGust,
        )
    }
    return WeatherData(current = current, hourly = hourly, utcOffsetSeconds = series.timeZoneOffsetSeconds)
}

/** best_match 补充包 → 逐日（今日起）+ 16 天全套窄字段；昨日留给 yesterday 兜底。 */
internal fun omSupplementsToWeatherData(sup: OmSupplements, nowMillis: Long): WeatherData? {
    val offset = java.time.ZoneOffset.ofTotalSeconds(sup.timeZoneOffsetSeconds)
    val today = cityDate(nowMillis, sup.timeZoneOffsetSeconds)
    val daily = sup.dailyTime.mapIndexedNotNull { i, t ->
        val date = try {
            java.time.LocalDate.parse(t)
        } catch (_: Exception) {
            return@mapIndexedNotNull null
        }
        if (date.isBefore(today)) return@mapIndexedNotNull null
        DailyWeather(
            dateMillis = date.atStartOfDay(offset).toInstant().toEpochMilli(),
            high = sup.dailyHigh.getOrNull(i),
            low = sup.dailyLow.getOrNull(i),
            condition = com.zhisheng.weather.model.wmoToCondition(sup.dailyWeatherCode.getOrNull(i)),
            profile = com.zhisheng.weather.model.wmoProfile(sup.dailyWeatherCode.getOrNull(i)),
            precipProbability = sup.dailyPrecipProbMax.getOrNull(i)?.roundToInt()?.takeIf { it in 0..100 },
            precipMm = sup.dailyPrecipSumMm.getOrNull(i),
            sunrise = sup.dailySunrise.getOrNull(i)?.substringAfter('T'),
            sunset = sup.dailySunset.getOrNull(i)?.substringAfter('T'),
            humidity = sup.dailyHumidityMean.getOrNull(i),
            cloudCover = sup.dailyCloudMean.getOrNull(i),
            uvIndex = sup.dailyUvMax.getOrNull(i)?.roundToInt()?.takeIf { it >= 0 },
            windSpeed = sup.dailyWindMaxKmh.getOrNull(i),
            windGust = sup.dailyGustMaxKmh.getOrNull(i),
            windDirectionDeg = sup.dailyWindDirDeg.getOrNull(i),
        )
    }
    if (daily.isEmpty()) return null
    return WeatherData(daily = daily, utcOffsetSeconds = sup.timeZoneOffsetSeconds)
}
