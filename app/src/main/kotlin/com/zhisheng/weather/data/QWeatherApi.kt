package com.zhisheng.weather.data

import java.util.Locale
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// 和风天气 API（新版 v1 + 旧版 v7 混合，JWT Bearer 认证）
interface QWeatherService {

    @GET("weather/v1/current/{lat}/{lon}")
    suspend fun current(
        @Path("lat") lat: String,
        @Path("lon") lon: String,
        @Query("localTime") localTime: Boolean = true,
        @Query("lang") lang: String = "zh",
    ): QwCurrent

    @GET("weather/v1/hourly/{lat}/{lon}")
    suspend fun hourly(
        @Path("lat") lat: String,
        @Path("lon") lon: String,
        @Query("hours") hours: Int,
        @Query("localTime") localTime: Boolean = true,
        @Query("lang") lang: String = "zh",
    ): QwHourly

    @GET("weather/v1/daily/{lat}/{lon}")
    suspend fun daily(
        @Path("lat") lat: String,
        @Path("lon") lon: String,
        @Query("days") days: Int,
        @Query("localTime") localTime: Boolean = true,
        @Query("lang") lang: String = "zh",
    ): QwDaily

    @GET("weatheralert/v1/current/{lat}/{lon}")
    suspend fun alerts(
        @Path("lat") lat: String,
        @Path("lon") lon: String,
        @Query("localTime") localTime: Boolean = true,
        @Query("lang") lang: String = "zh",
    ): QwAlerts

    @GET("airquality/v1/current/{lat}/{lon}")
    suspend fun air(
        @Path("lat") lat: String,
        @Path("lon") lon: String,
        @Query("lang") lang: String = "zh",
    ): QwAir

    @GET("v7/minutely/5m")
    suspend fun minutely(@Query("location") location: String): QwMinutely

    @GET("v7/indices/1d")
    suspend fun indices(
        @Query("location") location: String,
        @Query("type") type: String,
    ): QwIndices

    @GET("geo/v2/city/lookup")
    suspend fun cityLookup(
        @Query("location") query: String,
        @Query("number") number: Int = 10,
    ): QwCityLookup
}

object QWeatherApi {

    val enabled: Boolean
        get() = SecretStore.resolvedQw().ready

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Volatile private var cachedHost: String? = null
    @Volatile private var cachedService: QWeatherService? = null

    private val authInterceptor = Interceptor { chain ->
        val req = chain.request()
        val creds = SecretStore.resolvedQw()
        val authed = when {
            creds.jwtReady -> {
                val token = QwAuth.token()
                if (token != null) req.newBuilder().header("Authorization", "Bearer $token").build() else req
            }
            creds.keyReady -> req.newBuilder().header("X-QW-Api-Key", creds.apiKey).build()
            else -> req
        }
        var resp = chain.proceed(authed)
        if (resp.code == 401 && creds.jwtReady) {
            resp.close()
            QwAuth.invalidate()
            val t2 = QwAuth.token()
            resp = if (t2 != null) {
                chain.proceed(req.newBuilder().header("Authorization", "Bearer $t2").build())
            } else {
                chain.proceed(req)
            }
        }
        resp
    }

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    fun invalidateClient() {
        cachedHost = null
        cachedService = null
    }

    val service: QWeatherService
        get() {
            val host = SecretStore.resolvedQw().host.trimEnd('/').ifBlank { "https://n1.qweatherapi.com" }
            val existing = cachedService
            if (existing != null && cachedHost == host) return existing
            synchronized(this) {
                val again = cachedService
                if (again != null && cachedHost == host) return again
                val created = Retrofit.Builder()
                    .baseUrl(host.trimEnd('/') + "/")
                    .client(okHttp)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(QWeatherService::class.java)
                cachedHost = host
                cachedService = created
                return created
            }
        }

    // 和风天气坐标接口按官方约 1 km 网格、最多 2 位小数接收坐标。
    // 输入仍来自 GPS，而不是城市 ID；统一格式化可避免部分接口因超规格小数返回 4xx。
    fun lat(v: Double) = String.format(Locale.US, "%.2f", v)
    fun lonLat(c: com.zhisheng.weather.model.City) =
        "${lat(c.longitude)},${lat(c.latitude)}"
}
