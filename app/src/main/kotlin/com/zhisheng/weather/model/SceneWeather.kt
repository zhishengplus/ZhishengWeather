package com.zhisheng.weather.model

/** 独立于普通天气自动优选的场景预报；任何字段缺失都不影响主页天气。 */
data class SceneWeatherData(
    val sky: SkyPhotographyForecast? = null,
    val coast: CoastalForecast? = null,
    val fetchedAtMillis: Long = System.currentTimeMillis(),
    val stale: Boolean = false,
    val skyError: String? = null,
    val coastError: String? = null,
)

data class SkyPhotographyForecast(
    val glow: GlowForecast?,
    val stars: StarForecast?,
    val source: String = "Open-Meteo",
    val updatedAtMillis: Long,
    val zoneId: String = "UTC",
    val stale: Boolean = false,
    val unavailableReason: String? = null,
)

enum class GlowKind(val label: String) { DAWN("朝霞"), DUSK("晚霞") }

data class GlowForecast(
    val kind: GlowKind,
    val grade: String,
    val startMillis: Long,
    val endMillis: Long,
    val reasons: List<String>,
    val quality: String = "本地点云层推算，未检查太阳方向远处遮挡",
)

data class StarForecast(
    val score: Int,
    val grade: String,
    val startMillis: Long,
    val endMillis: Long,
    val reasons: List<String>,
    val recommendation: String,
    val moonIllumination: Double = 0.0,
)

data class CoastalForecast(
    val trend: TideTrend,
    val points: List<MarinePoint>,
    val nextHigh: TideTurningPoint?,
    val nextLow: TideTurningPoint?,
    val waveHeightM: Double?,
    val waveDirectionDeg: Double?,
    val wavePeriodSeconds: Double?,
    val swellHeightM: Double?,
    val swellPeriodSeconds: Double?,
    val seaTemperatureC: Double?,
    val currentVelocityKmh: Double?,
    val currentDirectionDeg: Double?,
    val gridDistanceKm: Double,
    val source: String = "Open-Meteo Marine",
    val updatedAtMillis: Long,
    val zoneId: String = "UTC",
    val stale: Boolean = false,
    val sampleTimeMillis: Long = updatedAtMillis,
    val windWaveHeightM: Double? = null,
    val windWavePeriodSeconds: Double? = null,
    val windWaveDirectionDeg: Double? = null,
    val swellDirectionDeg: Double? = null,
)

enum class TideTrend(val label: String) { RISING("潮位上升"), FALLING("潮位下降"), STEADY("潮位变化较小"), UNKNOWN("潮位趋势暂缺") }

data class MarinePoint(val timeMillis: Long, val seaLevelM: Double?)
data class TideTurningPoint(val timeMillis: Long, val heightM: Double)
