package com.zhisheng.weather.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class HistoricalDay(
    val date: String,
    val weatherCode: Int? = null,
    val high: Double? = null,
    val low: Double? = null,
    val mean: Double? = null,
    val precipitationMm: Double? = null,
    val windMaxKmh: Double? = null,
    val gustMaxKmh: Double? = null,
    val source: String = "OPEN-METEO",
    val model: String = "BEST-MATCH",
) {
    val localDate: LocalDate get() = LocalDate.parse(date)
    val condition: WeatherCondition get() = wmoToCondition(weatherCode)
    val hasUsableWeather: Boolean
        get() = weatherCode != null || high != null || low != null || mean != null ||
            precipitationMm != null || windMaxKmh != null || gustMaxKmh != null
}

data class HistoricalReview(
    val referenceDate: LocalDate,
    val days: List<HistoricalDay>,
    val requestedCount: Int,
) {
    val missingCount: Int get() = (requestedCount - days.size).coerceAtLeast(0)
    val summary: HistoricalSummary get() = summarizeHistoricalDays(days)
}

data class HistoricalSummary(
    val averageHigh: Double? = null,
    val averageLow: Double? = null,
    val warmest: HistoricalDay? = null,
    val coldest: HistoricalDay? = null,
    val wettest: HistoricalDay? = null,
)

fun historicalTargetDates(referenceDate: LocalDate, count: Int): List<LocalDate> {
    if (count <= 0) return emptyList()
    val result = mutableListOf<LocalDate>()
    var year = referenceDate.year - 1
    while (result.size < count && year >= 1940) {
        runCatching { LocalDate.of(year, referenceDate.monthValue, referenceDate.dayOfMonth) }
            .getOrNull()
            ?.let(result::add)
        year--
    }
    return result
}

fun summarizeHistoricalDays(days: List<HistoricalDay>): HistoricalSummary {
    val highs = days.mapNotNull(HistoricalDay::high)
    val lows = days.mapNotNull(HistoricalDay::low)
    return HistoricalSummary(
        averageHigh = highs.takeIf { it.isNotEmpty() }?.average(),
        averageLow = lows.takeIf { it.isNotEmpty() }?.average(),
        warmest = days.filter { it.high != null }.maxByOrNull { it.high ?: Double.NEGATIVE_INFINITY },
        coldest = days.filter { it.low != null }.minByOrNull { it.low ?: Double.POSITIVE_INFINITY },
        wettest = days.filter { it.precipitationMm != null }
            .maxByOrNull { it.precipitationMm ?: Double.NEGATIVE_INFINITY },
    )
}
