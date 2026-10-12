package com.zhisheng.weather.widget

import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.model.City

internal data class WeatherCityIdentity(val key: String, val latitude: Double, val longitude: Double, val providerKey: String?)
internal fun weatherCityIdentity(city: City) = WeatherCityIdentity(city.locationKey, city.latitude, city.longitude, city.weatherLocationKey)
internal data class WidgetWeatherContext(val selected: WeatherCityIdentity?, val cities: List<WeatherCityIdentity>,
    val locatedKey: String?, val source: SourcePref, val temperatureUnit: String)
internal fun widgetWeatherContext(selected: City?, cities: List<City>, locatedKey: String?, source: SourcePref, unit: String) =
    WidgetWeatherContext(selected?.let(::weatherCityIdentity), cities.map(::weatherCityIdentity), locatedKey, source, unit)
