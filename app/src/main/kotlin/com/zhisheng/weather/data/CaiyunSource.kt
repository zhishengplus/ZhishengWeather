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
import com.zhisheng.weather.model.WeatherLocationMatch
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

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
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
        // Use the persisted snapshot returned by this read, not a separately updated
        // background mirror which can still contain an older empty value.
        val token = SecretStore.currentCaiyun().token
        if (token.isBlank()) return WeatherData(error = "未配置彩云天气 Token")
        return try {
            val lng = caiyunCoordinate(city.longitude)
            val lat = caiyunCoordinate(city.latitude)
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
            android.util.Log.w("ZhishengWeather", "彩云请求失败：${e.javaClass.simpleName}")
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
        val rt = r.realtime?.takeIf { it.status == null || it.status.equals("ok", true) }
        val hourlyBlock = r.hourly?.takeIf { it.status == null || it.status.equals("ok", true) }
        val dailyBlock = r.daily?.takeIf { it.status == null || it.status.equals("ok", true) }
        val alertBlock = r.alert?.takeIf { it.status == null || it.status.equals("ok", true) }
        val fetchedAt = System.currentTimeMillis()
        val now = (providerUpdateTime ?: fetchedAt) / Nowcast.MINUTE_MS * Nowcast.MINUTE_MS
        val minutely = r.minutely?.takeIf { it.status == null || it.status.equals("ok", true) }
        val precip2h = sequenceOf(minutely?.precipitation2h, minutely?.precipitation)
            .firstOrNull { values -> values?.any { it != null && it.isFinite() && it >= 0.0 } == true }
        // Keep the original index when a provider sample is invalid; dropping it first shifts time.
        // metric:v2 is already mm/h, with one sample per minute from the current minute.
        val minutes = precip2h.orEmpty().mapIndexedNotNull { index, value ->
            value?.toFloat()?.takeIf { it.isFinite() && it >= 0f }
                ?.let { MinutePrecip(now + index * Nowcast.MINUTE_MS, it) }
        }
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
                    // 请求固定 metric:v2；官方单位表中能见度已经是公里。
                    visibility = it.visibility,
                    cloudCover = ratioToPercent(it.cloudrate),
                    precipMm = it.precipitation?.local?.intensity,
                )
            },
            hourly = hourlyBlock?.let { h ->
                fun <T> index(values: List<T>?, time: (T) -> String?) = datedValues(values) { parseTime(time(it), offsetHint) }
                val temps = index(h.temperature) { it.datetime }
                val apparents = index(h.apparentTemperature) { it.datetime }
                val skies = index(h.skycon) { it.datetime }
                val winds = index(h.wind) { it.datetime }
                val precips = index(h.precipitation) { it.datetime }
                val humidities = index(h.humidity) { it.datetime }
                val pressures = index(h.pressure) { it.datetime }
                val visibilities = index(h.visibility) { it.datetime }
                val clouds = index(h.cloudrate) { it.datetime }
                val airs = index(h.airQuality?.aqi) { it.datetime }
                // Each array carries its own timestamp. Missing samples must not
                // move rain/wind from another hour onto the temperature row.
                (temps.keys + apparents.keys + skies.keys + winds.keys + precips.keys + humidities.keys +
                    pressures.keys + visibilities.keys + clouds.keys + airs.keys).sorted().take(48).map { timeMillis ->
                    val temp = temps[timeMillis]
                    val apparent = apparents[timeMillis]
                    val sky = skies[timeMillis]
                    val wind = winds[timeMillis]
                    val precip = precips[timeMillis]
                    val humidity = humidities[timeMillis]
                    val pressure = pressures[timeMillis]
                    val visibility = visibilities[timeMillis]
                    val cloudrate = clouds[timeMillis]
                    val air = airs[timeMillis]
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
            daily = dailyBlock?.let { daily ->
                fun <T> index(values: List<T>?, date: (T) -> String?) = datedValues(values) { dailyDate(date(it), offsetHint) }
                val temps = index(daily.temperature) { it.date }
                val skies = index(daily.skycon) { it.date ?: it.datetime }
                val daySkies = index(daily.skyconDay) { it.date ?: it.datetime }
                val nightSkies = index(daily.skyconNight) { it.date ?: it.datetime }
                val astros = index(daily.astro) { it.date }
                val precips = index(daily.precipitation) { it.date }
                val dayPrecips = index(daily.precipitationDay) { it.date }
                val nightPrecips = index(daily.precipitationNight) { it.date }
                val humidities = index(daily.humidity) { it.date }
                val clouds = index(daily.cloudrate) { it.date }
                val winds = index(daily.wind) { it.date }
                val dayWinds = index(daily.windDay) { it.date }
                val nightWinds = index(daily.windNight) { it.date }
                val uvs = index(daily.lifeIndex?.ultraviolet) { it.date }
                (temps.keys + skies.keys + daySkies.keys + nightSkies.keys + astros.keys + precips.keys +
                    dayPrecips.keys + nightPrecips.keys + humidities.keys + clouds.keys + winds.keys +
                    dayWinds.keys + nightWinds.keys + uvs.keys).sorted().take(15).map { dateMillis ->
                    val temp = temps[dateMillis]
                    val sky = skies[dateMillis]
                    val daySky = daySkies[dateMillis]?.value
                    val nightSky = nightSkies[dateMillis]?.value
                    val astro = astros[dateMillis]
                    val precip = precips[dateMillis]
                    val precipDay = dayPrecips[dateMillis]
                    val precipNight = nightPrecips[dateMillis]
                    val humidity = humidities[dateMillis]
                    val cloudrate = clouds[dateMillis]
                    val wind = winds[dateMillis]
                    val windDay = dayWinds[dateMillis]
                    val windNight = nightWinds[dateMillis]
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
                        uvIndex = uvs[dateMillis]?.index?.toDoubleOrNull()?.roundToInt(),
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
            alerts = alertBlock?.content.orEmpty().mapNotNull { a ->
                val title = a.title?.trim().orEmpty()
                if (title.isEmpty()) null
                else AlertInfo(
                    title = title,
                    detail = a.description,
                    level = a.code,
                    severity = alertLevelOf(a.code ?: a.title),
                    id = a.alertId?.trim()?.takeIf(String::isNotEmpty),
                    pubTime = a.pubtimestamp?.takeIf { it > 0L }
                        ?.let { runCatching { java.time.Instant.ofEpochSecond(it).toString() }.getOrNull() },
                )
            },
            updateTime = providerUpdateTime,
            // 官方定义：minutely.description 是未来 2 小时短临，forecast_keypoint 是
            // 未来 24 小时变化。两者不能塞进同一个字段，否则“实况晴”下面紧接
            // “多云，今晚转雨”会被误读成同一时刻互相打架。
            rainNowcast = minutely?.description,
            forecastSummary = r.forecastKeypoint,
            rainMinutes = minutes,
            rainMeta = minutes.takeIf { it.isNotEmpty() }?.let {
                RainMeta("CAIYUN", 1, now,
                    horizonMinutes = ((it.last().timeMillis - now) / Nowcast.MINUTE_MS).toInt() + 1)
            },
            rainDistanceKm = rt?.precipitation?.nearest?.distance
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?.div(1_000.0),
            extraIndices = mapLifeIndices(dailyBlock?.lifeIndex),
            dataSource = "CAIYUN",
            blockSources = mapOf("current" to "CAIYUN", "hourly" to "CAIYUN", "daily" to "CAIYUN", "minutely" to "CAIYUN") +
                if (alertBlock != null) mapOf("alerts" to "CAIYUN") else emptyMap(),
            locationMatch = WeatherLocationMatch(
                requestedLatitude = city.latitude,
                requestedLongitude = city.longitude,
                providerLatitude = caiyunCoordinate(city.latitude).toDouble(),
                providerLongitude = caiyunCoordinate(city.longitude).toDouble(),
                preciseGps = city.isPreciseLocation,
            ),
            utcOffsetSeconds = offsetHint,
        )
    }

    private fun <T> datedValues(values: List<T>?, time: (T) -> Long?): Map<Long, T> =
        values.orEmpty().mapNotNull { item -> time(item)?.let { it to item } }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (date, items) -> items.distinct().singleOrNull()?.let { date to it } }.toMap()

    private fun dailyDate(raw: String?, offset: Int?): Long? {
        val zone = java.time.ZoneOffset.ofTotalSeconds(offset ?: 8 * 3_600)
        val instant = parseTime(raw, offset)
        val date = instant?.let { java.time.Instant.ofEpochMilli(it).atOffset(zone).toLocalDate() }
            ?: runCatching { java.time.LocalDate.parse(raw) }.getOrNull() ?: return null
        return date.atStartOfDay().toInstant(zone).toEpochMilli()
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
        "HEAVY_RAIN" -> "大雨"
        "STORM_RAIN" -> "暴雨"
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

internal fun caiyunCoordinate(value: Double): String =
    String.format(java.util.Locale.US, "%.6f", value)
