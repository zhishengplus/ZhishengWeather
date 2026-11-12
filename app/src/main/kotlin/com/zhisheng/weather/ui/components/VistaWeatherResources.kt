package com.zhisheng.weather.ui.components

import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.conditionIconRes

/** All themes share the Makin-Things static set (ConditionIcons.kt). */
internal fun vistaConditionIconRes(condition: WeatherCondition?, @Suppress("UNUSED_PARAMETER") light: Boolean): Int? {
    return conditionIconRes(condition)
}

internal fun classicConditionIconRes(condition: WeatherCondition?): Int? = conditionIconRes(condition)
