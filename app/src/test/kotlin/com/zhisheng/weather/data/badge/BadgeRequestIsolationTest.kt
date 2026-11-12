package com.zhisheng.weather.data.badge

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BadgeRequestIsolationTest {
    @Test fun cancellingWalletRefreshKeepsAnInFlightModelManifestAlive() {
        val entered = CountDownLatch(2); val release = CountDownLatch(1); val completed = CountDownLatch(1)
        val error = AtomicReference<Throwable?>()
        val wallet = OkHttpClient.Builder().addInterceptor {
            entered.countDown(); check(release.await(5, TimeUnit.SECONDS))
            Response.Builder().request(it.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{}".toResponseBody()).build()
        }.build()
        val models = badgeManifestClient(wallet)
        val refresh = wallet.newCall(Request.Builder().url("https://fixture.invalid/refresh").build())
        val manifest = models.newCall(Request.Builder().url("https://fixture.invalid/model").build())
        val ignored = object : Callback { override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() } }
        try {
            refresh.enqueue(ignored)
            manifest.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { error.set(e); completed.countDown() }
                override fun onResponse(call: Call, response: Response) { response.close(); completed.countDown() }
            })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            wallet.dispatcher.cancelAll()
            assertTrue(refresh.isCanceled()); assertFalse(manifest.isCanceled())
            release.countDown(); assertTrue(completed.await(5, TimeUnit.SECONDS)); assertNull(error.get())
        } finally {
            release.countDown(); wallet.dispatcher.cancelAll(); models.dispatcher.cancelAll()
            wallet.dispatcher.executorService.shutdownNow(); models.dispatcher.executorService.shutdownNow()
        }
    }
}
