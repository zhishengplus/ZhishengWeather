package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import kotlinx.serialization.json.*
import java.time.ZoneOffset

internal data class WeatherCnForecast(
    val daily: List<DailyWeather> = emptyList(),
    val hourly: List<HourlyWeather> = emptyList(),
    val publishedAt: Long? = null,
)

/** Website data, not a promised API. Dates come from the page's publication stamp, never today. */
internal fun parseWeatherCnForecast(html: String, areaId: String, now: Long): WeatherCnForecast {
    val nav = Regex("""<ul\b[^>]*id=["']someDayNav["'][^>]*>(.*?)</ul>""", RegexOption.DOT_MATCHES_ALL)
        .find(html)?.groupValues?.get(1).orEmpty()
    if (!nav.contains("/weather/$areaId.shtml")) return WeatherCnForecast()
    fun value(id: String): String? = Regex("""<input\b[^>]*>""").findAll(html)
        .firstOrNull { Regex("""\bid=["']${Regex.escape(id)}["']""").containsMatchIn(it.value) }
        ?.value?.let { Regex("""\bvalue=["']([^"']*)["']""").find(it)?.groupValues?.get(1) }
    val published = wcnPublishedTime(value("fc_24h_internal_update_time"), now) ?: return WeatherCnForecast()
    val firstDay = cityDate(published, 28800)
    val block = Regex("""<ul\b[^>]*class=["']t clearfix["'][^>]*>(.*?)</ul>""", RegexOption.DOT_MATCHES_ALL)
        .find(html)?.groupValues?.get(1).orEmpty()
    fun temperature(raw: String?): Double? = raw?.replace("℃", "")?.trim()?.toDoubleOrNull()?.takeIf { it in -90.0..60.0 }
    val days = Regex("""<li\b[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL).findAll(block)
        .take(7).mapIndexedNotNull { index, row ->
            val date = firstDay.plusDays(index.toLong())
            val text = row.groupValues[1]
            val dayNumber = Regex("""<h1>\s*(\d{1,2})日""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            if (dayNumber != date.dayOfMonth) return@mapIndexedNotNull null
            val temps = Regex("""<p\b[^>]*class=["']tem["'][^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
                .find(text)?.groupValues?.get(1).orEmpty()
            val high = temperature(Regex("""<span>(.*?)</span>""").find(temps)?.groupValues?.get(1))
            val low = temperature(Regex("""<i>(.*?)</i>""").find(temps)?.groupValues?.get(1))
            if (high == null && low == null) return@mapIndexedNotNull null
            if (high != null && low != null && high < low) return@mapIndexedNotNull null
            val codes = Regex("""png40\s+[dn](\d+)""").findAll(text)
                .map { WeatherCondition.fromCode(it.groupValues[1].trimStart('0').ifEmpty { "0" }) }.toList()
            val condition = codes.reduceOrNull { a, b -> WeatherCondition.moreSignificant(a, b) } ?: WeatherCondition.UNKNOWN
            val weatherText = Regex("""<p\b[^>]*class=["']wea["'][^>]*>([^<]*)</p>""").find(text)?.groupValues?.get(1)
            DailyWeather(date.atStartOfDay(ZoneOffset.ofHours(8)).toInstant().toEpochMilli(),
                high = high, low = low, condition = condition, weatherText = weatherText)
        }.toList()
    val hourPublished = wcnPublishedTime(value("fc_3h_internal_update_time"), now)
    val hourDay = hourPublished?.let { cityDate(it, 28800) }
    fun strings(element: JsonElement): List<String> = when (element) {
        is JsonObject -> element.values.flatMap(::strings)
        is JsonArray -> element.flatMap(::strings)
        is JsonPrimitive -> listOfNotNull(element.contentOrNull)
    }
    val hours = if (hourDay == null) emptyList() else WeatherComCnApi.extractVar(html, "hour3data")
        ?.let(::strings).orEmpty().mapNotNull { row ->
            val fields = row.split(',')
            val stamp = Regex("""^(\d{1,2})日(\d{1,2})时$""").find(fields.firstOrNull().orEmpty()) ?: return@mapNotNull null
            val day = (0L..8L).map(hourDay::plusDays).singleOrNull { it.dayOfMonth == stamp.groupValues[1].toInt() }
                ?: return@mapNotNull null
            val hour = stamp.groupValues[2].toInt().takeIf { it in 0..23 } ?: return@mapNotNull null
            val temp = temperature(fields.getOrNull(3)) ?: return@mapNotNull null
            val code = WeatherComCnApi.normalizeCode(fields.getOrNull(1))
            HourlyWeather(day.atTime(hour, 0).toInstant(ZoneOffset.ofHours(8)).toEpochMilli(), temperature = temp,
                condition = WeatherCondition.fromCode(code), windDirectionDeg = NmcSource.windDirToDegree(fields.getOrNull(4)))
        }.distinctBy { it.timeMillis }.sortedBy { it.timeMillis }
    return WeatherCnForecast(days, hours, published)
}
