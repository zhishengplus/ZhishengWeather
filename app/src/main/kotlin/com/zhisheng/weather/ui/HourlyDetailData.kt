package com.zhisheng.weather.ui

import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.ui.home.HourlyDisplayItem
import com.zhisheng.weather.ui.home.hourlyDisplaySequence
import com.zhisheng.weather.model.cityDate

internal fun hourlyDetailDisplayItems(current: CurrentWeather?, hours: List<HourlyWeather>, now: Long,
    currentAqi: Int?, currentAqiEstimated: Boolean = false): List<HourlyDisplayItem> {
    return hourlyDisplaySequence(current, hours, now, currentAqi, currentAqiEstimated)
}

/** Index runs consumed directly by the chart drawing code. */
internal fun hourlyCurveSegments(times: List<Long>, values: List<Double?>): List<List<Int>> = buildList {
    var run = mutableListOf<Int>()
    times.indices.forEach { i ->
        if (values.getOrNull(i)?.isFinite() != true) {
            if (run.isNotEmpty()) add(run.toList())
            run = mutableListOf()
        } else {
            if (run.isNotEmpty() && times[i] - times[run.last()] !in 1L..90 * 60_000L) {
                add(run.toList())
                run = mutableListOf()
            }
            run.add(i)
        }
    }
    if (run.isNotEmpty()) add(run.toList())
}

internal fun hourlyDetailDays(items: List<HourlyDisplayItem>, offsetSeconds: Int?) =
    items.groupBy { cityDate(it.weather.timeMillis, offsetSeconds).toString() }
