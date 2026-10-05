package com.zhisheng.weather.ui.home

import com.zhisheng.weather.model.CoastalForecast
import com.zhisheng.weather.model.MarinePoint

/** Keep missing timestamps as gaps; never connect a curve across missing observations. */
internal fun coastalDisplayPoints(points: List<MarinePoint>): List<MarinePoint> = points
    .groupBy { it.timeMillis }.toSortedMap().values.map { sameTime ->
        sameTime.firstOrNull { it.seaLevelM?.isFinite() == true } ?: sameTime.first().copy(seaLevelM = null)
    }

internal fun coastalDisplayFacts(coast: CoastalForecast, unit: String): List<Pair<String, String>> = buildList {
    fun addValue(label: String, value: Double?, suffix: String) {
        value?.takeIf { it.isFinite() && it >= 0 }?.let { add(label to atlasNumber(it, suffix)) }
    }
    addValue("有效浪高", coast.waveHeightM, "m")
    addValue("浪周期", coast.wavePeriodSeconds, "s")
    addValue("风浪高度", coast.windWaveHeightM, "m")
    addValue("风浪周期", coast.windWavePeriodSeconds, "s")
    addValue("涌浪高度", coast.swellHeightM, "m")
    addValue("涌浪周期", coast.swellPeriodSeconds, "s")
    coast.seaTemperatureC?.takeIf(Double::isFinite)?.let { add("海温" to atlasTemperature(it, unit)) }
    addValue("海流速度", coast.currentVelocityKmh?.div(3.6), "m/s")
    addValue("风浪来向", coast.windWaveDirectionDeg, "°")
    addValue("涌浪来向", coast.swellDirectionDeg, "°")
}
