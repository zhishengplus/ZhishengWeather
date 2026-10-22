package com.zhisheng.weather.ui

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.zhisheng.weather.data.SharedPlaceApi
import com.zhisheng.weather.data.radarRequestBytes
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.Request

internal data class CommunityDirectorySnapshot(val revision: Long, val sponsors: List<String>, val sections: List<ContributorSection>) {
    val contributors: List<String> get() = sections.flatMap { it.contributors }.distinct()
}
internal fun parseCommunityDirectory(raw: String): CommunityDirectorySnapshot {
    val root = Json.parseToJsonElement(raw).jsonObject
    require(root["protocol"]?.jsonPrimitive?.intOrNull == 1)
    val revision = root.getValue("revision").jsonPrimitive.long.also { require(it > 0) }
    fun value(element: JsonElement, max: Int) = element.jsonPrimitive.content.also {
        require(it.isNotBlank() && it == it.trim() && it.length <= max && it.none { c -> c.code < 32 || c.code == 127 })
    }
    val sponsors = root.getValue("sponsors").jsonArray.map { value(it, 100) }
    require(sponsors.size in 6..2000 && sponsors.distinct().size == sponsors.size && sponsors[5] == "Sh4d0W")
    val sections = root.getValue("sections").jsonArray.map { rawSection ->
        val s = rawSection.jsonObject
        val names = s.getValue("contributors").jsonArray.map { value(it, 100) }
        require(names.size <= 5000 && names.distinct().size == names.size)
        ContributorSection(value(s.getValue("key"), 40).also { require(it.matches(Regex("[A-Z0-9_]{1,40}"))) },
            value(s.getValue("title"), 60), value(s.getValue("subtitle"), 150), names)
    }
    require(sections.size in 1..12 && sections.map { it.key }.distinct().size == sections.size && sections.sumOf { it.contributors.size } in 1..5000)
    return CommunityDirectorySnapshot(revision, sponsors, sections)
}
internal object CommunityDirectory {
    val state = MutableStateFlow(CommunityDirectorySnapshot(0, SponsorBoard, CommunityContributorSections))
    private val mutex = Mutex()
    private var loaded = false
    private var lastRequest = 0L
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = AtomicFile(File(context.noBackupFilesDir, "community_directory_v1.json"))
            if (!loaded) {
                runCatching { file.openRead().bufferedReader().use { parseCommunityDirectory(it.readText()) } }.getOrNull()?.let { state.value = it }
                loaded = true
            }
            val now = android.os.SystemClock.elapsedRealtime()
            if (lastRequest != 0L && now >= lastRequest && now - lastRequest < 60_000) return@withLock
            lastRequest = now
            try {
                val response = radarRequestBytes(SharedPlaceApi.client(context), Request.Builder().url("${SharedPlaceApi.endpoint}/v1/community").build(), 400_000)
                if (!response.successful) return@withLock
                val raw = response.bytes.toString(Charsets.UTF_8)
                val next = parseCommunityDirectory(raw)
                if (next.revision < state.value.revision) return@withLock
                val out = file.startWrite()
                try { out.write(raw.toByteArray()); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out); throw e }
                state.value = next
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Keep the last complete, validated list; never blank the bundled list. */ }
        }
    }
}
@Composable
internal fun rememberCommunityDirectory(): CommunityDirectorySnapshot {
    val context = LocalContext.current.applicationContext
    val view = LocalView.current
    val snapshot by CommunityDirectory.state.collectAsState()
    LaunchedEffect(context) { if (!view.isInEditMode) CommunityDirectory.refresh(context) }
    return snapshot
}
