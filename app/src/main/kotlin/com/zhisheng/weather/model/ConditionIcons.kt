package com.zhisheng.weather.model

import com.zhisheng.weather.R

// 天气条件 → 图标资源（单一真源，when 表达式强制穷尽）。应用内与所有小部件共用。
// 图标：Makin-Things weather-icons 的 static/ 静态 SVG（MIT，基于 amCharts 天气图标，CC BY 4.0），
// 经 Chrome 栅格化为 512×512 无损 WebP（res/drawable-nodpi/mk_*.webp）：裁去透明边后按面积统一到
// 约 290px（最长边不超过 380px）居中，保持原配色不做二次着色。
//
// 条件                → Makin-Things 文件
// CLEAR               → clear-day
// CLEAR_NIGHT         → clear-night
// PARTLY_CLOUDY       → cloudy-2-day（云量中等，太阳半露）
// PARTLY_CLOUDY_NIGHT → cloudy-2-night
// CLOUDY / OVERCAST   → cloudy
// DRIZZLE             → rainy-1（单雨滴）
// RAIN                → rainy-2
// THUNDERSTORM        → thunderstorms
// HAIL                → hail
// FREEZING_RAIN       → rain-and-sleet-mix
// FREEZING_DRIZZLE    → rain-and-sleet-mix
// SNOW                → snowy-2
// SLEET（雨夹雪）      → rain-and-snow-mix
// FOG                 → fog
// HAZE                → haze
// SAND                → dust
// WIND                → wind
// UNKNOWN / null      → 无图标（显示「暂无数据」标记 / 小部件 ph_cloud 占位）
fun conditionIconRes(condition: WeatherCondition?): Int? = when (condition) {
    WeatherCondition.UNKNOWN -> null
    WeatherCondition.CLEAR -> R.drawable.mk_clear_day
    WeatherCondition.CLEAR_NIGHT -> R.drawable.mk_clear_night
    WeatherCondition.PARTLY_CLOUDY -> R.drawable.mk_cloudy_2_day
    WeatherCondition.PARTLY_CLOUDY_NIGHT -> R.drawable.mk_cloudy_2_night
    WeatherCondition.CLOUDY -> R.drawable.mk_cloudy
    WeatherCondition.OVERCAST -> R.drawable.mk_cloudy
    WeatherCondition.DRIZZLE -> R.drawable.mk_rainy_1
    WeatherCondition.RAIN -> R.drawable.mk_rainy_2
    WeatherCondition.THUNDERSTORM -> R.drawable.mk_thunderstorms
    WeatherCondition.HAIL -> R.drawable.mk_hail
    WeatherCondition.FREEZING_RAIN -> R.drawable.mk_rain_and_sleet_mix
    WeatherCondition.FREEZING_DRIZZLE -> R.drawable.mk_rain_and_sleet_mix
    WeatherCondition.SNOW -> R.drawable.mk_snowy_2
    WeatherCondition.SLEET -> R.drawable.mk_rain_and_snow_mix
    WeatherCondition.FOG -> R.drawable.mk_fog
    WeatherCondition.HAZE -> R.drawable.mk_haze
    WeatherCondition.SAND -> R.drawable.mk_dust
    WeatherCondition.WIND -> R.drawable.mk_wind
    null -> null
}
