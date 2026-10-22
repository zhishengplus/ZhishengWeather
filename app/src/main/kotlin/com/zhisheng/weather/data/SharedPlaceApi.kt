package com.zhisheng.weather.data

import android.content.Context
import com.zhisheng.weather.R
import com.zhisheng.weather.model.City
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Only this client trusts the location-service CA. Default IP/hostname verification is retained. */
internal object SharedPlaceApi {
    const val endpoint = "https://115.159.43.52"
    @Volatile private var instance: OkHttpClient? = null
    private val json = Json { ignoreUnknownKeys = true }

    internal fun client(context: Context): OkHttpClient = instance ?: synchronized(this) {
        instance ?: run {
            val certificate = context.resources.openRawResource(R.raw.place_service_ca).use {
                CertificateFactory.getInstance("X.509").generateCertificate(it)
            }
            val anchors = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("place-service", certificate) }
            val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(anchors) }
                .trustManagers.filterIsInstance<X509TrustManager>().single()
            val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
            OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, trust)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .connectTimeout(2, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).callTimeout(3500, TimeUnit.MILLISECONDS)
                .build().also { instance = it }
        }
    }
    suspend fun ready(context: Context): Boolean {
        val response = radarRequestBytes(client(context), Request.Builder().url("$endpoint/health").build(), 1024)
        val root = json.parseToJsonElement(response.bytes.toString(Charsets.UTF_8)).jsonObject
        return response.successful && root["protocol"]?.jsonPrimitive?.intOrNull == 1 && root["ready"]?.jsonPrimitive?.booleanOrNull == true
    }
    suspend fun eraseHistory(context: Context, config: SharedPlaceConfiguration) {
        require(config.installationId.isNotBlank() && config.installationToken.isNotBlank())
        val response = radarRequestBytes(client(context), request("/v1/history", config).delete().build(), 1024)
        val root = json.parseToJsonElement(response.bytes.toString(Charsets.UTF_8)).jsonObject
        if (!response.successful || root["deleted"]?.jsonPrimitive?.booleanOrNull != true) throw PlaceServiceException(PlaceFailure.UNAVAILABLE)
    }
    suspend fun reverse(context: Context, measured: City, streets: Boolean, config: SharedPlaceConfiguration): City? {
        if (!config.enabled || SecretStore.sharedPlaceFlow.first() != config) throw PlaceServiceException(PlaceFailure.CONFIG_CHANGED)
        val process = android.app.ActivityManager.RunningAppProcessInfo()
        android.app.ActivityManager.getMyMemoryState(process)
        if (process.importance != android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
            !LocationSource.hasPermission(context)) return null
        PlaceServiceGate.check(endpoint, "shared:reverse")
        val accuracy = measured.locationAccuracyMeters ?: return null
        val sampled = measured.locationSampleTimeMillis ?: return null
        val body = buildJsonObject {
            put("protocol", 1); put("consentVersion", 2); put("purpose", "weather_location")
            put("coordinateSystem", "WGS84"); put("latitude", measured.latitude); put("longitude", measured.longitude)
            put("accuracyMeters", accuracy); put("sampledAtMillis", sampled); put("street", streets)
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val response = radarRequestBytes(client(context), request("/v1/reverse", config).post(body).build(), 8192)
        if (!response.successful) {
            val failure = if (response.code == 429) PlaceFailure.QUOTA else PlaceFailure.UNAVAILABLE
            PlaceServiceGate.record(endpoint, "shared:reverse", failure, placeRetryAfterMillis(response.retryAfter))
            throw PlaceServiceException(failure)
        }
        if (SecretStore.sharedPlaceFlow.first() != config) throw PlaceServiceException(PlaceFailure.CONFIG_CHANGED)
        PlaceServiceGate.clear(endpoint, "shared:reverse")
        return parseSharedPlaceResponse(response.bytes.toString(Charsets.UTF_8), measured.latitude, measured.longitude, streets)
    }
    private fun request(path: String, config: SharedPlaceConfiguration) = Request.Builder().url(endpoint + path)
        .header("X-Place-Installation", config.installationId).header("Authorization", "Bearer ${config.installationToken}")
}

internal fun parseSharedPlaceResponse(raw: String, latitude: Double, longitude: Double, streets: Boolean): City? {
    val root = Json.parseToJsonElement(raw).jsonObject
    if (root["protocol"]?.jsonPrimitive?.intOrNull != 1 || root["retentionDays"]?.jsonPrimitive?.intOrNull != 365 ||
        root["source"]?.jsonPrimitive?.contentOrNull != "amap") throw PlaceServiceException(PlaceFailure.UNAVAILABLE)
    val address = root["address"]?.jsonObject ?: return null
    fun field(key: String) = address[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() && it.length <= 120 }
    return preciseAddressCity(field("district"), field("city"), field("province"), field("street").takeIf { streets },
        null, null, latitude, longitude, source = "zhisheng-amap")
}
