package com.zhisheng.weather.data

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.AtomicFile
import com.zhisheng.weather.BuildConfig
import java.io.File
import java.security.SecureRandom
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Explicit optional diagnostics; separate random identity, no coordinates or persistent hardware IDs. */
internal object ServiceDiagnostics {
    private val mutex = Mutex()
    @Volatile var locationStatus: String = "not_requested"
        private set
    fun noteLocation(status: String) { if (status in statuses) locationStatus = status }
    private val statuses = setOf("not_requested", "success", "timeout", "permission_denied", "provider_disabled", "unavailable")
    private fun file(context: Context) = AtomicFile(File(context.noBackupFilesDir, "optional_service_diagnostics.json"))
    private fun read(context: Context): JsonObject? = runCatching {
        file(context).openRead().bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }
    }.getOrNull()
    private fun save(context: Context, value: JsonObject) {
        val atomic = file(context); val stream = atomic.startWrite()
        try { stream.write(value.toString().toByteArray()); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
    suspend fun report(context: Context): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!SettingsRepository.serviceDiagnostics.first()) return@withLock false
            val process = ActivityManager.RunningAppProcessInfo(); ActivityManager.getMyMemoryState(process)
            if (process.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) return@withLock false
            try {
                val old = read(context)
                val last = old?.get("sentAt")?.jsonPrimitive?.longOrNull ?: 0
                val now = System.currentTimeMillis()
                if (now >= last && now - last < 86_400_000 && old?.get("versionCode")?.jsonPrimitive?.intOrNull == BuildConfig.VERSION_CODE) return@withLock true
                val identity = if (old != null) old else buildJsonObject {
                    put("id", UUID.randomUUID().toString())
                    put("token", ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) })
                }.also { save(context, it) }
                val profile = buildProfile(context)
                if (!SettingsRepository.serviceDiagnostics.first()) return@withLock false
                val body = buildJsonObject { put("protocol", 1); put("consentVersion", 1); put("purpose", "service_diagnostics"); put("profile", profile) }
                val response = radarRequestBytes(SharedPlaceApi.client(context), request(identity).post(body.toString().toRequestBody("application/json".toMediaType())).build(), 2048)
                val result = Json.parseToJsonElement(response.bytes.toString(Charsets.UTF_8)).jsonObject
                if (response.successful && result["protocol"]?.jsonPrimitive?.intOrNull == 1 && result["ok"]?.jsonPrimitive?.booleanOrNull == true && result["retentionDays"]?.jsonPrimitive?.intOrNull == 90) {
                    save(context, buildJsonObject { identity.forEach { (k, v) -> put(k, v) }; put("sentAt", now); put("versionCode", BuildConfig.VERSION_CODE) })
                    true
                } else {
                    android.util.Log.w("ServiceDiagnostics", "report status=${response.code}")
                    false
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.w("ServiceDiagnostics", "report type=${error.javaClass.simpleName}; cause=${error.cause?.javaClass?.simpleName.orEmpty()}")
                false
            }
        }
    }
    suspend fun erase(context: Context) = withContext(Dispatchers.IO) {
        SettingsRepository.setServiceDiagnostics(false)
        mutex.withLock {
            val identity = read(context) ?: return@withLock
            val response = radarRequestBytes(SharedPlaceApi.client(context), request(identity).delete().build(), 2048)
            val root = Json.parseToJsonElement(response.bytes.toString(Charsets.UTF_8)).jsonObject
            check(response.successful && root["deleted"]?.jsonPrimitive?.booleanOrNull == true)
            file(context).delete()
        }
    }
    private fun request(identity: JsonObject) = Request.Builder().url("${SharedPlaceApi.endpoint}/v1/diagnostics")
        .header("X-Place-Installation", identity.getValue("id").jsonPrimitive.content)
        .header("Authorization", "Bearer ${identity.getValue("token").jsonPrimitive.content}")
    private suspend fun buildProfile(context: Context): JsonObject {
        val config = context.resources.configuration
        val source = SettingsRepository.sourcePref.first().name.takeIf { it in sources } ?: "OTHER"
        return buildJsonObject {
            put("manufacturer", Build.MANUFACTURER.take(60).ifBlank { "unknown" }); put("brand", Build.BRAND.take(60).ifBlank { "unknown" })
            put("model", Build.MODEL.take(100).ifBlank { "unknown" }); put("osVersion", Build.VERSION.RELEASE.take(30).ifBlank { "unknown" })
            put("sdk", Build.VERSION.SDK_INT); put("appVersion", BuildConfig.VERSION_NAME); put("versionCode", BuildConfig.VERSION_CODE)
            put("screenWidthDp", config.screenWidthDp); put("screenHeightDp", config.screenHeightDp)
            put("densityDpi", config.densityDpi); put("fontScale", config.fontScale)
            put("locale", Locale.getDefault().toLanguageTag().take(35)); put("timeZone", TimeZone.getDefault().id.take(60))
            put("networkType", networkType(context))
            put("weatherSource", source); put("locationStatus", locationStatus)
            put("locationPermission", when { LocationSource.hasPrecisePermission(context) -> "precise"; LocationSource.hasPermission(context) -> "approximate"; else -> "none" })
        }
    }
    private fun networkType(context: Context): String = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return@runCatching "offline"
        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }.getOrDefault("unknown")
    private val sources = setOf("AUTO", "ZHISHENG", "NMC", "XIAOMI", "QWEATHER", "OPEN_METEO", "CAIYUN", "AMAP", "BAIDU", "TENCENT", "WEATHERCN", "SENIVERSE", "MOJI")
}
