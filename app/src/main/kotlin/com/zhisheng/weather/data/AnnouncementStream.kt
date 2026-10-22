package com.zhisheng.weather.data

import java.io.IOException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource

/** Bounded SSE decoder: null means a heartbeat; only the named public feed is accepted. */
internal fun readAnnouncementEvents(source: BufferedSource, emit: (String?) -> Unit) {
    var event = ""
    val data = StringBuilder()
    while (!source.exhausted()) {
        val line = source.readUtf8LineStrict(65_536)
        when {
            line.isEmpty() -> {
                if (event == "announcements" && data.isNotEmpty()) emit(data.toString().removeSuffix("\n"))
                event = ""; data.setLength(0)
            }
            line.startsWith(":") -> emit(null)
            line.startsWith("event:") -> event = line.substring(6).removePrefix(" ")
            line.startsWith("data:") -> {
                require(data.length + line.length <= 65_536)
                data.append(line.substring(5).removePrefix(" ")).append('\n')
            }
        }
    }
}

internal fun announcementEvents(client: OkHttpClient, request: Request): Flow<String?> = callbackFlow {
    val call = client.newCall(request)
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { close(e) }
        override fun onResponse(call: Call, response: Response) {
            try {
                response.use {
                    require(it.isSuccessful && it.header("Content-Type").orEmpty().startsWith("text/event-stream"))
                    readAnnouncementEvents(requireNotNull(it.body).source()) { value ->
                        if (trySend(value).isFailure) throw IOException("Announcement consumer unavailable")
                    }
                }
                close()
            } catch (e: Exception) { close(e) }
        }
    })
    awaitClose { call.cancel() }
}.buffer(16)
