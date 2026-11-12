package com.zhisheng.weather.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal const val MAX_HISTORY_RANGE_DAYS = 366
internal val FIRST_HISTORY_DATE: LocalDate = LocalDate.of(1940, 1, 1)

internal fun historyDateRangeError(start: LocalDate, end: LocalDate, today: LocalDate): String? = when {
    start < FIRST_HISTORY_DATE -> "可查询1940年起的历史天气"
    start >= today || end >= today -> "请选择今天以前的完整日期"
    end < start -> "结束日期不能早于开始日期"
    ChronoUnit.DAYS.between(start, end) + 1 > MAX_HISTORY_RANGE_DAYS -> "每次最多查询366天，请缩短日期段"
    else -> null
}

internal data class HistoricalDateRange(val start: LocalDate, val end: LocalDate, val days: List<HistoricalDay>) {
    val dates: List<LocalDate> get() = List((ChronoUnit.DAYS.between(start, end) + 1).toInt()) { start.plusDays(it.toLong()) }
    val missingCount: Int get() = dates.size - days.size
}

internal fun normalizedHistoryRange(start: LocalDate, end: LocalDate, days: List<HistoricalDay>): HistoricalDateRange =
    HistoricalDateRange(start, end, days.mapNotNull { it.normalized() }.filter { it.localDate in start..end }
        .distinctBy { it.date }.sortedBy { it.date })
