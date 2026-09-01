package com.zhisheng.weather.data

import com.zhisheng.weather.model.AqiInfo
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.MinutePrecip
import com.zhisheng.weather.model.RainMeta
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.wmoProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// Open-Meteo 作为**独立主源**（v0.0.2）：完全免 key、全球覆盖。
// 装不上和风凭据的用户在设置里锁定本源即可拿到实况/逐时/逐日/空气质量/分钟降水的完整体验。
// 与 OpenMeteoApi（只做补缺）分开：那个是补漏工具，这个是完整链路。
object OpenMeteoSource {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private suspend inline fun <reified T> get(url: String): T? = withContext(Dispatchers.IO) {
        try {
            okHttp.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body?.string()
                if (!resp.isSuccessful || body == null) null else json.decodeFromString<T>(body)
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetch(city: City): WeatherData = try {
        coroutineScope {
            val lat = city.latitude
            val lon = city.longitude
            val mainDeferred = async {
                get<OmFull>(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day," +
                        "precipitation,weather_code,cloud_cover,surface_pressure,pressure_msl,wind_speed_10m," +
                        "wind_direction_10m,wind_gusts_10m,visibility,dew_point_2m,uv_index" +
                        "&hourly=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code," +
                        "wind_speed_10m,wind_direction_10m,wind_gusts_10m,precipitation_probability," +
                        "precipitation,surface_pressure,visibility,dew_point_2m,cloud_cover,uv_index" +
                        "&daily=temperature_2m_max,temperature_2m_min,weather_code,wind_speed_10m_max," +
                        "wind_gusts_10m_max,wind_direction_10m_dominant,precipitation_probability_max," +
                        "precipitation_sum,sunrise,sunset,uv_index_max,relative_humidity_2m_mean,cloud_cover_mean" +
                        "&minutely_15=precipitation" +
                        // 页面只需要未来两小时。若不限制，forecast_days=16 会连带下载最多
                        // 16 天的 15 分钟数组，徒增首开耗时与流量。
                        "&forecast_days=16&forecast_hours=24&forecast_minutely_15=9&timezone=auto"
                )
            }
            val aqiDeferred = async {
                get<OmAirResult>(
                    "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
                        "&current=pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone,us_aqi" +
                        "&timezone=auto"
                )
            }

            val m = mainDeferred.await() ?: return@coroutineScope WeatherData(error = "公共源请求失败")
            val air = aqiDeferred.await()
            val offsetMs = m.utc_offset_seconds * 1000L

            // OM 的时间串是城市本地墙上时间且不带偏移，减去 offset 折回真实 epoch
            fun epochOf(local: String?): Long? = local?.let {
                try {
                    java.time.LocalDateTime.parse(it)
                        .toInstant(java.time.ZoneOffset.UTC).toEpochMilli() - offsetMs
                } catch (_: Exception) {
                    null
                }
            }

            val cur = m.current
            // is_day 缺失时按城市本地小时兜底判昼夜，避免夜间显示太阳（v0.0.4：原 null 恒判白天）
            val isDay = cur?.is_day?.let { it != 0 } ?: isDayAt(System.currentTimeMillis(), offsetMs)
            val current = cur?.let {
                val profile = wmoProfile(it.weather_code, isDay)
                CurrentWeather(
                    temperature = it.temperature_2m,
                    feelsLike = it.apparent_temperature,
                    condition = profile.condition,
                    weatherText = profile.condition.label,
                    humidity = it.relative_humidity_2m,
                    windSpeed = it.wind_speed_10m,
                    windDirectionDeg = it.wind_direction_10m,
                    // 遥测的“气压”统一使用站点/地面气压。金川等高海拔地区若误用
                    // pressure_msl，会比彩云和小米的站点气压高约 170 hPa，看起来像源在打架。
                    pressure = it.surface_pressure ?: it.pressure_msl,
                    // 当前紫外线必须使用当前时刻值；daily.uv_index_max 是全天峰值，
                    // 夜间拿它冒充实况会显示“紫外线很强”。
                    uvIndex = it.uv_index?.let { u -> Math.round(u).toInt() },
                    visibility = it.visibility?.let { v -> v / 1000.0 },
                    dewPoint = it.dew_point_2m,
                    cloudCover = it.cloud_cover,
                    windGust = it.wind_gusts_10m,
                    precipMm = it.precipitation?.let { mm ->
                        val periodMin = ((it.interval ?: 900) / 60).coerceAtLeast(1)
                        com.zhisheng.weather.model.Nowcast.accumulatedMmToRate(mm.toFloat(), periodMin).toDouble()
                    },
                    profile = profile,
                )
            }

            val nowMs = System.currentTimeMillis()
            val hourly = m.hourly?.let { h ->
                h.time?.mapIndexedNotNull { i, t ->
                    val e = epochOf(t) ?: return@mapIndexedNotNull null
                    // 逐时保留当前整点及以后
                    if (e < nowMs - 3_600_000L) null else {
                        val profile = wmoProfile(h.weather_code?.getOrNull(i), isDayAt(e, offsetMs))
                        HourlyWeather(
                        timeMillis = e,
                        temperature = h.temperature_2m?.getOrNull(i),
                        feelsLike = h.apparent_temperature?.getOrNull(i),
                        condition = profile.condition,
                        windSpeed = h.wind_speed_10m?.getOrNull(i),
                        windDirectionDeg = h.wind_direction_10m?.getOrNull(i),
                        windGust = h.wind_gusts_10m?.getOrNull(i),
                        precipProb = h.precipitation_probability?.getOrNull(i)?.let { p -> Math.round(p).toInt() },
                        precipMm = h.precipitation?.getOrNull(i),
                        humidity = h.relative_humidity_2m?.getOrNull(i),
                        pressure = h.surface_pressure?.getOrNull(i),
                        visibility = h.visibility?.getOrNull(i)?.div(1_000.0),
                        dewPoint = h.dew_point_2m?.getOrNull(i),
                        cloudCover = h.cloud_cover?.getOrNull(i),
                        uvIndex = h.uv_index?.getOrNull(i)?.let { u -> Math.round(u).toInt() },
                        profile = profile,
                    )
                    }
                }?.take(24)
            } ?: emptyList()

            val daily = m.daily?.let { d ->
                d.time?.mapIndexedNotNull { i, day ->
                    val e = try {
                        java.time.LocalDate.parse(day).atStartOfDay(java.time.ZoneOffset.UTC)
                            .toInstant().toEpochMilli() - offsetMs
                    } catch (_: Exception) {
                        return@mapIndexedNotNull null
                    }
                    val profile = wmoProfile(d.weather_code?.getOrNull(i), true)
                    MoonCalc.enrich(
                        com.zhisheng.weather.model.DailyWeather(
                            dateMillis = e,
                            high = d.temperature_2m_max?.getOrNull(i),
                            low = d.temperature_2m_min?.getOrNull(i),
                            condition = profile.condition,
                            weatherText = profile.condition.label,
                            windSpeed = d.wind_speed_10m_max?.getOrNull(i),
                            windDirectionDeg = d.wind_direction_10m_dominant?.getOrNull(i),
                            windGust = d.wind_gusts_10m_max?.getOrNull(i),
                            precipProbability = d.precipitation_probability_max?.getOrNull(i)
                                ?.let { p -> Math.round(p).toInt() },
                            precipMm = d.precipitation_sum?.getOrNull(i),
                            humidity = d.relative_humidity_2m_mean?.getOrNull(i),
                            cloudCover = d.cloud_cover_mean?.getOrNull(i),
                            sunrise = clockOf(d.sunrise?.getOrNull(i)),
                            sunset = clockOf(d.sunset?.getOrNull(i)),
                            profile = profile,
                        ),
                        lat,
                        lon,
                    )
                }?.take(15)
            } ?: emptyList()

            // Open-Meteo 的 15 分钟降水时间戳是“前 15 分钟累计”的区间终点。
            // 内部改存区间起点，否则整条雨带和开始时间都会被画晚约 15 分钟。
            // 请求 9 点，丢掉已经结束的当前桶后仍覆盖完整 2 小时。
            val precip = m.minutely_15?.let { mm ->
                mm.time?.mapIndexedNotNull { i, t ->
                    val intervalEnd = epochOf(t) ?: return@mapIndexedNotNull null
                    val intervalStart = intervalEnd - 15 * 60_000L
                    if (intervalEnd <= nowMs) null
                    // minutely_15.precipitation 是 15 分钟累计毫米；统一换成 mm/h。
                    else MinutePrecip(intervalStart, com.zhisheng.weather.model.Nowcast.accumulatedMmToRate(
                        mm.precipitation?.getOrNull(i)?.toFloat() ?: 0f,
                        15,
                    ))
                }?.take(8)
            } ?: emptyList()

            val aqiInfo = air?.current?.let { a ->
                AqiInfo(
                    value = a.us_aqi?.let { Math.round(it).toInt() },
                    level = WeatherRepository.usAqiLevel(a.us_aqi?.let { Math.round(it).toInt() }),
                    standard = "美国",
                    pm25 = a.pm2_5?.let { fmt1(it) },
                    pm10 = a.pm10?.let { fmt1(it) },
                    o3 = a.ozone?.let { fmt1(it) },
                    no2 = a.nitrogen_dioxide?.let { fmt1(it) },
                    so2 = a.sulphur_dioxide?.let { fmt1(it) },
                    // Open-Meteo 所有气体浓度均为 µg/m³；应用其余天气源的 CO 按 mg/m³
                    // 展示，因此需除以 1000。此前 142 µg/m³ 被直接显示成 142。
                    co = a.carbon_monoxide?.let { fmtCoMg(it) },
                    pollutantUnits = WeatherRepository.CHINA_POLLUTANT_UNITS,
                )
            }

            WeatherData(
                current = current,
                hourly = hourly,
                daily = daily,
                aqi = aqiInfo,
                alerts = emptyList(), // 公共源不提供官方预警
                updateTime = epochOf(cur?.time) ?: System.currentTimeMillis(),
                // 不编 rainNowcast：接口没有短时降水文案。主屏一句话走分钟序列/温差。
                rainMinutes = if (precip.size >= 2) precip else emptyList(),
                rainMeta = precip.takeIf { it.size >= 2 }?.let {
                    RainMeta("OPEN-METEO", 15, System.currentTimeMillis())
                },
                dataSource = "OPEN-METEO",
                blockSources = mapOf("current" to "OPEN-METEO", "hourly" to "OPEN-METEO", "daily" to "OPEN-METEO", "minutely" to "OPEN-METEO"),
                utcOffsetSeconds = m.utc_offset_seconds,
            )
        }
    } catch (ce: kotlinx.coroutines.CancellationException) {
        throw ce
    } catch (e: Exception) {
        WeatherData(error = WeatherRepository.userFacingFetchError("公共天气源"))
    }

    private fun fmt1(v: Double): String =
        if (v == Math.floor(v)) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)

    internal fun fmtCoMg(microgramsPerCubicMeter: Double): String =
        String.format(java.util.Locale.US, "%.2f", microgramsPerCubicMeter / 1000.0)
            .trimEnd('0')
            .trimEnd('.')

    // 逐时图标昼夜：按城市本地小时判断（6-18 视为白天），避免夜里整排太阳
    private fun isDayAt(epochMs: Long, offsetMs: Long): Boolean {
        val localHour = ((epochMs + offsetMs) / 3_600_000L % 24L).toInt()
        return localHour in 6..18
    }

    private fun clockOf(s: String?): String? {
        if (s.isNullOrEmpty()) return null
        return try {
            java.time.LocalDateTime.parse(s).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: Exception) {
            null
        }
    }

    // WMO 映射已收敛到 model.WmoMaps.wmoToCondition（v0.0.4，原私有 wmo 与 WeatherRepository.fromWmoCode 双份重复）

    // —— 城市检索（Open-Meteo Geocoding，免 key，支持中文） ——
    suspend fun searchCity(query: String): List<City> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val r = get<OmGeoResult>(
            "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=10&language=zh&format=json"
        ) ?: return emptyList()
        return r.results.orEmpty().map { g ->
            City(
                name = g.name.orEmpty(),
                affiliation = listOfNotNull(g.admin1, g.admin2)
                    .filter { it.isNotBlank() }.distinct().joinToString("·"),
                latitude = g.latitude,
                longitude = g.longitude,
                locationKey = "om:${g.latitude},${g.longitude}",
            )
        }.filter { it.name.isNotBlank() }
    }
}

@Serializable
data class OmFull(
    val utc_offset_seconds: Int = 0,
    val current: OmCurrentFull? = null,
    val hourly: OpenMeteoHourly? = null,
    val daily: OmDailyFull? = null,
    val minutely_15: OmMinutely15? = null,
)

@Serializable
data class OmCurrentFull(
    val time: String? = null,
    val temperature_2m: Double? = null,
    val relative_humidity_2m: Double? = null,
    val apparent_temperature: Double? = null,
    val is_day: Int? = null,
    val precipitation: Double? = null,
    val weather_code: Int? = null,
    val interval: Int? = null,
    val cloud_cover: Double? = null,
    val surface_pressure: Double? = null,
    val pressure_msl: Double? = null,
    val wind_speed_10m: Double? = null,
    val wind_direction_10m: Double? = null,
    val wind_gusts_10m: Double? = null,
    val visibility: Double? = null,
    val dew_point_2m: Double? = null,
    val uv_index: Double? = null,
)

@Serializable
data class OmDailyFull(
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
    val uv_index_max: List<Double?>? = null,
    val relative_humidity_2m_mean: List<Double?>? = null,
    val cloud_cover_mean: List<Double?>? = null,
)

@Serializable
data class OmMinutely15(
    val time: List<String>? = null,
    val precipitation: List<Double?>? = null,
)

@Serializable
data class OmAirResult(val current: OmAirCurrent? = null)

@Serializable
data class OmAirCurrent(
    val pm10: Double? = null,
    val pm2_5: Double? = null,
    val carbon_monoxide: Double? = null,
    val nitrogen_dioxide: Double? = null,
    val sulphur_dioxide: Double? = null,
    val ozone: Double? = null,
    val us_aqi: Double? = null,
)

@Serializable
data class OmGeoResult(val results: List<OmGeoItem>? = null)

@Serializable
data class OmGeoItem(
    val name: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val admin1: String? = null,
    val admin2: String? = null,
    val country: String? = null,
)
