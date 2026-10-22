package com.zhisheng.weather.data

import android.content.Context
import android.app.ActivityManager
import android.os.SystemClock
import android.util.AtomicFile
import com.zhisheng.weather.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal data class AppAnnouncement(val id: String, val title: String, val body: String,
    val publishedAt: Long, val expiresAt: Long, val minVersionCode: Int)
internal data class AnnouncementFeed(val revision: Long, val etag: String, val items: List<AppAnnouncement>)
internal data class AnnouncementSnapshot(val feed: AnnouncementFeed? = null, val verifiedAt: Long = 0L)
internal val announcementIdPattern = Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")

internal fun parseAnnouncements(raw: String): AnnouncementFeed {
    val root = Json.parseToJsonElement(raw).jsonObject
    require(root["protocol"]?.jsonPrimitive?.intOrNull == 1)
    val revision = root.getValue("revision").jsonPrimitive.long.also { require(it > 0) }
    val etag = root.getValue("etag").jsonPrimitive.content.also {
        require(it.matches(Regex("\"announcements-$revision-[a-f0-9]{16}\"")))
    }
    val items = root.getValue("items").jsonArray.also { require(it.size <= 10) }.map { element ->
        val row = element.jsonObject
        fun text(key: String, max: Int, multiline: Boolean = false) = row.getValue(key).jsonPrimitive.also { require(it.isString) }.content.also {
            require(it.isNotBlank() && it == it.trim() && it.length <= max)
            require(it.none { c -> (c.code < 32 && !(multiline && (c == '\n' || c == '\t'))) || c.code == 127 })
        }
        val id = text("id", 36).also { require(announcementIdPattern.matches(it)) }
        val start = row.getValue("publishedAt").jsonPrimitive.long
        val end = row.getValue("expiresAt").jsonPrimitive.long
        require(start > 0 && end > start && end - start <= 90L * 86_400_000)
        AppAnnouncement(id, text("title", 60), text("body", 2000, true), start, end,
            row.getValue("minVersionCode").jsonPrimitive.int.also { require(it > 0) })
    }
    require(items.map { it.id }.distinct().size == items.size)
    return AnnouncementFeed(revision, etag, items)
}
internal fun eligibleAnnouncements(feed: AnnouncementFeed?, now: Long, version: Int): List<AppAnnouncement> =
    feed?.items.orEmpty().filter { it.publishedAt <= now && it.expiresAt > now && version >= it.minVersionCode }
        .sortedWith(compareBy<AppAnnouncement> { it.publishedAt }.thenBy { it.id })

internal fun announcementReceiptIdentity(seed: String, id: String): Pair<String, String> {
    require(seed.matches(Regex("[a-f0-9]{64}")) && announcementIdPattern.matches(id))
    fun digest(purpose: String) = MessageDigest.getInstance("SHA-256").digest("$purpose:$id:$seed".toByteArray())
        .joinToString("") { "%02x".format(it) }
    return digest("announcement-receipt-id") to digest("announcement-receipt-proof")
}

/** Only foreground checks. Fetch is anonymous; optional receipts obey the existing statistics choice. */
internal object Announcements {
    val state = MutableStateFlow(AnnouncementSnapshot())
    val readIds = MutableStateFlow<Set<String>>(emptySet())
    private val mutex = Mutex()
    private var loaded = false
    private var lastRequest = -1L
    private val levels = mutableMapOf<String, Int>()
    private val sent = mutableMapOf<String, Int>()
    @Volatile private var liveClient: okhttp3.OkHttpClient? = null
    private fun streamClient(context: Context) = liveClient ?: synchronized(this) {
        liveClient ?: SharedPlaceApi.client(context).newBuilder().callTimeout(0, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS).build().also { liveClient = it }
    }
    private suspend fun accept(context: Context, next: AnnouncementFeed) = mutex.withLock {
        load(context)
        if (next.revision < (state.value.feed?.revision ?: 0L)) return@withLock
        state.value = AnnouncementSnapshot(next, SystemClock.elapsedRealtime())
        if (SettingsRepository.usageStatistics.first()) {
            eligibleAnnouncements(next, System.currentTimeMillis(), BuildConfig.VERSION_CODE).forEach {
                levels[it.id] = maxOf(levels[it.id] ?: 0, 1)
            }
            try { save(context) } catch (_: Exception) { }
        }
    }
    /** One cancellable foreground connection; the initial SSE frame includes all active notices. */
    suspend fun follow(context: Context) = withContext(Dispatchers.IO) {
        var backoff = 1000L
        while (currentCoroutineContext().isActive) {
            var connected = false
            try {
                announcementEvents(streamClient(context), Request.Builder()
                    .url("${SharedPlaceApi.endpoint}/v1/announcements/stream")
                    .header("Accept", "text/event-stream").header("Cache-Control", "no-cache").build()).collect { raw ->
                    if (raw != null) { accept(context, parseAnnouncements(raw)); connected = true }
                    else mutex.withLock {
                        if (connected) state.value = state.value.copy(verifiedAt = SystemClock.elapsedRealtime())
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Reconnect without retaining a background connection. */ }
            refresh(context)
            backoff = if (connected) 1000L else (backoff * 2).coerceAtMost(30_000L)
            delay(backoff + kotlin.random.Random.nextLong(500L))
        }
    }
    private fun local(context: Context) = AtomicFile(File(context.noBackupFilesDir, "announcements_v1.json"))
    private fun load(context: Context) {
        if (loaded) return
        runCatching { local(context).openRead().bufferedReader().use { reader ->
            val raw = reader.readText().also { require(it.length <= 300_000) }
            val root = Json.parseToJsonElement(raw).jsonObject
            readIds.value = root["read"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }
                .filter(announcementIdPattern::matches).takeLast(1000).toSet()
            fun restore(key: String, target: MutableMap<String, Int>) {
                root[key]?.jsonObject?.entries?.toList()?.takeLast(1000)?.forEach { (id, value) ->
                    val stage = value.jsonPrimitive.intOrNull
                    if (announcementIdPattern.matches(id) && stage != null && stage in 1..3) target[id] = stage
                }
            }
            restore("levels", levels); restore("sent", sent)
        } }
        loaded = true
    }
    private fun save(context: Context) {
        val value = buildJsonObject {
            put("read", JsonArray(readIds.value.toList().takeLast(1000).map(::JsonPrimitive)))
            for ((key, entries) in listOf("levels" to levels, "sent" to sent)) put(key, buildJsonObject {
                entries.entries.toList().takeLast(1000).forEach { (id, level) -> put(id, level) }
            })
        }
        val file = local(context); val stream = file.startWrite()
        try { stream.write(value.toString().toByteArray()); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            load(context)
            val elapsed = SystemClock.elapsedRealtime()
            if (lastRequest >= 0 && elapsed >= lastRequest && elapsed - lastRequest < 15_000) return@withLock
            lastRequest = elapsed
            try {
                val builder = Request.Builder().url("${SharedPlaceApi.endpoint}/v1/announcements")
                state.value.feed?.etag?.let { builder.header("If-None-Match", it) }
                val response = radarRequestBytes(SharedPlaceApi.client(context), builder.build(), 65_536)
                val next = when {
                    response.code == 304 -> state.value.feed ?: return@withLock
                    response.successful -> parseAnnouncements(response.bytes.toString(Charsets.UTF_8))
                    else -> return@withLock
                }
                if (next.revision < (state.value.feed?.revision ?: 0L)) return@withLock
                state.value = AnnouncementSnapshot(next, SystemClock.elapsedRealtime())
                if (SettingsRepository.usageStatistics.first()) {
                    eligibleAnnouncements(next, System.currentTimeMillis(), BuildConfig.VERSION_CODE).forEach {
                        levels[it.id] = maxOf(levels[it.id] ?: 0, 1)
                    }
                    save(context)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Never invent an announcement or mark it read on network failure. */ }
        }
    }
    suspend fun mark(context: Context, id: String, stage: Int) = withContext(Dispatchers.IO) {
        require(announcementIdPattern.matches(id) && stage in 2..3)
        mutex.withLock {
            load(context)
            if (stage == 3) readIds.value = (readIds.value + id).toList().takeLast(1000).toSet()
            if (SettingsRepository.usageStatistics.first()) levels[id] = maxOf(levels[id] ?: 0, stage)
            try { save(context) } catch (_: Exception) { /* Session still remembers the dismissal. */ }
        }
    }
    suspend fun dismiss(context: Context, id: String, confirmed: Boolean) = withContext(Dispatchers.IO) {
        require(announcementIdPattern.matches(id))
        mutex.withLock {
            load(context)
            readIds.value = (readIds.value + id).toList().takeLast(1000).toSet()
            if (SettingsRepository.usageStatistics.first()) levels[id] = maxOf(levels[id] ?: 0, if (confirmed) 3 else 2)
            try { save(context) } catch (_: Exception) { }
        }
    }
    private fun seed(context: Context): String {
        val file = AtomicFile(File(context.noBackupFilesDir, "announcement_receipt_seed"))
        runCatching { file.openRead().bufferedReader().use { it.readText() } }.getOrNull()
            ?.takeIf { it.matches(Regex("[a-f0-9]{64}")) }?.let { return it }
        val value = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        val stream = file.startWrite()
        try { stream.write(value.toByteArray()); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
        return value
    }
    suspend fun syncReceipts(context: Context) = withContext(Dispatchers.IO) {
        if (!SettingsRepository.usageStatistics.first()) {
            mutex.withLock { load(context); levels.clear(); try { save(context) } catch (_: Exception) { } }
            return@withContext
        }
        val batch = mutex.withLock { load(context); levels.filter { (id, stage) -> stage > (sent[id] ?: 0) }.entries.take(10).map { it.key to it.value } }
        for ((id, stage) in batch) {
            try {
                if (!SettingsRepository.usageStatistics.first()) break
                val process = ActivityManager.RunningAppProcessInfo()
                ActivityManager.getMyMemoryState(process)
                if (process.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) break
                val identity = mutex.withLock { announcementReceiptIdentity(seed(context), id) }
                val body = buildJsonObject {
                    put("protocol", 1); put("consentVersion", 1); put("purpose", "announcement_receipt")
                    put("announcementId", id); put("receiptId", identity.first); put("proof", identity.second)
                    put("event", listOf("", "received", "shown", "read")[stage])
                }
                val response = radarRequestBytes(SharedPlaceApi.client(context), Request.Builder()
                    .url("${SharedPlaceApi.endpoint}/v1/announcements/receipt")
                    .post(body.toString().toRequestBody("application/json".toMediaType())).build(), 2048)
                val accepted = response.successful && runCatching { Json.parseToJsonElement(response.bytes.toString(Charsets.UTF_8)).jsonObject["ok"]?.jsonPrimitive?.booleanOrNull == true }.getOrDefault(false)
                if (accepted || response.code == 404) mutex.withLock {
                    sent[id] = maxOf(sent[id] ?: 0, stage); save(context)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { break }
        }
    }
}
