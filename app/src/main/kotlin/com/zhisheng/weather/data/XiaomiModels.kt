package com.zhisheng.weather.data

import kotlinx.serialization.Serializable

// 小米天气 API 响应模型（精简为需要的字段）

@Serializable
data class XiaomiUnitValue(
    val unit: String? = null,
    val value: String? = null,
)

@Serializable
data class XiaomiWind(
    val direction: XiaomiUnitValue? = null,
    val speed: XiaomiUnitValue? = null,
)

@Serializable
data class XiaomiCurrent(
    val feelsLike: XiaomiUnitValue? = null,
    val humidity: XiaomiUnitValue? = null,
    val pressure: XiaomiUnitValue? = null,
    val pubTime: String? = null,
    val temperature: XiaomiUnitValue? = null,
    val uvIndex: String? = null,
    val visibility: XiaomiUnitValue? = null,
    val weather: String? = null,
    val wind: XiaomiWind? = null,
)

@Serializable
data class XiaomiFromTo(
    val from: String? = null,
    val to: String? = null,
)

@Serializable
data class XiaomiDailyTemperature(
    val unit: String? = null,
    val value: List<XiaomiFromTo>? = null,
)

@Serializable
data class XiaomiDailyWeather(
    val value: List<XiaomiFromTo>? = null,
)

@Serializable
data class XiaomiForecastDaily(
    val pubTime: String? = null,
    val aqi: XiaomiIntList? = null,
    val temperature: XiaomiDailyTemperature? = null,
    val weather: XiaomiDailyWeather? = null,
    val sunRiseSet: XiaomiFromToList? = null,
    val precipitationProbability: XiaomiStrList? = null,
    val moonPhase: XiaomiStrList? = null,
    val wind: XiaomiDailyWind? = null,
)

@Serializable
data class XiaomiFromToList(val value: List<XiaomiFromTo>? = null)

@Serializable
data class XiaomiStrList(val value: List<String>? = null)

@Serializable
data class XiaomiDailyWind(
    val direction: XiaomiFromToList? = null,
    val speed: XiaomiFromToList? = null,
)

@Serializable
data class XiaomiHourlyWindValue(
    val datetime: String? = null,
    val direction: String? = null,
    val speed: String? = null,
)

@Serializable
data class XiaomiHourlyWind(val value: List<XiaomiHourlyWindValue>? = null)

@Serializable
data class XiaomiIntList(
    val pubTime: String? = null,
    val value: List<Int>? = null,
)

@Serializable
data class XiaomiForecastHourly(
    val pubTime: String? = null,
    val temperature: XiaomiIntList? = null,
    val weather: XiaomiIntList? = null,
    val wind: XiaomiHourlyWind? = null,
    val aqi: XiaomiIntList? = null,
)

@Serializable
data class XiaomiAqi(
    val aqi: String? = null,
    val pm25: String? = null,
    val pm10: String? = null,
    val o3: String? = null,
    val no2: String? = null,
    val so2: String? = null,
    val co: String? = null,
    val pubTime: String? = null,
    val primary: String? = null,
    val suggest: String? = null,
)

@Serializable
data class XiaomiAlert(
    val title: String? = null,
    val level: String? = null,
    val type: String? = null,
    val detail: String? = null,
    val pubTime: String? = null,
)

@Serializable
data class XiaomiForecastResult(
    val current: XiaomiCurrent? = null,
    val forecastDaily: XiaomiForecastDaily? = null,
    val forecastHourly: XiaomiForecastHourly? = null,
    val aqi: XiaomiAqi? = null,
    val alerts: List<XiaomiAlert>? = null,
    val updateTime: String? = null,
    val minutely: XiaomiMinutely? = null,
    val indices: XiaomiIndices? = null,
    val yesterday: XiaomiYesterday? = null,
    val typhoon: List<XiaomiTyphoon>? = null,
)

@Serializable
data class XiaomiMinutely(
    val precipitation: XiaomiMinutelyPrecip? = null,
    val probability: XiaomiMinutelyProbability? = null,
)

@Serializable
data class XiaomiMinutelyProbability(
    val maxProbability: String? = null,
    val probabilityDesc: String? = null,
    val probabilityDescV2: String? = null,
)

@Serializable
data class XiaomiMinutelyPrecip(
    val description: String? = null,
    val shortDescription: String? = null,
    val headDescription: String? = null,
    // 小米实包是数值数组（常见为 4 个分段概率），不是描述字符串。
    val probability: List<Double>? = null,
    val firstRainOrSnow: Boolean? = null,
    val isRainOrSnow: Int? = null,
    // 实包返回 "1" 这类字符串天气码。
    val weather: String? = null,
    val kmNum: String? = null,
    // 未来约 120 分钟逐分钟强度（0=无雨）。此前只接了 description/kmNum，公共版国内看不到降水柱。
    val value: List<Double>? = null,
    val pubTime: String? = null,
)

@Serializable
data class XiaomiIndices(
    val indices: List<XiaomiIndexItem>? = null,
)

@Serializable
data class XiaomiIndexItem(
    val type: String? = null,
    val value: String? = null,
)

@Serializable
data class XiaomiYesterday(
    val date: String? = null,
    val tempMax: String? = null,
    val tempMin: String? = null,
    val aqi: String? = null,
    val weatherStart: String? = null,
    val weatherEnd: String? = null,
    val sunRise: String? = null,
    val sunSet: String? = null,
    val windDircStart: String? = null,
    val windDircEnd: String? = null,
    val windSpeedStart: String? = null,
    val windSpeedEnd: String? = null,
)

@Serializable
data class XiaomiTyphoon(
    val typhoonCname: String? = null,
    val typhoonEname: String? = null,
    val typhoonCode: String? = null,
    val typhoonType: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val centWindSpeed: Double? = null,
)

@Serializable
data class XiaomiLocationResult(
    val name: String? = null,
    val affiliation: String? = null,
    val latitude: String? = null,
    val longitude: String? = null,
    val locationKey: String? = null,
    val status: Int? = null,
)
