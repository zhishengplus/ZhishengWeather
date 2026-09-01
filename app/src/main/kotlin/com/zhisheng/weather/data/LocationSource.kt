package com.zhisheng.weather.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.zhisheng.weather.model.City
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.coroutines.resume

// 定位（v0.0.2）——严格可选：
// · 权限只在用户主动点「定位当前城市」时申请，App 启动/刷新绝不触碰位置
// · 只用系统 LocationManager / Geocoder，不引入 Google Play 服务
// · 默认只申请 COARSE；用户主动开启街道级定位时才同时请求 FINE
// · 不申请后台位置；精确权限或街道反查不可用时自动降级到城市级
object LocationSource {

    const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION
    const val PRECISE_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION

    // 精度过滤阈值：COARSE 网络定位超过 20km 误差的坐标不用于反查城市
    private const val ACCURACY_MAX_M = 20_000f
    // 超过此误差时不显示街道，以免在街道边界给出过度确定的结果。
    private const val STREET_ACCURACY_MAX_M = 500f

    enum class StreetStatus {
        NOT_REQUESTED,
        RESOLVED,
        APPROXIMATE_PERMISSION,
        UNAVAILABLE,
    }

    sealed interface Result {
        data class Ok(val city: City, val streetStatus: StreetStatus) : Result
        data class Failed(val message: String) : Result
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun hasPrecisePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PRECISE_PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun requestedPermissions(precise: Boolean): Array<String> = if (precise) {
        arrayOf(PERMISSION, PRECISE_PERMISSION)
    } else {
        arrayOf(PERMISSION)
    }

    fun locationEnabledOnDevice(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return try {
            LocationManagerCompatIsEnabled(lm)
        } catch (_: Exception) {
            false
        }
    }

    private fun LocationManagerCompatIsEnabled(lm: LocationManager): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)

    // 定位 + 反查城市。调用前必须已确认权限（由 UI 层申请），此处再兜一次校验。
    // v0.0.4：整体 15s 上限（此前 12s 定位 + 15s 小米 + 12s 和风最坏约 40s），
    // 并对新鲜定位做精度过滤（COARSE 网络定位在城市边界可能反查出隔壁城市）。
    suspend fun locate(context: Context): Result {
        if (!hasPermission(context)) return Result.Failed("未授予位置权限")
        if (!locationEnabledOnDevice(context)) return Result.Failed("系统定位服务未开启")

        val preciseRequested = SettingsRepository.preciseLocationEnabled.first()
        val preciseGranted = preciseRequested && hasPrecisePermission(context)
        val loc = withTimeoutOrNull(13_000L) { currentLocation(context, preferGps = preciseGranted) }
            ?: return Result.Failed("定位超时，请到空旷处重试或手动搜索城市")
        if (loc.hasAccuracy() && loc.accuracy > ACCURACY_MAX_M) {
            return Result.Failed("定位精度不足（${loc.accuracy.toInt()}m），请到空旷处重试或手动搜索")
        }
        val city = withTimeoutOrNull(8_000L) { reverseGeocode(loc.latitude, loc.longitude) }
            ?: return Result.Failed("已取到坐标但未能反查城市名，请手动搜索")

        val canResolveStreet = preciseGranted && (!loc.hasAccuracy() || loc.accuracy <= STREET_ACCURACY_MAX_M)
        // 街道反查有自己的超时，不会拖垮已经成功的城市定位。
        val street = if (canResolveStreet) reverseStreet(context, loc.latitude, loc.longitude, city.name) else null
        val status = when {
            !preciseRequested -> StreetStatus.NOT_REQUESTED
            !preciseGranted -> StreetStatus.APPROXIMATE_PERMISSION
            street != null -> StreetStatus.RESOLVED
            else -> StreetStatus.UNAVAILABLE
        }
        return Result.Ok(city.copy(latitude = loc.latitude, longitude = loc.longitude, street = street), status)
    }

    @Suppress("MissingPermission")
    private suspend fun currentLocation(context: Context, preferGps: Boolean): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        // 手动/自动复核都优先请求新位置；缓存只在新位置暂时不可得时兜底，避免换城市后仍停在旧定位。
        val providerPriority = if (preferGps) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        }
        val providers = providerPriority
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val cached = providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (providers.isEmpty()) return cached?.takeIf { isRecentFallback(it) }

        // 单次定位请求（回调在主线程 Looper 上注册）
        var bestReceived: Location? = null
        val fresh = withTimeoutOrNull(12_000L) {
            suspendCancellableCoroutine { cont ->
                val listener = object : android.location.LocationListener {
                    private var done = false
                    override fun onLocationChanged(location: Location) {
                        if (done) return
                        val previous = bestReceived
                        if (previous == null || !previous.hasAccuracy() ||
                            (location.hasAccuracy() && location.accuracy < previous.accuracy)
                        ) {
                            bestReceived = location
                        }
                        // 精确模式最多等待到 500m 内；超时后仍可用本轮较好的结果做城市级降级。
                        if (preferGps && location.hasAccuracy() && location.accuracy > STREET_ACCURACY_MAX_M) return
                        done = true
                        runCatching { lm.removeUpdates(this) }
                        if (cont.isActive) cont.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                val requested = providers.count { provider ->
                    runCatching {
                        lm.requestLocationUpdates(
                            provider, 0L, 0f, listener,
                            android.os.Looper.getMainLooper(),
                        )
                    }.isSuccess
                }
                if (requested == 0) {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                cont.invokeOnCancellation { runCatching { lm.removeUpdates(listener) } }
            }
        }
        return fresh ?: bestReceived ?: cached?.takeIf { isRecentFallback(it) }
    }

    private fun isRecentFallback(location: Location): Boolean =
        System.currentTimeMillis() - location.time in 0..15 * 60_000L

    // 坐标 → 中文城市。小米 geo 接口免 key 且直接给 locationKey + 归属地，优先用；
    // 失败且已开开发者模式时才退和风 GeoAPI。两者都失败则如实报错，不猜城市。
    private suspend fun reverseGeocode(lat: Double, lon: Double): City? =
        xiaomiReverse(lat, lon) ?: qweatherReverse(lat, lon)

    private suspend fun reverseStreet(context: Context, lat: Double, lon: Double, cityName: String): String? {
        // 开发者自行配置高德 Web 服务 Key 后，优先用国内街道数据增强名称。
        // 高德请求包含官方 GPS 坐标转换；超时、额度或鉴权失败全部退回系统 Geocoder。
        if (SettingsRepository.amapUnlocked()) {
            val key = SecretStore.currentAmap().webServiceKey
            val amapStreet = withTimeoutOrNull(7_000L) {
                AmapApi.reverseStreetFromWgs84(key, lat, lon, cityName).street
            }
            if (!amapStreet.isNullOrBlank()) return amapStreet
        }
        return systemReverseStreet(context, lat, lon, cityName)
    }

    private suspend fun systemReverseStreet(
        context: Context,
        lat: Double,
        lon: Double,
        cityName: String,
    ): String? {
        if (!Geocoder.isPresent()) return null
        val address = withTimeoutOrNull(4_000L) {
            geocodeAddress(context, lat, lon)
        } ?: return null
        return streetLabel(
            subLocality = address.subLocality,
            thoroughfare = address.thoroughfare,
            locality = address.locality,
            subAdminArea = address.subAdminArea,
            cityName = cityName,
        )
    }

    private suspend fun geocodeAddress(context: Context, lat: Double, lon: Double): Address? {
        val geocoder = Geocoder(context.applicationContext, Locale.SIMPLIFIED_CHINESE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (cont.isActive) cont.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
            }
        }
    }

    private suspend fun xiaomiReverse(lat: Double, lon: Double): City? = try {
        XiaomiApi.instance.geoCity(latitude = lat, longitude = lon)
            .firstOrNull { it.status == 0 && !it.locationKey.isNullOrBlank() }
            ?.let { h ->
                City(
                    name = h.name.orEmpty().ifBlank { return null },
                    affiliation = h.affiliation.orEmpty().split(",").map { it.trim() }
                        .filter { it.isNotBlank() && it != "中国" }.reversed().joinToString("·"),
                    latitude = h.latitude?.toDoubleOrNull() ?: lat,
                    longitude = h.longitude?.toDoubleOrNull() ?: lon,
                    locationKey = h.locationKey!!,
                )
            }
    } catch (ce: kotlinx.coroutines.CancellationException) {
        throw ce
    } catch (_: Exception) {
        null
    }

    private suspend fun qweatherReverse(lat: Double, lon: Double): City? {
        if (!SettingsRepository.qweatherUnlocked()) return null
        return try {
            val loc = QWeatherApi.service
                .cityLookup(String.format(java.util.Locale.US, "%.2f,%.2f", lon, lat))
                .location.firstOrNull() ?: return null
            City(
                name = loc.name.orEmpty().ifBlank { return null },
                affiliation = listOfNotNull(loc.adm1, loc.adm2)
                    .filter { it.isNotBlank() }.distinct().joinToString("·"),
                latitude = loc.lat?.toDoubleOrNull() ?: lat,
                longitude = loc.lon?.toDoubleOrNull() ?: lon,
                locationKey = loc.id ?: "$lon,$lat",
            )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
    }

}

internal fun streetLabel(
    subLocality: String?,
    thoroughfare: String?,
    locality: String?,
    subAdminArea: String?,
    cityName: String?,
): String? {
    val administrativeNames = setOf(cityName, locality, subAdminArea)
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .toSet()
    return listOf(subLocality, thoroughfare)
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .filterNot { it in administrativeNames }
        .distinct()
        .take(2)
        .joinToString("·")
        .ifBlank { null }
}
