package com.zhisheng.weather.model

import kotlin.math.abs

// 把同一份 WeatherData 里互相打架的信号收成一套「现在」。
// 实况、逐时第一格、短时降水文案必须能同时成立，不能上头下雨、下面写没雨。
object WeatherConsistency {
    const val PAST_HOUR_GRACE_MS = 50 * 60_000L
    const val NOW_HOUR_PAST_MS = 40 * 60_000L
    const val NOW_HOUR_FUTURE_MS = 10 * 60_000L

    fun dropPastHourly(
        data: WeatherData,
        nowMillis: Long = System.currentTimeMillis(),
    ): WeatherData {
        if (data.hourly.isEmpty()) return data
        val kept = data.hourly.filter { it.timeMillis >= nowMillis - PAST_HOUR_GRACE_MS }
        return if (kept.size == data.hourly.size) data else data.copy(hourly = kept)
    }

    fun align(
        data: WeatherData,
        nowMillis: Long = System.currentTimeMillis(),
    ): WeatherData {
        val sane = sanitize(data, nowMillis)
        if (sane.error != null) return dropPastHourly(sane, nowMillis)
        var d = dropPastHourly(sane, nowMillis)
        if (d.current == null) return d
        d = ensureCurrentHour(d, nowMillis)
        d = syncCurrentWithNowcast(d, nowMillis)
        d = overlayCurrentOntoNowHour(d, nowMillis)
        d = reconcileNowcastText(d, nowMillis)
        return d
    }

    /**
     * 最后一层数据闸门：供应商偶发的 NaN、负降水、越界百分比或重复乱序时间点
     * 不得直接进入图表。这里只丢弃不可能值，不用“看起来合理”的数字替换源数据。
     */
    internal fun sanitize(
        data: WeatherData,
        nowMillis: Long = System.currentTimeMillis(),
    ): WeatherData {
        val current = data.current?.let { cur ->
            cur.copy(
                temperature = cur.temperature.inRange(-110.0, 70.0),
                feelsLike = cur.feelsLike.inRange(-130.0, 90.0),
                humidity = cur.humidity.inRange(0.0, 100.0),
                windSpeed = cur.windSpeed.inRange(0.0, 500.0),
                windDirectionDeg = cur.windDirectionDeg.asDirection(),
                pressure = cur.pressure.inRange(250.0, 1_100.0),
                uvIndex = cur.uvIndex?.takeIf { it in 0..50 },
                visibility = cur.visibility.inRange(0.0, 1_000.0),
                dewPoint = cur.dewPoint.inRange(-120.0, 80.0),
                cloudCover = cur.cloudCover.inRange(0.0, 100.0),
                windGust = cur.windGust.inRange(0.0, 600.0),
                precipMm = cur.precipMm.inRange(0.0, 1_000.0),
            )
        }?.takeIf { cur ->
            cur.temperature != null || cur.feelsLike != null || cur.condition?.takeIf { it != WeatherCondition.UNKNOWN } != null ||
                cur.weatherText?.trim()?.takeIf { it.isNotEmpty() && it != "未知" && it != "--" } != null ||
                cur.humidity != null || cur.windSpeed != null ||
                cur.pressure != null || cur.visibility != null || cur.precipMm != null
        }
        val hourly = data.hourly.asSequence()
            .filter { it.timeMillis > 0L }
            .distinctBy(HourlyWeather::timeMillis)
            .sortedBy(HourlyWeather::timeMillis)
            .map { hour ->
                hour.copy(
                    temperature = hour.temperature.inRange(-110.0, 70.0),
                    feelsLike = hour.feelsLike.inRange(-130.0, 90.0),
                    windSpeed = hour.windSpeed.inRange(0.0, 500.0),
                    windDirectionDeg = hour.windDirectionDeg.asDirection(),
                    windGust = hour.windGust.inRange(0.0, 600.0),
                    precipProb = hour.precipProb?.takeIf { it in 0..100 },
                    precipMm = hour.precipMm.inRange(0.0, 1_000.0),
                    humidity = hour.humidity.inRange(0.0, 100.0),
                    pressure = hour.pressure.inRange(250.0, 1_100.0),
                    visibility = hour.visibility.inRange(0.0, 1_000.0),
                    dewPoint = hour.dewPoint.inRange(-120.0, 80.0),
                    cloudCover = hour.cloudCover.inRange(0.0, 100.0),
                    uvIndex = hour.uvIndex?.takeIf { it in 0..50 },
                    aqi = hour.aqi?.takeIf { it in 0..1_000 },
                )
            }
            .toList()
        val daily = data.daily.asSequence()
            .filter { it.dateMillis > 0L }
            .distinctBy { cityDate(it.dateMillis, data.utcOffsetSeconds) }
            .sortedBy(DailyWeather::dateMillis)
            .map { day ->
                val rawHigh = day.high.inRange(-110.0, 70.0)
                val rawLow = day.low.inRange(-110.0, 70.0)
                val high = if (rawHigh != null && rawLow != null) maxOf(rawHigh, rawLow) else rawHigh
                val low = if (rawHigh != null && rawLow != null) minOf(rawHigh, rawLow) else rawLow
                day.copy(
                    high = high,
                    low = low,
                    average = day.average.inRange(-110.0, 70.0),
                    windSpeed = day.windSpeed.inRange(0.0, 500.0),
                    windDirectionDeg = day.windDirectionDeg.asDirection(),
                    windGust = day.windGust.inRange(0.0, 600.0),
                    precipProbability = day.precipProbability?.takeIf { it in 0..100 },
                    precipMm = day.precipMm.inRange(0.0, 5_000.0),
                    humidity = day.humidity.inRange(0.0, 100.0),
                    cloudCover = day.cloudCover.inRange(0.0, 100.0),
                    uvIndex = day.uvIndex?.takeIf { it in 0..50 },
                )
            }
            .toList()
        val minutes = data.rainMinutes.asSequence()
            .filter { it.timeMillis > 0L && it.precip.isFinite() && it.precip in 0f..1_000f }
            .distinctBy(MinutePrecip::timeMillis)
            .sortedBy(MinutePrecip::timeMillis)
            .toList()
        val aqi = data.aqi?.let { air ->
            air.copy(
                value = air.value?.takeIf { it in 0..1_000 },
                level = air.level.cleanText(),
                standard = air.standard.cleanText(),
                primary = air.primary.cleanText(),
                pm25 = air.pm25.cleanMeasurement(),
                pm10 = air.pm10.cleanMeasurement(),
                o3 = air.o3.cleanMeasurement(),
                no2 = air.no2.cleanMeasurement(),
                so2 = air.so2.cleanMeasurement(),
                co = air.co.cleanMeasurement(),
                suggest = air.suggest.cleanText(),
            )
        }
        val validTimeRange = 946_684_800_000L..(nowMillis + 5 * 60_000L)
        return data.copy(
            current = current,
            hourly = hourly,
            daily = daily,
            rainMinutes = minutes,
            rainMeta = data.rainMeta?.takeIf {
                minutes.isNotEmpty() && it.intervalMinutes in 1..180 && it.horizonMinutes in 1..1_440
            },
            rainDistanceKm = data.rainDistanceKm.inRange(0.0, 20_000.0),
            aqi = aqi,
            updateTime = data.updateTime?.takeIf { it in validTimeRange },
            utcOffsetSeconds = data.utcOffsetSeconds?.takeIf { it in -18 * 3_600..18 * 3_600 },
        )
    }

    // 与逐时 UI 共用同一格「现在」：优先包含当前时刻的小时格（10:50 属于 10:00），
    // 找不到时才看 10 分钟内即将开始的下一整点，或已被裁掉的过去 40 分钟格。
    fun currentHourIndex(
        hourly: List<HourlyWeather>,
        nowMillis: Long,
    ): Int {
        if (hourly.isEmpty()) return -1
        val containing = hourly.indexOfFirst { h ->
            h.timeMillis <= nowMillis && nowMillis < h.timeMillis + 3_600_000L
        }
        if (containing >= 0) return containing
        val upcoming = hourly.indexOfFirst { h ->
            val delta = h.timeMillis - nowMillis
            delta in 0..NOW_HOUR_FUTURE_MS
        }
        if (upcoming >= 0) return upcoming
        return hourly.indices
            .filter { hourly[it].timeMillis < nowMillis && nowMillis - hourly[it].timeMillis <= NOW_HOUR_PAST_MS }
            .minByOrNull { nowMillis - hourly[it].timeMillis }
            ?: -1
    }

    fun upcomingHourStartIndex(hourly: List<HourlyWeather>, nowMillis: Long): Int {
        val current = currentHourIndex(hourly, nowMillis)
        return if (current >= 0) current + 1 else 0
    }

    internal fun ensureCurrentHour(data: WeatherData, nowMillis: Long): WeatherData {
        val cur = data.current ?: return data
        if (currentHourIndex(data.hourly, nowMillis) >= 0) return data
        val nowHour = HourlyWeather(
            timeMillis = nowMillis,
            temperature = cur.temperature,
            condition = cur.condition,
            windSpeed = cur.windSpeed,
            profile = cur.profile,
        )
        return data.copy(hourly = listOf(nowHour) + data.hourly)
    }

    internal fun syncCurrentWithNowcast(data: WeatherData, nowMillis: Long): WeatherData {
        val cur = data.current ?: return data
        val seriesWet = Nowcast.seriesWetAt(data.rainMinutes, nowMillis)
        if (cur.condition?.isPrecipitation == true || !seriesWet) return data
        val nearby = data.rainMinutes.filter { abs(it.timeMillis - nowMillis) <= Nowcast.NOW_WINDOW_MS }
        val intensity = nearby.maxOfOrNull { it.precip } ?: 0f
        val phase = nearby.maxByOrNull { it.precip }?.phase ?: cur.profile?.phase ?: PrecipitationPhase.RAIN
        val upgraded = when (phase) {
            PrecipitationPhase.SNOW -> WeatherCondition.SNOW
            PrecipitationPhase.MIXED -> WeatherCondition.SLEET
            PrecipitationPhase.FREEZING_RAIN -> WeatherCondition.FREEZING_RAIN
            PrecipitationPhase.FREEZING_DRIZZLE -> WeatherCondition.FREEZING_DRIZZLE
            PrecipitationPhase.HAIL -> WeatherCondition.HAIL
            else -> if (intensity >= 0.25f) WeatherCondition.RAIN else WeatherCondition.DRIZZLE
        }
        val profile = WeatherProfile(
            condition = upgraded,
            intensity = when {
                intensity >= 7.6f -> WeatherIntensity.HEAVY
                intensity >= 2.5f -> WeatherIntensity.MODERATE
                else -> WeatherIntensity.LIGHT
            },
            phase = phase,
            source = "NOWCAST",
        )
        return data.copy(
            current = cur.copy(
                condition = upgraded,
                weatherText = upgraded.label,
                profile = profile,
                precipMm = cur.precipMm?.takeIf { it > 0.05 } ?: intensity.toDouble(),
            ),
        )
    }

    internal fun overlayCurrentOntoNowHour(data: WeatherData, nowMillis: Long): WeatherData {
        val cur = data.current ?: return data
        val idx = currentHourIndex(data.hourly, nowMillis)
        if (idx < 0) return data
        val hour = data.hourly[idx]
        if (hour.condition == cur.condition && hour.temperature == cur.temperature) return data
        val patched = hour.copy(
            condition = cur.condition ?: hour.condition,
            temperature = cur.temperature ?: hour.temperature,
            windSpeed = cur.windSpeed ?: hour.windSpeed,
            profile = cur.profile ?: hour.profile,
        )
        val hours = data.hourly.toMutableList()
        hours[idx] = patched
        return data.copy(hourly = hours)
    }

    internal fun reconcileNowcastText(data: WeatherData, nowMillis: Long): WeatherData {
        val api = data.rainNowcast?.trim().orEmpty()
        if (api.isEmpty()) return data
        val precipNow = data.current.let { cur ->
            cur != null && (cur.condition?.isPrecipitation == true || (cur.precipMm ?: 0.0) > 0.05)
        }
        val timing = Nowcast.rainTiming(data.rainMinutes, nowMillis, currentPrecip = precipNow)
        if (timing.hasRain && Nowcast.isDryNowcast(api)) {
            return data.copy(rainNowcast = null)
        }
        return data
    }

    private fun Double?.inRange(min: Double, max: Double): Double? =
        this?.takeIf { it.isFinite() && it in min..max }

    private fun Double?.asDirection(): Double? = this
        ?.takeIf(Double::isFinite)
        ?.let { ((it % 360.0) + 360.0) % 360.0 }

    private fun String?.cleanText(): String? = this?.trim()?.takeIf(String::isNotEmpty)

    private fun String?.cleanMeasurement(): String? {
        val raw = this?.trim() ?: return null
        val value = raw.toDoubleOrNull() ?: return null
        return raw.takeIf { value.isFinite() && value >= 0.0 }
    }
}
