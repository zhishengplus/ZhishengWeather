package com.zhisheng.weather.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.zhisheng.weather.model.City
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
        data class Ok(val city: City, val streetStatus: StreetStatus, val accuracyMeters: Float?, val fixAgeSeconds: Long, val configurationRevision: Long = 0, val usingRecentApproximateFix: Boolean = false) : Result
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

    private val labelCache = PlaceResolutionCache()

    // Native coordinates become usable before the bounded address lookup starts.
    suspend fun locate(context: Context, onCoordinatesReady: suspend (Result.Ok) -> Boolean = { true }): Result {
        if (!hasPermission(context)) { ServiceDiagnostics.noteLocation("permission_denied"); return Result.Failed("未授予位置权限") }
        if (!locationEnabledOnDevice(context)) { ServiceDiagnostics.noteLocation("provider_disabled"); return Result.Failed("系统定位服务未开启") }
        // First-run consent/precision writes can overlap city initialization.
        // Wait for them instead of making the first explicit tap fail immediately.
        val startVersion = reminderDeliveryGate.awaitContextVersion()
        if (!hasPermission(context)) return Result.Failed("未授予位置权限")
        if (!locationEnabledOnDevice(context)) return Result.Failed("系统定位服务未开启")
        val placeRevision = PlaceConfigurationRevision.current()
        val preciseRequested = SettingsRepository.preciseLocationEnabled.first()
        val preciseGranted = preciseRequested && hasPrecisePermission(context)
        val loc = currentLocation(context, preferGps = preciseGranted)
            ?: run { ServiceDiagnostics.noteLocation("timeout"); return Result.Failed("暂未取得位置，请检查系统定位及网络后重试，也可手动选择城市") }
        ServiceDiagnostics.noteLocation("success")
        if (!hasPermission(context)) return Result.Failed("位置权限已关闭，已保留原地点")
        if (reminderDeliveryGate.contextVersion() != startVersion ||
            SettingsRepository.preciseLocationEnabled.first() != preciseRequested ||
            (preciseGranted && !hasPrecisePermission(context))) return Result.Failed("定位设置或权限已变化，请重新定位")
        val granted = preciseGranted && hasPrecisePermission(context)
        val currentFix = LocationFixPolicy.isCurrent(loc, System.currentTimeMillis(), android.os.SystemClock.elapsedRealtimeNanos())
        val canStreet = granted && currentFix && LocationFixPolicy.canResolveStreet(loc)
        val measured = measuredLocationCity(loc, granted)
        fun result(city: City, resolved: Boolean) = Result.Ok(city, when {
            !preciseRequested -> StreetStatus.NOT_REQUESTED
            !granted -> StreetStatus.APPROXIMATE_PERMISSION
            !canStreet -> StreetStatus.INSUFFICIENT_ACCURACY
            resolved -> StreetStatus.RESOLVED
            else -> StreetStatus.UNAVAILABLE
        }, loc.accuracyMeters,
            (LocationFixPolicy.ageMillis(loc, System.currentTimeMillis(), android.os.SystemClock.elapsedRealtimeNanos()) ?: 0) / 1_000,
            configurationRevision = placeRevision, usingRecentApproximateFix = !currentFix && !granted)
        val initial = result(measured, false)
        if (!onCoordinatesReady(initial)) return initial
        val credentials = currentPlaceCredentials()
        val label = resolveLabel(context, measured, canStreet, credentials)
        // A setting or permission change must invalidate a pending enhancement.
        if (!hasPermission(context) || currentPlaceCredentials() != credentials ||
            SettingsRepository.preciseLocationEnabled.first() != preciseRequested ||
            (granted && !hasPrecisePermission(context))) return Result.Failed("定位设置或权限已变化，已保留取得的坐标")
        val stillFine = granted && hasPrecisePermission(context)
        val enriched = label?.let {
            applyResolvedLabel(measured, it, it.street.takeIf { canStreet && stillFine }, it.addressSource ?: "system")
        } ?: measured
        val finalCity = enriched.copy(precisePermissionGranted = stillFine,
            street = enriched.street.takeIf { stillFine })
        return result(finalCity, finalCity.street != null)
    }

    private suspend fun resolveLabel(context: Context, measured: City, streets: Boolean, credentials: PlaceCredentials): City? {
        val lat = measured.latitude
        val lon = measured.longitude
        val scopeSuffix = ":$streets:${credentials.revision}"
        var administrativeLabel: City? = null
        suspend fun cached(scope: String, request: suspend () -> City?): List<City> {
            labelCache.get(scope + scopeSuffix, lat, lon)?.let {
                administrativeLabel = it
                return if (streets && it.street == null) emptyList() else listOf(it)
            }
            return request()?.let { label ->
                val clean = label.copy(street = label.street.takeIf { streets })
                labelCache.put(scope + scopeSuffix, lat, lon, clean)
                administrativeLabel = clean
                if (streets && clean.street == null) emptyList() else listOf(clean)
            }.orEmpty()
        }
        val providers = mutableListOf<suspend () -> List<City>>()
        if (credentials.amap.isNotBlank() && (streets || credentials.sharedPlace.enabled)) providers += {
            cached("amap:" + credentials.amap) {
                val r = AmapApi.reverseStreetFromWgs84(credentials.amap, lat, lon, null)
                if (!r.ok) throw PlaceServiceException(r.failure ?: amapFailure(r.infocode))
                preciseAddressCity(r.district, r.city, r.province, r.street, null, null, lat, lon, source = "amap")
            }
        }
        if (credentials.sharedPlace.enabled) providers += {
            cached("shared") { SharedPlaceApi.reverse(context, measured, streets, credentials.sharedPlace) }
        }
        providers += {
            cached("system") {
                if (!Geocoder.isPresent()) throw PlaceServiceException(PlaceFailure.UNAVAILABLE)
                val address = geocodeAddress(context, lat, lon) ?: return@cached null
                val label = preciseAddressCity(address.subAdminArea, address.locality, address.adminArea,
                    address.featureName, null, null, lat, lon, source = "system") ?: return@cached null
                label.copy(street = streetLabel(address.subLocality, address.thoroughfare,
                    address.locality, address.subAdminArea, label.name))
            }
        }
        if (streets && credentials.baidu.isNotBlank()) providers += {
            cached("baidu:" + credentials.baidu) {
                val r = BaiduApi.reverseStreetFromWgs84(credentials.baidu, lat, lon)
                if (!r.ok) throw PlaceServiceException(PlaceFailure.UNAVAILABLE)
                preciseAddressCity(r.district, r.city, r.province, r.street, null, null, lat, lon, source = "baidu")
            }
        }
        val outcome = coordinatePlaceSearch(providers, isCurrent = { currentPlaceCredentials() == credentials })
        return if (outcome.failure == PlaceFailure.CONFIG_CHANGED) null
            else outcome.cities.firstOrNull() ?: administrativeLabel
    }

    @Suppress("MissingPermission")
    private suspend fun currentLocation(context: Context, preferGps: Boolean): LocationFix? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

        // Use single-fix requests: continuous coarse updates can be throttled for
        // ten minutes, which is incompatible with a short startup timeout.
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
        val cached = (providers + LocationManager.PASSIVE_PROVIDER).distinct().mapNotNull { provider ->
            runCatching { lm.getLastKnownLocation(provider)?.fix() }.getOrNull()
        }
        val executor = ContextCompat.getMainExecutor(context)
        val approximatePermission = !hasPrecisePermission(context)
        // Some OEM fused providers return null without waking their GNSS engine.
        // A second, bounded active request is cancelled with the single-fix race.
        val activeGps = if (!approximatePermission && LocationManager.GPS_PROVIDER in providers)
            listOf(LocationFixRequest { onResult ->
                val listener = object : androidx.core.location.LocationListenerCompat {
                    override fun onLocationChanged(location: Location) {
                        val value = location.fix()
                        if (LocationFixPolicy.acceptsCallback(value, System.currentTimeMillis(),
                                android.os.SystemClock.elapsedRealtimeNanos())) onResult(value)
                    }
                }
                val request = androidx.core.location.LocationRequestCompat.Builder(1_000L)
                    .setQuality(androidx.core.location.LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                    .setDurationMillis(25_000L).build()
                try { LocationManagerCompat.requestLocationUpdates(lm, LocationManager.GPS_PROVIDER, request, executor, listener) }
                catch (error: Exception) { lm.removeUpdates(listener); throw error }
                ({ lm.removeUpdates(listener) })
            }) else emptyList()
        android.util.Log.i("WeatherLocation", "request approximate=$approximatePermission providers=${providers.joinToString(",")}")
        val fix = acquireLocationFix(
            requests = providers.map { provider -> LocationFixRequest { onResult ->
                val cancellation = CancellationSignal()
                try {
                    LocationManagerCompat.getCurrentLocation(lm, provider, cancellation, executor) {
                        onResult(it?.fix())
                    }
                } catch (error: Exception) {
                    cancellation.cancel()
                    throw error
                }
                ({ cancellation.cancel() })
            } } + activeGps,
            cached = cached,
            approximate = approximatePermission,
            preferStreet = preferGps,
            nowMillis = System::currentTimeMillis,
            elapsedNanos = android.os.SystemClock::elapsedRealtimeNanos,
        )
        android.util.Log.i("WeatherLocation", if (fix == null) "no usable fix" else
            "fix provider=${fix.provider} ageMs=${LocationFixPolicy.ageMillis(fix, System.currentTimeMillis(), android.os.SystemClock.elapsedRealtimeNanos())} approximate=$approximatePermission")
        return fix
    }

    private fun Location.fix() = LocationFix(latitude, longitude, time, elapsedRealtimeNanos, accuracy.takeIf { hasAccuracy() }, provider = provider)

    private const val FUSED_PROVIDER = "fused"

    private suspend fun geocodeAddress(context: Context, lat: Double, lon: Double): Address? {
        val geocoder = Geocoder(context.applicationContext, Locale.SIMPLIFIED_CHINESE)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (cont.isActive) cont.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (cont.isActive) cont.resumeWithException(PlaceServiceException(PlaceFailure.UNAVAILABLE))
                    }
                })
            }
        } else {
            geocoderCall(strict = true) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
            }
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
