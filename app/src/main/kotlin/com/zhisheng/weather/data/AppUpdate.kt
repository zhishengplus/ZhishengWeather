package com.zhisheng.weather.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.FileProvider
import com.zhisheng.weather.BuildConfig
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class AppUpdateInfo(
    val versionCode: Int? = null,
    val versionName: String,
    val apkUrl: String,
    val sha256: String? = null,
    val notes: String = "",
    val pageUrl: String = AppUpdate.RELEASES_PAGE,
    val channel: UpdateChannel = UpdateChannel.GITHUB,
)

enum class UpdateChannel(val displayName: String) {
    GITHUB("GitHub"),
    GITEE("Gitee"),
}

sealed class AppUpdateCheck {
    data class Available(val info: AppUpdateInfo) : AppUpdateCheck()
    data object UpToDate : AppUpdateCheck()
    data class Failed(val message: String) : AppUpdateCheck()
}

object AppUpdate {
    const val RELEASES_PAGE = "https://github.com/zhishengplus/ZhishengWeather/releases"
    const val GITEE_RELEASES_PAGE = "https://gitee.com/zhisheng8888/ZhishengWeather/releases"
    private const val MANIFEST_PRIMARY =
        "https://raw.githubusercontent.com/zhishengplus/ZhishengWeather/main/update.json"
    private const val MANIFEST_MIRROR =
        "https://cdn.jsdelivr.net/gh/zhishengplus/ZhishengWeather@main/update.json"
    private const val GITHUB_LATEST =
        "https://api.github.com/repos/zhishengplus/ZhishengWeather/releases/latest"
    private const val GITHUB_RELEASE_FEED =
        "https://api.github.com/repos/zhishengplus/ZhishengWeather/releases?per_page=8"
    private val GITEE_MANIFESTS = listOf(
        "https://gitee.com/zhisheng8888/ZhishengWeather/raw/main/update.json",
        "https://gitee.com/zhisheng8888/ZhishengWeather/raw/master/update.json",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    // HTTPS 护栏：应用拦截器先拒绝非 HTTPS 初始地址，网络拦截器再校验
    // 每一次真实网络请求。followSslRedirects=false 从根上禁止 HTTPS↔HTTP 跨协议跳转。
    private fun httpsGuard() = okhttp3.Interceptor { chain ->
        val request = chain.request()
        if (!request.url.isHttps) throw IOException("非 HTTPS 地址已拦截：${request.url.host}")
        chain.proceed(request)
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .addInterceptor(httpsGuard())
        .addNetworkInterceptor(httpsGuard())
        .build()
    private val downloadHttp = http.newBuilder()
        // A stalled transfer at 1% should fail over instead of appearing frozen for two minutes.
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // 检查缓存规则：进行中的检查互斥串行；成功结果短期复用；失败不缓存（下次立即重试）；
    // refresh=true（用户主动检查）绕过缓存。@Volatile 不够，Mutex 保证并发安全。
    private val checkMutex = Mutex()
    private var lastResult: AppUpdateCheck? = null
    private var lastCompletedResult: AppUpdateCheck? = null
    private var lastSuccessElapsed = 0L
    private val checkGeneration = AtomicLong(0L)

    private const val SUCCESS_REUSE_MS = 5 * 60_000L

    internal fun shouldReuseCache(cached: AppUpdateCheck?, refresh: Boolean, elapsedMs: Long): Boolean =
        cached != null && cached !is AppUpdateCheck.Failed && !refresh &&
            elapsedMs < SUCCESS_REUSE_MS

    suspend fun check(): AppUpdateCheck = check(refresh = false)

    suspend fun check(refresh: Boolean): AppUpdateCheck = withContext(Dispatchers.IO) {
        // 进入互斥区前记住代数：如果等锁期间别的检查已经完成，直接共享该结果。
        // 这样启动静默检查与用户手动 refresh 撞在一起时也不会连续请求两遍。
        val observedGeneration = checkGeneration.get()
        checkMutex.withLock {
            // A manual refresh must perform its own network check even if a
            // startup check finished while it waited for the mutex. Sharing
            // that stale result made the first tap say "up to date" and the
            // second tap discover the new release.
            if (!refresh && checkGeneration.get() != observedGeneration) {
                lastCompletedResult?.let { return@withContext it }
            }
            val cached = lastResult
            if (cached != null && shouldReuseCache(cached, refresh, SystemClock.elapsedRealtime() - lastSuccessElapsed)) {
                return@withContext cached
            }
            // 国内用户先走 Gitee；失败时再用 GitHub 清单和 Release 兜底。
            val fresh = fetchNewest()
            lastCompletedResult = fresh
            checkGeneration.incrementAndGet()
            if (fresh is AppUpdateCheck.Failed) {
                lastResult = null // 失败不缓存，下次调用立即重试
            } else {
                lastResult = fresh
                lastSuccessElapsed = SystemClock.elapsedRealtime()
            }
            fresh
        }
    }

    private fun fetchNewest(): AppUpdateCheck {
        val info = fetchGiteeManifest()
            ?: newestOf(fetchGithubManifest(), fetchGithubLatest())
            ?: return AppUpdateCheck.Failed("暂时连不上 GitHub 与 Gitee 更新源，可稍后再试。")
        return if (isNewer(info, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME))
            AppUpdateCheck.Available(info) else AppUpdateCheck.UpToDate
    }

    internal fun newestOf(manifest: AppUpdateInfo?, release: AppUpdateInfo?): AppUpdateInfo? = when {
        manifest == null -> release
        release == null -> manifest
        compareSemver(release.versionName, manifest.versionName) > 0 -> release
        else -> manifest // Same version: the manifest carries the authoritative versionCode and SHA.
    }

    /** True only when the GitHub public APK can safely replace this installed build. */
    fun canSelfUpdate(): Boolean = BuildConfig.CAN_SELF_UPDATE

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))

    fun apkFile(context: Context): File = File.createTempFile(
        "ZhishengWeather-update-", ".apk", File(context.cacheDir, "updates").apply { mkdirs() },
    )

    suspend fun download(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (Float?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        try {
            downloadInternal(context, info, onProgress)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            // 仅传输类失败（网络/HTTP/TLS/源站/校验损坏）才切到另一站的同版本附件；
            // 用户取消、磁盘不足、安装权限类失败不在此列，不触发备用重试。
            if (!isTransportFailure(e)) throw e
            val fallback = when (info.channel) {
                UpdateChannel.GITHUB -> giteeFallbackInfo(info)
                UpdateChannel.GITEE -> githubFallbackInfo(info)
            } ?: error("下载失败：${e.message}（备用下载源不可用）")
            try {
                downloadInternal(context, fallback, onProgress)
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (retry: Exception) {
                error("Gitee 与 GitHub 下载均失败：${retry.message ?: e.message}")
            }
        }
    }

    private fun isTransportFailure(e: Exception): Boolean {
        if (e is kotlinx.coroutines.CancellationException) return false
        val msg = (e.message ?: "").lowercase()
        if (listOf("enospc", "no space", "空间不足", "磁盘").any { it in msg }) return false
        return e is IOException || e is RetryableDownloadException
    }

    // Gitee 备用地址：优先 release API 的真实附件（同版本、公共渠道）；API 不可用时回退按文件名拼接
    private fun giteeFallbackInfo(info: AppUpdateInfo): AppUpdateInfo? = runCatching {
        val tag = "v${info.versionName}"
        val raw = getText("https://gitee.com/api/v5/repos/zhisheng8888/ZhishengWeather/releases/tags/$tag")
            ?: return@runCatching null
        val release = json.decodeFromString<GithubReleaseDto>(raw)
        val asset = pickPublicApk(release.assets, info.versionName) ?: return@runCatching null
        info.copy(
            apkUrl = asset.browserDownloadUrl,
            pageUrl = "$GITEE_RELEASES_PAGE/tag/$tag",
            channel = UpdateChannel.GITEE,
        )
    }.getOrNull() ?: runCatching { asGiteeRelease(info) }.getOrNull()

    private fun githubFallbackInfo(info: AppUpdateInfo): AppUpdateInfo? = runCatching {
        val fileName = info.apkUrl.substringBefore('?').substringAfterLast('/')
        require(fileName.endsWith(".apk", ignoreCase = true))
        val tag = "v${info.versionName}"
        info.copy(
            apkUrl = "$RELEASES_PAGE/download/$tag/$fileName",
            pageUrl = "$RELEASES_PAGE/tag/$tag",
            channel = UpdateChannel.GITHUB,
        )
    }.getOrNull()

    private suspend fun downloadInternal(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (Float?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val expectedSha = info.sha256?.trim()?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }
            ?: throw IllegalArgumentException("更新源未提供可验证的 SHA-256，请前往发布页手动下载")
        val dest = apkFile(context)
        var completed = false
        try {
            val request = Request.Builder()
                .url(info.apkUrl)
                .header("User-Agent", userAgent())
                .build()
            val call = downloadHttp.newCall(request)
            val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        throw RetryableDownloadException("下载失败（HTTP ${resp.code}）")
                    }
                    val body = resp.body ?: throw RetryableDownloadException("下载失败：空响应")
                    val total = body.contentLength()
                    dest.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                            var read = 0L
                            var lastPercent = -1
                            var lastCallbackAt = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buf)
                                currentCoroutineContext().ensureActive()
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                read += n
                                if (total > 0) {
                                    val percent = ((read * 100L) / total).toInt().coerceIn(0, 100)
                                    val now = SystemClock.elapsedRealtime()
                                    if (shouldEmitDownloadProgress(lastPercent, lastCallbackAt, percent, now)) {
                                        lastPercent = percent
                                        lastCallbackAt = now
                                        withContext(Dispatchers.Main.immediate) {
                                            onProgress(read.toFloat() / total.toFloat())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } finally { cancellation.cancel() }
            currentCoroutineContext().ensureActive()
            val actual = sha256Hex(dest)
            if (!actual.equals(expectedSha, ignoreCase = true)) {
                throw RetryableDownloadException("安装包校验失败，已取消安装")
            }
            currentCoroutineContext().ensureActive()
            completed = true
            dest
        } finally {
            // 网络异常、取消或校验失败都不留下可能被误安装的半截 APK。
            if (!completed) dest.delete()
        }
    }

    internal fun shouldEmitDownloadProgress(
        lastPercent: Int,
        lastCallbackAtMillis: Long,
        percent: Int,
        nowMillis: Long,
    ): Boolean = percent >= 100 || percent > lastPercent || nowMillis - lastCallbackAtMillis >= 100L

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    internal fun parseManifest(
        raw: String,
        channel: UpdateChannel = UpdateChannel.GITHUB,
    ): AppUpdateInfo {
        val dto = json.decodeFromString<UpdateManifestDto>(raw)
        val name = dto.versionName.trim()
        require(name.isNotEmpty() && dto.apkUrl.isNotBlank()) { "更新清单缺少版本或下载地址" }
        val apkUrl = dto.apkUrl.trim()
        require(apkUrl.startsWith("https://", ignoreCase = true)) { "更新清单下载地址必须是 HTTPS" }
        val source = if (URI(apkUrl).host.equals("gitee.com", ignoreCase = true))
            UpdateChannel.GITEE else channel
        return AppUpdateInfo(
            versionCode = dto.versionCode,
            versionName = name.removePrefix("v"),
            apkUrl = apkUrl,
            sha256 = dto.sha256?.trim()?.takeIf { it.isNotEmpty() },
            notes = dto.notes.orEmpty().trim(),
            pageUrl = dto.pageUrl?.trim()?.ifBlank { null }
                ?: if (source == UpdateChannel.GITEE) GITEE_RELEASES_PAGE else RELEASES_PAGE,
            channel = source,
        )
    }

    internal fun parseGithubLatest(raw: String): AppUpdateInfo? {
        val release = json.decodeFromString<GithubReleaseDto>(raw)
        return parseGithubRelease(release)
    }

    internal fun parseGithubFeed(raw: String): AppUpdateInfo? =
        json.decodeFromString<List<GithubReleaseDto>>(raw).firstNotNullOfOrNull(::parseGithubRelease)

    private fun parseGithubRelease(release: GithubReleaseDto): AppUpdateInfo? {
        if (release.draft) return null
        val name = release.tagName.removePrefix("v").trim()
        if (name.isEmpty()) return null
        val asset = pickPublicApk(release.assets, name) ?: return null
        val digest = asset.digest
            ?.removePrefix("sha256:")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return AppUpdateInfo(
            versionName = name,
            apkUrl = asset.browserDownloadUrl,
            sha256 = digest,
            notes = release.body.orEmpty().trim().lineSequence()
                .take(8)
                .joinToString("\n")
                .take(400),
            pageUrl = release.htmlUrl.ifBlank { RELEASES_PAGE },
        )
    }

    internal fun isNewer(
        info: AppUpdateInfo,
        localCode: Int,
        localName: String,
    ): Boolean {
        val remoteCode = info.versionCode
        if (remoteCode != null && remoteCode > 0) return remoteCode > localCode
        return compareSemver(info.versionName, localName) > 0
    }

    internal fun pickPublicApk(
        assets: List<GithubAssetDto>,
        expectedVersion: String? = null,
    ): GithubAssetDto? {
        val apk = assets.filter { it.state == "uploaded" && it.name.endsWith(".apk", ignoreCase = true) }
        val versionToken = expectedVersion?.removePrefix("v")?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
        val public = apk.filter { asset ->
            isPublicApkName(asset.name) &&
                (versionToken == null || apkNameMatchesVersion(asset.name, versionToken))
        }
        return public.firstOrNull { it.name.contains("-public", ignoreCase = true) }
            ?: public.firstOrNull()
    }

    internal fun isPublicApkName(name: String): Boolean {
        val lower = name.lowercase()
        if (!lower.endsWith(".apk")) return false
        if (listOf("private", "parallel", "preview", "full", "owner").any { it in lower }) return false
        return lower.contains("zhisheng-weather") ||
            lower.contains("zhishengweather") ||
            lower.contains("-public")
    }

    internal fun apkNameMatchesVersion(name: String, version: String): Boolean {
        val normalizedName = name.lowercase()
        val token = version.removePrefix("v").lowercase()
        return "v$token.apk" in normalizedName || "v$token-" in normalizedName
    }

    internal fun compareSemver(remote: String, local: String): Int {
        val r = parseSemver(remote)
        val l = parseSemver(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv.compareTo(lv)
        }
        // 数字段相等：比预发布后缀。排序 beta5 < beta5-2 < beta6 < beta10 < 同版本正式版。
        return comparePrerelease(prereleaseSuffix(remote), prereleaseSuffix(local))
    }

    private fun prereleaseSuffix(value: String): String? =
        value.removePrefix("v").substringAfter('-', missingDelimiterValue = "")
            .trim().takeIf { it.isNotEmpty() }

    // 预发布串按 数字/非数字 分词逐位比：数字按数值（beta10 > beta6），数字优于字母，
    // 同位字母按字典序，多出的段胜出（beta5-2 > beta5）；正式版（无后缀）> 任何预发布。
    private fun comparePrerelease(remote: String?, local: String?): Int {
        if (remote == null && local == null) return 0
        if (remote == null) return 1
        if (local == null) return -1
        val rTokens = prereleaseTokens(remote)
        val lTokens = prereleaseTokens(local)
        for (i in 0 until maxOf(rTokens.size, lTokens.size)) {
            val r = rTokens.getOrNull(i)
            val l = lTokens.getOrNull(i)
            when {
                r == null && l == null -> {}
                r == null -> return -1
                l == null -> return 1
                r is Int && l is Int -> if (r != l) return r.compareTo(l)
                r is Int -> return 1
                l is Int -> return -1
                else -> {
                    val rs = r as String
                    val ls = l as String
                    if (rs != ls) return rs.compareTo(ls)
                }
            }
        }
        return 0
    }

    private fun prereleaseTokens(value: String): List<Any> =
        Regex("\\d+|\\D+").findAll(value)
            .map { it.value.toIntOrNull() ?: it.value as Any }
            .toList()

    private fun parseSemver(value: String): List<Int> =
        value.removePrefix("v")
            .takeWhile { it.isDigit() || it == '.' }
            .split('.')
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }

    private fun userAgent(): String = "ZhishengWeather/${BuildConfig.VERSION_NAME}"

    private fun fetchGithubManifest(): AppUpdateInfo? {
        for (url in listOf(MANIFEST_PRIMARY, MANIFEST_MIRROR)) {
            val raw = getText(cacheBusted(url)) ?: continue
            runCatching { parseManifest(raw) }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun fetchGiteeManifest(): AppUpdateInfo? {
        for (url in GITEE_MANIFESTS) {
            val raw = getText(cacheBusted(url)) ?: continue
            runCatching { parseManifest(raw, UpdateChannel.GITEE) }.getOrNull()?.let { info ->
                // 镜像仓库可以直接复用同一份 update.json；命中 Gitee 时把发布页与
                // 同名附件切到 Gitee，确保不是“从 Gitee 清单又跳回 GitHub”。
                return asGiteeRelease(info)
            }
        }
        return null
    }

    internal fun asGiteeRelease(info: AppUpdateInfo): AppUpdateInfo {
        val fileName = info.apkUrl.substringBefore('?').substringAfterLast('/')
        require(fileName.endsWith(".apk", ignoreCase = true)) { "Gitee 更新清单缺少 APK 文件名" }
        val tag = "v${info.versionName}"
        return info.copy(
            apkUrl = "$GITEE_RELEASES_PAGE/download/$tag/$fileName",
            pageUrl = "$GITEE_RELEASES_PAGE/tag/$tag",
            channel = UpdateChannel.GITEE,
        )
    }

    private fun fetchGithubLatest(): AppUpdateInfo? {
        val feed = getText(GITHUB_RELEASE_FEED, accept = "application/vnd.github+json")
        feed?.let { runCatching { parseGithubFeed(it) }.getOrNull() }?.let { return it }
        val raw = getText(GITHUB_LATEST, accept = "application/vnd.github+json") ?: return null
        return runCatching { parseGithubLatest(raw) }.getOrNull()
    }

    private fun getText(url: String, accept: String = "application/json"): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent())
            .header("Accept", accept)
            .header("Cache-Control", "no-cache")
            .build()
        return runCatching {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull()
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun cacheBusted(url: String): String = "$url?check=${System.currentTimeMillis()}"

    private class RetryableDownloadException(message: String) : IllegalStateException(message)
}

@Serializable
internal data class UpdateManifestDto(
    val versionCode: Int? = null,
    val versionName: String,
    val apkUrl: String,
    val sha256: String? = null,
    val notes: String? = null,
    val pageUrl: String? = null,
)

@Serializable
internal data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = AppUpdate.RELEASES_PAGE,
    val assets: List<GithubAssetDto> = emptyList(),
)

@Serializable
internal data class GithubAssetDto(
    val name: String,
    val state: String = "uploaded",
    val digest: String? = null,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)
