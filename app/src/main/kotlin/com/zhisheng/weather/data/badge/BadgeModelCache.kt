package com.zhisheng.weather.data.badge

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock

// Bounded process-wide locks also cover a home/collection ViewModel overlap.
// Sharing one verified binary must not start another download for every serial.
private val badgeModelAssetLocks = Array(32) { Mutex() }
internal suspend fun <T> withBadgeModelAsset(hash: String, block: suspend () -> T): T =
    badgeModelAssetLocks[(hash.hashCode() and Int.MAX_VALUE) % badgeModelAssetLocks.size].withLock { block() }

internal suspend fun <T> retryBadgeModelTransfer(block: suspend () -> T): T {
    try { return block() }
    catch (error: BadgeClientException) {
        if (error.code !in setOf("NETWORK_UNAVAILABLE", "SERVICE_UNAVAILABLE", "TEMPORARILY_UNAVAILABLE")) throw error
        delay(750)
        return block()
    }
}

internal suspend fun <T> withinBadgeModelBudget(millis: Long, block: suspend () -> T): T = try {
    withTimeout(millis) { block() }
} catch (timeout: TimeoutCancellationException) {
    // Leaving the screen remains cancellation. An expired model budget is a
    // retryable tile failure; rethrowing it as cancellation leaves "loading" behind.
    currentCoroutineContext().ensureActive()
    throw BadgeClientException("MODEL_PREPARATION_TIMEOUT")
}

internal suspend fun <T> withBadgeModelPermit(queue: Semaphore, waitMillis: Long = 30_000L, block: suspend () -> T): T {
    var acquired = false
    try {
        withinBadgeModelBudget(waitMillis) { queue.acquire(); acquired = true }
        return block()
    } finally { if (acquired) queue.release() }
}

/** Sibling of the IO job: an uncooperative local read cannot trap the UI in loading. */
internal fun watchBadgeModelWork(scope: CoroutineScope, work: Job, millis: Long,
    current: () -> Boolean, expired: () -> Unit): Job = scope.launch {
    delay(millis)
    if (current()) { expired(); work.cancel() }
}

@Serializable internal data class BadgeModelManifest(val protocol: Int, val purpose: String,
    val subjectId: String, val grantId: String, val badgeId: String, val serial: Long,
    val assetId: String, val assetVersion: String, val modelVariant: String,
    val engravingPublicId: String? = null, val sha256: String, val bytes: Long, val mime: String,
    val definitionProof: BadgeReceipt? = null, val presentation: BadgePresentationSpec? = null)
internal data class CachedBadgeModel(val manifest: BadgeModelManifest, val file: File) {
    val assetKey: String get() = "remote-${manifest.sha256}"
}
internal data class BadgeModelUiState(val status: String = "idle", val model: CachedBadgeModel? = null,
    val received: Long = 0, val total: Long = 0, val error: String? = null, val attempt: Long = 0,
    val requestedAssetId: String? = null)
internal fun shouldPrepareBadgeModel(state: BadgeModelUiState?, retry: Boolean, running: Boolean): Boolean =
    !running && (retry || state?.status !in setOf("ready", "error"))
internal fun badgeModelBindingChanged(state: BadgeModelUiState?, grant: BadgeGrant): Boolean =
    state != null && ((state.attempt > 0 && state.requestedAssetId != grant.modelAssetId) ||
        (grant.modelAssetId != null && state.model?.manifest?.assetId?.let { it != grant.modelAssetId } == true))
internal fun validateCachedModelFile(model: CachedBadgeModel) {
    require(model.file.name == "${model.manifest.sha256}.glb" && model.file.length() == model.manifest.bytes)
    val hash=MessageDigest.getInstance("SHA-256");model.file.inputStream().use { input -> val b=ByteArray(32768);while(true){val n=input.read(b);if(n<0)break;hash.update(b,0,n)} }
    require(hash.digest().joinToString(""){ "%02x".format(it) }==model.manifest.sha256);validateDownloadedGlb(model.file)
}

internal fun validateModelManifest(value: BadgeModelManifest, grant: BadgeGrant) {
    require(value.protocol == 1 && value.purpose == "badge-model-v1" && value.subjectId == grant.subjectId &&
        (grant.modelAssetId == null || value.assetId == grant.modelAssetId) && value.grantId == grant.grantId && value.badgeId == grant.badgeId && value.serial == grant.serial)
    require((value.badgeId in setOf("clear-sky-crown", "together-with-you") || grant.protocol == 2 && value.presentation != null && value.definitionProof != null) && value.mime == "model/gltf-binary")
    require(value.assetId.matches(Regex("[a-z0-9-]{1,64}")) && value.assetVersion.matches(Regex("[a-f0-9]{64}")) &&
        value.sha256.matches(Regex("[a-f0-9]{64}")) && value.bytes in 20L..12L * 1024 * 1024 &&
        value.modelVariant.matches(Regex("[a-z0-9_-]{1,64}")) &&
        (value.engravingPublicId == null || value.engravingPublicId.length <= 48 && value.engravingPublicId.none { it.code < 32 }))
}
/** Bounded GLB inspection before GPU decode. No ZIP, external URI, compression decoder or arbitrary URL. */
internal fun validateDownloadedGlb(file: File) {
    require(file.length() in 20L..12L * 1024 * 1024)
    val bytes = file.readBytes(); val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    require(buffer.int == 0x46546C67 && buffer.int == 2 && buffer.int == bytes.size)
    val jsonLength = buffer.int; require(buffer.int == 0x4E4F534A && jsonLength in 2..2_097_152 && jsonLength <= buffer.remaining())
    val json = ByteArray(jsonLength); buffer.get(json)
    val root = badgeJson.parseToJsonElement(json.toString(Charsets.UTF_8).trimEnd(' ', '\u0000')).jsonObject
    fun noExternal(value: JsonElement) { when (value) {
        is JsonObject -> value.forEach { (key, item) -> require(key != "uri" || item is JsonNull); noExternal(item) }
        is JsonArray -> value.forEach(::noExternal)
        else -> Unit
    } }
    noExternal(root)
    val supported = setOf("KHR_materials_clearcoat", "KHR_materials_specular", "KHR_materials_transmission", "KHR_materials_ior")
    require(root["extensionsRequired"]?.jsonArray.orEmpty().all { it.jsonPrimitive.content in supported })
    require(root["extensionsUsed"]?.jsonArray.orEmpty().none { it.jsonPrimitive.content in setOf("KHR_draco_mesh_compression", "EXT_meshopt_compression") })
    val accessors = root["accessors"]?.jsonArray.orEmpty()
    require(accessors.all { (it.jsonObject["count"]?.jsonPrimitive?.longOrNull ?: 0) in 0L..2_000_000L })
    require(accessors.sumOf { it.jsonObject["count"]?.jsonPrimitive?.longOrNull ?: 0 } <= 8_000_000L)
    var binaryOffset = bytes.size; var binaryLength = 0
    if (buffer.remaining() > 0) { require(buffer.remaining() >= 8); val length = buffer.int; require(buffer.int == 0x004E4942 && length >= 0 && length == buffer.remaining()); binaryOffset = buffer.position(); binaryLength = length }
    val views = root["bufferViews"]?.jsonArray.orEmpty()
    for (view in views) { val v = view.jsonObject; require((v["buffer"]?.jsonPrimitive?.intOrNull ?: 0) == 0)
        val start = v["byteOffset"]?.jsonPrimitive?.longOrNull ?: 0; val length = v["byteLength"]?.jsonPrimitive?.longOrNull ?: -1
        require(start >= 0 && length >= 0 && start + length <= binaryLength.toLong()) }
    val images = root["images"]?.jsonArray.orEmpty(); require(images.size <= 8); var totalPixels = 0L
    for (image in images) {
        val obj = image.jsonObject; val index = obj["bufferView"]?.jsonPrimitive?.intOrNull ?: error("External model image")
        val view = views.getOrNull(index)?.jsonObject ?: error("Invalid image view")
        val offset = binaryOffset + (view["byteOffset"]?.jsonPrimitive?.int ?: 0); val length = view["byteLength"]!!.jsonPrimitive.int
        require(offset >= binaryOffset && length >= 24 && offset.toLong() + length <= bytes.size)
        // All approved models use PNG. Unknown image encodings fail explicitly instead of allocating unbounded decoders.
        require(obj["mimeType"]?.jsonPrimitive?.content == "image/png")
        require(bytes.copyOfRange(offset, offset + 8).contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10)))
        val dimensions = ByteBuffer.wrap(bytes, offset + 16, 8).order(ByteOrder.BIG_ENDIAN); val w = dimensions.int; val h = dimensions.int
        require(w in 1..4096 && h in 1..4096); totalPixels += w.toLong() * h; require(totalPixels <= 16_777_216L)
    }
}
/** Private, content-addressed cache; signed metadata is linked to the existing grant, never a new identity. */
internal class BadgeModelCache(private val directory: File, private val verifier: BadgeReceiptVerifier,
    private val openOutput: (File) -> FileOutputStream = { FileOutputStream(it) }) {
    init { require(directory.mkdirs() || directory.isDirectory) }
    private fun child(name: String): File = File(directory, name).also { require(it.canonicalFile.parentFile == directory.canonicalFile) }
    private fun metadata(grant: BadgeGrant): File { require(grant.grantId.matches(Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}"))); return child("${grant.grantId}.json") }
    fun validatedFile(proof: BadgeModelManifest): File? = runCatching {
        val file = child("${proof.sha256}.glb"); require(file.length() == proof.bytes)
        val digest = MessageDigest.getInstance("SHA-256"); file.inputStream().use { input -> val b=ByteArray(32768); while(true){val n=input.read(b);if(n<0)break;digest.update(b,0,n)} }
        require(digest.digest().joinToString("") { "%02x".format(it) } == proof.sha256); validateDownloadedGlb(file); file
    }.getOrNull()
    @Synchronized fun cached(grant: BadgeGrant): CachedBadgeModel? = runCatching {
        val meta = metadata(grant); require(meta.length() in 1..16384)
        val signed = badgeJson.decodeFromString<BadgeReceipt>(meta.readText()); val proof = verifier.verifyModel(signed, grant)
        CachedBadgeModel(proof, validatedFile(proof) ?: error("Cache invalid"))
    }.getOrNull()
    private fun saveMetadata(grant: BadgeGrant, signed: BadgeReceipt) {
        val target=metadata(grant);val temp=File.createTempFile("badge-meta-", ".part",directory)
        try { openOutput(temp).use { it.write(badgeJson.encodeToString(signed).toByteArray());it.fd.sync() }; atomic(temp,target) } finally { temp.delete() }
    }
    /** Rebind separately signed metadata to existing, fully verified bytes before HTTP download. */
    @Synchronized fun reuseVerifiedAsset(grant: BadgeGrant, signed: BadgeReceipt): CachedBadgeModel? {
        val proof = verifier.verifyModel(signed, grant)
        val file = validatedFile(proof) ?: return null
        saveMetadata(grant, signed)
        return CachedBadgeModel(proof, file)
    }
    fun commit(grant: BadgeGrant, signed: BadgeReceipt, input: InputStream, active: () -> Boolean = {true}, progress: (Long,Long) -> Unit = {_,_->}): CachedBadgeModel {
        val proof=verifier.verifyModel(signed,grant)
        synchronized(this) { validatedFile(proof)?.let { saveMetadata(grant,signed);return CachedBadgeModel(proof,it) } }
        val target=child("${proof.sha256}.glb");val temp=File.createTempFile("badge-model-", ".part",directory)
        try {
            val hash=MessageDigest.getInstance("SHA-256");var count=0L;val b=ByteArray(32768)
            openOutput(temp).use { out -> while(true){check(active()){ "MODEL_CANCELLED" };val n=input.read(b);if(n<0)break;count+=n;require(count<=proof.bytes);hash.update(b,0,n);out.write(b,0,n);progress(count,proof.bytes)};out.fd.sync() }
            require(count==proof.bytes && hash.digest().joinToString(""){ "%02x".format(it) }==proof.sha256)
            validateDownloadedGlb(temp)
            // A slow network read must not hold the cache monitor and block
            // unrelated ready badges. Only atomic publication shares the lock.
            return synchronized(this) {
                check(active()) { "MODEL_CANCELLED" }
                val file=validatedFile(proof) ?: target.also { atomic(temp,it) }
                saveMetadata(grant,signed);prune(proof.sha256)
                CachedBadgeModel(proof,file)
            }
        } finally { temp.delete() }
    }
    @Synchronized fun forget(grantId: String) { if(grantId.matches(Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")))child("$grantId.json").delete() }
    private fun atomic(from: File,to: File) { try { Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING) } catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(from.toPath(),to.toPath(),StandardCopyOption.REPLACE_EXISTING) } }
    private fun prune(protected: String) { val files=directory.listFiles().orEmpty().filter { it.name.matches(Regex("[a-f0-9]{64}\\.glb")) }.sortedBy { it.lastModified() };var total=files.sumOf { it.length() };for(file in files)if(total>96L*1024*1024&&file.name!="$protected.glb"){val size=file.length();if(file.delete())total-=size} }
}
