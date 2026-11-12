package com.zhisheng.weather.data.badge

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zhisheng.weather.BuildConfig
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable internal data class BadgeServiceConfiguration(val endpoint: String = "", val receiptKeys: Map<String, String> = emptyMap())
@Serializable private data class EncryptedBadgeWallet(val protocol: Int = 1, val iv: String, val data: String)
/** Fixed path/alias across app versions; no age-based deletion and no silent reset on key loss. */
internal class AndroidBadgeWalletStore(context: Context) : BadgeWalletStore {
    private val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "first_light_wallet_v1.enc"))
    private val alias = "zhisheng_badge_wallet_v1"
    private fun key(create: Boolean): SecretKey {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create || file.baseFile.exists()) throw BadgeClientException("WALLET_UNREADABLE")
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    override suspend fun load(): BadgeLocalState {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return BadgeLocalState()
        try {
            val encrypted = file.openRead().use { input ->
                require(input.channel.size() <= 8388608)
                badgeJson.decodeFromString<EncryptedBadgeWallet>(input.bufferedReader().readText())
            }
            require(encrypted.protocol == 1)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, Base64.getDecoder().decode(encrypted.iv)))
            return badgeJson.decodeFromString(cipher.doFinal(Base64.getDecoder().decode(encrypted.data)).toString(Charsets.UTF_8))
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { throw BadgeClientException("WALLET_UNREADABLE") }
    }
    override suspend fun save(value: BadgeLocalState) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        val encrypted = EncryptedBadgeWallet(iv = Base64.getEncoder().encodeToString(cipher.iv),
            data = Base64.getEncoder().encodeToString(cipher.doFinal(badgeJson.encodeToString(value).toByteArray())))
        val bytes = badgeJson.encodeToString(encrypted).toByteArray()
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (e: Exception) { file.failWrite(output); throw e }
    }
}
internal fun badgeManifestClient(walletClient: OkHttpClient): OkHttpClient =
    walletClient.newBuilder().dispatcher(okhttp3.Dispatcher()).build()

internal class HttpBadgeTransport(endpoint: String) : BadgeTransport {
    private val origin = endpoint.trimEnd('/').also {
        val parsed = java.net.URI(it)
        require(parsed.scheme == "https" && parsed.host != null && parsed.rawUserInfo == null &&
            parsed.rawQuery == null && parsed.rawFragment == null && parsed.path.isNullOrEmpty())
    }
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()
    private val manifestClient = badgeManifestClient(client)
    fun cancelJson() = client.dispatcher.cancelAll()
    fun cancel() { cancelJson();manifestClient.dispatcher.cancelAll();binaryClient.dispatcher.cancelAll() }
    private val binaryClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(120, TimeUnit.SECONDS).build()
    suspend fun downloadModel(grant: BadgeGrant, signed: BadgeReceipt, proof: BadgeModelManifest,
        identity: BadgeIdentity, cache: BadgeModelCache, progress: (Long, Long) -> Unit): CachedBadgeModel {
        validateModelManifest(proof, grant)
        val path = "/v1/badges/models/${grant.grantId}/${proof.sha256}.glb"
        val request = Request.Builder().url(origin + path).header("X-Badge-Subject", identity.id)
            .header("Authorization", "Bearer ${identity.token}").header("Accept-Encoding", "identity").get().build()
        return suspendCancellableCoroutine { continuation ->
            val call = binaryClient.newCall(request); continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(BadgeClientException("NETWORK_UNAVAILABLE")) }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use {
                        if (!it.isSuccessful) throw BadgeClientException(when (it.code) {
                            403 -> "MODEL_FORBIDDEN"
                            502, 503, 504 -> "SERVICE_UNAVAILABLE"
                            else -> "MODEL_UNAVAILABLE"
                        })
                        require(!it.isRedirect && it.request.url.toString() == origin + path)
                        require(it.header("Content-Type")?.substringBefore(';')?.trim() == "model/gltf-binary")
                        require(it.body?.contentLength() == proof.bytes)
                        cache.commit(grant, signed, it.body!!.byteStream(), { continuation.isActive }, progress)
                    } }
                    if (continuation.isActive) result.fold({ continuation.resume(it) }, {
                        continuation.resumeWithException(when (it) {
                            is BadgeClientException -> it
                            is IOException -> BadgeClientException("NETWORK_UNAVAILABLE")
                            else -> BadgeClientException("MODEL_INVALID")
                        })
                    })
                }
            })
        }
    }
    override suspend fun call(path: String, method: String, body: JsonObject?, identity: BadgeIdentity?): JsonObject {
        require(path.startsWith("/v1/badges/") && !path.contains('?') && !path.contains('#'))
        val request = Request.Builder().url(origin + path).header("X-Badge-Grant-Protocols", "1,2").header("X-Badge-Ownership-Protocol", "1").apply {
            if (identity != null) { header("X-Badge-Subject", identity.id); header("Authorization", "Bearer ${identity.token}") }
            if (method == "POST") post((body ?: buildJsonObject { put("protocol", 1) }).toString().toRequestBody("application/json".toMediaType()))
            else require(method == "GET")
        }.build()
        return suspendCancellableCoroutine { continuation ->
            val call = (if (path.startsWith("/v1/badges/models/")) manifestClient else client).newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(BadgeClientException("NETWORK_UNAVAILABLE"))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            require((it.body?.contentLength() ?: 0) <= 262144)
                            val raw = it.peekBody(262145).string(); require(raw.toByteArray().size <= 262144)
                            val root = badgeJson.parseToJsonElement(raw).jsonObject
                            if (!it.isSuccessful) throw BadgeClientException(root["error"]?.jsonPrimitive?.contentOrNull?.takeIf { code -> code.matches(Regex("[A-Z_]{1,40}")) } ?: "SERVICE_UNAVAILABLE")
                            require(root["protocol"]?.jsonPrimitive?.intOrNull == 1)
                            root
                        }
                    }
                    if (continuation.isActive) result.fold({ continuation.resume(it) }, {
                        continuation.resumeWithException(if (it is BadgeClientException) it else BadgeClientException("SERVICE_UNAVAILABLE"))
                    })
                }
            })
        }
    }
}
internal data class BadgeUiState(val grant: BadgeGrant? = null, val campaign: BadgeCampaign = BadgeCampaign(),
    val busy: Boolean = false, val message: String? = null, val reward: String? = null, val profile: BadgeProfileInfo? = null,
    val collection: List<BadgeGrant> = emptyList(), val featuredGrantIds: List<String> = emptyList(), val nextCursor: String? = null, val displaySlots: List<String?> = emptyList(),
    val operationLabel: String? = null, val messageIsError: Boolean = false, val collectingAwardId: String? = null,
    val syncDeferred: Boolean = false, val automaticClaimConsumed: Boolean = false, val backupNeeded: Boolean = false)
internal class BadgeViewModel(application: Application) : AndroidViewModel(application) {
    private val experience = if (BuildConfig.BADGE_EXPERIENCE) BadgeExperienceTransport(application) else null
    private val config = runCatching { application.assets.open("badges/service.json").bufferedReader().use {
        badgeJson.decodeFromString<BadgeServiceConfiguration>(it.readText())
    } }.getOrDefault(BadgeServiceConfiguration())
    private val transport = if (experience != null) null else runCatching { if (config.endpoint.isBlank() || config.receiptKeys.isEmpty()) null else HttpBadgeTransport(config.endpoint) }.getOrNull()
    private val wallet = BadgeWallet(AndroidBadgeWalletStore(application), experience ?: transport,
        BadgeReceiptVerifier(experience?.verifierKeys ?: config.receiptKeys), BuildConfig.VERSION_CODE,
        createIdentity = { experience?.identity ?: newBadgeIdentity() })
    private val mutable = MutableStateFlow(BadgeUiState())
    val state: StateFlow<BadgeUiState> = mutable
    private var work: Job? = null
    private var workIsRefresh = false
    private var awardAcknowledgement: Job? = null
    private var operationGeneration = 0
    private val modelVerifier = BadgeReceiptVerifier(experience?.verifierKeys ?: config.receiptKeys)
    private val modelCache = runCatching { BadgeModelCache(File(application.noBackupFilesDir, "badge_model_cache"), modelVerifier) }.getOrNull()
    private val modelMutable = MutableStateFlow<Map<String, BadgeModelUiState>>(emptyMap())
    val models: StateFlow<Map<String, BadgeModelUiState>> = modelMutable
    private val modelJobs = mutableMapOf<String, Job>()
    private val modelEpoch = java.util.concurrent.atomic.AtomicInteger(0)
    private val modelQueue = kotlinx.coroutines.sync.Semaphore(2)
    private var modelAttempt = 0L
    fun prepareModel(grantId: String, retry: Boolean = false) {
        val grant = mutable.value.collection.firstOrNull { it.grantId == grantId } ?: return
        if (grant.badgeId == FIRST_LIGHT_BADGE || grant.protocol != 2 && grant.badgeId !in setOf("clear-sky-crown", "together-with-you")) return
        if (!shouldPrepareBadgeModel(modelMutable.value[grantId], retry, modelJobs[grantId]?.isActive == true)) return
        val epoch = modelEpoch.get()
        val attempt = ++modelAttempt
        val job = viewModelScope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            val ownerJob = coroutineContext[Job]!!
            val deadline = watchBadgeModelWork(viewModelScope, ownerJob, 180_000L,
                current = { modelEpoch.get() == epoch && modelJobs[grantId] === ownerJob },
                expired = { modelMutable.update { it + (grantId to BadgeModelUiState("error",
                    error = badgeErrorMessage("MODEL_PREPARATION_TIMEOUT"), attempt = attempt, requestedAssetId = grant.modelAssetId)) } })
            var retryCurrentAsset = false
            try {
                modelMutable.update { it + (grantId to BadgeModelUiState("loading", attempt = attempt, requestedAssetId = grant.modelAssetId)) }
                val cache = modelCache ?: throw BadgeClientException("MODEL_STORAGE_UNAVAILABLE")
                val cached = withinBadgeModelBudget(30_000L) { withContext(Dispatchers.IO) { cache.cached(grant) } }
                val model = cached ?: withBadgeModelPermit(modelQueue) {
                    withinBadgeModelBudget(120_000L) { retryBadgeModelTransfer {
                    val access = withContext(Dispatchers.IO) { wallet.modelAccess(grantId) }
                    val downloaded = if (experience != null) withContext(Dispatchers.IO) { experience.model(access.second, cache) } else {
                        val api = transport ?: throw BadgeClientException("NOT_CONFIGURED")
                        val response = withContext(Dispatchers.IO) { api.call("/v1/badges/models/$grantId/manifest", "GET", null, access.first) }
                        val signed = badgeJson.decodeFromJsonElement<BadgeReceipt>(response.getValue("manifest"))
                        val proof = modelVerifier.verifyModel(signed, access.second)
                        withBadgeModelAsset(proof.sha256) {
                            withContext(Dispatchers.IO) { cache.reuseVerifiedAsset(access.second, signed) }
                                ?: api.downloadModel(access.second, signed, proof, access.first, cache) { received, total ->
                                    if (modelEpoch.get() == epoch && modelJobs[grantId] === ownerJob && mutable.value.collection.any { it.grantId == grantId }) modelMutable.update { it + (grantId to BadgeModelUiState("loading", received = received, total = total, attempt = attempt, requestedAssetId = grant.modelAssetId)) }
                                }
                        }
                    }
                    if (!withContext(Dispatchers.IO) { wallet.modelAccessStillCurrent(access.first, grantId) }) throw BadgeClientException("MODEL_IDENTITY_CHANGED")
                    downloaded
                    } }
                }
                val current = mutable.value.collection.firstOrNull { it.grantId == grantId }
                if (modelEpoch.get() == epoch && modelJobs[grantId] === ownerJob && current != null) {
                    // A signed asset binding may change while a cached file or old download is in flight.
                    retryCurrentAsset = current.modelAssetId != null && current.modelAssetId != model.manifest.assetId
                    modelMutable.update { it + (grantId to if (retryCurrentAsset) BadgeModelUiState() else BadgeModelUiState("ready", model, attempt = attempt, requestedAssetId = grant.modelAssetId)) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (modelEpoch.get() == epoch && modelJobs[grantId] === ownerJob) modelMutable.update { it + (grantId to BadgeModelUiState("error", error = badgeErrorMessage((e as? BadgeClientException)?.code), attempt = attempt, requestedAssetId = grant.modelAssetId)) } }
            finally {
                deadline.cancel()
                if (modelEpoch.get() == epoch && modelJobs[grantId] === ownerJob) {
                    modelJobs.remove(grantId)
                    if (retryCurrentAsset) prepareModel(grantId)
                }
            }
        }
        modelJobs[grantId] = job; job.start()
    }
    private fun prepareFeaturedModels(retryFailed: Boolean = false) {
        mutable.value.displaySlots.filterNotNull().take(3).forEach {
            prepareModel(it, retryFailed && modelMutable.value[it]?.status == "error")
        }
    }
    private fun removeRevokedModels(owned: List<BadgeGrant>) {
        val ids = owned.map { it.grantId }.toSet(); val removed = modelMutable.value.keys - ids
        removed.forEach { modelJobs.remove(it)?.cancel(); viewModelScope.launch(Dispatchers.IO) { modelCache?.forget(it) } }
        modelMutable.update { it.filterKeys { id -> id in ids } }
    }
    private fun apply(result: BadgeResult) {
        val changed = result.collection.filter { grant -> badgeModelBindingChanged(modelMutable.value[grant.grantId], grant) }
            .map { it.grantId }.toSet()
        changed.forEach { modelJobs.remove(it)?.cancel() }
        if (changed.isNotEmpty()) modelMutable.update { it.filterKeys { id -> id !in changed } }
        mutable.value = mutable.value.copy(grant = result.grant, campaign = result.campaign,
        reward = result.grant?.grantId.takeIf { result.celebrate }, message = null, messageIsError = false, profile = result.profile, collection = result.collection,
        featuredGrantIds = result.featuredGrantIds, nextCursor = result.nextCursor, displaySlots = result.displaySlots,
        syncDeferred = result.syncDeferred, automaticClaimConsumed = result.automaticClaimConsumed,
        backupNeeded = result.backupNeeded); removeRevokedModels(result.collection); prepareFeaturedModels() }
    fun open(retryFailedModels: Boolean = false) = run(awardInterruptible = true) {
        apply(withContext(Dispatchers.IO) { wallet.cached() })
        apply(withContext(Dispatchers.IO) { automaticAward(tolerateOffline = false) })
        if (retryFailedModels) prepareFeaturedModels(retryFailed = true)
    }
    // A user who stays on the collection page must also recover from a failed first claim.
    fun syncOwnership() = run(quiet = true, awardInterruptible = true) {
        apply(withContext(Dispatchers.IO) { automaticAward() })
    }
    fun claim() = run { apply(withContext(Dispatchers.IO) { wallet.claim() }); apply(withContext(Dispatchers.IO) { wallet.refresh() }) }
    fun redeem(code: String) = run { val before=mutable.value.collection.map { it.grantId }.toSet();apply(withContext(Dispatchers.IO) { wallet.redeem(code) });mutable.value.collection.filter { it.grantId !in before }.forEach { prepareModel(it.grantId) } }
    fun collectionPage(badgeId: String? = null, more: Boolean = false) = run { apply(withContext(Dispatchers.IO) { wallet.collectionPage(badgeId, more) }) }
    fun feature(grantId: String) = run { apply(withContext(Dispatchers.IO) { wallet.feature(grantId) }) }
    fun place(grantId: String?, slot: Int) = run { apply(withContext(Dispatchers.IO) { wallet.place(grantId, slot) }) }
    private suspend fun automaticAward(tolerateOffline: Boolean = true) =
        wallet.automaticAward(deviceModel, deviceName, claimDeviceKey, tolerateOffline) {
            if (experience != null) null else currentBadgeLocation(getApplication())
        }
    /** Foreground acquisition is independent of presentation. Closed campaigns never create a profile or collect a fix. */
    fun checkAutomaticAward() = run(quiet = true) {
        apply(withContext(Dispatchers.IO) { wallet.cached() })
        apply(withContext(Dispatchers.IO) { automaticAward() })
    }
    fun retryFirstLight() = run(label = "正在核对初光领取资格") {
        val result = withContext(Dispatchers.IO) { automaticAward(tolerateOffline = false) }
        apply(result)
        if (result.collection.none { it.badgeId == FIRST_LIGHT_BADGE }) {
            throw BadgeClientException(if (!result.campaign.eligible(BuildConfig.VERSION_CODE)) "CLAIM_CLOSED" else "AUTOMATIC_ALREADY_CLAIMED")
        }
    }
    private val claimDeviceKey: String? get() = if (experience != null) "c".repeat(64) else runCatching {
        badgeClaimDeviceKey(android.provider.Settings.Secure.getString(getApplication<android.app.Application>().contentResolver,
            android.provider.Settings.Secure.ANDROID_ID))
    }.getOrNull()
    private val deviceModel: String get() = android.os.Build.MODEL.take(80)
    private val deviceName: String get() = (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).take(80)
    fun registerDevice(thenClaim: Boolean = false) = run {
        apply(withContext(Dispatchers.IO) { wallet.registerDevice(deviceModel, deviceName, currentBadgeLocation(getApplication())) })
        apply(withContext(Dispatchers.IO) { if (thenClaim) wallet.claim(claimDeviceKey = claimDeviceKey) else wallet.refresh() })
    }
    fun recover(code: String) = run { cancelModels();apply(withContext(Dispatchers.IO) { wallet.recover(code, deviceModel, deviceName) }); mutable.value = mutable.value.copy(message = "已恢复原有徽章档案") }
    fun recoverInstance(code: String) = run(label = "正在转入这枚徽章") {
        apply(withContext(Dispatchers.IO) { wallet.recoverInstance(code) })
        mutable.value = mutable.value.copy(message = "已加入收藏，其他徽章均已保留。请为这枚徽章保存新的恢复码")
    }
    fun instanceRecoveryCode(grantId: String, onReady: (String) -> Unit) = run(label = "正在准备单枚恢复码") {
        onReady(withContext(Dispatchers.IO) { wallet.instanceRecoveryText(grantId) })
    }
    fun prepareRecoveryBackup(onReady: (BadgeRecoveryBackup) -> Unit) = run(label = "正在准备所有恢复码") {
        val backup = withContext(Dispatchers.IO) { wallet.recoveryBackup() }
        apply(withContext(Dispatchers.IO) { wallet.cached() })
        onReady(backup)
    }
    fun backupSaved(backup: BadgeRecoveryBackup) = viewModelScope.launch {
        try {
            val result = withContext(Dispatchers.IO) { wallet.confirmBackupSaved(backup) }
            mutable.value = mutable.value.copy(backupNeeded = result.backupNeeded,
                message = "已保存 ${backup.entries.size} 枚徽章的恢复码", messageIsError = false)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutable.value = mutable.value.copy(message = "恢复码文件已写出，备份提示状态未能保存；请保留该文件", messageIsError = false) }
    }
    fun backupSaveFailed() { mutable.value = mutable.value.copy(message = "文件未保存成功，请重试并检查目标文件", messageIsError = true) }
    suspend fun recoveryCode(): String? = withContext(Dispatchers.IO) { wallet.recoveryText() }
    fun pendingAwards() = run(quiet = true) { apply(withContext(Dispatchers.IO) { wallet.cached() }) }
    fun updateFirstLightId(grantId: String, value: String, onSaved: () -> Unit) = run(label = "正在保存自定义 ID") {
        apply(withContext(Dispatchers.IO) { wallet.updateFirstLightId(grantId, value) }); onSaved()
    }
    fun consumeReward() { mutable.value = mutable.value.copy(reward = null) }
    private fun interruptWalletRefresh(force: Boolean = false) {
        if (!force && work?.isActive == true && !workIsRefresh) return
        ++operationGeneration; work?.cancel(); transport?.cancelJson()
        mutable.value = mutable.value.copy(busy = false, operationLabel = null)
    }
    fun collectAward(grantId: String, onCollected: () -> Unit) {
        if (mutable.value.collectingAwardId != null) return
        awardAcknowledgement?.cancel()
        interruptWalletRefresh(force = true)
        mutable.value = mutable.value.copy(collectingAwardId = grantId)
        run(label = "正在放入收藏") {
            try {
                apply(withContext(Dispatchers.IO) { wallet.collectAward(grantId) })
                onCollected()
            } finally { mutable.value = mutable.value.copy(collectingAwardId = null) }
        }
    }
    fun automaticAwardPresented(grantId: String) {
        if (mutable.value.collectingAwardId != null) return
        // A presentation acknowledgement must not cancel other medals' model downloads.
        interruptWalletRefresh()
        awardAcknowledgement?.cancel()
        awardAcknowledgement = viewModelScope.launch {
            try { withContext(Dispatchers.IO) { wallet.markAutomaticAwardPresented(grantId) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Keep the durable pending presentation if storage is unavailable. */ }
            consumeReward()
        }
    }
    fun clearMessage() { mutable.value = mutable.value.copy(message = null) }
    private fun run(quiet: Boolean = false, label: String? = null, awardInterruptible: Boolean = false, operation: suspend () -> Unit) {
        if (work?.isActive == true) return
        workIsRefresh = awardInterruptible
        val generation = ++operationGeneration
        mutable.value = mutable.value.copy(busy = true, message = null, messageIsError = false, operationLabel = label)
        // Wallet/HTTP work is dispatched above; state updates and lifecycle cancellation stay on Main.
        work = viewModelScope.launch {
            try { operation() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (!quiet) mutable.value = mutable.value.copy(message = badgeErrorMessage((e as? BadgeClientException)?.code), messageIsError = true)
            }
            finally { if (generation == operationGeneration) mutable.value = mutable.value.copy(busy = false, operationLabel = null) }
        }
    }
    private fun cancelModels() { modelEpoch.incrementAndGet();modelJobs.values.forEach { it.cancel() };modelJobs.clear();modelMutable.update { it.mapValues { (_,v) -> if(v.status=="loading") BadgeModelUiState() else v } } }
    fun stop() { cancelModels();++operationGeneration; work?.cancel(); transport?.cancel(); mutable.value = mutable.value.copy(busy = false, reward = null, operationLabel = null, collectingAwardId = null) }
    override fun onCleared() { stop(); super.onCleared() }
}
internal fun badgeErrorMessage(code: String?): String = when (code) {
    "NO_BADGES_TO_BACKUP" -> "当前没有可保存恢复码的徽章"
    "BACKUP_COLLECTION_CHANGED" -> "收藏已发生变化，请重新保存恢复码"
    "INVALID_INSTANCE_CODE" -> "请输入这枚徽章的 ZB1 恢复码；旧版 FL1 档案码请使用旧版档案恢复"
    "INSTANCE_CODE_UNAVAILABLE" -> "恢复码已失效或徽章已转移，请使用当前持有者保存的新恢复码"
    "GRANT_NOT_OWNED", "OWNERSHIP_CHANGED" -> "这枚徽章的归属已改变，请刷新收藏"
    "ALREADY_OWNED" -> "这枚徽章已在你的收藏中，无需再次转入"
    "CLIENT_UPGRADE_REQUIRED" -> "请更新应用后使用逐枚恢复"
    "DEVICE_CHECK_REQUIRED" -> "暂时无法核验本机领取资格，已有徽章和恢复码仍可使用"
        "AUTOMATIC_ALREADY_CLAIMED" -> "本档案已领取过初光，转移后不会重复补发；仍可转入其他编号"
    "MODEL_STORAGE_UNAVAILABLE" -> "模型缓存暂不可用，请检查存储空间；权益仍保留"
    "MODEL_PREPARATION_TIMEOUT" -> "模型准备较久，请重试；徽章仍在收藏中"
    "MODEL_FORBIDDEN", "MODEL_IDENTITY_CHANGED" -> "请先同步徽章档案，再重试模型下载"
    "MODEL_UNAVAILABLE", "MODEL_NOT_CONFIGURED" -> "专属模型暂未就绪，权益仍保留"
    "MODEL_INVALID", "INVALID_MODEL_MANIFEST" -> "模型校验未通过，未展示损坏资源，请重试"
    "INVALID_GIFT_CODE", "GIFT_UNAVAILABLE" -> "赠送码无效、已使用、已作废或已过期"
    "REGISTER_PROFILE_FIRST" -> "请先登记或恢复您的徽章档案，再兑换赠送码"
    "ENTITLEMENT_REVOKED" -> "此枚徽章已被收回，联网同步后已更新收藏"
    "FEATURED_LIMIT" -> "精选展位最多三枚，请先取消一枚；完整收藏数量不受此限制"
    "BADGE_DISABLED" -> "此徽章仍为草稿，尚未开放"
    "NOT_CONFIGURED" -> "徽章服务尚未开放"
    "CLAIM_CLOSED" -> "本次领取尚未开放或已经结束，已领取徽章不受影响"
    "BUILD_NOT_ELIGIBLE" -> "当前构建不在本次领取范围内"
    "INVALID_RECOVERY", "FORBIDDEN", "INVALID_IDENTITY" -> "恢复码或访问凭据无效，请核对后重试"
    "WALLET_UNREADABLE" -> "本机档案暂时无法读取，请使用已保存的恢复码恢复；原文件已保留"
    "INVALID_RECEIPT", "RECEIPT_CONFLICT" -> "徽章凭证未通过校验，未发放或替换本机权益"
    "INVALID_ENGRAVING" -> "请填写1至32字的 ID，不含特殊控制字符"
    "ID_NOT_ALLOWED" -> "这个 ID 含有不适合使用的词语或引流信息，请换一个"
    "METADATA_CHANGED" -> "ID 已被更新，请刷新徽章后再修改"
    "RATE_LIMIT" -> "请求较多，请稍后再试"
    else -> "暂时无法连接徽章服务；已领取徽章可离线查看。领取超时可安全重试"
}
