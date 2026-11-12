package com.zhisheng.weather.data

import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test

class TyphoonHttpTest {
    private class PendingCall : Call {
        var cancelled = false
        var callback: Callback? = null
        override fun request() = Request.Builder().url("https://example.com/typhoon").build()
        override fun execute(): Response = error("Use asynchronous cancellable requests")
        override fun enqueue(responseCallback: Callback) { callback = responseCallback }
        override fun cancel() { cancelled = true }
        override fun isExecuted() = callback != null
        override fun isCanceled() = cancelled
        override fun timeout() = Timeout()
        override fun clone(): Call = PendingCall()
        fun respond(payload: String, code: Int = 200) {
            callback!!.onResponse(this, Response.Builder().request(request())
                .protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(payload.toResponseBody()).build())
        }
    }

    @Test fun switchingStormCancelsTheSocketAndIgnoresLateFailure() = runBlocking {
        val call = PendingCall()
        var delivered = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            typhoonAwaitPayload(call)
            delivered = true
        }
        job.cancel()
        job.join()
        assertTrue(call.cancelled)
        call.callback!!.onFailure(call, IOException("cancelled"))
        assertFalse(delivered)
    }

    @Test fun responseSizeIsBoundedBeforeParsing() = runBlocking {
        val call = PendingCall()
        val result = async(start = CoroutineStart.UNDISPATCHED) { runCatching { typhoonAwaitPayload(call, 4) } }
        call.respond("12345")
        assertTrue(result.await().exceptionOrNull() is IOException)
    }

    @Test fun emptyBodyAndHttpFailureCannotBecomeAnEmptySeason() = runBlocking {
        for ((body, code) in listOf("" to 200, "[]" to 503)) {
            val call = PendingCall()
            val result = async(start = CoroutineStart.UNDISPATCHED) { runCatching { typhoonAwaitPayload(call) } }
            call.respond(body, code)
            assertTrue(result.await().exceptionOrNull() is IOException)
        }
    }

    @Test fun validEmptyArrayReachesTheParserUnchanged() = runBlocking {
        val call = PendingCall()
        val result = async(start = CoroutineStart.UNDISPATCHED) { typhoonAwaitPayload(call) }
        call.respond("[]")
        assertEquals("[]", result.await())
    }
}
