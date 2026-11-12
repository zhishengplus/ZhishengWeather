package com.zhisheng.weather.ui.components

import com.zhisheng.weather.R
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.conditionIconRes

/** Reuse the canonical condition mapping. */
internal fun vistaConditionIconRes(condition: WeatherCondition?, light: Boolean): Int? = when (conditionIconRes(condition)) {
    R.drawable.weather_sun -> if (light) R.drawable.vista_day_sun else R.drawable.vista_night_sun
    R.drawable.weather_moon -> if (light) R.drawable.vista_day_moon else R.drawable.vista_night_moon
    R.drawable.weather_cloud_sun -> if (light) R.drawable.vista_day_cloud_sun else R.drawable.vista_night_cloud_sun
    R.drawable.weather_cloud_moon -> if (light) R.drawable.vista_day_cloud_moon else R.drawable.vista_night_cloud_moon
    R.drawable.weather_cloud -> if (light) R.drawable.vista_day_cloud else R.drawable.vista_night_cloud
    R.drawable.weather_clouds -> if (light) R.drawable.vista_day_clouds else R.drawable.vista_night_clouds
    R.drawable.weather_rain -> if (light) R.drawable.vista_day_rain else R.drawable.vista_night_rain
    R.drawable.weather_drizzle -> if (light) R.drawable.vista_day_drizzle else R.drawable.vista_night_drizzle
    R.drawable.weather_bolt -> if (light) R.drawable.vista_day_bolt else R.drawable.vista_night_bolt
    R.drawable.weather_sleet -> if (light) R.drawable.vista_day_sleet else R.drawable.vista_night_sleet
    R.drawable.weather_snow -> if (light) R.drawable.vista_day_snow else R.drawable.vista_night_snow
    R.drawable.weather_fog -> if (light) R.drawable.vista_day_fog else R.drawable.vista_night_fog
    R.drawable.weather_haze -> if (light) R.drawable.vista_day_haze else R.drawable.vista_night_haze
    R.drawable.weather_sand -> if (light) R.drawable.vista_day_sand else R.drawable.vista_night_sand
    R.drawable.weather_wind -> if (light) R.drawable.vista_day_wind else R.drawable.vista_night_wind
    else -> null
}

/**
 * 经典主题使用 Meteocons Fill 静态图标，区别于默认无界图标。
 * 原始 SVG、生成脚本和 MIT 许可见 artwork/ 与 scripts/build_classic_meteocons.cjs。
 */
internal fun classicConditionIconRes(condition: WeatherCondition?): Int? = when (conditionIconRes(condition)) {
    R.drawable.weather_sun -> R.drawable.classic_sun
    R.drawable.weather_moon -> R.drawable.classic_moon
    R.drawable.weather_cloud_sun -> R.drawable.classic_cloud_sun
    R.drawable.weather_cloud_moon -> R.drawable.classic_cloud_moon
    R.drawable.weather_cloud -> R.drawable.classic_cloud
    R.drawable.weather_clouds -> R.drawable.classic_clouds
    R.drawable.weather_rain -> R.drawable.classic_rain
    R.drawable.weather_drizzle -> R.drawable.classic_drizzle
    R.drawable.weather_bolt -> R.drawable.classic_bolt
    R.drawable.weather_sleet -> R.drawable.classic_sleet
    R.drawable.weather_snow -> R.drawable.classic_snow
    R.drawable.weather_fog -> R.drawable.classic_fog
    R.drawable.weather_haze -> R.drawable.classic_haze
    R.drawable.weather_sand -> R.drawable.classic_sand
    R.drawable.weather_wind -> R.drawable.classic_wind
    else -> null
}
