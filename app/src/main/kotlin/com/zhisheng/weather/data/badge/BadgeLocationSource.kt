package com.zhisheng.weather.data.badge

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.zhisheng.weather.data.LocationSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** One bounded foreground fix. Never requests a permission or uses a selected weather city. */
@SuppressLint("MissingPermission")
internal suspend fun currentBadgeLocation(context: Context): BadgeLocation? {
    if (!LocationSource.hasPermission(context) || !LocationSource.locationEnabledOnDevice(context)) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    fun usable(fix: Location): BadgeLocation? {
        val age = (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000L
        if (age !in 0..120_000 || !fix.latitude.isFinite() || !fix.longitude.isFinite() ||
            fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0) return null
        return BadgeLocation(fix.latitude, fix.longitude,
            fix.accuracy.takeIf { fix.hasAccuracy() && it.isFinite() && it >= 0f }, System.currentTimeMillis() - age)
    }
    return try {
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
        providers.mapNotNull { manager.getLastKnownLocation(it) }.sortedByDescending { it.elapsedRealtimeNanos }
            .firstNotNullOfOrNull(::usable)?.let { return it }
        val provider = providers.firstOrNull() ?: return null
        withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { continuation ->
                val cancellation = CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                LocationManagerCompat.getCurrentLocation(manager, provider, cancellation, ContextCompat.getMainExecutor(context)) { fix ->
                    if (continuation.isActive) continuation.resume(fix?.let(::usable))
                }
            }
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }
}
