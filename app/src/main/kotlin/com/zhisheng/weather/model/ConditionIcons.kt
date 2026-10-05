package com.zhisheng.weather.model

import com.zhisheng.weather.R

// 天气条件 → 图标资源（单一真源，when 表达式强制穷尽）。
// v0.0.4 合并原双份拷贝：ui/components/WeatherIcon.kt 的 mapOf 与
// Android drawable mapping and Compose weather rendering share the same condition names.
// 新增条件枚举时编译器在此兜底。
fun conditionIconRes(condition: WeatherCondition?): Int? = when (condition) {
    WeatherCondition.UNKNOWN -> null
    WeatherCondition.CLEAR -> R.drawable.weather_sun
    WeatherCondition.CLEAR_NIGHT -> R.drawable.weather_moon
    WeatherCondition.PARTLY_CLOUDY -> R.drawable.weather_cloud_sun
    WeatherCondition.PARTLY_CLOUDY_NIGHT -> R.drawable.weather_cloud_moon
    WeatherCondition.CLOUDY -> R.drawable.weather_cloud
    WeatherCondition.OVERCAST -> R.drawable.weather_clouds
    WeatherCondition.RAIN -> R.drawable.weather_rain
    WeatherCondition.DRIZZLE -> R.drawable.weather_drizzle
    WeatherCondition.THUNDERSTORM -> R.drawable.weather_bolt
    WeatherCondition.HAIL -> R.drawable.weather_sleet
    WeatherCondition.FREEZING_RAIN, WeatherCondition.FREEZING_DRIZZLE -> R.drawable.weather_sleet
    WeatherCondition.SNOW -> R.drawable.weather_snow
    WeatherCondition.SLEET -> R.drawable.weather_sleet
    WeatherCondition.FOG -> R.drawable.weather_fog
    WeatherCondition.HAZE -> R.drawable.weather_haze
    WeatherCondition.SAND -> R.drawable.weather_sand
    WeatherCondition.WIND -> R.drawable.weather_wind
    null -> null
}
