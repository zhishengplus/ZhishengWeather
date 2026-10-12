package com.zhisheng.weather.data

import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test

class RadarHttpCancellationTest {
    private class PendingCall : Call {
        var cancelled = false
        var callback: Callback? = null
        override fun request() = Request.Builder().url("https://example.com/radar").build()
        override fun execute(): Response = error("Must use cancellable asynchronous call")
        override fun enqueue(responseCallback: Callback) { callback = responseCallback }
        override fun cancel() { cancelled = true }
        override fun isExecuted() = callback != null
        override fun isCanceled() = cancelled
        override fun timeout() = Timeout()
        override fun clone(): Call = PendingCall()
    }
    @Test fun cancellingPageCancelsActualHttpCallAndLateFailureCannotBecomeData() = runBlocking {
        val call = PendingCall()
        var delivered = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            radarAwaitBytes(call, 1024)
            delivered = true
        }
        assertNotNull(call.callback)
        job.cancel()
        job.join()
        assertTrue(call.cancelled)
        call.callback!!.onFailure(call, IOException("cancelled"))
        assertFalse(delivered)
    }
}
