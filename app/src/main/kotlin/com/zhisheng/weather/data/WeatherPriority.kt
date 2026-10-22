package com.zhisheng.weather.data

import com.zhisheng.weather.model.*

internal fun weatherNeedsSupplement(data: WeatherData): Boolean =
    WeatherModule.entries.any { !modulePresent(data, it) } || data.hourly.isEmpty() || data.daily.isEmpty() ||
        data.current?.let { current -> listOf(current.temperature, current.feelsLike, current.condition,
            current.weatherText, current.humidity, current.windSpeed, current.windDirectionDeg,
            current.pressure, current.uvIndex, current.visibility, current.dewPoint, current.cloudCover,
            current.windGust, current.precipMm).any { it == null } } == true ||
        data.hourly.any { hour -> missingCondition(hour.condition) || hour.temperature == null ||
            hour.feelsLike == null || hour.windSpeed == null || hour.windDirectionDeg == null ||
            hour.windGust == null || hour.precipProb == null || hour.precipMm == null ||
            hour.humidity == null || hour.pressure == null || hour.visibility == null ||
            hour.dewPoint == null || hour.cloudCover == null || hour.uvIndex == null || hour.aqi == null } ||
        data.daily.any { day -> missingCondition(day.condition) || day.high == null || day.low == null ||
            day.average == null || day.windSpeed == null || day.windDirectionDeg == null ||
            day.windGust == null || day.precipProbability == null || day.precipMm == null ||
            day.humidity == null || day.cloudCover == null || day.uvIndex == null || day.aqi == null ||
            day.weatherText.isNullOrBlank() || day.sunrise == null || day.sunset == null ||
            day.moonrise == null || day.moonset == null || day.moonPhase == null }

private fun missingCondition(condition: WeatherCondition?) = condition == null || condition == WeatherCondition.UNKNOWN

private fun fillHour(base: HourlyWeather, extra: HourlyWeather): HourlyWeather {
    val useCondition = missingCondition(base.condition) && !missingCondition(extra.condition)
    return base.copy(
        temperature = base.temperature ?: extra.temperature,
        condition = if (useCondition) extra.condition else base.condition,
        profile = if (useCondition && (base.profile == null || missingCondition(base.profile.condition)))
            extra.profile?.takeIf { it.condition == extra.condition } else base.profile,
        windSpeed = base.windSpeed ?: extra.windSpeed,
        precipProb = base.precipProb ?: extra.precipProb,
        aqi = base.aqi ?: extra.aqi,
        aqiEstimated = if (base.aqi == null && extra.aqi != null) extra.aqiEstimated else base.aqiEstimated,
        feelsLike = base.feelsLike ?: extra.feelsLike,
        windDirectionDeg = base.windDirectionDeg ?: extra.windDirectionDeg,
        windGust = base.windGust ?: extra.windGust,
        precipMm = base.precipMm ?: extra.precipMm,
        humidity = base.humidity ?: extra.humidity,
        pressure = base.pressure ?: extra.pressure,
        visibility = base.visibility ?: extra.visibility,
        dewPoint = base.dewPoint ?: extra.dewPoint,
        cloudCover = base.cloudCover ?: extra.cloudCover,
        uvIndex = base.uvIndex ?: extra.uvIndex,
    )
}

private fun fillDay(base: DailyWeather, extra: DailyWeather): DailyWeather {
    val high = base.high ?: extra.high?.takeIf { base.low == null || it >= base.low }
    val low = base.low ?: extra.low?.takeIf { high == null || it <= high }
    val useCondition = missingCondition(base.condition) && !missingCondition(extra.condition)
    val condition = if (useCondition) extra.condition else base.condition
    return base.copy(
        high = high, low = low,
        condition = condition,
        profile = if (useCondition && (base.profile == null || missingCondition(base.profile.condition)))
            extra.profile?.takeIf { it.condition == extra.condition } else base.profile,
        weatherText = base.weatherText?.takeIf(String::isNotBlank)
            ?: extra.weatherText?.takeIf { missingCondition(base.condition) || condition == extra.condition },
        windSpeed = base.windSpeed ?: extra.windSpeed,
        precipProbability = base.precipProbability ?: extra.precipProbability,
        sunrise = base.sunrise ?: extra.sunrise,
        sunset = base.sunset ?: extra.sunset,
        moonrise = base.moonrise ?: extra.moonrise,
        moonset = base.moonset ?: extra.moonset,
        moonPhase = base.moonPhase ?: extra.moonPhase,
        precipMm = base.precipMm ?: extra.precipMm,
        average = base.average ?: extra.average,
        windDirectionDeg = base.windDirectionDeg ?: extra.windDirectionDeg,
        windGust = base.windGust ?: extra.windGust,
        humidity = base.humidity ?: extra.humidity,
        cloudCover = base.cloudCover ?: extra.cloudCover,
        uvIndex = base.uvIndex ?: extra.uvIndex,
        aqi = base.aqi ?: extra.aqi,
    )
}

private fun combinedSources(vararg sources: String?): String = sources.filterNotNull()
    .flatMap { it.split(" + ") }.map(String::trim).filter(String::isNotEmpty).distinct().joinToString(" + ")

/** Never replaces an existing value, including zero rainfall and confirmed zero alerts. */
internal fun fillMissingWeather(base: WeatherData, donor: WeatherData): WeatherData {
    if (donor.error != null) return base
    var result = base
    WeatherModule.entries.filter { !modulePresent(result, it) && modulePresent(donor, it) }.forEach {
        result = replaceModule(result, donor, it)
        if (it == WeatherModule.CURRENT) result = result.copy(dataSource = donor.dataSource)
    }
    fun evidence(module: WeatherModule) {
        val existing = result.moduleEvidence[module.key]
        val extra = donor.moduleEvidence[module.key]
        val source = combinedSources(existing?.source, extra?.source ?: donor.dataSource)
        result = result.copy(moduleEvidence = result.moduleEvidence + listOfNotNull(
            (existing ?: extra)?.copy(source = source)?.let { module.key to it }).toMap(),
            sourceDiagnostics = result.sourceDiagnostics + (module.key to "缺失字段按优先级由${donor.dataSource ?: source}补充"))
    }
    if (result.hourly.isEmpty() && donor.hourly.isNotEmpty()) {
        result = result.copy(hourly = donor.hourly, utcOffsetSeconds = result.utcOffsetSeconds ?: donor.utcOffsetSeconds,
            blockSources = result.blockSources + ("hourly" to (donor.blockSources["hourly"] ?: donor.dataSource.orEmpty())))
        evidence(WeatherModule.FORECAST)
    }
    if (result.daily.isEmpty() && donor.daily.isNotEmpty()) {
        result = result.copy(daily = donor.daily, utcOffsetSeconds = result.utcOffsetSeconds ?: donor.utcOffsetSeconds,
            blockSources = result.blockSources + ("daily" to (donor.blockSources["daily"] ?: donor.dataSource.orEmpty())))
        evidence(WeatherModule.FORECAST)
    }
    // Preserve the primary horizon/order. Match hours by the city's hour bucket,
    // preferring an exact timestamp; never borrow a neighbouring hour or an
    // ambiguous duplicate. Each provider's own offset identifies its local day.
    val offset = result.utcOffsetSeconds ?: donor.utcOffsetSeconds
    val hours = donor.hourly.groupBy { FusionEngine.hourBucket(it.timeMillis, offset) }
    val hourly = result.hourly.map { hour ->
        val candidates = hours[FusionEngine.hourBucket(hour.timeMillis, offset)].orEmpty()
        val extra = candidates.singleOrNull { it.timeMillis == hour.timeMillis } ?: candidates.singleOrNull()
        if (extra == null) hour else fillHour(hour, extra)
    }
    val days = donor.daily.groupBy { cityDate(it.dateMillis, donor.utcOffsetSeconds ?: offset) }
    val daily = result.daily.map { day ->
        val extra = days[cityDate(day.dateMillis, offset)]?.singleOrNull()
        if (extra == null) day else fillDay(day, extra)
    }
    if (hourly != result.hourly || daily != result.daily) {
        val sources = result.blockSources.toMutableMap()
        if (hourly != result.hourly) sources["hourly-supplement"] =
            combinedSources(sources["hourly-supplement"], donor.blockSources["hourly"] ?: donor.dataSource)
        if (daily != result.daily) sources["daily-supplement"] =
            combinedSources(sources["daily-supplement"], donor.blockSources["daily"] ?: donor.dataSource)
        result = result.copy(hourly = hourly, daily = daily, blockSources = sources, utcOffsetSeconds = offset)
        evidence(WeatherModule.FORECAST)
    }
    val current = result.current
    val extra = donor.current
    if (current != null && extra != null) {
        val filled = current.copy(
            temperature = current.temperature ?: extra.temperature,
            feelsLike = current.feelsLike ?: extra.feelsLike,
            condition = current.condition ?: extra.condition,
            weatherText = current.weatherText ?: extra.weatherText,
            humidity = current.humidity ?: extra.humidity,
            windSpeed = current.windSpeed ?: extra.windSpeed,
            windDirectionDeg = current.windDirectionDeg ?: extra.windDirectionDeg,
            pressure = current.pressure ?: extra.pressure,
            uvIndex = current.uvIndex ?: extra.uvIndex,
            visibility = current.visibility ?: extra.visibility,
            dewPoint = current.dewPoint ?: extra.dewPoint,
            cloudCover = current.cloudCover ?: extra.cloudCover,
            windGust = current.windGust ?: extra.windGust,
            precipMm = current.precipMm ?: extra.precipMm,
        )
        if (filled != current) {
            result = result.copy(current = filled,
                blockSources = result.blockSources + ("current-supplement" to
                    combinedSources(result.blockSources["current-supplement"], donor.dataSource ?: "未知")))
            evidence(WeatherModule.CURRENT)
        }
    }
    return result
}
