package com.zhisheng.weather.data

import com.zhisheng.weather.model.AqiInfo
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.MinutePrecip
import com.zhisheng.weather.model.RainMeta
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.YesterdayInfo
import com.zhisheng.weather.model.cityDate
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 枳生天气源融合引擎：纯函数（无网络 / 无 DataStore / 无系统时钟，nowMillis 注入）。
 *
 * 规则（与方案一致）：标量加权中位数（鲁棒）；风走 u/v 向量加权平均；现象权重投票
 * （中央气象台票 ×1.5，平票取更严重者）；实况偏差订正（0-6h 温度，指数衰减）；
 * 官方锚（|融合-央台|>3°C 且央台在场时向它拉 0.5）；逐日按 cityDate 分桶并按
 * 参与源并集长度输出；月相/月升落经 MoonCalc 本地补齐；露点（Magnus）、体感、
 * 平均温、AQI（国标 GB HJ 633-2012）等按完全体契约本地兜底，保证字段不空。
 */
object FusionEngine {

    internal const val HOUR_MS = 3_600_000L
    internal const val MIN_FUSED_SOURCES = 2
    internal const val FUSED_HOURLY_HOURS = 48
    internal const val NMC_OFFICIAL_VOTE_BOOST = 1.5
    internal const val OFFICIAL_ANCHOR_TEMP_DIVERGENCE_C = 3.0
    internal const val OFFICIAL_ANCHOR_PULL = 0.5
    internal const val BIAS_DECAY_HOURS = 3.0
    internal const val BIAS_HORIZON_HOURS = 6
    internal const val BIAS_MAX_DELTA_C = 15.0
    internal const val WIND_CALM_KMH = 0.8

    internal const val OFFICIAL_ID = "NMC"
    internal val MINUTELY_PRIORITY = listOf("CAIYUN", "QWEATHER", "XIAOMI")
    internal val AQI_PRIORITY = listOf("NMC", "WEATHERCN", "XIAOMI", "CAIYUN", "QWEATHER")
    internal val OFFSET_PRIORITY = listOf("OPEN-METEO", "XIAOMI", "CAIYUN", "QWEATHER", "NMC", "TENCENT", "WEATHERCN")

    internal data class FusionSource(
        val id: String,
        val label: String,
        val weight: Double,
        val data: WeatherData,
    )

    internal data class Input(
        val city: City,
        val sources: List<FusionSource>,
        val nowMillis: Long,
        val omAir: OmAirQuality? = null,
        val omSupplements: OmSupplements? = null,
    )

    internal fun hourBucket(ms: Long, utcOffsetSeconds: Int? = null): Long {
        val offset = (utcOffsetSeconds?.takeIf { it in -64_800..64_800 } ?: 0) * 1_000L
        return Math.floorDiv(ms + offset, HOUR_MS) * HOUR_MS - offset
    }

    // —— 入口 ——

    internal fun fuse(input: Input): WeatherData {
        val utcOffset = resolveUtcOffset(input)
        val nowBucket = hourBucket(input.nowMillis, utcOffset)
        val startHour = nowBucket - HOUR_MS
        val endHour = nowBucket + FUSED_HOURLY_HOURS * HOUR_MS

        val current = fuseCurrent(input.sources)
        val hourlyRaw = fuseHourly(input, startHour, endHour)
        val hourlyCorrected = applyBiasCorrection(hourlyRaw, current, input.nowMillis, utcOffset)
        val hourlyFilled = fillHourlyCompleteness(hourlyCorrected, input.omSupplements)

        return WeatherData(
            current = current,
            hourly = hourlyFilled,
            daily = fuseDaily(input.sources, input.city, utcOffset),
            aqi = fuseAqi(input),
            alerts = pickAlerts(input.sources),
            rainNowcast = pickRainNowcast(input.sources),
            rainMinutes = pickRainMinutes(input.sources, input.omSupplements, input.nowMillis),
            rainMeta = pickRainMeta(input.sources, input.omSupplements, input.nowMillis),
            rainDistanceKm = input.sources.firstNotNullOfOrNull { it.data.rainDistanceKm },
            carWashOk = input.sources.firstNotNullOfOrNull { it.data.carWashOk },
            sportsOk = input.sources.firstNotNullOfOrNull { it.data.sportsOk },
            extraIndices = mergeExtraIndices(input.sources),
            yesterday = pickYesterday(input.sources, input.omSupplements, input.nowMillis, utcOffset),
            typhoons = input.sources.firstNotNullOfOrNull { it.data.typhoons?.takeIf { list -> list.isNotEmpty() } }.orEmpty(),
            forecastSummary = pickForecastSummary(input.sources)
                ?: deriveForecastSummary(hourlyFilled, current, input.nowMillis),
            updateTime = input.sources.mapNotNull { it.data.updateTime }.maxOrNull(),
            dataSource = "ZHISHENG",
            blockSources = buildBlockSources(input.sources),
            fusionSources = input.sources.sortedByDescending { it.weight }.map { it.label }.distinct(),
            utcOffsetSeconds = utcOffset,
        )
    }

    internal fun resolveUtcOffset(input: Input): Int? {
        OFFSET_PRIORITY.forEach { id ->
            input.sources.firstOrNull { it.id == id }?.data?.utcOffsetSeconds?.let { return it }
        }
        return input.sources.firstNotNullOfOrNull { it.data.utcOffsetSeconds }
            ?: input.sources.firstOrNull()?.data?.utcOffsetSeconds
    }

    // —— 标量：加权中位数（排序后累计权过半数取该值） ——

    internal fun weightedMedian(votes: List<Pair<Double, Double>>): Double? {
        val clean = votes.filter { it.first.isFinite() && it.second > 0.0 }
        if (clean.isEmpty()) return null
        val sorted = clean.sortedBy { it.first }
        val total = sorted.sumOf { it.second }
        var acc = 0.0
        for ((value, w) in sorted) {
            acc += w
            if (acc >= total / 2.0) return value
        }
        return sorted.last().first
    }

    // —— 风：u/v 向量加权平均 ——

    internal data class WindSample(val speedKmh: Double, val dirDeg: Double?, val weight: Double)

    internal fun vectorMeanWind(samples: List<WindSample>): WindSample? {
        val valid = samples.filter { it.speedKmh.isFinite() && it.speedKmh >= 0.0 && it.weight > 0.0 }
        if (valid.isEmpty()) return null
        val withDir = valid.filter { it.dirDeg != null && it.dirDeg.isFinite() }
        if (withDir.isEmpty()) {
            val speed = weightedMedian(valid.map { it.speedKmh to it.weight }) ?: return null
            return WindSample(speed, null, valid.sumOf { it.weight })
        }
        var u = 0.0
        var v = 0.0
        var wSum = 0.0
        withDir.forEach { s ->
            val rad = Math.toRadians(s.dirDeg!!)
            // 风向为"来向"：风往 -dir 方向吹
            u += -s.speedKmh * sin(rad) * s.weight
            v += -s.speedKmh * cos(rad) * s.weight
            wSum += s.weight
        }
        if (wSum <= 0.0) return null
        u /= wSum
        v /= wSum
        val speed = hypot(u, v)
        if (speed < WIND_CALM_KMH) {
            // 静风（含对吹抵消）：方向回退最大权重源，速度回退加权中位数（不让对吹互相放大）
            val dir = valid.maxByOrNull { it.weight }?.dirDeg
            val med = weightedMedian(valid.map { it.speedKmh to it.weight }) ?: return null
            return WindSample(med, dir, valid.sumOf { it.weight })
        }
        val dir = (Math.toDegrees(atan2(-u, -v)) + 360.0) % 360.0
        return WindSample(speed, dir, valid.sumOf { it.weight })
    }

    // —— 现象：权重投票（央台加成；平票取更严重者） ——

    internal data class ConditionVote(val condition: WeatherCondition, val weight: Double, val official: Boolean)

    internal fun voteCondition(votes: List<ConditionVote>): WeatherCondition? {
        if (votes.isEmpty()) return null
        val scored = votes.groupBy { it.condition }.map { (cond, list) ->
            cond to list.sumOf { it.weight * if (it.official) NMC_OFFICIAL_VOTE_BOOST else 1.0 }
        }
        val max = scored.maxOf { it.second }
        return scored.filter { it.second >= max - 1e-9 }
            .maxByOrNull { severityRank(it.first) }?.first
    }

    private fun severityRank(c: WeatherCondition): Int = when (c) {
        WeatherCondition.THUNDERSTORM -> 9
        WeatherCondition.HAIL -> 8
        WeatherCondition.SNOW, WeatherCondition.FREEZING_RAIN -> 7
        WeatherCondition.RAIN -> 6
        WeatherCondition.SLEET -> 5
        WeatherCondition.DRIZZLE -> 4
        WeatherCondition.SAND, WeatherCondition.HAZE, WeatherCondition.FOG -> 3
        WeatherCondition.OVERCAST -> 2
        WeatherCondition.PARTLY_CLOUDY, WeatherCondition.PARTLY_CLOUDY_NIGHT -> 1
        else -> 0
    }

    // —— 实况融合 ——

    internal fun fuseCurrent(sources: List<FusionSource>): CurrentWeather? {
        val pairs = sources.mapNotNull { s -> s.data.current?.let { s to it } }
        if (pairs.isEmpty()) return null
        fun med(selector: (CurrentWeather) -> Double?): Double? =
            weightedMedian(pairs.mapNotNull { (s, c) -> selector(c)?.let { it to s.weight } })

        val wind = vectorMeanWind(pairs.mapNotNull { (s, c) ->
            c.windSpeed?.let { WindSample(it, c.windDirectionDeg, s.weight) }
        })
        val condition = voteCondition(pairs.mapNotNull { (s, c) ->
            c.condition?.takeIf { it != WeatherCondition.UNKNOWN }
                ?.let { ConditionVote(it, s.weight, s.id == OFFICIAL_ID) }
        })
        // 文本不融合：取"选中现象"里权重最高来源的原文
        val textSource = pairs.filter { it.second.condition == condition }
            .maxByOrNull { it.first.weight }?.second

        val temp = med { it.temperature }
        val humidity = med { it.humidity }
        val conditionProfiles = pairs.filter { it.second.condition == condition }.map { it.second }
        val profile = conditionProfiles.firstNotNullOfOrNull { c -> c.profile?.takeIf { it.condition == condition } }
            ?: profileFor(condition)

        return CurrentWeather(
            temperature = temp,
            feelsLike = med { it.feelsLike } ?: localFeelsLike(temp, humidity, wind?.speedKmh),
            condition = condition,
            weatherText = textSource?.weatherText?.takeIf { it.isNotBlank() } ?: condition?.label,
            humidity = humidity,
            windSpeed = wind?.speedKmh,
            windDirectionDeg = wind?.dirDeg,
            pressure = med { it.pressure },
            uvIndex = med { it.uvIndex?.toDouble() }?.roundToInt(),
            visibility = med { it.visibility },
            dewPoint = med { it.dewPoint } ?: localDewPoint(temp, humidity),
            cloudCover = med { it.cloudCover },
            windGust = med { it.windGust } ?: wind?.speedKmh,
            precipMm = med { it.precipMm },
            profile = profile,
        )
    }

    // —— 逐时融合（1h 网格；每变量独立覆盖率） ——

    internal fun fuseHourly(input: Input, startHour: Long, endHour: Long): List<HourlyWeather> {
        val buckets = LinkedHashMap<Long, MutableList<Pair<FusionSource, HourlyWeather>>>()
        val utcOffset = resolveUtcOffset(input)
        input.sources.forEach { s ->
            s.data.hourly.forEach { h ->
                if (h.timeMillis <= 0L) return@forEach
                val b = hourBucket(h.timeMillis, utcOffset)
                if (b in startHour..endHour) buckets.getOrPut(b) { mutableListOf() } += s to h
            }
        }
        return buckets.entries.sortedBy { it.key }.mapNotNull { (bucket, list) ->
            val hasAnyValue = list.any { (_, h) ->
                h.temperature != null || h.condition != null || h.windSpeed != null || h.precipProb != null
            }
            if (!hasAnyValue) return@mapNotNull null
            fun med(selector: (HourlyWeather) -> Double?): Double? =
                weightedMedian(list.mapNotNull { (s, h) -> selector(h)?.let { it to s.weight } })
            val wind = vectorMeanWind(list.mapNotNull { (s, h) ->
                h.windSpeed?.let { WindSample(it, h.windDirectionDeg, s.weight) }
            })
            val condition = voteCondition(list.mapNotNull { (s, h) ->
                h.condition?.takeIf { it != WeatherCondition.UNKNOWN }
                    ?.let { ConditionVote(it, s.weight, s.id == OFFICIAL_ID) }
            })
            var temp = med { it.temperature }
            // 官方锚：与中央气象台极端分歧时向央台拉（仅它在场时）
            val nmcTemp = list.firstOrNull { (s, _) -> s.id == OFFICIAL_ID }?.second?.temperature
            if (temp != null && nmcTemp != null && abs(temp - nmcTemp) > OFFICIAL_ANCHOR_TEMP_DIVERGENCE_C) {
                temp += (nmcTemp - temp) * OFFICIAL_ANCHOR_PULL
            }
            val humidity = med { it.humidity }
            // profile 优先复用参与源自带的（含强度/相态语义），缺时按现象构造
            val profile = list.firstNotNullOfOrNull { (_, h) ->
                h.profile?.takeIf { it.condition == condition }
            } ?: profileFor(condition)
            HourlyWeather(
                timeMillis = bucket,
                temperature = temp,
                condition = condition,
                windSpeed = wind?.speedKmh,
                precipProb = med { it.precipProb?.toDouble() }?.roundToInt()?.takeIf { it in 0..100 },
                aqi = med { it.aqi?.toDouble() }?.roundToInt()?.takeIf { it in 0..1000 }
                    ?: gbAqiAt(input.omAir, bucket)?.first,
                profile = profile,
                feelsLike = med { it.feelsLike } ?: localFeelsLike(temp, humidity, wind?.speedKmh),
                windDirectionDeg = wind?.dirDeg,
                windGust = med { it.windGust } ?: wind?.speedKmh,
                precipMm = med { it.precipMm },
                humidity = humidity,
                pressure = med { it.pressure },
                visibility = med { it.visibility },
                dewPoint = med { it.dewPoint } ?: localDewPoint(temp, humidity),
                cloudCover = med { it.cloudCover },
                uvIndex = med { it.uvIndex?.toDouble() }?.roundToInt()?.takeIf { it >= 0 },
            )
        }
    }

    /** 补充包兜底：逐时紫外线缺位时按 best_match 序列补；露点/体感已在上游本地兜底。 */
    internal fun fillHourlyCompleteness(
        hourly: List<HourlyWeather>,
        om: OmSupplements?,
    ): List<HourlyWeather> {
        if (om == null || om.uvIndex.isEmpty()) return hourly
        val uvByHour = HashMap<Long, Double>()
        om.hourlyTimeMillis.forEachIndexed { i, t ->
            om.uvIndex.getOrNull(i)?.let { uvByHour[hourBucket(t, om.timeZoneOffsetSeconds)] = it }
        }
        return hourly.map { h ->
            if (h.uvIndex == null) {
                uvByHour[hourBucket(h.timeMillis, om.timeZoneOffsetSeconds)]?.takeIf { it >= 0 }
                    ?.let { h.copy(uvIndex = it.roundToInt()) } ?: h
            } else h
        }
    }

    // —— 实况偏差订正（0-6h 温度；指数衰减） ——

    internal fun applyBiasCorrection(
        hourly: List<HourlyWeather>,
        current: CurrentWeather?,
        nowMillis: Long,
        utcOffsetSeconds: Int? = null,
    ): List<HourlyWeather> {
        val nowTemp = current?.temperature ?: return hourly
        val nowBucket = hourBucket(nowMillis, utcOffsetSeconds)
        val forecastNow = hourly.firstOrNull { it.timeMillis == nowBucket }?.temperature ?: return hourly
        val delta = nowTemp - forecastNow
        if (!delta.isFinite() || abs(delta) > BIAS_MAX_DELTA_C) return hourly
        return hourly.map { h ->
            val leadH = (h.timeMillis - nowBucket) / HOUR_MS
            if (h.temperature != null && leadH in 1..BIAS_HORIZON_HOURS.toLong()) {
                h.copy(temperature = h.temperature + delta * exp(-leadH.toDouble() / BIAS_DECAY_HOURS))
            } else h
        }
    }

    // —— 逐日融合（cityDate 分桶；并集长度；月相本地补齐） ——

    internal fun fuseDaily(
        sources: List<FusionSource>,
        city: City,
        utcOffsetSeconds: Int?,
    ): List<DailyWeather> {
        data class DayVote(val source: FusionSource, val day: DailyWeather, val date: LocalDate)

        val votes = mutableListOf<DayVote>()
        sources.forEach { s ->
            val off = s.data.utcOffsetSeconds ?: utcOffsetSeconds
            s.data.daily.forEach { d ->
                if (d.dateMillis <= 0L) return@forEach
                votes += DayVote(s, d, cityDate(d.dateMillis, off))
            }
        }
        if (votes.isEmpty()) return emptyList()
        val offset = ZoneOffset.ofTotalSeconds(utcOffsetSeconds ?: 28800)
        return votes.groupBy { it.date }.toSortedMap().map { (date, dayVotes) ->
            fun med(selector: (DailyWeather) -> Double?): Double? =
                weightedMedian(dayVotes.mapNotNull { (s, d) -> selector(d)?.let { it to s.weight } })
            val rawHigh = med { it.high }
            val rawLow = med { it.low }
            val high = if (rawHigh != null && rawLow != null) maxOf(rawHigh, rawLow) else rawHigh
            val low = if (rawHigh != null && rawLow != null) minOf(rawHigh, rawLow) else rawLow
            val condition = voteCondition(dayVotes.mapNotNull { (s, d) ->
                d.condition?.takeIf { it != WeatherCondition.UNKNOWN }
                    ?.let { ConditionVote(it, s.weight, s.id == OFFICIAL_ID) }
            })
            val textSource = dayVotes.filter { it.day.condition == condition }
                .maxByOrNull { it.source.weight }?.day
            val byWeight = dayVotes.sortedByDescending { it.source.weight }.map { it.day }
            fun firstNonNull(selector: (DailyWeather) -> String?): String? =
                byWeight.firstNotNullOfOrNull { selector(it)?.takeIf { v -> v.isNotBlank() } }
            fun firstNonNullAny(selector: (DailyWeather) -> Double?): Double? =
                byWeight.firstNotNullOfOrNull { selector(it) }

            val fused = DailyWeather(
                dateMillis = date.atStartOfDay(offset).toInstant().toEpochMilli(),
                high = high,
                low = low,
                condition = condition,
                windSpeed = med { it.windSpeed } ?: firstNonNullAny { it.windSpeed },
                precipProbability = med { it.precipProbability?.toDouble() }?.roundToInt()?.takeIf { it in 0..100 },
                sunrise = firstNonNull { it.sunrise },
                sunset = firstNonNull { it.sunset },
                moonrise = firstNonNull { it.moonrise },
                moonset = firstNonNull { it.moonset },
                moonPhase = firstNonNull { it.moonPhase },
                precipMm = med { it.precipMm },
                weatherText = textSource?.weatherText?.takeIf { it.isNotBlank() } ?: condition?.label,
                profile = profileFor(condition),
                average = med { it.average } ?: if (high != null && low != null) (high + low) / 2.0 else null,
                windDirectionDeg = med { it.windDirectionDeg } ?: firstNonNullAny { it.windDirectionDeg },
                windGust = med { it.windGust },
                humidity = med { it.humidity },
                cloudCover = med { it.cloudCover },
                uvIndex = med { it.uvIndex?.toDouble() }?.roundToInt()?.takeIf { it >= 0 },
                aqi = med { it.aqi?.toDouble() }?.roundToInt()?.takeIf { it in 0..1000 },
            )
            // 月相 / 月升月落：本地天文计算补齐（完全体恒有）
            MoonCalc.enrich(fused, city.latitude, city.longitude)
        }
    }

    // —— AQI（国标整条优先；缺时本地按 GB HJ 633-2012 用浓度计算；污染物可跨源补空） ——

    internal fun fuseAqi(input: Input): AqiInfo? {
        val fromSource = AQI_PRIORITY.firstNotNullOfOrNull { id ->
            input.sources.firstOrNull { it.id == id }?.data?.aqi
        }
        val om = input.omAir
        if (fromSource != null) {
            return fromSource.copy(
                pm25 = fromSource.pm25 ?: om?.currentPm25?.roundToInt()?.toString(),
                pm10 = fromSource.pm10 ?: om?.currentPm10?.roundToInt()?.toString(),
                o3 = fromSource.o3 ?: om?.currentO3?.roundToInt()?.toString(),
                no2 = fromSource.no2 ?: om?.currentNo2?.roundToInt()?.toString(),
                so2 = fromSource.so2 ?: om?.currentSo2?.roundToInt()?.toString(),
                co = fromSource.co ?: coDisplay(om?.currentCo),
                pollutantUnits = fromSource.pollutantUnits.ifEmpty { WeatherRepository.CHINA_POLLUTANT_UNITS }
                    .let { units -> if (fromSource.co == null && coDisplay(om?.currentCo) != null)
                        units + ("co" to "mg/m³") else units },
            )
        }
        if (om == null || om.currentPm25 == null && om.currentPm10 == null && om.currentO3 == null) return null
        val (value, primary) = gbAqiNow(om) ?: return null
        return AqiInfo(
            value = value,
            level = WeatherRepository.aqiLevel(value),
            standard = "中国",
            primary = primary,
            pm25 = om.currentPm25?.roundToInt()?.toString(),
            pm10 = om.currentPm10?.roundToInt()?.toString(),
            o3 = om.currentO3?.roundToInt()?.toString(),
            no2 = om.currentNo2?.roundToInt()?.toString(),
            so2 = om.currentSo2?.roundToInt()?.toString(),
            co = coDisplay(om.currentCo),
            pollutantUnits = WeatherRepository.CHINA_POLLUTANT_UNITS,
            suggest = localAqiSuggest(value),
        )
    }

    private fun coDisplay(value: Double?): String? = value
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }

    internal fun gbAqiNow(om: OmAirQuality): Pair<Int, String?>? {
        val items = listOfNotNull(
            om.currentPm25?.let { iaqi(it, GB_PM25)?.let { v -> v to "PM2.5" } },
            om.currentPm10?.let { iaqi(it, GB_PM10)?.let { v -> v to "PM10" } },
            om.currentO3?.let { iaqi(it, GB_O3)?.let { v -> v to "O₃" } },
            om.currentNo2?.let { iaqi(it, GB_NO2)?.let { v -> v to "NO₂" } },
            om.currentSo2?.let { iaqi(it, GB_SO2)?.let { v -> v to "SO₂" } },
            om.currentCo?.let { iaqi(it, GB_CO)?.let { v -> v to "CO" } },
        )
        if (items.isEmpty()) return null
        val (value, name) = items.maxByOrNull { it.first }!!
        return value.toInt().coerceIn(0, 500) to name.takeIf { value > 50 }
    }

    /** 逐时兜底：OM 空气质量逐时浓度 → 国标 AQI（按整点就近取，±90 分钟内）。 */
    internal fun gbAqiAt(om: OmAirQuality?, bucket: Long): Pair<Int, String?>? {
        om ?: return null
        val idx = om.hourlyTimeMillis.indices.minByOrNull { abs(om.hourlyTimeMillis[it] - bucket) }
            ?.takeIf { abs(om.hourlyTimeMillis[it] - bucket) <= HOUR_MS / 2 + HOUR_MS } ?: return null
        val items = listOfNotNull(
            om.hourlyPm25.getOrNull(idx)?.let { iaqi(it, GB_PM25) },
            om.hourlyPm10.getOrNull(idx)?.let { iaqi(it, GB_PM10) },
            om.hourlyO3.getOrNull(idx)?.let { iaqi(it, GB_O3) },
            om.hourlyNo2.getOrNull(idx)?.let { iaqi(it, GB_NO2) },
            om.hourlySo2.getOrNull(idx)?.let { iaqi(it, GB_SO2) },
            om.hourlyCo.getOrNull(idx)?.let { iaqi(it, GB_CO) },
        )
        val max = items.maxOrNull() ?: return null
        return max.toInt().coerceIn(0, 500) to null
    }

    // IAQI 分段线性（HJ 633-2012）。断点表 = [浓度断点] 对应 IAQI [0,50,100,150,200,300,400,500]。
    private val IAQI_LEVELS = doubleArrayOf(0.0, 50.0, 100.0, 150.0, 200.0, 300.0, 400.0, 500.0)
    private val GB_PM25 = doubleArrayOf(0.0, 35.0, 75.0, 115.0, 150.0, 250.0, 350.0, 500.0)
    private val GB_PM10 = doubleArrayOf(0.0, 50.0, 150.0, 250.0, 350.0, 420.0, 500.0, 600.0)
    private val GB_O3 = doubleArrayOf(0.0, 160.0, 200.0, 300.0, 400.0, 800.0, 1000.0, 1200.0)
    private val GB_NO2 = doubleArrayOf(0.0, 100.0, 200.0, 700.0, 1200.0, 2340.0, 3090.0, 3840.0)
    private val GB_SO2 = doubleArrayOf(0.0, 150.0, 500.0, 650.0, 800.0, 1600.0, 2100.0, 2620.0)
    private val GB_CO = doubleArrayOf(0.0, 5.0, 10.0, 35.0, 60.0, 90.0, 120.0, 150.0)

    private fun iaqi(concentration: Double, breakpoints: DoubleArray): Double? {
        if (!concentration.isFinite() || concentration < 0.0) return null
        val last = breakpoints.lastIndex
        if (concentration >= breakpoints[last]) return IAQI_LEVELS[last]
        for (i in 0 until last) {
            val lo = breakpoints[i]
            val hi = breakpoints[i + 1]
            if (concentration <= hi) {
                val iaqiLo = IAQI_LEVELS[i]
                val iaqiHi = IAQI_LEVELS[i + 1]
                return (iaqiHi - iaqiLo) / (hi - lo) * (concentration - lo) + iaqiLo
            }
        }
        return null
    }

    private fun localAqiSuggest(value: Int): String = when {
        value <= 50 -> "空气很好，适宜户外活动"
        value <= 100 -> "空气尚可，敏感人群适当减少户外活动"
        value <= 150 -> "轻度污染，外出宜佩戴口罩"
        value <= 200 -> "中度污染，减少户外活动"
        else -> "污染较重，尽量留在室内"
    }

    // —— 完全体本地兜底：露点 / 体感 / 现象 profile ——

    /** Magnus 公式：由温度+湿度求露点。 */
    internal fun localDewPoint(temp: Double?, humidity: Double?): Double? {
        if (temp == null || humidity == null || humidity <= 0.0 || humidity > 100.0) return null
        val a = 17.27
        val b = 237.7
        val gamma = a * temp / (b + temp) + ln(humidity / 100.0)
        val dew = b * gamma / (a - gamma)
        return dew.takeIf { it.isFinite() && it in -100.0..70.0 }
    }

    /** 体感：低温看风寒（加拿大公式），高温高湿看热指数；其余回退温度本身。 */
    internal fun localFeelsLike(temp: Double?, humidity: Double?, windKmh: Double?): Double? {
        if (temp == null) return null
        val windMs = ((windKmh ?: 0.0) / 3.6).coerceAtLeast(0.0)
        return when {
            temp <= 10.0 && windMs > 1.3 -> {
                val p = (windKmh ?: 0.0).coerceAtLeast(0.0).pow(0.16)
                13.12 + 0.6215 * temp - 11.37 * p + 0.3965 * temp * p
            }
            temp >= 27.0 && humidity != null && humidity in 0.0..100.0 -> {
                val t = temp
                val r = humidity
                val hi = -8.784695 + 1.61139411 * t + 2.338549 * r - 0.14611605 * t * r -
                    0.012308094 * t * t - 0.016424828 * r * r + 0.002211732 * t * t * r +
                    0.00072546 * t * r * r - 0.000003582 * t * t * r * r
                if (hi > t) hi else t
            }
            else -> temp
        }
    }

    private fun profileFor(condition: WeatherCondition?): com.zhisheng.weather.model.WeatherProfile? =
        condition?.takeIf { it != WeatherCondition.UNKNOWN }
            ?.let { com.zhisheng.weather.model.WeatherProfile(condition = it, source = "ZHISHENG") }

    // —— 直通块（不数值融合） ——

    private fun pickAlerts(sources: List<FusionSource>): List<com.zhisheng.weather.model.AlertInfo> {
        val priority = listOf("NMC", "TENCENT", "WEATHERCN")
        priority.forEach { id ->
            sources.firstOrNull { it.id == id }?.data?.alerts?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return sources.sortedByDescending { it.weight }
            .firstNotNullOfOrNull { it.data.alerts.takeIf { list -> list.isNotEmpty() } }
            .orEmpty()
    }

    private fun pickRainNowcast(sources: List<FusionSource>): String? =
        sources.sortedByDescending { it.weight }.firstNotNullOfOrNull { it.data.rainNowcast?.takeIf { s -> s.isNotBlank() } }

    private fun pickRainMinutes(
        sources: List<FusionSource>,
        om: OmSupplements?,
        nowMillis: Long,
    ): List<MinutePrecip> {
        MINUTELY_PRIORITY.forEach { id ->
            sources.firstOrNull { it.id == id }?.data?.rainMinutes?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return omMinutelyToMinutes(om, nowMillis)
    }

    private fun pickRainMeta(
        sources: List<FusionSource>,
        om: OmSupplements?,
        nowMillis: Long,
    ): RainMeta? {
        MINUTELY_PRIORITY.forEach { id ->
            sources.firstOrNull { it.id == id }?.data?.rainMeta?.let { return it }
        }
        return if (omMinutelyToMinutes(om, nowMillis).isNotEmpty()) {
            RainMeta(source = "OPEN-METEO", intervalMinutes = 15, horizonMinutes = 120)
        } else null
    }

    /** OM 15 分钟降水兜底：只取未来 2 小时窗口；mm/15min → mm/h 强度（真实标注 15 分钟粒度）。 */
    internal fun omMinutelyToMinutes(om: OmSupplements?, nowMillis: Long): List<MinutePrecip> {
        om ?: return emptyList()
        val end = nowMillis + 120 * 60_000L
        return om.minutely15TimeMillis.mapIndexedNotNull { i, t ->
            val start = t - 15 * 60_000L
            if (t <= nowMillis || start > end) return@mapIndexedNotNull null
            val mm = om.minutely15PrecipMm.getOrNull(i) ?: return@mapIndexedNotNull null
            MinutePrecip(timeMillis = start, precip = (mm * 4.0).toFloat())
        }
    }

    private fun mergeExtraIndices(sources: List<FusionSource>): List<com.zhisheng.weather.model.LifeIndexExtra> =
        sources.sortedByDescending { it.weight }
            .flatMap { it.data.extraIndices }
            .distinctBy { it.en }
            .take(12)

    internal fun pickYesterday(
        sources: List<FusionSource>,
        om: OmSupplements?,
        nowMillis: Long,
        utcOffsetSeconds: Int?,
    ): YesterdayInfo? {
        sources.sortedByDescending { it.weight }.firstNotNullOfOrNull { it.data.yesterday }?.let { return it }
        val today = cityDate(nowMillis, utcOffsetSeconds)
        // 腾讯 daily[0] 即昨日（实测语义）
        sources.sortedByDescending { it.weight }.forEach { s ->
            val first = s.data.daily.firstOrNull() ?: return@forEach
            val date = cityDate(first.dateMillis, s.data.utcOffsetSeconds ?: utcOffsetSeconds)
            if (date.isBefore(today)) {
                return YesterdayInfo(
                    high = first.high,
                    low = first.low,
                    condition = first.condition,
                    dateMillis = first.dateMillis,
                    aqi = first.aqi,
                )
            }
        }
        // OM past_days=1：首条 daily 即昨日
        if (om != null && om.dailyTime.isNotEmpty()) {
            val date = runCatching { LocalDate.parse(om.dailyTime.first()) }.getOrNull()
            if (date != null && date.isBefore(today)) {
                val offset = ZoneOffset.ofTotalSeconds(utcOffsetSeconds ?: 28800)
                return YesterdayInfo(
                    high = om.dailyHigh.getOrNull(0),
                    low = om.dailyLow.getOrNull(0),
                    dateMillis = date.atStartOfDay(offset).toInstant().toEpochMilli(),
                )
            }
        }
        return null
    }

    private fun pickForecastSummary(sources: List<FusionSource>): String? =
        sources.sortedByDescending { it.weight }
            .firstNotNullOfOrNull { it.data.forecastSummary?.takeIf { s -> s.isNotBlank() } }

    /** 本地派生 24h 摘要（完全体兜底，不产生精确数字断言）。 */
    internal fun deriveForecastSummary(
        hourly: List<HourlyWeather>,
        current: CurrentWeather?,
        nowMillis: Long,
    ): String? {
        val window = hourly.filter { it.timeMillis in (nowMillis + 1)..(nowMillis + 24 * HOUR_MS) }
        val temps = window.mapNotNull { it.temperature }
        if (temps.size < 3) return null
        val start = current?.temperature ?: temps.first()
        val max = temps.max()
        val min = temps.min()
        val peakProb = window.mapNotNull { it.precipProb }.maxOrNull() ?: 0
        val trend = when {
            max - start >= 4.0 -> "气温明显回升"
            start - min >= 4.0 -> "气温逐步走低"
            else -> "气温变化平缓"
        }
        val rain = when {
            peakProb >= 60 -> "降水概率较大"
            peakProb >= 30 -> "可能出现降水"
            else -> "降水概率较小"
        }
        return "未来 24 小时$trend，$rain"
    }

    // —— 分块主导源（用于排障与设置页展示） ——

    private fun buildBlockSources(sources: List<FusionSource>): Map<String, String> {
        fun anchor(labelSelector: (WeatherData) -> Boolean): String? =
            sources.filter { labelSelector(it.data) }.maxByOrNull { it.weight }?.label
        return buildMap {
            anchor { it.current != null }?.let { put("current", it) }
            anchor { it.hourly.isNotEmpty() }?.let { put("hourly", it) }
            anchor { it.daily.isNotEmpty() }?.let { put("daily", it) }
            anchor { it.aqi != null }?.let { put("aqi", it) }
            anchor { it.alerts.isNotEmpty() }?.let { put("alerts", it) }
            anchor { it.rainMinutes.isNotEmpty() }?.let { put("minutely", it) }
        }
    }
}
