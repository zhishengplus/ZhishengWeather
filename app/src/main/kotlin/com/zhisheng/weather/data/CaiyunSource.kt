package com.zhisheng.weather.data

import com.zhisheng.weather.model.AlertInfo
import com.zhisheng.weather.model.AqiInfo
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.LifeIndexExtra
import com.zhisheng.weather.model.MinutePrecip
import com.zhisheng.weather.model.RainMeta
import com.zhisheng.weather.model.Nowcast
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.WeatherIntensity
import com.zhisheng.weather.model.WeatherProfile
import com.zhisheng.weather.model.PrecipitationPhase
import com.zhisheng.weather.model.alertLevelOf
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

interface CaiyunService {
    @GET("v2.6/{token}/{lng},{lat}/weather")
    suspend fun weather(
        @Path("token") token: String,
        @Path("lng") lng: String,
        @Path("lat") lat: String,
        @Query("alert") alert: Boolean = true,
        @Query("dailysteps") dailySteps: Int = 15,
        // 官方允许 1..360；超出套餐上限会按套餐截断，因此直接请求最大能力，
        // 付费用户可拿到完整时效，免费套餐也不会因此失败。
        @Query("hourlysteps") hourlySteps: Int = 360,
        @Query("unit") unit: String = "metric:v2",
    ): CaiyunWeatherResponse
}

object CaiyunApi {
    val enabled: Boolean get() = SecretStore.caiyunReady

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
    val service: CaiyunService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.caiyunapp.com/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CaiyunService::class.java)
    }
}

object CaiyunSource {

    suspend fun fetch(city: City): WeatherData {
        val token = SecretStore.caiyunRuntime.token
        if (token.isBlank()) return WeatherData(error = "未配置彩云天气 Token")
        return try {
            val lng = String.format(java.util.Locale.US, "%.4f", city.longitude)
            val lat = String.format(java.util.Locale.US, "%.4f", city.latitude)
            val body = CaiyunApi.service.weather(token, lng, lat)
            if (!body.status.equals("ok", true) || body.result == null) {
                WeatherData(error = "彩云天气请求失败")
            } else {
                map(
                    r = body.result,
                    city = city,
                    utcOffsetSeconds = body.tzshift,
                    providerUpdateTime = body.serverTime?.takeIf { it > 0L }?.times(1_000L),
                )
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (e: Exception) {
            android.util.Log.w("ZhishengWeather", "彩云请求失败", e)
            WeatherData(error = "彩云天气请求失败（检查 Token 与网络）")
        }
    }

    suspend fun ping(): String {
        val token = SecretStore.currentCaiyun().token
        if (token.isBlank()) return "还没有填写 Token"
        return try {
            val body = CaiyunApi.service.weather(token, "116.4074", "39.9042")
            if (body.status.equals("ok", true) && body.result?.realtime != null) "连接成功，彩云已返回北京实况"
            else "服务没有返回有效天气，请核对 Token"
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            "连接失败，请核对 Token 与网络"
        }
    }

    internal fun map(
        r: CaiyunResult,
        city: City,
        utcOffsetSeconds: Int? = null,
        providerUpdateTime: Long? = null,
    ): WeatherData {
        val rt = r.realtime
        val fetchedAt = System.currentTimeMillis()
        val now = (providerUpdateTime ?: fetchedAt) / Nowcast.MINUTE_MS * Nowcast.MINUTE_MS
        val precip2h = r.minutely?.precipitation2h ?: r.minutely?.precipitation
        val minutes = precip2h?.let { Nowcast.minuteSeries(it.map { v -> v.toFloat() }, now) }.orEmpty()
        val offsetHint = utcOffsetSeconds?.takeIf { it in -18 * 3_600..18 * 3_600 }
            ?: offsetSeconds(r.hourly?.temperature?.firstOrNull()?.datetime)
            ?: offsetSeconds(r.daily?.temperature?.firstOrNull()?.date)
        return WeatherData(
            current = rt?.let {
                CurrentWeather(
                    temperature = it.temperature,
                    feelsLike = it.apparentTemperature,
                    condition = skycon(it.skycon),
                    weatherText = skyconLabel(it.skycon),
                    profile = skyconProfile(it.skycon),
                    humidity = ratioToPercent(it.humidity),
                    // 0.0.9-debug 修复：官方单位制表 metric（默认）下 wind.speed 就是 km/h，
                    // 内部风速单位也是 km/h。原实现 ×3.6 把风速放大 3.6 倍
                    //（2 级微风显示成 7 级大风）。仅 unit=SI 时才是 m/s。
                    windSpeed = it.wind?.speed,
                    windDirectionDeg = it.wind?.direction,
                    pressure = it.pressure?.div(100.0),
                    uvIndex = it.lifeIndex?.ultraviolet?.index?.roundToInt(),
                    visibility = it.visibility,
                    cloudCover = ratioToPercent(it.cloudrate),
                    precipMm = it.precipitation?.local?.intensity,
                )
            },
            hourly = r.hourly?.let { h ->
                val n = maxOf(
                    h.temperature?.size ?: 0,
                    h.apparentTemperature?.size ?: 0,
                    h.skycon?.size ?: 0,
                    h.wind?.size ?: 0,
                    h.precipitation?.size ?: 0,
                    h.humidity?.size ?: 0,
                    h.pressure?.size ?: 0,
                    h.visibility?.size ?: 0,
                    h.cloudrate?.size ?: 0,
                    h.airQuality?.aqi?.size ?: 0,
                ).coerceAtMost(360)
                (0 until n).mapNotNull { i ->
                    val temp = h.temperature?.getOrNull(i)
                    val apparent = h.apparentTemperature?.getOrNull(i)
                    val sky = h.skycon?.getOrNull(i)
                    val wind = h.wind?.getOrNull(i)
                    val precip = h.precipitation?.getOrNull(i)
                    val humidity = h.humidity?.getOrNull(i)
                    val pressure = h.pressure?.getOrNull(i)
                    val visibility = h.visibility?.getOrNull(i)
                    val cloudrate = h.cloudrate?.getOrNull(i)
                    val air = h.airQuality?.aqi?.getOrNull(i)
                    val rawTime = temp?.datetime ?: apparent?.datetime ?: sky?.datetime ?: wind?.datetime
                        ?: precip?.datetime ?: humidity?.datetime ?: pressure?.datetime
                        ?: visibility?.datetime ?: cloudrate?.datetime ?: air?.datetime
                    val timeMillis = parseTime(rawTime, offsetHint) ?: return@mapNotNull null
                    HourlyWeather(
                        timeMillis = timeMillis,
                        temperature = temp?.value,
                        feelsLike = apparent?.value,
                        condition = skycon(sky?.value),
                        profile = skyconProfile(sky?.value),
                        windSpeed = wind?.speed,
                        windDirectionDeg = wind?.direction,
                        precipProb = normalizeProbability(precip?.probability),
                        precipMm = precip?.value?.takeIf { it.isFinite() && it >= 0.0 },
                        humidity = ratioToPercent(humidity?.value),
                        pressure = pressure?.value?.div(100.0),
                        visibility = visibility?.value,
                        cloudCover = ratioToPercent(cloudrate?.value),
                        aqi = air?.value?.chn,
                    )
                }
            }.orEmpty(),
            daily = r.daily?.let { daily ->
                val n = maxOf(
                    daily.temperature?.size ?: 0,
                    daily.skycon?.size ?: 0,
                    daily.skyconDay?.size ?: 0,
                    daily.skyconNight?.size ?: 0,
                    daily.astro?.size ?: 0,
                    daily.precipitation?.size ?: 0,
                    daily.precipitationDay?.size ?: 0,
                    daily.precipitationNight?.size ?: 0,
                    daily.humidity?.size ?: 0,
                    daily.cloudrate?.size ?: 0,
                    daily.wind?.size ?: 0,
                    daily.windDay?.size ?: 0,
                    daily.windNight?.size ?: 0,
                ).coerceAtMost(15)
                (0 until n).mapNotNull { i ->
                    val temp = daily.temperature?.getOrNull(i)
                    val sky = daily.skycon?.getOrNull(i)
                    val daySky = daily.skyconDay?.getOrNull(i)?.value
                    val nightSky = daily.skyconNight?.getOrNull(i)?.value
                    val astro = daily.astro?.getOrNull(i)
                    val precip = daily.precipitation?.getOrNull(i)
                    val precipDay = daily.precipitationDay?.getOrNull(i)
                    val precipNight = daily.precipitationNight?.getOrNull(i)
                    val humidity = daily.humidity?.getOrNull(i)
                    val cloudrate = daily.cloudrate?.getOrNull(i)
                    val wind = daily.wind?.getOrNull(i)
                    val windDay = daily.windDay?.getOrNull(i)
                    val windNight = daily.windNight?.getOrNull(i)
                    val rawDate = temp?.date ?: sky?.datetime ?: sky?.date ?: astro?.date ?: precip?.date
                    val dateMillis = parseTime(rawDate, offsetHint) ?: return@mapNotNull null
                    val dayNight = dailyDayNight(daySky, nightSky, sky?.value)
                    MoonCalc.enrich(DailyWeather(
                        dateMillis = dateMillis,
                        high = temp?.max,
                        low = temp?.min,
                        average = listOfNotNull(temp?.max, temp?.min).takeIf { it.isNotEmpty() }?.average(),
                        condition = dayNight.first,
                        weatherText = dayNight.second,
                        profile = skyconProfile(daySky ?: sky?.value) ?: skyconProfile(nightSky),
                        sunrise = astro?.sunrise?.time,
                        sunset = astro?.sunset?.time,
                        windSpeed = listOfNotNull(
                            windDay?.max?.speed,
                            windNight?.max?.speed,
                            wind?.max?.speed,
                        ).maxOrNull(),
                        windDirectionDeg = wind?.avg?.direction ?: windDay?.avg?.direction ?: windNight?.avg?.direction,
                        precipProbability = listOfNotNull(
                            normalizeProbability(precip?.probability),
                            normalizeProbability(precipDay?.probability),
                            normalizeProbability(precipNight?.probability),
                        ).maxOrNull(),
                        humidity = ratioToPercent(humidity?.avg),
                        cloudCover = ratioToPercent(cloudrate?.avg),
                        uvIndex = daily.lifeIndex?.ultraviolet?.getOrNull(i)?.index?.toDoubleOrNull()?.roundToInt(),
                        // metric:v2 的 daily.precipitation.max 是峰值雨强 mm/h，不是日累计。
                        // 没有日合计就留空，不用峰值冒充全天降水量。
                    ), city.latitude, city.longitude)
                }
            }.orEmpty(),
            aqi = rt?.airQuality?.let { a ->
                AqiInfo(
                    value = a.aqi?.chn,
                    level = a.description?.chn,
                    standard = "中国",
                    pm25 = a.pm25?.toInt()?.toString(),
                    pm10 = a.pm10?.toInt()?.toString(),
                    o3 = a.o3?.toInt()?.toString(),
                    no2 = a.no2?.toInt()?.toString(),
                    so2 = a.so2?.toInt()?.toString(),
                    co = a.co?.toString(),
                    pollutantUnits = WeatherRepository.CHINA_POLLUTANT_UNITS,
                )
            },
            alerts = r.alert?.content.orEmpty().mapNotNull { a ->
                val title = a.title?.trim().orEmpty()
                if (title.isEmpty()) null
                else AlertInfo(
                    title = title,
                    detail = a.description,
                    level = a.code,
                    severity = alertLevelOf(a.code ?: a.title),
                )
            },
            updateTime = providerUpdateTime ?: fetchedAt,
            // 官方定义：minutely.description 是未来 2 小时短临，forecast_keypoint 是
            // 未来 24 小时变化。两者不能塞进同一个字段，否则“实况晴”下面紧接
            // “多云，今晚转雨”会被误读成同一时刻互相打架。
            rainNowcast = r.minutely?.description,
            forecastSummary = r.forecastKeypoint,
            rainMinutes = minutes,
            rainMeta = minutes.takeIf { it.isNotEmpty() }?.let {
                RainMeta("CAIYUN", 1, now, horizonMinutes = it.size.coerceIn(30, 180))
            },
            rainDistanceKm = rt?.precipitation?.nearest?.distance
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?.div(1_000.0),
            extraIndices = mapLifeIndices(r.daily?.lifeIndex),
            dataSource = "CAIYUN",
            blockSources = mapOf("current" to "CAIYUN", "hourly" to "CAIYUN", "daily" to "CAIYUN", "minutely" to "CAIYUN"),
            utcOffsetSeconds = offsetHint,
        )
    }

    internal fun mapLifeIndices(life: CaiyunLifeIndex?): List<LifeIndexExtra> {
        if (life == null) return emptyList()
        return listOfNotNull(
            life.ultraviolet.firstIndex("紫外线", "UV"),
            life.carWashing.firstIndex("洗车", "CAR WASH"),
            life.dressing.firstIndex("穿衣", "DRESS"),
            life.comfort.firstIndex("舒适", "COMFORT"),
            life.coldRisk.firstIndex("感冒", "COLD"),
        )
    }

    // 彩云历史/不同套餐响应中 probability 既出现过 0..1，也出现过 0..100。
    // 按值域归一，不能无条件乘 100，否则 60 会被错误显示为 6000%。
    internal fun normalizeProbability(value: Double?): Int? {
        if (value == null || !value.isFinite() || value < 0.0) return null
        return when {
            value <= 1.0 -> (value * 100.0).roundToInt()
            value <= 100.0 -> value.roundToInt()
            else -> null
        }
    }

    private fun List<CaiyunLifeIndexItem>?.firstIndex(name: String, en: String): LifeIndexExtra? {
        val item = this?.firstOrNull { !it.desc.isNullOrBlank() } ?: return null
        return LifeIndexExtra(name, en, item.desc!!.trim())
    }

    internal fun parseTime(raw: String?, fallbackOffsetSeconds: Int? = null): Long? {
        if (raw.isNullOrBlank()) return null
        try {
            return OffsetDateTime.parse(raw).toInstant().toEpochMilli()
        } catch (_: Exception) {
        }
        return try {
            val local = java.time.LocalDateTime.parse(raw.take(19))
            val zone = fallbackOffsetSeconds
                ?.takeIf { it in -18 * 3_600..18 * 3_600 }
                ?.let { java.time.ZoneOffset.ofTotalSeconds(it) }
                ?: java.time.ZoneOffset.ofHours(8)
            local.atZone(zone).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    internal fun ratioToPercent(value: Double?): Double? {
        if (value == null || !value.isFinite() || value < 0.0) return null
        val pct = if (value <= 1.0) value * 100.0 else value
        return pct.takeIf { it <= 100.0 }
    }

    internal fun dailyDayNight(
        daySky: String?,
        nightSky: String?,
        fallbackSky: String?,
    ): Pair<WeatherCondition?, String?> {
        val dayCondition = skycon(daySky)
        val nightCondition = skycon(nightSky)
        val fallbackCondition = skycon(fallbackSky)
        val condition = when {
            dayCondition != null && nightCondition != null ->
                WeatherCondition.moreSignificant(dayCondition, nightCondition)
            else -> dayCondition ?: nightCondition ?: fallbackCondition
        }
        val dayText = skyconLabel(daySky)
        val nightText = skyconLabel(nightSky)
        val text = when {
            dayText != null && nightText != null && dayText != nightText -> "${dayText}转${nightText}"
            else -> dayText ?: nightText ?: skyconLabel(fallbackSky)
        }
        return condition to text
    }

    internal fun skycon(code: String?): WeatherCondition? = skyconProfile(code)?.condition

    internal fun skyconProfile(code: String?): WeatherProfile? {
        val raw = code?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
        fun p(
            condition: WeatherCondition,
            intensity: WeatherIntensity? = null,
            phase: PrecipitationPhase = PrecipitationPhase.NONE,
        ) = WeatherProfile(condition, intensity, phase, source = "CAIYUN", rawCode = raw)
        return when (raw) {
            "CLEAR_DAY" -> p(WeatherCondition.CLEAR)
            "CLEAR_NIGHT" -> p(WeatherCondition.CLEAR_NIGHT)
            "PARTLY_CLOUDY_DAY" -> p(WeatherCondition.PARTLY_CLOUDY)
            "PARTLY_CLOUDY_NIGHT" -> p(WeatherCondition.PARTLY_CLOUDY_NIGHT)
            "CLOUDY" -> p(WeatherCondition.OVERCAST)
            "LIGHT_HAZE" -> p(WeatherCondition.HAZE, WeatherIntensity.LIGHT)
            "MODERATE_HAZE" -> p(WeatherCondition.HAZE, WeatherIntensity.MODERATE)
            "HEAVY_HAZE" -> p(WeatherCondition.HAZE, WeatherIntensity.HEAVY)
            "LIGHT_RAIN" -> p(WeatherCondition.DRIZZLE, WeatherIntensity.LIGHT, PrecipitationPhase.RAIN)
            "MODERATE_RAIN" -> p(WeatherCondition.RAIN, WeatherIntensity.MODERATE, PrecipitationPhase.RAIN)
            "HEAVY_RAIN" -> p(WeatherCondition.RAIN, WeatherIntensity.HEAVY, PrecipitationPhase.RAIN)
            "STORM_RAIN" -> p(WeatherCondition.RAIN, WeatherIntensity.EXTREME, PrecipitationPhase.RAIN)
            "FOG" -> p(WeatherCondition.FOG)
            "LIGHT_SNOW" -> p(WeatherCondition.SNOW, WeatherIntensity.LIGHT, PrecipitationPhase.SNOW)
            "MODERATE_SNOW" -> p(WeatherCondition.SNOW, WeatherIntensity.MODERATE, PrecipitationPhase.SNOW)
            "HEAVY_SNOW" -> p(WeatherCondition.SNOW, WeatherIntensity.HEAVY, PrecipitationPhase.SNOW)
            "STORM_SNOW" -> p(WeatherCondition.SNOW, WeatherIntensity.EXTREME, PrecipitationPhase.SNOW)
            "DUST", "SAND" -> p(WeatherCondition.SAND, WeatherIntensity.MODERATE)
            "WIND" -> p(WeatherCondition.WIND)
            "LIGHT_HAIL" -> p(WeatherCondition.HAIL, WeatherIntensity.LIGHT, PrecipitationPhase.HAIL)
            "MODERATE_HAIL" -> p(WeatherCondition.HAIL, WeatherIntensity.MODERATE, PrecipitationPhase.HAIL)
            "HEAVY_HAIL" -> p(WeatherCondition.HAIL, WeatherIntensity.HEAVY, PrecipitationPhase.HAIL)
            else -> WeatherProfile(WeatherCondition.UNKNOWN, source = "CAIYUN", rawCode = raw)
        }
    }

    private fun offsetSeconds(raw: String?): Int? = try {
        if (raw.isNullOrBlank()) null else OffsetDateTime.parse(raw).offset.totalSeconds
    } catch (_: Exception) {
        null
    }

    internal fun skyconLabel(code: String?): String? = when (code?.uppercase()) {
        "CLEAR_DAY", "CLEAR_NIGHT" -> "晴"
        "PARTLY_CLOUDY_DAY", "PARTLY_CLOUDY_NIGHT" -> "多云"
        "CLOUDY" -> "阴"
        "LIGHT_HAZE", "MODERATE_HAZE", "HEAVY_HAZE" -> "霾"
        "LIGHT_RAIN" -> "小雨"
        "MODERATE_RAIN" -> "中雨"
        "HEAVY_RAIN", "STORM_RAIN" -> "大雨"
        "FOG" -> "雾"
        "LIGHT_SNOW" -> "小雪"
        "MODERATE_SNOW", "HEAVY_SNOW", "STORM_SNOW" -> "雪"
        "DUST", "SAND" -> "沙尘"
        "WIND" -> "大风"
        "LIGHT_HAIL" -> "小冰雹"
        "MODERATE_HAIL" -> "冰雹"
        "HEAVY_HAIL" -> "强冰雹"
        else -> skycon(code)?.label
    }
}
