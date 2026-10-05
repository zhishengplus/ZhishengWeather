package com.zhisheng.weather.data

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// 小米天气 API（免 key、国内直连）
interface XiaomiApi {

    @GET("location/city/search")
    suspend fun searchCity(
        @Query("name") name: String,
        @Query("locale") locale: String = "zh_CN",
    ): List<XiaomiLocationResult>

    // 按坐标反查城市（定位用，免 key）：返回 name/affiliation/locationKey
    @GET("location/city/geo")
    suspend fun geoCity(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("locale") locale: String = "zh_CN",
        @Query("appKey") appKey: String = APP_KEY,
        @Query("sign") sign: String = SIGN,
    ): List<XiaomiLocationResult>

    @GET("weather/all")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("isLocated") isLocated: Boolean = false,
        @Query("locationKey") locationKey: String,
        @Query("days") days: Int = 7,
        @Query("appKey") appKey: String = APP_KEY,
        @Query("sign") sign: String = SIGN,
        @Query("isGlobal") isGlobal: Boolean = false,
        @Query("locale") locale: String = "zh_CN",
    ): XiaomiForecastResult

    companion object {
        private const val BASE_URL = "https://weatherapi.market.xiaomi.com/wtr-v3/"
        const val APP_KEY = "weather20151024"
        const val SIGN = "zUFJoAR2ZVrDy1vF3D07"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        private val okHttp = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

        val instance: XiaomiApi by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttp)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(XiaomiApi::class.java)
        }
    }
}
