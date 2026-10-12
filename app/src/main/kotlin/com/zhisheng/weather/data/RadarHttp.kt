package com.zhisheng.weather.data

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal data class RadarHttpResult(val code: Int, val bytes: ByteArray, val retryAfter: String? = null) {
    val successful: Boolean get() = code in 200..299
}

/** Cancel the actual socket call when leaving the page; bound responses before parsing/decoding. */
internal suspend fun radarRequestBytes(client: OkHttpClient, request: Request, limit: Long): RadarHttpResult =
    radarAwaitBytes(client.newCall(request), limit)

internal suspend fun radarAwaitBytes(call: Call, limit: Long): RadarHttpResult = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            try {
                val result = response.use {
                    val source = it.body?.source()
                    if (source != null && source.request(limit + 1)) throw IOException("Radar response exceeds limit")
                    RadarHttpResult(it.code, source?.readByteArray() ?: byteArrayOf(), it.header("Retry-After")?.take(200))
                }
                if (continuation.isActive) continuation.resume(result)
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    })
}
