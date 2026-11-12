package com.zhisheng.weather.data

import com.zhisheng.weather.model.*

/** Acceptance checks, not an accuracy ranking. Never use download time as observation time. */
internal object WeatherQuality {
    const val CURRENT_MAX_AGE_MS = 3 * 60 * 60_000L
    private const val FUTURE_TOLERANCE_MS = 5 * 60_000L

    fun usableCurrent(data: WeatherData): Boolean = data.error == null && data.current?.temperature != null

    fun prepare(data: WeatherData, now: Long = System.currentTimeMillis()): WeatherData {
        val diagnostics = data.sourceDiagnostics.toMutableMap()
        diagnostics.remove("current-time")
        diagnostics.remove("pressure-reference")
        var result = WeatherConsistency.align(data, now)
        val currentTime = data.moduleEvidence["current"]?.observationTime ?: data.updateTime
        val badCurrentTime = currentTime != null &&
            (currentTime <= 0 || currentTime > now + FUTURE_TOLERANCE_MS || now - currentTime > CURRENT_MAX_AGE_MS)
        if (badCurrentTime) {
            result = result.copy(current = null, updateTime = null,
                blockSources = result.blockSources - setOf("current", "current-supplement"),
                moduleEvidence = result.moduleEvidence - "current")
            diagnostics["current"] = "实况时间过期或异常，未作为当前天气使用"
        } else if (result.current != null && result.current?.temperature == null) {
            result = result.copy(current = null, updateTime = null,
                blockSources = result.blockSources - setOf("current", "current-supplement"),
                moduleEvidence = result.moduleEvidence - "current")
            diagnostics["current"] = "实况缺少有效温度，未作为当前天气使用"
        } else if (result.current != null && currentTime == null) {
            diagnostics["current-time"] = "来源未提供观测时间；获取时间不代表观测时间"
        }
        val today = cityDate(now, result.utcOffsetSeconds)
        val hourly = result.hourly.filter { it.timeMillis <= now + 16 * 24 * 60 * 60_000L &&
            (it.temperature != null || it.precipMm != null || it.windSpeed != null ||
                it.condition?.let { c -> c != WeatherCondition.UNKNOWN } == true) }
        val daily = result.daily.filter { cityDate(it.dateMillis, result.utcOffsetSeconds) in today..today.plusDays(16) &&
            (it.high != null || it.low != null || it.average != null || it.precipMm != null ||
                it.condition?.let { c -> c != WeatherCondition.UNKNOWN } == true) }
        val minutes = result.rainMinutes.filter { it.timeMillis >= now - 15 * 60_000L && it.timeMillis <= now + 3 * 60 * 60_000L }
        val rainTime = result.rainMeta?.updateTime
        val rainExpired = rainTime != null && (rainTime > now + FUTURE_TOLERANCE_MS || now - rainTime > 90 * 60_000L)
        val airTime = data.moduleEvidence["air"]?.observationTime ?: currentTime
        val airExpired = airTime != null && (airTime > now + FUTURE_TOLERANCE_MS || now - airTime > CURRENT_MAX_AGE_MS)
        val keptMinutes = if (rainExpired) emptyList() else minutes
        result = result.copy(hourly = hourly, daily = daily,
            rainMinutes = keptMinutes,
            rainNowcast = result.rainNowcast.takeIf { keptMinutes.isNotEmpty() },
            rainMeta = result.rainMeta.takeIf { keptMinutes.isNotEmpty() },
            rainDistanceKm = result.rainDistanceKm.takeIf { keptMinutes.isNotEmpty() },
            aqi = result.aqi.takeUnless { airExpired },
            alerts = result.alerts.filter { it.expiresAt == null || it.expiresAt > now },
            sourceDiagnostics = diagnostics)
        // Remove stale evidence as well as values, so missing modules can actually be supplemented.
        val absent = buildSet {
            if (hourly.isEmpty()) add("hourly")
            if (daily.isEmpty()) add("daily")
            if (keptMinutes.isEmpty()) add("minutely")
            if (result.aqi == null) { add("air"); add("aqi") }
        }
        result = result.copy(blockSources = result.blockSources - absent,
            moduleEvidence = result.moduleEvidence - absent -
                (if (hourly.isEmpty() && daily.isEmpty()) setOf("forecast") else emptySet()))
        diagnostics["coverage"] = "当前取得 ${hourly.size} 个预报时段、${daily.size} 天日预报；未提供的项目保持缺失"
        val currentSource = result.blockSources["current"] ?: result.moduleEvidence["current"]?.source?.substringBefore(" + ") ?: result.dataSource
        if (result.current != null && currentSource == "OPEN-METEO") diagnostics["current-time"] =
            "当前值来自天气模型，数据时间为模型有效时间，不代表现场测站观测"
        if (result.current?.pressure != null) when (currentSource) {
            "QWEATHER" -> diagnostics["pressure-reference"] = "和风返回海平面气压，与地面或站点气压口径不同"
            "OPEN-METEO" -> diagnostics["pressure-reference"] = "气压为模型地面气压，与海平面气压口径不同"
        }
        result = result.copy(sourceDiagnostics = diagnostics)
        val hasData = result.current != null || hourly.isNotEmpty() || daily.isNotEmpty() ||
            keptMinutes.isNotEmpty() || result.alerts.isNotEmpty() || result.aqi != null || result.extraIndices.isNotEmpty()
        return result.copy(error = if (hasData) data.error else data.error ?: "来源未返回有效且及时的天气，请换用其他来源")
    }

    /** Keep one provider's current values intact. Fill only missing forecast fields/other modules. */
    fun combine(candidates: List<WeatherData>, city: City, branded: Boolean, now: Long): WeatherData {
        val usable = candidates.map { stampModules(prepare(it, now), city) }.filter { it.error == null }
        val primary = usable.firstOrNull { usableCurrent(it) && it.updateTime != null }
            ?: usable.firstOrNull(::usableCurrent)
            ?: return WeatherData(error = "各来源暂未返回有效实况，请稍后重试或检查网络")
        var result = primary
        usable.filter { it !== primary }.forEach { donor ->
            result = fillMissingWeather(result, donor, allowCurrentFields = false)
        }
        val used = result.blockSources.values.flatMap { it.split(" + ") }.filter(String::isNotBlank).distinct()
        return result.copy(dataSource = if (branded) "ZHISHENG" else primary.dataSource,
            fusionSources = if (branded) used else emptyList(),
            sourceDiagnostics = result.sourceDiagnostics + ("selection" to
                "自动选源保留主源当前值，其他来源只补缺；各模块实际来源见下方说明"))
    }
}
