package com.zhisheng.weather.data

import android.content.Context
import android.provider.Settings
import android.util.AtomicFile
import com.zhisheng.weather.BuildConfig
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal class UsageStatistics(context: Context) {
    private val app = context.applicationContext
    private val file = AtomicFile(File(app.noBackupFilesDir, "device_version_registration.json"))
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(12, TimeUnit.SECONDS).build()
    private val reporter = InstallRegistration(object : InstallRegistrationStore {
        override suspend fun read(): InstallRegistrationState? {
            if (!file.baseFile.exists()) return null
            return Json.decodeFromString<InstallRegistrationState>(file.openRead().bufferedReader().use { it.readText() })
        }
        override suspend fun write(state: InstallRegistrationState) {
            val out = file.startWrite()
            try { out.write(Json.encodeToString(state).toByteArray()); file.finishWrite(out) }
            catch (e: Exception) { file.failWrite(out); throw e }
        }
    }, createId = {
        val androidId = Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID)
        val source = androidId?.takeIf { it.matches(Regex("[a-fA-F0-9]{1,16}")) && it != "0" }
            ?: UUID.randomUUID().toString()
        MessageDigest.getInstance("SHA-256").digest(("zhisheng-weather-device-v1:" + source.lowercase()).toByteArray())
            .joinToString("") { "%02x".format(it) }
    }, send = { id, version ->
        // Public readiness gate prevents transmission before the matching backend is deployed.
        val ready = request(Request.Builder().url("$ENDPOINT?ready=1").build())
        if (ready?.get("protocol")?.jsonPrimitive?.content != "3" || ready["enabled"]?.jsonPrimitive?.content != "true") false
        else {
            val body = "{\"deviceId\":\"$id\",\"versionCode\":$version}"
            val reply = request(Request.Builder().url(ENDPOINT).post(body.toRequestBody("application/json".toMediaType())).build())
            reply?.get("protocol")?.jsonPrimitive?.content == "3" && reply["ok"]?.jsonPrimitive?.content == "true"
        }
    })

    suspend fun register(enabled: Boolean) = withContext(Dispatchers.IO) {
        if (!enabled || BuildConfig.DEBUG || !BuildConfig.CAN_SELF_UPDATE) return@withContext
        try { reporter.report(true, BuildConfig.VERSION_CODE) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Retry on the next foreground entry; never interrupt weather. */ }
    }

    private suspend fun request(request: Request) = suspendCancellableCoroutine<String?> { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { continuation.resumeWith(Result.success(null)) }
            override fun onResponse(call: Call, response: Response) {
                val value = try { response.use { if (it.isSuccessful) it.peekBody(2048).string() else null } }
                    catch (_: IOException) { null }
                continuation.resumeWith(Result.success(value))
            }
        })
    }?.let { Json.parseToJsonElement(it).jsonObject }

    private companion object { const val ENDPOINT = "https://zhishengweather.site/api/app-installs" }
}
