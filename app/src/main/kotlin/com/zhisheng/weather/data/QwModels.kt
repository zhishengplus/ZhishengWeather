package com.zhisheng.weather.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement

// 和风天气新版 API 响应模型（weather/v1、weatheralert/v1、airquality/v1）
// 与旧版 v7 模型（minutely / indices / geo）共存

@Serializable
data class QwVal(val value: Double? = null, val unit: String? = null)

internal fun qwNumberFrom(element: JsonElement?): Double? = when (element) {
    null, JsonNull -> null
    is JsonPrimitive -> element.doubleOrNull ?: element.content.toDoubleOrNull()
    is JsonObject -> qwNumberFrom(element["value"])
    else -> null
}

internal fun qwValFrom(element: JsonElement?): QwVal? = when (element) {
    null, JsonNull -> null
    is JsonPrimitive -> element.doubleOrNull?.let { QwVal(it, null) }
    is JsonObject -> QwVal(
        value = qwNumberFrom(element["value"]),
        unit = (element["unit"] as? JsonPrimitive)?.contentOrNull,
    )
    else -> null
}

internal object QwPrecipSerializer : KSerializer<QwPrecip> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("QwPrecip") {
        element<QwVal?>("amount", isOptional = true)
        element<QwVal?>("intensity", isOptional = true)
        element<Double?>("probability", isOptional = true)
        element<String?>("type", isOptional = true)
    }

    override fun deserialize(decoder: Decoder): QwPrecip {
        val json = decoder as? JsonDecoder ?: return QwPrecip()
        val element = json.decodeJsonElement()
        if (element is JsonNull) return QwPrecip()
        val obj = element as? JsonObject ?: return QwPrecip()
        return QwPrecip(
            amount = qwValFrom(obj["amount"]),
            intensity = qwValFrom(obj["intensity"]),
            probability = qwNumberFrom(obj["probability"]),
            type = (obj["type"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    override fun serialize(encoder: Encoder, value: QwPrecip) {
        val json = encoder as? JsonEncoder ?: return
        json.encodeJsonElement(
            buildJsonObject {
                value.amount?.let { put("amount", json.json.encodeToJsonElement(QwVal.serializer(), it)) }
                value.intensity?.let { put("intensity", json.json.encodeToJsonElement(QwVal.serializer(), it)) }
                value.probability?.let { put("probability", JsonPrimitive(it)) }
                value.type?.let { put("type", JsonPrimitive(it)) }
            },
        )
    }
}

// icon 才带昼夜变体（晴天白天 100 / 夜间 150），code 恒为白天码。
// 只读 code 会让夜里显示太阳（v0.0.2 修复），故两者都收，优先用 icon。
@Serializable
data class QwCondition(
    val text: String? = null,
    val code: String? = null,
    val icon: String? = null,
)

@Serializable
data class QwWindDir(val degree: Double? = null, val compass: String? = null)

@Serializable
data class QwWind(
    val direction: QwWindDir? = null,
    val speed: QwVal? = null,
    val scale: Int? = null,
)

@Serializable(with = QwPrecipSerializer::class)
data class QwPrecip(
    val amount: QwVal? = null,
    // amount 是当前数据时段累计量；intensity 才是实时雨强（通常为 mm/h）。
    val intensity: QwVal? = null,
    // weather/v1 文档是 0..1 小数；实包也可能是百分数、字符串，或 {value, unit}。
    val probability: Double? = null,
    val type: String? = null,
)

@Serializable
data class QwCurrent(
    val condition: QwCondition? = null,
    val temperature: QwVal? = null,
    val feelsLike: QwVal? = null,
    val humidity: Double? = null,
    val wind: QwWind? = null,
    val windGust: QwVal? = null,
    val precipitation: QwPrecip? = null,
    val pressure: QwVal? = null,
    val visibility: QwVal? = null,
    val dewPoint: QwVal? = null,
    val cloudCover: Double? = null,
    val uvIndex: Int? = null,
)

@Serializable
data class QwHour(
    val forecastTime: String? = null,
    val condition: QwCondition? = null,
    val temperature: QwVal? = null,
    val feelsLike: QwVal? = null,
    val humidity: Double? = null,
    val wind: QwWind? = null,
    val windGust: QwVal? = null,
    val precipitation: QwPrecip? = null,
    val pressure: QwVal? = null,
    val visibility: QwVal? = null,
    val dewPoint: QwVal? = null,
    val cloudCover: Double? = null,
    val uvIndex: Int? = null,
)

@Serializable
data class QwHourly(val hours: List<QwHour> = emptyList())

@Serializable
data class QwAstro(
    val sunrise: String? = null,
    val sunset: String? = null,
    val moonrise: String? = null,
    val moonset: String? = null,
    val moonPhase: String? = null,
)

@Serializable
data class QwDayPeriod(
    val condition: QwCondition? = null,
    val wind: QwWind? = null,
    val windGustMax: QwVal? = null,
    val precipitation: QwPrecip? = null,
    val humidity: Double? = null,
    val cloudCover: Double? = null,
)

@Serializable
data class QwDay(
    val forecastStartTime: String? = null,
    val astro: QwAstro? = null,
    val temperatureMax: QwVal? = null,
    val temperatureMin: QwVal? = null,
    val temperatureAvg: QwVal? = null,
    val uvIndexMax: Int? = null,
    val daytime: QwDayPeriod? = null,
    val nighttime: QwDayPeriod? = null,
)

@Serializable
data class QwDaily(val days: List<QwDay> = emptyList())

@Serializable
data class QwEventType(val name: String? = null, val code: String? = null)

@Serializable
data class QwAlert(
    val id: String? = null,
    val expireTime: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val severity: String? = null,
    val issuedTime: String? = null,
    val eventType: QwEventType? = null,
    // 官方预警色（当地习惯）：code 为 blue/yellow/orange/red；当地无颜色习惯时为 null
    val color: QwAlertColor? = null,
)

@Serializable
data class QwAlertColor(val code: String? = null)

@Serializable
data class QwAlerts(val alerts: List<QwAlert> = emptyList())

@Serializable
data class QwPollutantConc(val value: Double? = null, val unit: String? = null)

@Serializable
data class QwPollutant(
    val code: String? = null,
    val concentration: QwPollutantConc? = null,
)

@Serializable
data class QwPrimaryPollutant(val code: String? = null, val name: String? = null)

@Serializable
data class QwAirIndex(
    val code: String? = null,
    // QAQI 使用小数值，不能按 Int 反序列化，否则海外空气质量整路会失败。
    val aqi: Double? = null,
    val aqiDisplay: String? = null,
    val level: String? = null,
    val category: String? = null,
    val primaryPollutant: QwPrimaryPollutant? = null,
)

@Serializable
data class QwAir(
    val indexes: List<QwAirIndex> = emptyList(),
    val pollutants: List<QwPollutant> = emptyList(),
)

// —— 旧版 v7 风格接口 ——

@Serializable
data class QwMinutelyItem(
    val fxTime: String? = null,
    val precip: String? = null,
    val type: String? = null,
)

@Serializable
data class QwMinutely(
    val code: String? = null,
    val updateTime: String? = null,
    val summary: String? = null,
    val minutely: List<QwMinutelyItem> = emptyList(),
)

@Serializable
data class QwIndexItem(
    val type: String? = null,
    val name: String? = null,
    val level: String? = null,
    val category: String? = null,
)

@Serializable
data class QwIndices(
    val code: String? = null,
    val daily: List<QwIndexItem> = emptyList(),
)

@Serializable
data class QwCityLoc(
    val name: String? = null,
    val id: String? = null,
    val lat: String? = null,
    val lon: String? = null,
    val adm1: String? = null,
    val adm2: String? = null,
)

@Serializable
data class QwCityLookup(
    val code: String? = null,
    val location: List<QwCityLoc> = emptyList(),
)
