package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import java.io.IOException

internal enum class PlaceFailure { UNAVAILABLE, TIMEOUT, AUTH, QUOTA, CONFIG_CHANGED }
internal class PlaceServiceException(val failure: PlaceFailure, val retryAfterMillis: Long? = null) :
    IOException("Place service: ${failure.name}")
internal data class PlaceSearchOutcome(val cities: List<City>, val failure: PlaceFailure? = null)

internal fun cityMatchesSearchArea(city: City, areas: List<String>): Boolean {
    fun normalized(value: String): String = value.trim().removeSuffix("特别行政区")
        .removeSuffix("壮族自治区").removeSuffix("回族自治区").removeSuffix("维吾尔自治区")
        .removeSuffix("自治区").removeSuffix("省").removeSuffix("市")
    val parts = (city.affiliation.split('·', ',', '，', ' ') + city.name).map(::normalized)
    return areas.all { area -> normalized(area) in parts }
}

/** One budget for the entire chain. Cancellation is never a reason to try another provider. */
internal suspend fun coordinatePlaceSearch(
    providers: List<suspend () -> List<City>>,
    totalMillis: Long = 8_000,
    providerMillis: Long = 4_000,
    isCurrent: suspend () -> Boolean = { true },
): PlaceSearchOutcome {
    val deadline = System.nanoTime() + totalMillis.coerceIn(1, 30_000) * 1_000_000
    var sawEmpty = false
    var failure = PlaceFailure.UNAVAILABLE
    for (provider in providers) {
        if (!isCurrent()) return PlaceSearchOutcome(emptyList(), PlaceFailure.CONFIG_CHANGED)
        val remaining = (deadline - System.nanoTime()) / 1_000_000
        if (remaining <= 0) { failure = PlaceFailure.TIMEOUT; break }
        try {
            val result = withTimeoutOrNull(minOf(providerMillis.coerceAtLeast(1), remaining)) { provider() }
            if (!isCurrent()) return PlaceSearchOutcome(emptyList(), PlaceFailure.CONFIG_CHANGED)
            if (result == null) failure = PlaceFailure.TIMEOUT
            else {
                val valid = result.filter { validSearchCoordinates(it.latitude, it.longitude) && it.name.isNotBlank() }
                if (valid.isNotEmpty()) return PlaceSearchOutcome(valid)
                sawEmpty = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: PlaceServiceException) {
            failure = error.failure
        } catch (_: Exception) {
            failure = PlaceFailure.UNAVAILABLE
        }
    }
    return PlaceSearchOutcome(emptyList(), if (sawEmpty) null else failure)
}

internal fun mergePlaceSearchResults(local: List<City>, online: List<City>): List<City> {
    val result = mutableListOf<City>()
    fun administrative(city: City) = city.coordinateKind == com.zhisheng.weather.model.CoordinateKind.ADMINISTRATIVE ||
        (!city.isPreciseLocation && city.street.isNullOrBlank())
    fun name(city: City) = city.name.trim().removeSuffix("市")
    fun region(city: City) = city.affiliation.split('·', ',', '，').map(String::trim)
        .filter { it.isNotEmpty() && it != "中国" && it.removeSuffix("市") != name(city) }.joinToString("·")
    for (city in local + online) {
        if (result.any { previous -> previous.locationKey == city.locationKey ||
            (administrative(previous) && administrative(city) && name(previous) == name(city) &&
                (previous.countryCode == null || city.countryCode == null || previous.countryCode == city.countryCode) &&
                region(previous) == region(city) &&
                (city.coordinateKind == com.zhisheng.weather.model.CoordinateKind.ADMINISTRATIVE &&
                    previous.coordinateKind == com.zhisheng.weather.model.CoordinateKind.ADMINISTRATIVE ||
                    (name(city) in setOf("北京", "上海", "天津", "重庆") &&
                        distanceKm(previous.latitude, previous.longitude, city.latitude, city.longitude) <= 100.0) ||
                    distanceKm(previous.latitude, previous.longitude, city.latitude, city.longitude) <= 5.0)) }) continue
        result += city
        if (result.size >= 60) break
    }
    return result
}

internal fun amapFailure(code: String?): PlaceFailure = when (code) {
    "10001", "10002", "10005", "10006", "10007", "10008", "10009", "10012", "10013" -> PlaceFailure.AUTH
    "10003", "10004", "10010", "10014", "10019", "10020", "10021", "10044", "10045" -> PlaceFailure.QUOTA
    else -> PlaceFailure.UNAVAILABLE
}

/** Baidu web-service status codes (status 200 is an invalid AK, not HTTP success). */
internal fun baiduFailure(code: Int?): PlaceFailure = when (code) {
    3, 5, 9, 101, 200, 201, 202, 203, 210, 211, 220, 230, 240, 250, 251, 252 -> PlaceFailure.AUTH
    4, 301, 302, 401 -> PlaceFailure.QUOTA
    else -> PlaceFailure.UNAVAILABLE
}

/** Equality is used only in memory; credentials never appear in toString or telemetry. */
internal data class PlaceCredentials(val amap: String = "", val baidu: String = "", val qweatherEnabled: Boolean = false, val revision: Long = 0,
    val sharedPlace: SharedPlaceConfiguration = SharedPlaceConfiguration()) {
    override fun toString() = "PlaceCredentials(redacted)"
}

/** Unlike the reminder context, this revision is unaffected by moving the location slot. */
internal object PlaceConfigurationRevision {
    private val value = java.util.concurrent.atomic.AtomicLong()
    fun current(): Long = value.get()
    fun changed() { value.incrementAndGet() }
}

internal suspend fun currentPlaceCredentials(): PlaceCredentials {
    repeat(3) {
        val context = reminderDeliveryGate.contextVersion() ?: throw PlaceServiceException(PlaceFailure.CONFIG_CHANGED)
        val revision = PlaceConfigurationRevision.current()
        val enabled = SettingsRepository.developerMode.first()
        val shared = SecretStore.sharedPlaceFlow.first()
        val snapshot = if (!enabled) PlaceCredentials(revision = revision, sharedPlace = shared) else PlaceCredentials(
            SecretStore.currentAmap().webServiceKey,
            SecretStore.currentBaidu().webServiceAk,
            SettingsRepository.qweatherUnlocked(), revision, shared)
        if (reminderDeliveryGate.contextVersion() == context && PlaceConfigurationRevision.current() == revision) return snapshot
    }
    throw PlaceServiceException(PlaceFailure.CONFIG_CHANGED)
}
