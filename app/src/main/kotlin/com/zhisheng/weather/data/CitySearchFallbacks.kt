package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import kotlinx.coroutines.CancellationException
import java.io.IOException

internal suspend fun searchCityWithFallbacks(providers: List<suspend () -> List<City>>): List<City> {
    var succeeded = false
    for (provider in providers) {
        val cities = try { provider().also { succeeded = true } } catch (e: CancellationException) { throw e }
            catch (_: Exception) { continue }
        if (cities.isNotEmpty()) return cities
    }
    if (!succeeded && providers.isNotEmpty()) throw IOException("City search services unavailable")
    return emptyList()
}
