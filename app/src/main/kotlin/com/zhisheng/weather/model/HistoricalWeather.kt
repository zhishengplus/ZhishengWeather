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
    val hasComparableTemperature: Boolean get() = high != null || low != null || mean != null
}

/**
 * 历史接口是模型/再分析数据，也必须走与实时天气同等级的数据闸门。异常日期、
 * 非有限值、倒置高低温或越界量不能进入多年平均，否则一个坏点就会污染整段结论。
 */
fun HistoricalDay.normalized(expectedDate: LocalDate? = null): HistoricalDay? {
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
    if (expectedDate != null && parsedDate != expectedDate) return null
    fun Double?.within(min: Double, max: Double): Double? =
        this?.takeIf { it.isFinite() && it in min..max }
    val a = high.within(-110.0, 70.0)
    val b = low.within(-110.0, 70.0)
    val clean = copy(
        date = parsedDate.toString(),
        high = if (a != null && b != null) maxOf(a, b) else a,
        low = if (a != null && b != null) minOf(a, b) else b,
        mean = mean.within(-110.0, 70.0),
        precipitationMm = precipitationMm.within(0.0, 5_000.0),
        windMaxKmh = windMaxKmh.within(0.0, 500.0),
        gustMaxKmh = gustMaxKmh.within(0.0, 600.0),
    )
    return clean.takeIf(HistoricalDay::hasComparableTemperature)
}

fun normalizeRecentWeatherDays(days: List<HistoricalDay>, endDate: LocalDate): List<HistoricalDay> {
    val startDate = endDate.minusDays(6)
    return days.asSequence()
        .mapNotNull { it.normalized() }
        .filter { it.localDate in startDate..endDate }
        .distinctBy(HistoricalDay::date)
        .sortedBy(HistoricalDay::date)
        .toList()
}

data class HistoricalReview(
    val referenceDate: LocalDate,
    val days: List<HistoricalDay>,
    val requestedCount: Int,
) {
    val missingCount: Int get() = (requestedCount - days.size).coerceAtLeast(0)
    val summary: HistoricalSummary get() = summarizeHistoricalDays(days)
    val requestedYears: List<Int> get() = historicalTargetDates(referenceDate, requestedCount).map(LocalDate::getYear)
    val requestedYearRange: String
        get() = requestedYears.let { years ->
            when (years.size) {
                0 -> "--"
                1 -> years.first().toString()
                else -> "${years.last()}—${years.first()}"
            }
        }
    val includedYears: List<Int> get() = days.map { it.localDate.year }.distinct().sorted()
    val includedYearsText: String get() = includedYears.joinToString(" · ")
}

data class RecentWeatherWeek(
    val days: List<HistoricalDay>,
) {
    val startDate: LocalDate? get() = days.minByOrNull(HistoricalDay::date)?.localDate
    val endDate: LocalDate? get() = days.maxByOrNull(HistoricalDay::date)?.localDate
    val summary: HistoricalSummary get() = summarizeHistoricalDays(days)
    val totalPrecipitationMm: Double?
        get() = days.mapNotNull(HistoricalDay::precipitationMm).takeIf(List<Double>::isNotEmpty)?.sum()
}

data class HistoricalSummary(
    val averageHigh: Double? = null,
    val averageLow: Double? = null,
    val averageMean: Double? = null,
    val averagePrecipitationMm: Double? = null,
    val rainyYears: Int = 0,
    val precipitationSampleCount: Int = 0,
    val warmest: HistoricalDay? = null,
    val coldest: HistoricalDay? = null,
    val wettest: HistoricalDay? = null,
)

enum class HistoricalTemperatureBand(val label: String) {
    MUCH_WARMER("明显偏暖"),
    WARMER("略偏暖"),
    TYPICAL("接近往年"),
    COOLER("略偏凉"),
    MUCH_COOLER("明显偏凉"),
}

data class HistoricalComparison(
    val highDelta: Double?,
    val lowDelta: Double?,
    val representativeDelta: Double,
    val band: HistoricalTemperatureBand,
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
    val means = days.mapNotNull(HistoricalDay::mean)
    val precipitation = days.mapNotNull(HistoricalDay::precipitationMm)
    return HistoricalSummary(
        averageHigh = highs.takeIf { it.isNotEmpty() }?.average(),
        averageLow = lows.takeIf { it.isNotEmpty() }?.average(),
        averageMean = means.takeIf { it.isNotEmpty() }?.average(),
        averagePrecipitationMm = precipitation.takeIf { it.isNotEmpty() }?.average(),
        rainyYears = precipitation.count { it >= 0.1 },
        precipitationSampleCount = precipitation.size,
        warmest = days.filter { it.high != null }.maxByOrNull { it.high ?: Double.NEGATIVE_INFINITY },
        coldest = days.filter { it.low != null }.minByOrNull { it.low ?: Double.POSITIVE_INFINITY },
        wettest = days.filter { it.precipitationMm != null }
            .maxByOrNull { it.precipitationMm ?: Double.NEGATIVE_INFINITY },
    )
}

fun compareWithHistorical(
    forecastHigh: Double?,
    forecastLow: Double?,
    summary: HistoricalSummary,
): HistoricalComparison? {
    val highDelta = forecastHigh?.let { high -> summary.averageHigh?.let { high - it } }
    val lowDelta = forecastLow?.let { low -> summary.averageLow?.let { low - it } }
    val representative = listOfNotNull(highDelta, lowDelta).takeIf { it.isNotEmpty() }?.average() ?: return null
    // 只有 5～10 个同日样本时，1℃ 的轻微差异不应被夸大成“偏暖/偏凉”。
    // 高低温异常的平均值代表全天温度区间整体平移，3.5℃ 以上才判为明显异常。
    val band = when {
        representative >= 3.5 -> HistoricalTemperatureBand.MUCH_WARMER
        representative >= 1.5 -> HistoricalTemperatureBand.WARMER
        representative <= -3.5 -> HistoricalTemperatureBand.MUCH_COOLER
        representative <= -1.5 -> HistoricalTemperatureBand.COOLER
        else -> HistoricalTemperatureBand.TYPICAL
    }
    return HistoricalComparison(highDelta, lowDelta, representative, band)
}
