package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class MojiConnectionRegressionTest {
    private val city = City("北京", "北京市", 39.9042, 116.4074, "test")
    private val creds = MojiRuntimeCreds("synthetic-appcode",
        "https://aliv8.data.moji.com/whapi/json/aliweather/condition", "synthetic-token")
    // Shape and field types from the one authorized real response; no credentials.
    private val condition = """{"code":0,"msg":"success","rc":{"c":0,"p":"success"},"data":{
        "city":{"cityId":284609,"counname":"中国","ianatimezone":"Asia/Shanghai","name":"东城区","timezone":"8"},
        "condition":{"condition":"阴","temp":"10","realFeel":"6","humidity":"79","pressure":"1019","updatetime":"2026-10-20 11:00:08"}}}"""

    @Test fun legacyInputCompletesOneCurrentRequestWithoutRequiringDailySubscription() = runBlocking {
        val calls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            assertEquals("7b975b4aa6684075a85d760b6c1c99cf-cn-beijing.alicloudapi.com", chain.request().url.host)
            assertEquals("https", chain.request().url.scheme)
            assertEquals("POST", chain.request().method)
            assertEquals("APPCODE synthetic-appcode", chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("synthetic").body(condition.toResponseBody()).build()
        }.build()
        val data = DomesticWeatherSource.moji(city, creds, client = client)
        assertNull(data.error)
        assertEquals(10.0, data.current!!.temperature!!, 0.0)
        assertEquals(6.0, data.current!!.feelsLike!!, 0.0)
        assertEquals("MOJI", data.dataSource)
        assertEquals("东城区", data.locationMatch!!.matchedRegionName)
        assertTrue(data.daily.isEmpty())
        assertEquals(1, calls.get())
        assertEquals(creds.copy(currentEndpoint = mojiRequest(city, creds)!!.url.toString()), normalizedMojiCredentials(creds))
    }

    @Test fun certificateFailuresAreExplainedWithoutLeakingExceptionOrCredentialText() = runBlocking {
        for (exception in listOf<IOException>(SSLPeerUnverifiedException("synthetic-appcode synthetic-token"),
            SSLHandshakeException("synthetic-appcode synthetic-token"))) {
            val client = OkHttpClient.Builder().addInterceptor { throw exception }.build()
            val data = DomesticWeatherSource.moji(city, creds, client = client)
            assertNull(data.current)
            assertTrue(data.error.orEmpty().contains("证书"))
            assertFalse(data.error.orEmpty().contains("synthetic"))
            assertFalse(data.error.orEmpty().contains("超时"))
        }
    }

    @Test fun successfulHttpWithInvalidBodyIsNotMisreportedAsBadCredentials() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("synthetic").body("<html>gateway</html>".toResponseBody()).build()
        }.build()
        val data = DomesticWeatherSource.moji(city, creds, client = client)
        assertTrue(data.error.orEmpty().contains("无法解析"))
        assertFalse(data.error.orEmpty().contains("AppCode"))
        assertNull(data.current)
    }
}
