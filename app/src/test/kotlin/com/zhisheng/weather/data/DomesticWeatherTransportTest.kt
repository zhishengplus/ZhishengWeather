package com.zhisheng.weather.data

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DomesticWeatherTransportTest {
    private val request = Request.Builder().url("https://synthetic.example.test/weather").build()
    @Test fun httpFailureAndOversizedBodiesNeverBecomeWeather() = runBlocking {
        for ((code, payload) in listOf(403 to "{\"results\":[{}]}", 200 to "x".repeat(2_000_001), 200 to "ok")) {
            val client = OkHttpClient.Builder().addInterceptor { chain -> Response.Builder().request(chain.request())
                .protocol(Protocol.HTTP_1_1).code(code).message("synthetic").body(payload.toResponseBody()).build() }.build()
            assertEquals(if (code == 200 && payload == "ok") "ok" else null, weatherAwaitText(client, request))
        }
    }
    @Test fun cancellingAWeatherRequestCancelsItsSocketCall() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val active = AtomicReference<Call>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            active.set(chain.call()); started.countDown(); release.await(3, TimeUnit.SECONDS)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                .message("synthetic").body("late weather".toResponseBody()).build()
        }.build()
        val task = async(Dispatchers.IO) { weatherAwaitText(client, request) }
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS)); task.cancel()
            withTimeout(2000) { while (!active.get().isCanceled()) delay(5) }
            assertTrue(active.get().isCanceled())
        } finally { release.countDown(); task.cancelAndJoin() }
    }
}
