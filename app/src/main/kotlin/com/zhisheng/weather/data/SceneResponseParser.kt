package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.abs

/** 只接收 UNIX 时间：跨时区/夏令时不靠“本地时间减今天偏移”猜测。单位异常则拒绝对应响应。 */
internal object SceneResponseParser {
    private val json = Json { ignoreUnknownKeys = true }
    private const val HOUR = 3_600_000L
    fun skyUrl(city: City) =
        "https://api.open-meteo.com/v1/forecast?latitude=${city.latitude}&longitude=${city.longitude}" +
            "&hourly=temperature_2m,relative_humidity_2m,dew_point_2m,precipitation," +
            "precipitation_probability,visibility,cloud_cover,cloud_cover_low,cloud_cover_mid," +
            "cloud_cover_high,wind_speed_10m,wind_gusts_10m" +
            "&daily=sunrise,sunset&forecast_days=3&timezone=auto&timeformat=unixtime&wind_speed_unit=kmh" +
            openMeteoCellSelection(city.isPreciseLocation)

    fun marineUrl(city: City) =
        "https://marine-api.open-meteo.com/v1/marine?latitude=${city.latitude}&longitude=${city.longitude}" +
            "&hourly=wave_height,wave_direction,wave_period,wind_wave_height,wind_wave_direction,wind_wave_period," +
            "swell_wave_height,swell_wave_direction,swell_wave_period,sea_surface_temperature," +
            "ocean_current_velocity,ocean_current_direction,sea_level_height_msl" +
            "&forecast_days=3&timezone=auto&cell_selection=sea&timeformat=unixtime&length_unit=metric"

    fun sky(raw: String, city: City, fetched: Long, now: Long, includeStars: Boolean = true): SkyPhotographyForecast? = parse(raw) { r ->
        if (!r.unitsAre("visibility" to "m", "precipitation" to "mm", "wind_speed_10m" to "km/h",
                "temperature_2m" to "°C", "time" to "unixtime")) return@parse null
        val hours = r.times().map { (i, t) ->
            SceneHour(t,
                r.value("temperature_2m", i, -100.0..65.0),
                r.value("relative_humidity_2m", i, 0.0..100.0),
                r.value("dew_point_2m", i, -120.0..65.0),
                r.value("precipitation", i, 0.0..1000.0),
                r.value("precipitation_probability", i, 0.0..100.0),
                r.value("visibility", i, 0.0..1_000_000.0)?.div(1000),
                r.value("cloud_cover", i, 0.0..100.0),
                r.value("cloud_cover_low", i, 0.0..100.0),
                r.value("cloud_cover_mid", i, 0.0..100.0),
                r.value("cloud_cover_high", i, 0.0..100.0),
                r.value("wind_speed_10m", i, 0.0..500.0),
                r.value("wind_gusts_10m", i, 0.0..500.0),
            )
        }
        if (hours.none { it.hasCore && it.timeMillis in now - HOUR..now + 36 * HOUR }) return@parse null
        val events = buildList {
            r.daily?.get("sunrise")?.jsonArray?.forEach { unix(it)?.let { t -> add(t to GlowKind.DAWN) } }
            r.daily?.get("sunset")?.jsonArray?.forEach { unix(it)?.let { t -> add(t to GlowKind.DUSK) } }
        }
        val glow = SceneWeatherCalculator.glow(hours, events, now)
        val stars = if (includeStars) SceneWeatherCalculator.stars(hours, city.latitude, city.longitude, now, r.zone()) else null
        SkyPhotographyForecast(glow, stars, updatedAtMillis = fetched, zoneId = r.zone(),
            unavailableReason = if (includeStars && stars == null) "本夜没有数据完整的连续两小时天文黑夜；短夜、极昼或缺测均可能导致" else null)
    }

    data class MarineResult(val forecast: CoastalForecast?)
    fun marine(raw: String, city: City, fetched: Long, now: Long): MarineResult? = parse(raw) { r ->
        if (!r.unitsAre("wave_height" to "m", "sea_level_height_msl" to "m",
                "sea_surface_temperature" to "°C", "time" to "unixtime")) return@parse null
        val lat = r.latitude ?: return@parse null
        val lon = r.longitude ?: return@parse null
        if (!validSceneCoordinates(lat, lon)) return@parse null
        val distance = SceneWeatherRepository.haversineKm(city.latitude, city.longitude, lat, lon)
        if (distance > SceneWeatherRepository.MAX_COAST_GRID_DISTANCE_KM) return@parse MarineResult(null)
        val times = r.times()
        if (times.isEmpty()) return@parse null
        // 当前小时，而不是四舍五入成未来一个小时。时间索引不因坏时间/缺失值位移。
        val current = times.lastOrNull { it.second <= now && now - it.second < HOUR } ?: return@parse null
        val i = current.first
        val points = times.map { (index, t) -> MarinePoint(t, r.value("sea_level_height_msl", index, -30.0..30.0)) }
        val wave = r.value("wave_height", i, 0.0..40.0)
        val temp = r.value("sea_surface_temperature", i, -5.0..50.0)
        val hasSea = wave != null || temp != null || points.any { it.timeMillis >= now && it.seaLevelM != null }
        if (!hasSea) return@parse MarineResult(null) // 陆地附近返回全 null，不显示伪造的平潮/0米
        val turns = SceneWeatherCalculator.tide(points, now)
        val velocity = r.value("ocean_current_velocity", i, 0.0..200.0)?.let {
            when (r.hourly_units["ocean_current_velocity"]) {
                null, "km/h" -> it
                "m/s" -> it * 3.6
                "kn", "knots" -> it * 1.852
                "mph" -> it * 1.609344
                else -> null
            }
        }
        MarineResult(CoastalForecast(
            trend = turns.first,
            points = points.filter { it.timeMillis in now - HOUR..now + 24 * HOUR },
            nextHigh = turns.second?.takeIf { it.timeMillis <= now + 24 * HOUR },
            nextLow = turns.third?.takeIf { it.timeMillis <= now + 24 * HOUR },
            waveHeightM = wave, waveDirectionDeg = r.value("wave_direction", i, 0.0..360.0),
            wavePeriodSeconds = r.value("wave_period", i, 0.0..100.0),
            swellHeightM = r.value("swell_wave_height", i, 0.0..40.0),
            swellPeriodSeconds = r.value("swell_wave_period", i, 0.0..100.0),
            seaTemperatureC = temp, currentVelocityKmh = velocity,
            currentDirectionDeg = r.value("ocean_current_direction", i, 0.0..360.0),
            gridDistanceKm = distance, updatedAtMillis = fetched, zoneId = r.zone(), sampleTimeMillis = current.second,
            windWaveHeightM = r.value("wind_wave_height", i, 0.0..40.0),
            windWavePeriodSeconds = r.value("wind_wave_period", i, 0.0..100.0),
            windWaveDirectionDeg = r.value("wind_wave_direction", i, 0.0..360.0),
            swellDirectionDeg = r.value("swell_wave_direction", i, 0.0..360.0),
        ))
    }

    private fun <T> parse(raw: String, block: (Payload) -> T?): T? = try {
        if (raw.length > 512_000) null else block(json.decodeFromString<Payload>(raw))
    } catch (_: Exception) { null }

    private fun unix(v: JsonElement): Long? = (v as? JsonPrimitive)?.longOrNull
        ?.takeIf { it in 946684800L..4102444800L }?.times(1000)

    @Serializable
    private data class Payload(
        val latitude: Double? = null, val longitude: Double? = null,
        val timezone: String? = null, val utc_offset_seconds: Int = 0,
        val hourly: JsonObject? = null, val daily: JsonObject? = null,
        val hourly_units: Map<String, String> = emptyMap(),
    ) {
        fun zone(): String = timezone?.takeIf { runCatching { ZoneId.of(it) }.isSuccess }
            ?: ZoneOffset.ofTotalSeconds(utc_offset_seconds.coerceIn(-64800, 64800)).id
        fun unitsAre(vararg fields: Pair<String, String>) = fields.all { (key, expected) ->
            hourly_units[key]?.let { it == expected } ?: true
        }
        fun times() = hourly?.get("time")?.jsonArray.orEmpty().take(100).mapIndexedNotNull { i, v ->
            unix(v)?.let { i to it }
        }.sortedBy { it.second }.distinctBy { it.second }
        fun value(key: String, index: Int, range: ClosedFloatingPointRange<Double>): Double? =
            (hourly?.get(key) as? JsonArray)?.getOrNull(index)?.jsonPrimitive?.doubleOrNull
                ?.takeIf { it.isFinite() && it in range }
    }
}
