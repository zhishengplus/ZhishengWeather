package com.zhisheng.weather.data

import android.content.ContextWrapper
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppUpdateDownloadTest {
    @get:Rule val temporary = TemporaryFolder()
    private val context get() = object : ContextWrapper(null) {
        override fun getCacheDir(): File = temporary.root
    }

    @Test fun closingDownloadCancelsItsActiveHttpCall() {
        val stalled = CountDownLatch(1)
        val release = CountDownLatch(1)
        val call = AtomicReference<Call>()
        val bytes = "verified update bytes".toByteArray()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            call.set(chain.call())
            response(chain, blockingBody(bytes, stalled, release) {
                if (chain.call().isCanceled()) throw IOException("Transfer cancelled")
            })
        }.build()
        withDownloadClient(client) {
            runBlocking {
                val download = async(Dispatchers.IO) { AppUpdate.download(context, info("first.apk", bytes)) {} }
                try {
                    assertTrue("The body must be read before cancellation", stalled.await(2, TimeUnit.SECONDS))
                    download.cancel()
                    val cancelled = withTimeoutOrNull(2_000) {
                        while (!call.get().isCanceled()) delay(5)
                        true
                    } ?: false
                    assertTrue("Closing a download must cancel the active OkHttp call", cancelled)
                } finally {
                    release.countDown()
                    download.cancel()
                    withTimeout(2_000) { download.join() }
                }
            }
        }
    }

    @Test fun cancelledAttemptCannotCorruptTheActiveRetry() {
        val firstStalled = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val retryStalled = CountDownLatch(1)
        val releaseRetry = CountDownLatch(1)
        val retryRequests = AtomicInteger()
        val firstBytes = "cancelled update bytes".toByteArray()
        val retryBytes = "verified retry bytes".toByteArray()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val body = when (chain.request().url.encodedPath.substringAfterLast('/')) {
                "first.apk" -> blockingBody(firstBytes, firstStalled, releaseFirst,
                    lateChunk = "late cancelled bytes must not enter the active retry ".repeat(2).toByteArray(),
                    onReleased = {
                    throw CancellationException("Cancelled reader finished after the retry started")
                })
                "retry.apk" -> if (retryRequests.incrementAndGet() == 1) {
                    blockingBody(retryBytes, retryStalled, releaseRetry)
                } else body(Buffer().write(retryBytes))
                else -> error("Unexpected download request")
            }
            response(chain, body)
        }.build()
        withDownloadClient(client) {
            runBlocking {
                val first = async(Dispatchers.IO) { AppUpdate.download(context, info("first.apk", firstBytes)) {} }
                var retry: kotlinx.coroutines.Deferred<File>? = null
                try {
                    assertTrue("First attempt must have written before cancellation", firstStalled.await(2, TimeUnit.SECONDS))
                    first.cancel()
                    val activeRetry = async(Dispatchers.IO) { AppUpdate.download(context, info("retry.apk", retryBytes)) {} }
                    retry = activeRetry
                    assertTrue("Retry must have written before the old reader resumes", retryStalled.await(2, TimeUnit.SECONDS))
                    releaseFirst.countDown()
                    withTimeout(2_000) { first.join() }
                    releaseRetry.countDown()
                    val file = withTimeout(2_000) { activeRetry.await() }
                    assertArrayEquals("The active retry must retain all verified bytes", retryBytes, file.readBytes())
                    assertEquals("Cancelled attempt must not force the retry to download again", 1, retryRequests.get())
                } finally {
                    releaseFirst.countDown()
                    releaseRetry.countDown()
                    first.cancel()
                    retry?.cancel()
                    withTimeout(2_000) { first.join(); retry?.join() }
                }
            }
        }
    }

    private fun info(fileName: String, bytes: ByteArray) = AppUpdateInfo(
        versionCode = 100,
        versionName = "1.0",
        apkUrl = "https://updates.example.test/$fileName",
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        channel = UpdateChannel.GITEE,
    )

    private fun response(chain: Interceptor.Chain, body: ResponseBody) = Response.Builder()
        .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()

    private fun body(source: BufferedSource) = object : ResponseBody() {
        override fun contentType() = "application/vnd.android.package-archive".toMediaType()
        override fun contentLength() = -1L
        override fun source() = source
    }

    private fun blockingBody(bytes: ByteArray, stalled: CountDownLatch, release: CountDownLatch,
                             lateChunk: ByteArray = byteArrayOf(),
                             onReleased: () -> Unit = {}, onWaiting: () -> Unit = {}): ResponseBody {
        val remaining = Buffer().write(bytes)
        val late = Buffer().write(lateChunk)
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (remaining.size > 0) return remaining.read(sink, byteCount)
                stalled.countDown()
                while (!release.await(5, TimeUnit.MILLISECONDS)) onWaiting()
                // A response can deliver already buffered bytes before it notices
                // cancellation. This also catches overlap on hosts that cannot
                // unlink the retry's open file during old-attempt cleanup.
                if (late.size > 0) return late.read(sink, byteCount)
                onReleased()
                return -1L
            }
            override fun timeout() = Timeout.NONE
            override fun close() = Unit
        }
        return body(source.buffer())
    }

    // Keep the external response seam in tests; execute the production reader and
    // its cancellation/finally paths without contacting a release server.
    private fun withDownloadClient(client: OkHttpClient, block: () -> Unit) = synchronized(clientLock) {
        AppUpdate.canSelfUpdate()
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").let {
            it.isAccessible = true
            it.get(null)
        }
        val field = AppUpdate::class.java.getDeclaredField("downloadHttp")
        val base = unsafeClass.getMethod("staticFieldBase", java.lang.reflect.Field::class.java).invoke(unsafe, field)
        val offset = unsafeClass.getMethod("staticFieldOffset", java.lang.reflect.Field::class.java).invoke(unsafe, field)
        val getObject = unsafeClass.getMethod("getObject", Any::class.java, java.lang.Long.TYPE)
        val putObject = unsafeClass.getMethod("putObjectVolatile", Any::class.java, java.lang.Long.TYPE, Any::class.java)
        val previous = getObject.invoke(unsafe, base, offset)
        putObject.invoke(unsafe, base, offset, client)
        try { block() } finally { putObject.invoke(unsafe, base, offset, previous) }
    }

    private companion object { val clientLock = Any() }
}
