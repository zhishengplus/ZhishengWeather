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
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.coroutines.resume

// 定位（v0.0.2）——严格可选：
// · 权限由 UI 主动申请；用户启用定位后，前台自动复核及当前位置刷新可请求位置
// · 只用系统 LocationManager / Geocoder，不引入 Google Play 服务
// · 默认只申请 COARSE；用户主动开启街道级定位时才同时请求 FINE
// · 不申请后台位置；精确权限或街道反查不可用时自动降级到城市级
object LocationSource {

    const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION
    const val PRECISE_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION

    enum class StreetStatus {
        NOT_REQUESTED,
        RESOLVED,
        APPROXIMATE_PERMISSION,
        INSUFFICIENT_ACCURACY,
        UNAVAILABLE,
    }

    sealed interface Result {
        data class Ok(val city: City, val streetStatus: StreetStatus, val accuracyMeters: Float?, val fixAgeSeconds: Long) : Result
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
    // 时长口径：定位最多 13s + 城市反查 8s + 街道反查总预算 8s；
    // 并对新鲜定位做精度过滤（COARSE 网络定位在城市边界可能反查出隔壁城市）。
    suspend fun locate(context: Context): Result {
        if (!hasPermission(context)) return Result.Failed("未授予位置权限")
        if (!locationEnabledOnDevice(context)) return Result.Failed("系统定位服务未开启")

        val preciseRequested = SettingsRepository.preciseLocationEnabled.first()
        val preciseGranted = preciseRequested && hasPrecisePermission(context)
        val loc = withTimeoutOrNull(13_000L) { currentLocation(context, preferGps = preciseGranted) }
            ?: return Result.Failed("定位超时，请到空旷处重试或手动搜索城市")
        val reverse = withTimeoutOrNull(8_000L) { reverseGeocode(context, loc.latitude, loc.longitude) }
            ?: ReverseCityResult(coordinateLocation(loc.latitude, loc.longitude))
        val city = reverse.city

        val canResolveStreet = preciseGranted && LocationFixPolicy.canResolveStreet(loc.fix())
        // 街道反查有自己的超时，不会拖垮已经成功的城市定位。
        val street = if (canResolveStreet) {
            try { withTimeoutOrNull(8_000L) { reverseStreet(context, loc.latitude, loc.longitude, city.name, reverse.baidu) } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { null }
        } else {
            null
        }
        val status = when {
            !preciseRequested -> StreetStatus.NOT_REQUESTED
            !preciseGranted -> StreetStatus.APPROXIMATE_PERMISSION
            !canResolveStreet -> StreetStatus.INSUFFICIENT_ACCURACY
            street != null -> StreetStatus.RESOLVED
            else -> StreetStatus.UNAVAILABLE
        }
        val locatedCity = city.copy(
            latitude = loc.latitude,
            longitude = loc.longitude,
            street = street,
            // 精确坐标必须拥有自己的地址身份，否则同一城市的多个街道会被城市 ID 覆盖。
            locationKey = if (preciseGranted) preciseLocationKey(loc.latitude, loc.longitude) else city.locationKey,
            weatherLocationKey = if (preciseGranted) city.locationKey else city.weatherLocationKey,
        )
        return Result.Ok(locatedCity, status, loc.accuracy.takeIf { loc.hasAccuracy() },
            (LocationFixPolicy.ageMillis(loc.fix(), System.currentTimeMillis(), android.os.SystemClock.elapsedRealtimeNanos()) ?: 0L) / 1_000)
    }

    @Suppress("MissingPermission")
    private suspend fun currentLocation(context: Context, preferGps: Boolean): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        // 手动/自动复核都优先请求新位置；缓存只在新位置暂时不可得时兜底，避免换城市后仍停在旧定位。
        val builtInPriority = if (preferGps) {
            listOf(FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        } else {
            listOf(LocationManager.NETWORK_PROVIDER, FUSED_PROVIDER, LocationManager.GPS_PROVIDER)
        }
        // 部分国产 ROM 只把可工作的融合定位注册成自有 provider 名称。
        // 保留标准 provider 的优先级，同时纳入所有已启用、非 passive 的厂商 provider。
        val vendorProviders = runCatching { lm.allProviders }.getOrDefault(emptyList())
            .filterNot { it == LocationManager.PASSIVE_PROVIDER || it in builtInPriority }
        val providers = (builtInPriority + vendorProviders)
            .distinct()
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val cachedLocations = providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
        val cachedFix = LocationFixPolicy.cachedFix(cachedLocations.map { it.fix() }, System.currentTimeMillis(), android.os.SystemClock.elapsedRealtimeNanos())
        val cached = cachedLocations.firstOrNull { it.fix() == cachedFix }
        if (providers.isEmpty()) return null

        // 单次定位请求（回调在主线程 Looper 上注册）
        var bestReceived: Location? = null
        val fresh = withTimeoutOrNull(12_000L) {
            suspendCancellableCoroutine { cont ->
                val listener = object : android.location.LocationListener {
                    private var done = false
                    override fun onLocationChanged(location: Location) {
                        if (done) return
                        if (!LocationFixPolicy.acceptsCallback(location.fix(), System.currentTimeMillis(), android.os.SystemClock.elapsedRealtimeNanos())) return
                        val previous = bestReceived
                        if (previous == null || !previous.hasAccuracy() ||
                            (location.hasAccuracy() && location.accuracy < previous.accuracy)
                        ) {
                            bestReceived = location
                        }
                        // 精确模式最多等待到 500m 内；超时后仍可用本轮较好的结果做城市级降级。
                        if (preferGps && !LocationFixPolicy.canResolveStreet(location.fix())) return
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
        return listOfNotNull(fresh, bestReceived, cached).firstOrNull {
            LocationFixPolicy.acceptsCallback(it.fix(), System.currentTimeMillis(), android.os.SystemClock.elapsedRealtimeNanos())
        }
    }

    private fun Location.fix() = LocationFix(latitude, longitude, time, elapsedRealtimeNanos, accuracy.takeIf { hasAccuracy() })

    private const val FUSED_PROVIDER = "fused"

    // 坐标 → 中文城市。小米 geo 接口免 key 且直接给 locationKey + 归属地，优先用；
    // 失败且已开开发者模式时才退和风 GeoAPI。两者都失败则如实报错，不猜城市。
    private data class ReverseCityResult(
        val city: City,
        val baidu: BaiduLookupResult? = null,
    )

    private suspend fun reverseGeocode(context: Context, lat: Double, lon: Double): ReverseCityResult? {
        return supervisorScope {
            val system = async { withTimeoutOrNull(4_000L) { systemReverseCity(context, lat, lon) } }
            val xiaomi = async { withTimeoutOrNull(3_000L) { xiaomiReverse(lat, lon) } }
            val primary = xiaomi.await()
            if (primary != null) {
                system.cancel()
                ReverseCityResult(primary)
            } else {
                val local = system.await()
                if (local != null) ReverseCityResult(local)
                else withTimeoutOrNull(1_500L) { qweatherReverse(lat, lon) }?.let(::ReverseCityResult)
                    ?: withTimeoutOrNull(1_500L) { baiduReverse(lat, lon) }
            }
        }
    }

    private suspend fun baiduReverse(lat: Double, lon: Double): ReverseCityResult? {
        if (!SettingsRepository.baiduUnlocked()) return null
        val result = BaiduApi.reverseStreetFromWgs84(SecretStore.currentBaidu().webServiceAk, lat, lon)
        if (!result.ok) return null
        val city = preciseAddressCity(
            district = result.district,
            city = result.city,
            province = result.province,
            featureName = result.street,
            thoroughfare = null,
            premises = null,
            latitude = lat,
            longitude = lon,
        ) ?: return null
        return ReverseCityResult(city, result)
    }

    private suspend fun systemReverseCity(context: Context, lat: Double, lon: Double): City? {
        if (!Geocoder.isPresent()) return null
        val address = withTimeoutOrNull(4_000L) { safeGeocodeAddress(context, lat, lon) } ?: return null
        val name = listOf(address.subAdminArea, address.locality, address.adminArea)
            .firstOrNull { !it.isNullOrBlank() }?.trim() ?: return null
        val affiliation = listOf(address.adminArea, address.locality)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .filterNot { it == name }
            .distinct()
            .joinToString("·")
        return City(
            name = name,
            affiliation = affiliation,
            latitude = lat,
            longitude = lon,
            locationKey = String.format(Locale.US, "geo-city:%.3f,%.3f", lat, lon),
        )
    }

    private suspend fun reverseStreet(
        context: Context,
        lat: Double,
        lon: Double,
        cityName: String,
        seededBaidu: BaiduLookupResult? = null,
    ): String? {
        // 常驻地点命中（200 米内）：街道/区县/adcode 直接复用，零付费调用。
        // 付费的是高德/百度的逆地理与坐标转换——缓存只省这些，定位坐标仍由调用方实时获取。
        KnownPlaceStore.find(lat, lon)?.let { known ->
            // 部分地点的付费源只能识别区县/adcode，不给街道。这种部分命中也要
            // 阻止重复计费，但仍允许免费的系统 Geocoder 尝试补一次展示文字。
            return known.street ?: systemReverseStreet(context, lat, lon, cityName)
        }
        val amapUnlocked = SettingsRepository.amapUnlocked()
        val baiduUnlocked = SettingsRepository.baiduUnlocked()
        var amap: AmapLookupResult? = null
        var baidu: BaiduLookupResult? = seededBaidu

        if (amapUnlocked && baiduUnlocked) {
            // 双 key：新地点首次反查（缓存未命中）才并行两源，做一次性四级交叉验证
            supervisorScope {
                val amapRequest = async {
                    withTimeoutOrNull(7_000L) {
                        AmapApi.reverseStreetFromWgs84(
                            SecretStore.currentAmap().webServiceKey,
                            lat,
                            lon,
                            cityName,
                        )
                    }
                }
                val baiduRequest = seededBaidu?.let { null } ?: async {
                    withTimeoutOrNull(7_000L) {
                        BaiduApi.reverseStreetFromWgs84(SecretStore.currentBaidu().webServiceAk, lat, lon)
                    }
                }
                amap = amapRequest.await()
                baidu = seededBaidu ?: baiduRequest?.await()
            }
            if (amap?.ok == true || baidu?.ok == true) {
                val level = crossCheckDistricts(amap?.adcode, baidu?.adcode, baidu?.district, cityName)
                android.util.Log.i(
                    "ZhishengWeather",
                    "双源交叉验证 $level：高德码=${amap?.adcode} 百度码=${baidu?.adcode} 百度区县=${baidu?.district}",
                )
                when (level) {
                    GeoMatchLevel.MATCH_HIGH -> {
                        val street = mergeStreetSegments(
                            listOf(amap?.street, baidu?.street),
                            listOf(cityName, baidu?.district),
                        )
                        if (!street.isNullOrBlank()) {
                            upsertKnownPlace(lat, lon, amap, baidu, street)
                            return street
                        }
                    }
                    GeoMatchLevel.MATCH_CITY_GRAIN -> {
                        // 只能确认同城时选择一家较完整的街道，不把可能来自不同区县的两段地址硬拼。
                        val chosen = moreCompleteStreet(amap?.street, baidu?.street)
                        val street = mergeStreetSegments(listOf(chosen), listOf(cityName, baidu?.district))
                        if (!street.isNullOrBlank()) {
                            upsertKnownPlace(lat, lon, amap, baidu, street)
                            return street
                        }
                    }
                    GeoMatchLevel.MISMATCH -> {
                        // 跨城分歧：降城市级展示（不给街道），不写缓存避免固化错误标注
                        return null
                    }
                }
            }
            // 双源都失败或未产出街道 → 落到系统 Geocoder（不再重发已失败的付费请求）
        } else {
            if (amapUnlocked) {
                amap = withTimeoutOrNull(7_000L) {
                    AmapApi.reverseStreetFromWgs84(SecretStore.currentAmap().webServiceKey, lat, lon, cityName)
                }
                if (!amap?.street.isNullOrBlank()) {
                    upsertKnownPlace(lat, lon, amap, null)
                    return amap?.street
                }
            }
            if (baiduUnlocked) {
                baidu = seededBaidu ?: withTimeoutOrNull(7_000L) {
                    BaiduApi.reverseStreetFromWgs84(SecretStore.currentBaidu().webServiceAk, lat, lon)
                }
                if (!baidu?.street.isNullOrBlank()) {
                    upsertKnownPlace(lat, lon, null, baidu)
                    return baidu?.street
                }
            }
        }
        val systemStreet = systemReverseStreet(context, lat, lon, cityName)
        // 系统文字本身不入缓存；但高德/百度任一成功即记录该坐标已查，
        // 即使没有街道也不让下次定位重复计费。
        if (amap?.ok == true || baidu?.ok == true) {
            upsertKnownPlace(lat, lon, amap, baidu)
        }
        return systemStreet
    }

    // 有街道时缓存展示文字；只有区县/adcode 时也记录“该坐标已查”，
    // 下次只走免费系统 Geocoder，不重复消耗用户的付费额度。缓存写失败不影响定位主流程。
    private suspend fun upsertKnownPlace(
        lat: Double,
        lon: Double,
        amap: AmapLookupResult?,
        baidu: BaiduLookupResult?,
        streetOverride: String? = null,
    ) {
        val street = streetOverride
            ?: amap?.street?.takeIf(String::isNotBlank)
            ?: baidu?.street?.takeIf(String::isNotBlank)
        if (street == null && amap?.adcode.isNullOrBlank() && baidu?.adcode.isNullOrBlank() && baidu?.district.isNullOrBlank()) {
            return
        }
        runCatching {
            KnownPlaceStore.upsert(
                KnownPlace(
                    latGrid = lat,
                    lonGrid = lon,
                    street = street,
                    district = baidu?.district,
                    amapAdcode = amap?.adcode,
                    baiduAdcode = baidu?.adcode,
                ),
            )
        }.onFailure { android.util.Log.w("ZhishengWeather", "常驻地点缓存写入失败", it) }
    }

    private suspend fun systemReverseStreet(
        context: Context,
        lat: Double,
        lon: Double,
        cityName: String,
    ): String? {
        if (!Geocoder.isPresent()) return null
        val address = withTimeoutOrNull(4_000L) {
            safeGeocodeAddress(context, lat, lon)
        } ?: return null
        return streetLabel(
            subLocality = address.subLocality,
            thoroughfare = address.thoroughfare,
            locality = address.locality,
            subAdminArea = address.subAdminArea,
            cityName = cityName,
        )
    }

    private suspend fun safeGeocodeAddress(context: Context, lat: Double, lon: Double): Address? =
        try { geocodeAddress(context, lat, lon) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { null }

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
            geocoderCall {
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
                .cityLookup("${QWeatherApi.lat(lon)},${QWeatherApi.lat(lat)}")
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

/** 约 100 米量级的稳定地址键，抑制室内 GPS 漂移产生重复地址。 */
internal fun preciseLocationKey(lat: Double, lon: Double): String =
    String.format(Locale.US, "geo:%.3f,%.3f", lat, lon)

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

/** Coordinates are enough for weather even when every reverse-geocoder is unavailable. */
internal fun coordinateLocation(lat: Double, lon: Double) = City(
    name = "当前位置", affiliation = "地址名称暂不可用", latitude = lat, longitude = lon,
    locationKey = preciseLocationKey(lat, lon),
)
