package com.zhisheng.weather.data

import java.math.BigDecimal
import java.math.RoundingMode

/** HJ 633—2026 table 3, clauses 4.2.2–4.2.6. These inputs remain model estimates,
 * not the monitoring one-hour averages needed for an official real-time AQI report.
 * https://www.mee.gov.cn/ywgz/fgbz/bz/bzwb/jcffbz/202602/W020260225366493492011.pdf */
internal object ChineseAqi2026 {
    private val levels = intArrayOf(0, 50, 100, 150, 200, 300, 400, 500)
    private val breakpoints = linkedMapOf(
        "PM2.5" to doubleArrayOf(0.0, 35.0, 60.0, 115.0, 150.0, 250.0, 350.0, 500.0),
        "PM10" to doubleArrayOf(0.0, 50.0, 120.0, 250.0, 350.0, 420.0, 500.0, 600.0),
        "O₃" to doubleArrayOf(0.0, 160.0, 200.0, 300.0, 400.0, 800.0, 1000.0, 1200.0),
        "NO₂" to doubleArrayOf(0.0, 100.0, 200.0, 700.0, 1200.0, 2340.0, 3090.0, 3840.0),
        "SO₂" to doubleArrayOf(0.0, 150.0, 500.0, 650.0, 800.0),
        "CO" to doubleArrayOf(0.0, 5.0, 10.0, 35.0, 60.0, 90.0, 120.0, 150.0),
    )

    internal data class Estimate(
        val value: Int?,
        val primary: String?,
        val subIndices: Map<String, Int>,
        val missingPollutants: List<String>,
    ) {
        val note: String get() = "HJ 633—2026 分段公式；Open-Meteo 瞬时模型浓度估算，非监测1小时平均" +
            if (missingPollutants.isEmpty()) "" else "；缺少${missingPollutants.joinToString("、")}，不计算综合AQI"
    }

    fun estimate(pm25: Double?, pm10: Double?, o3: Double?, no2: Double?, so2: Double?, co: Double?): Estimate {
        val values = linkedMapOf("PM2.5" to pm25, "PM10" to pm10, "O₃" to o3,
            "NO₂" to no2, "SO₂" to so2, "CO" to co)
        val indices = values.mapNotNull { (name, raw) ->
            subIndex(name, raw)?.let { name to it }
        }.toMap()
        val missing = values.keys.filter { it !in indices }
        // Incomplete data cannot justify a clean-air claim or an unqualified composite value.
        val value = indices.values.maxOrNull()?.takeIf { missing.isEmpty() }?.coerceIn(1, 500)
        val primary = value?.takeIf { it > 50 }?.let { maximum ->
            indices.filterValues { it == maximum }.keys.joinToString("、")
        }
        return Estimate(value, primary, indices, missing)
    }

    fun subIndex(pollutant: String, concentration: Double?): Int? {
        val raw = concentration?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        val points = breakpoints[pollutant] ?: return null
        // GB/T 8170: ties to even; gases/particles use integral µg/m³, CO tenths of mg/m³.
        val value = BigDecimal.valueOf(raw).setScale(if (pollutant == "CO") 1 else 0,
            RoundingMode.HALF_EVEN)
        // Table 3 note b: one-hour SO₂ above 800 µg/m³ uses IAQI 200.
        if (pollutant == "SO₂" && value > BigDecimal.valueOf(800L)) return 200
        if (value >= BigDecimal.valueOf(points.last())) return levels[points.lastIndex]
        for (index in 0 until points.lastIndex) {
            val low = BigDecimal.valueOf(points[index])
            val high = BigDecimal.valueOf(points[index + 1])
            if (value <= high) {
                // Divide the exact decimal numerator straight to an integer. A Double
                // intermediary can turn IAQI 7 into 7.000000000000001 and wrongly ceil to 8.
                val numerator = value.subtract(low).multiply(BigDecimal.valueOf(
                    (levels[index + 1] - levels[index]).toLong()))
                return levels[index] + numerator.divide(high.subtract(low), 0, RoundingMode.CEILING).intValueExact()
            }
        }
        return null
    }
}
