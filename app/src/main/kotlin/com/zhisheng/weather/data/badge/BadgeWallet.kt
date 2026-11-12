package com.zhisheng.weather.data.badge

import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

internal const val FIRST_LIGHT_BADGE = "first-light-016"
internal val OWNER_EDITABLE_BADGES = setOf(FIRST_LIGHT_BADGE, "clear-sky-ring")
internal const val FIRST_LIGHT_EDITION = "初光 · 0.1.6"
internal val badgeJson = Json { ignoreUnknownKeys = true }
private val badgeWalletOperationMutex = Mutex() // Also serialize two activity/ViewModel instances in one process.
@Serializable internal data class BadgeIdentity(val id: String, val token: String, val recoveryCode: String)
@Serializable internal data class BadgeReceipt(val keyId: String, val payload: String, val signature: String)
@Serializable internal data class BadgeGrant(val protocol: Int, val badgeId: String, val edition: String,
    val grantId: String, val subjectId: String, val serial: Long, val claimedAt: Long, val allocation: String = "standard", val kind: String? = null,
    val definitionRevision: Int? = null, val definitionHash: String? = null, val ownershipRevision: Long = 0, val engravingPublicId: String? = null,
    val metadataRevision: Long = 0, val modelAssetId: String? = null)
internal data class BadgeRevocation(val grantId: String, val ownershipRevision: Long?) {
    fun removes(grant: BadgeGrant) = grantId == grant.grantId && (ownershipRevision == null || ownershipRevision >= grant.ownershipRevision)
}
@Serializable internal data class BadgeInstanceCode(val code: String, val ownershipRevision: Long)
@Serializable internal data class BadgePendingTransfer(val requestId: String, val code: String, val newCode: String)
@Serializable internal data class BadgeDevice(val installationId: String, val model: String, val name: String,
    val noticeVersion: String, val location: BadgeLocation? = null)
@Serializable internal data class BadgeLocation(val latitude: Double, val longitude: Double,
    val accuracyMeters: Float? = null, val capturedAt: Long)
internal data class BadgeProfileInfo(val subjectId: String, val device: BadgeDevice)
@Serializable internal data class BadgeLocalState(val identity: BadgeIdentity? = null, val receipt: BadgeReceipt? = null,
    val celebratedGrantId: String? = null, val device: BadgeDevice? = null, val profileRegistered: Boolean = false,
    val pendingAutomaticAwardId: String? = null, val pendingAwardIds: List<String> = emptyList(), val receipts: List<BadgeReceipt> = emptyList(),
    val revocations: List<BadgeReceipt> = emptyList(), val featuredGrantIds: List<String> = emptyList(),
    val collectionCursor: String? = null, val displaySlots: List<String?> = emptyList(),
    val instanceCodes: Map<String, BadgeInstanceCode> = emptyMap(), val pendingTransfer: BadgePendingTransfer? = null,
    val automaticClaimExhausted: Boolean = false, val registeredClaimDeviceKey: String? = null,
    val backedUpCodeDigests: Map<String, String> = emptyMap())
@Serializable internal data class BadgeCampaign(val protocol: Int = 1, val badgeId: String = FIRST_LIGHT_BADGE,
    val edition: String = FIRST_LIGHT_EDITION, val state: String = "not_configured", val opensAt: Long = 0,
    val closesAt: Long = 0, val serverTime: Long = 0, val claimEnabled: Boolean = false,
    val allowedBuildCodes: List<Int> = emptyList(), val claimMode: String = "scheduled") {
    fun eligible(build: Int) = protocol == 1 && badgeId == FIRST_LIGHT_BADGE && state == "open" &&
        claimEnabled && (claimMode == "manual" || claimMode == "scheduled" && build in allowedBuildCodes)
}
internal data class BadgeResult(val grant: BadgeGrant?, val campaign: BadgeCampaign = BadgeCampaign(),
    val celebrate: Boolean = false, val profile: BadgeProfileInfo? = null,
    val collection: List<BadgeGrant> = emptyList(), val featuredGrantIds: List<String> = emptyList(),
    val nextCursor: String? = null, val displaySlots: List<String?> = emptyList(),
    val syncDeferred: Boolean = false, val automaticClaimConsumed: Boolean = false, val backupNeeded: Boolean = false)
internal class BadgeClientException(val code: String) : Exception(code)
internal interface BadgeWalletStore { suspend fun load(): BadgeLocalState; suspend fun save(value: BadgeLocalState) }
internal interface BadgeTransport {
    suspend fun call(path: String, method: String = "GET", body: JsonObject? = null, identity: BadgeIdentity? = null): JsonObject
}
internal fun badgeSecret(): String = ByteArray(32).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
internal fun newBadgeIdentity() = BadgeIdentity(UUID.randomUUID().toString(), badgeSecret(), badgeSecret())
internal fun badgeRecoveryText(identity: BadgeIdentity) = "FL1.${identity.id}.${identity.recoveryCode}"
internal fun parseBadgeInstanceCode(raw: String): String {
    val code = raw.trim()
    if (raw.length > 160 || !code.matches(Regex("ZB1\\.[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}\\.[a-f0-9]{64}")))
        throw BadgeClientException("INVALID_INSTANCE_CODE")
    return code
}
internal fun parseBadgeRecovery(raw: String): BadgeIdentity {
    if (raw.length > 160) throw BadgeClientException("INVALID_RECOVERY")
    val parts = raw.trim().split('.')
    if (parts.size != 3 || parts[0] != "FL1" || !parts[1].matches(Regex("[a-fA-F0-9-]{36}")) ||
        !parts[2].matches(Regex("[a-fA-F0-9]{64}"))) throw BadgeClientException("INVALID_RECOVERY")
    val id = runCatching { UUID.fromString(parts[1]).toString() }.getOrNull() ?: throw BadgeClientException("INVALID_RECOVERY")
    return BadgeIdentity(id, badgeSecret(), parts[2].lowercase())
}
internal fun badgeSerialText(serial: Long): String {
    require(serial in 1L..999999999999L)
    return "NO." + serial.toString().padStart(6, '0')
}
/** Verify the exact signed bytes; never infer ownership from a local flag or displayed number. */
internal class BadgeReceiptVerifier(private val trustedKeys: Map<String, String>) {
    private fun signedBytes(receipt: BadgeReceipt, limit: Int = 4096): ByteArray {
            require(receipt.keyId.length <= 64 && receipt.payload.length <= limit && receipt.signature.length <= 100)
            val bytes = Base64.getUrlDecoder().decode(receipt.payload)
            val signature = Base64.getUrlDecoder().decode(receipt.signature)
            require(signature.size == 64)
            val encoded = trustedKeys[receipt.keyId] ?: error("Unknown signing key")
            require(encoded.length <= 1024)
            val key = SubjectPublicKeyInfo.getInstance(Base64.getDecoder().decode(encoded))
            require(key.algorithm.algorithm.id == "1.3.101.112" && key.publicKeyData.bytes.size == 32)
            val verifier = Ed25519Signer().apply { init(false, Ed25519PublicKeyParameters(key.publicKeyData.bytes, 0)); update(bytes, 0, bytes.size) }
            require(verifier.verifySignature(signature))
            return bytes
    }
    fun verifyModel(receipt: BadgeReceipt, grant: BadgeGrant): BadgeModelManifest {
        try {
            var model = badgeJson.decodeFromString<BadgeModelManifest>(signedBytes(receipt, 24000).toString(Charsets.UTF_8))
            if (grant.protocol == 2) {
                val signed = model.definitionProof ?: error("Missing signed definition")
                val bytes = signedBytes(signed, 16000)
                require(java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == grant.definitionHash)
                val envelope = badgeJson.decodeFromString<BadgeDefinitionEnvelope>(bytes.toString(Charsets.UTF_8))
                require(envelope.protocol == 1 && envelope.purpose == "badge-definition-v1")
                val definition = envelope.definition;validateBadgeDefinition(definition, grant)
                require(model.assetId == definition.asset.assetId && model.assetVersion == definition.asset.version &&
                    model.sha256 == definition.asset.sha256 && model.bytes == definition.asset.bytes && model.mime == definition.asset.mime)
                model = model.copy(presentation = definition.presentation)
            }
            validateModelManifest(model, grant); return model
        }
        catch (e: BadgeClientException) { throw e }
        catch (_: Exception) { throw BadgeClientException("INVALID_MODEL_MANIFEST") }
    }
    fun verifyRevocation(receipt: BadgeReceipt, subjectId: String): String = verifyDeparture(receipt, subjectId).grantId
    fun verifyDeparture(receipt: BadgeReceipt, subjectId: String): BadgeRevocation {
        try {
            val value = badgeJson.parseToJsonElement(signedBytes(receipt).toString(Charsets.UTF_8)).jsonObject
            require(value["protocol"]?.jsonPrimitive?.int == 1 && value["kind"]?.jsonPrimitive?.content == "badge-revocation")
            require(value["subjectId"]?.jsonPrimitive?.content == subjectId && value["revokedAt"]!!.jsonPrimitive.long > 0)
            val id = value["grantId"]!!.jsonPrimitive.content; UUID.fromString(id)
            val epoch = value["ownershipRevision"]?.jsonPrimitive?.long
            require(epoch == null || epoch in 0L..9007199254740991L)
            return BadgeRevocation(id, epoch)
        } catch (_: Exception) { throw BadgeClientException("INVALID_RECEIPT") }
    }
    fun verify(receipt: BadgeReceipt, subjectId: String): BadgeGrant {
        try {
            val bytes = signedBytes(receipt)
            val grant = badgeJson.decodeFromString<BadgeGrant>(bytes.toString(Charsets.UTF_8))
            val dynamic = grant.protocol == 2 && grant.kind == "badge-grant" && grant.badgeId.matches(Regex("[a-z][a-z0-9-]{0,63}")) &&
                grant.edition.length in 1..48 && grant.edition.none { it.code < 32 } && (grant.definitionRevision ?: 0) >= 1 &&
                grant.definitionHash?.matches(Regex("[a-f0-9]{64}")) == true
            require((dynamic || grant.protocol == 1 && (grant.badgeId == FIRST_LIGHT_BADGE && grant.edition == FIRST_LIGHT_EDITION ||
                    grant.badgeId == "clear-sky-crown" && grant.edition == "晴空冠冕" ||
                    grant.badgeId == "together-with-you" && grant.edition in setOf("与君同行", "与你同行"))) &&
                grant.metadataRevision in 0L..9007199254740991L && (grant.engravingPublicId == null ||
                    grant.badgeId in setOf(FIRST_LIGHT_BADGE, "clear-sky-crown", "together-with-you", "clear-sky-ring") && grant.engravingPublicId.length in 1..32 &&
                    !Regex("[\\p{C}]").containsMatchIn(grant.engravingPublicId)) &&
                (grant.modelAssetId == null || grant.modelAssetId.matches(Regex("[a-z0-9-]{1,64}"))) &&
                grant.subjectId == subjectId && grant.claimedAt > 0 && grant.ownershipRevision in 0L..9007199254740991L &&
                (grant.serial in 11L..999999999999L && grant.allocation == "standard" ||
                    grant.serial == 1L && grant.allocation == "owner-reserved" ||
                    grant.serial in 1L..10L && grant.allocation == "admin-reserved"))
            UUID.fromString(grant.grantId)
            return grant
        } catch (_: Exception) { throw BadgeClientException("INVALID_RECEIPT") }
    }
}
/** One mutex covers persistence and network transitions, including double taps and retries. */
internal class BadgeWallet(private val store: BadgeWalletStore, private val transport: BadgeTransport?,
    private val verifier: BadgeReceiptVerifier, private val buildCode: Int,
    private val createIdentity: () -> BadgeIdentity = ::newBadgeIdentity) {
    private val mutex = badgeWalletOperationMutex
    private fun allOwned(local: BadgeLocalState): List<BadgeGrant> {
        val id = local.identity?.id ?: return emptyList()
        val revoked = local.revocations.map { verifier.verifyDeparture(it, id) }
        return (local.receipts + listOfNotNull(local.receipt)).map { verifier.verify(it, id) }
            .groupBy { it.grantId }.values.map { it.maxWith(compareBy<BadgeGrant> { grant -> grant.ownershipRevision }.thenBy { grant -> grant.metadataRevision }) }
            .filterNot { grant -> revoked.any { it.removes(grant) } }
    }
    private fun slots(local: BadgeLocalState): List<String?> {
        val ownedIds = allOwned(local).map { it.grantId }.toSet()
        val saved = if (local.displaySlots.size == 3) local.displaySlots else local.featuredGrantIds
        return (0..2).map { saved.getOrNull(it)?.takeIf { id -> id in ownedIds } }
    }
    private fun owned(local: BadgeLocalState): BadgeGrant? = allOwned(local).firstOrNull { it.badgeId == FIRST_LIGHT_BADGE }
    private fun pendingAwards(local: BadgeLocalState): List<String> {
        val current = allOwned(local).map { it.grantId }.toSet()
        return (listOfNotNull(local.pendingAutomaticAwardId) + local.pendingAwardIds).distinct().filter { it in current }
    }
    private fun result(local: BadgeLocalState, campaign: BadgeCampaign = BadgeCampaign(), celebrate: Boolean = false): BadgeResult =
        BadgeResult(allOwned(local).firstOrNull { it.grantId == pendingAwards(local).firstOrNull() } ?: owned(local), campaign, celebrate,
            profile(local), allOwned(local), slots(local).filterNotNull(), local.collectionCursor, slots(local),
            automaticClaimConsumed = local.automaticClaimExhausted,
            backupNeeded = allOwned(local).any { grant ->
                val code = local.instanceCodes[grant.grantId]?.takeIf { it.ownershipRevision == grant.ownershipRevision }?.code
                code == null || local.backedUpCodeDigests[grant.grantId] != badgeBackupDigest(code)
            })
    private fun merge(local: BadgeLocalState, response: JsonObject): BadgeLocalState {
        val id = local.identity?.id ?: throw BadgeClientException("INVALID_PROFILE")
        val received = response["receipts"]?.jsonArray.orEmpty().map { badgeJson.decodeFromJsonElement<BadgeReceipt>(it) } +
            response["items"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["receipt"]?.takeUnless { v -> v is JsonNull } }
                .map { badgeJson.decodeFromJsonElement<BadgeReceipt>(it) }
        val revoked = response["revocations"]?.jsonArray.orEmpty().map { badgeJson.decodeFromJsonElement<BadgeReceipt>(it) } +
            response["items"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["revocation"]?.takeUnless { v -> v is JsonNull } }
                .map { badgeJson.decodeFromJsonElement<BadgeReceipt>(it) }
        val receipts = (local.receipts + listOfNotNull(local.receipt) + received).groupBy { verifier.verify(it, id).grantId }
            .values.map { list -> list.maxWith(compareBy<BadgeReceipt> { verifier.verify(it, id).ownershipRevision }.thenBy { verifier.verify(it, id).metadataRevision }) }
        val tombstones = (local.revocations + revoked).groupBy { verifier.verifyDeparture(it, id).grantId }
            .values.map { list -> list.maxBy { verifier.verifyDeparture(it, id).ownershipRevision ?: Long.MAX_VALUE } }
        val departures = tombstones.map { verifier.verifyDeparture(it, id) }
        val active = receipts.filterNot { receipt -> departures.any { it.removes(verifier.verify(receipt, id)) } }
        val owned = active.map { verifier.verify(it, id) }.associateBy { it.grantId }
        val before = allOwned(local).map { it.grantId }.toSet()
        val arrived = owned.keys.filter { it !in before && it != local.celebratedGrantId }
        val queue = (pendingAwards(local) + arrived).distinct().filter { it in owned }
        val updated = local.copy(receipts = active, pendingAwardIds = queue, revocations = tombstones,
            receipt = active.firstOrNull { verifier.verify(it, id).badgeId == FIRST_LIGHT_BADGE },
            featuredGrantIds = local.featuredGrantIds.filter { it in owned },
            displaySlots = local.displaySlots.map { it?.takeIf { id -> id in owned } },
            instanceCodes = local.instanceCodes.filter { (key, code) -> owned[key]?.ownershipRevision == code.ownershipRevision },
            pendingAutomaticAwardId = queue.firstOrNull(),
            collectionCursor = response["nextCursor"]?.jsonPrimitive?.contentOrNull)
        return updated
    }
    private suspend fun syncCollection(local: BadgeLocalState, cursor: String? = null, badgeId: String? = null): BadgeLocalState {
        val api = transport ?: throw BadgeClientException("NOT_CONFIGURED")
        val id = local.identity ?: throw BadgeClientException("INVALID_PROFILE")
        val known = allOwned(local).map { it.grantId }.chunked(50).ifEmpty { listOf(emptyList()) }
        var updated = local
        var pageCursor = cursor
        var index = 0
        val completeSync = cursor == null && badgeId == null
        val visitedCursors = mutableSetOf<String>()
        do {
            val chunk = known.getOrElse(index) { emptyList() }
            val response = api.call("/v1/badges/collection", "POST", buildJsonObject {
                put("protocol", 1); put("limit", 30)
                if (pageCursor != null) put("cursor", pageCursor)
                if (badgeId != null) put("badgeId", badgeId)
                put("knownGrantIds", JsonArray(chunk.map(::JsonPrimitive)))
            }, id)
            val savedCursor = updated.collectionCursor
            updated = merge(updated, response)
            val next = response["nextCursor"]?.jsonPrimitive?.contentOrNull
            if (completeSync && next != null && !visitedCursors.add(next)) throw BadgeClientException("INVALID_RECEIPT")
            if (!completeSync && index > 0) updated = updated.copy(collectionCursor = savedCursor)
            pageCursor = if (completeSync) next else cursor
            index++
            // Walk every owned page so later awards and ID updates cannot hide behind the first 30.
            // Remaining known-ID chunks still collect signed transfer departures.
            if (completeSync && next == null && index < known.size) {
                for (remaining in known.drop(index)) {
                    val tail = api.call("/v1/badges/collection", "POST", buildJsonObject {
                        put("protocol", 1); put("limit", 1)
                        put("knownGrantIds", JsonArray(remaining.map(::JsonPrimitive)))
                    }, id)
                    updated = merge(updated, tail).copy(collectionCursor = null)
                }
                break
            }
        } while (if (completeSync) pageCursor != null else index < known.size)
        return updated
    }
    suspend fun modelAccess(grantId: String): Pair<BadgeIdentity, BadgeGrant> = mutex.withLock {
        val local=store.load();val grant=allOwned(local).firstOrNull { it.grantId==grantId } ?: throw BadgeClientException("MODEL_FORBIDDEN")
        (local.identity ?: throw BadgeClientException("INVALID_PROFILE")) to grant
    }
    suspend fun modelAccessStillCurrent(identity: BadgeIdentity, grantId: String): Boolean = mutex.withLock {
        val local=store.load();local.identity==identity && allOwned(local).any { it.grantId==grantId }
    }
    suspend fun redeem(raw: String): BadgeResult = mutex.withLock {
        val code = raw.trim()
        if (!code.matches(Regex("ZG1\\.[A-Za-z0-9_-]{43}"))) throw BadgeClientException("INVALID_GIFT_CODE")
        var local = store.load()
        if (!local.profileRegistered) throw BadgeClientException("REGISTER_PROFILE_FIRST")
        val api = transport ?: throw BadgeClientException("NOT_CONFIGURED")
        val response = api.call("/v1/badges/redeem", "POST", buildJsonObject {
            put("protocol", 1); put("requestId", UUID.randomUUID().toString()); put("code", code)
        }, local.identity)
        val receipt = response["receipt"] ?: throw BadgeClientException("INVALID_RECEIPT")
        local = merge(local, buildJsonObject { put("receipts", buildJsonArray { add(receipt) }) })
        store.save(local)
        return@withLock result(local, celebrate = pendingAwards(local).isNotEmpty())
    }
    suspend fun collectionPage(badgeId: String? = null, more: Boolean = false): BadgeResult = mutex.withLock {
        val local = store.load()
        if (more && local.collectionCursor == null) return@withLock result(local)
        val next = syncCollection(local, if (more) local.collectionCursor else null, badgeId)
        store.save(next); result(next, celebrate = pendingAwards(next).isNotEmpty())
    }
    suspend fun feature(grantId: String): BadgeResult = mutex.withLock {
        val local = store.load(); val ids = slots(local).toMutableList()
        require(allOwned(local).any { it.grantId == grantId })
        val existing = ids.indexOf(grantId)
        if (existing >= 0) ids[existing] = null else {
            val free = ids.indexOf(null); if (free < 0) throw BadgeClientException("FEATURED_LIMIT"); ids[free] = grantId
        }
        val next = local.copy(featuredGrantIds = ids.filterNotNull(), displaySlots = ids)
        store.save(next); result(next)
    }
    suspend fun place(grantId: String?, slot: Int): BadgeResult = mutex.withLock {
        require(slot in 0..2)
        val local = store.load(); val ids = slots(local).toMutableList()
        require(grantId == null || allOwned(local).any { it.grantId == grantId })
        if (grantId != null) for (i in ids.indices) if (ids[i] == grantId) ids[i] = null
        ids[slot] = grantId
        val next = local.copy(featuredGrantIds = ids.filterNotNull(), displaySlots = ids)
        store.save(next); result(next)
    }
    private fun profile(local: BadgeLocalState): BadgeProfileInfo? = if (local.profileRegistered && local.identity != null && local.device != null)
        BadgeProfileInfo(local.identity.id, local.device) else null
    private fun device(local: BadgeLocalState, model: String, name: String, location: BadgeLocation? = null): BadgeDevice {
        require(model.isNotBlank() && name.isNotBlank() && model.length <= 80 && name.length <= 80)
        return BadgeDevice(local.device?.installationId ?: UUID.randomUUID().toString(), model, name, "badge-device-location-v2", location)
    }
    suspend fun cached(): BadgeResult = mutex.withLock { val local = store.load(); result(local, celebrate = pendingAwards(local).isNotEmpty()) }
    suspend fun recoveryText(): String? = mutex.withLock { store.load().identity?.let(::badgeRecoveryText) }
    /** Called only after the document stream was flushed and closed successfully. */
    suspend fun confirmBackupSaved(backup: BadgeRecoveryBackup): BadgeResult = mutex.withLock {
        val local = store.load()
        val owned = allOwned(local).associateBy { it.grantId }
        val saved = local.backedUpCodeDigests.toMutableMap()
        for (entry in backup.entries) {
            val code = parseBadgeInstanceCode(entry.code)
            val id = code.split('.')[1]
            val current = local.instanceCodes[id]
            if (current?.code == code && current.ownershipRevision == owned[id]?.ownershipRevision)
                saved[id] = badgeBackupDigest(code)
        }
        val next = local.copy(backedUpCodeDigests = saved.filterKeys { it in owned })
        store.save(next); result(next, celebrate = pendingAwards(next).isNotEmpty())
    }
    /** Persist the replacement secret before sending it, so a lost response is safely retryable. */
    suspend fun instanceRecoveryText(grantId: String): String = mutex.withLock { instanceRecoveryTextLocked(grantId) }
    private suspend fun instanceRecoveryTextLocked(grantId: String): String {
        var local = store.load()
        val grant = allOwned(local).firstOrNull { it.grantId == grantId } ?: throw BadgeClientException("GRANT_NOT_OWNED")
        val api = transport ?: throw BadgeClientException("NOT_CONFIGURED")
        val saved = local.instanceCodes[grantId]?.takeIf { it.ownershipRevision == grant.ownershipRevision }
            ?: BadgeInstanceCode("ZB1.$grantId.${badgeSecret()}", grant.ownershipRevision)
        local = local.copy(instanceCodes = local.instanceCodes + (grantId to saved)); store.save(local)
        api.call("/v1/badges/instance-code", "POST", buildJsonObject {
            put("protocol", 1); put("requestId", UUID.randomUUID().toString()); put("grantId", grantId)
            put("expectedRevision", grant.ownershipRevision); put("code", saved.code)
        }, local.identity)
        return saved.code
    }
    suspend fun recoveryBackup(): BadgeRecoveryBackup = mutex.withLock {
        var local = syncCollection(store.load()); store.save(local)
        val grants = allOwned(local)
        if (grants.isEmpty()) throw BadgeClientException("NO_BADGES_TO_BACKUP")
        val entries = grants.map { grant ->
            BadgeRecoveryBackup.Entry(badgeDisplayName(grant), grant.serial, grant.engravingPublicId,
                instanceRecoveryTextLocked(grant.grantId))
        }
        local = syncCollection(store.load()); store.save(local)
        if (grants.map { it.grantId to it.ownershipRevision }.toSet() != allOwned(local).map { it.grantId to it.ownershipRevision }.toSet())
            throw BadgeClientException("BACKUP_COLLECTION_CHANGED")
        BadgeRecoveryBackup(entries)
    }
    suspend fun recoverInstance(raw: String): BadgeResult = mutex.withLock {
        val code = parseBadgeInstanceCode(raw); val grantId = code.split('.')[1]
        var local = store.load()
        if (!local.profileRegistered || local.identity == null) throw BadgeClientException("REGISTER_PROFILE_FIRST")
        val api = transport ?: throw BadgeClientException("NOT_CONFIGURED")
        val pending = local.pendingTransfer?.takeIf { it.code == code }
            ?: BadgePendingTransfer(UUID.randomUUID().toString(), code, "ZB1.$grantId.${badgeSecret()}")
        local = local.copy(pendingTransfer = pending); store.save(local)
        val response = api.call("/v1/badges/instance-recover", "POST", buildJsonObject {
            put("protocol", 1); put("requestId", pending.requestId); put("code", pending.code); put("newCode", pending.newCode)
        }, local.identity)
        val receipt = badgeJson.decodeFromJsonElement<BadgeReceipt>(response["receipt"] ?: throw BadgeClientException("INVALID_RECEIPT"))
        val grant = verifier.verify(receipt, local.identity!!.id)
        if (grant.grantId != grantId || grant.ownershipRevision < 1) throw BadgeClientException("INVALID_RECEIPT")
        local = merge(local, buildJsonObject { put("receipts", buildJsonArray { add(badgeJson.encodeToJsonElement(receipt)) }) })
        local = local.copy(instanceCodes = local.instanceCodes + (grantId to BadgeInstanceCode(pending.newCode, grant.ownershipRevision)), pendingTransfer = null)
        store.save(local); result(local, celebrate = pendingAwards(local).isNotEmpty())
    }
    suspend fun updateFirstLightId(grantId: String, publicId: String): BadgeResult = mutex.withLock {
        var local = store.load()
        val grant = allOwned(local).firstOrNull { it.grantId == grantId && it.badgeId in OWNER_EDITABLE_BADGES }
            ?: throw BadgeClientException("MODEL_FORBIDDEN")
        val value = publicId.trim()
        if (value.length !in 1..32 || Regex("[\\p{C}]").containsMatchIn(value)) throw BadgeClientException("INVALID_ENGRAVING")
        val response = (transport ?: throw BadgeClientException("NOT_CONFIGURED")).call("/v1/badges/engraving", "POST", buildJsonObject {
            put("protocol", 1); put("requestId", UUID.randomUUID().toString()); put("grantId", grantId)
            put("engravingPublicId", value); put("expectedMetadataRevision", grant.metadataRevision)
        }, local.identity)
        val receipt = badgeJson.decodeFromJsonElement<BadgeReceipt>(response["receipt"] ?: throw BadgeClientException("INVALID_RECEIPT"))
        val updated = verifier.verify(receipt, local.identity!!.id)
        if (updated.grantId != grantId || updated.metadataRevision <= grant.metadataRevision) throw BadgeClientException("INVALID_RECEIPT")
        local = merge(local, buildJsonObject { put("receipts", buildJsonArray { add(badgeJson.encodeToJsonElement(receipt)) }) })
        store.save(local); result(local)
    }
    suspend fun automaticAward(model: String, name: String, claimDeviceKey: String? = null, tolerateOffline: Boolean = true, location: suspend () -> BadgeLocation?): BadgeResult {
        val transient = setOf("NETWORK_UNAVAILABLE", "SERVICE_UNAVAILABLE", "TEMPORARILY_UNAVAILABLE")
        for (attempt in 0..1) {
            try { return automaticAwardOnce(model, name, claimDeviceKey, location) }
            catch (error: BadgeClientException) {
                if (attempt == 0 && error.code in transient) { kotlinx.coroutines.delay(500); continue }
                if (!tolerateOffline || error.code !in transient + setOf("NOT_CONFIGURED", "RATE_LIMIT")) throw error
                return mutex.withLock {
                    val local = store.load()
                    result(local, celebrate = pendingAwards(local).isNotEmpty()).copy(syncDeferred = true)
                }
            }
        }
        error("Unreachable automatic award retry")
    }
    private suspend fun automaticAwardOnce(model: String, name: String, claimDeviceKey: String?, location: suspend () -> BadgeLocation?): BadgeResult {
        // Existing owners still sync while online. Offline display needs no server lease.
        val current = refresh(automatic = true)
        var local = mutex.withLock { store.load() }
        // Bind historical owners even when the campaign is closed, without granting anything.
        if (local.identity != null && (local.profileRegistered || local.receipt != null) && claimDeviceKey != null && local.registeredClaimDeviceKey != claimDeviceKey) {
            bindClaimDevice(claimDeviceKey)
            local = mutex.withLock { store.load() }
        }
        if (current.collection.any { it.badgeId == FIRST_LIGHT_BADGE } || !current.campaign.eligible(buildCode)) return current
        if (local.automaticClaimExhausted) return current.copy(automaticClaimConsumed = true)
        if (claimDeviceKey == null) throw BadgeClientException("DEVICE_CHECK_REQUIRED")
        if (!local.profileRegistered) registerDevice(model, name, location())
        bindClaimDevice(claimDeviceKey)
        // The server may have committed a claim before this installation persisted
        // registration/its first reply. Sync this authenticated profile immediately;
        // the device key itself never restores another profile's ownership.
        if (mutex.withLock { store.load().automaticClaimExhausted }) return refresh(automatic = true)
        try { return claim(automatic = true, claimDeviceKey = claimDeviceKey) } // Server rechecks inside the allocation transaction.
        catch (e: BadgeClientException) {
            if (e.code != "AUTOMATIC_ALREADY_CLAIMED") throw e
            mutex.withLock { store.save(store.load().copy(automaticClaimExhausted = true)) }
            return refresh(automatic = true)
        }
    }
    suspend fun bindClaimDevice(key: String) = mutex.withLock {
        val local = store.load()
        if (local.registeredClaimDeviceKey == key) return@withLock
        if (!key.matches(Regex("[0-9a-f]{64}"))) throw BadgeClientException("DEVICE_CHECK_REQUIRED")
        val identity = local.identity ?: throw BadgeClientException("REGISTER_PROFILE_FIRST")
        val api = transport ?: throw BadgeClientException("NOT_CONFIGURED")
        val response = api.call("/v1/badges/claim-device", "POST", buildJsonObject {
            put("protocol", 1); put("deviceKey", key)
        }, identity)
        val alreadyClaimed = response["alreadyClaimed"]?.jsonPrimitive?.booleanOrNull
            ?: throw BadgeClientException("INVALID_PROFILE")
        store.save(local.copy(registeredClaimDeviceKey = key, automaticClaimExhausted = local.automaticClaimExhausted || alreadyClaimed))
    }
    suspend fun markAutomaticAwardPresented(grantId: String) = mutex.withLock {
        val local = store.load()
        if (grantId in pendingAwards(local)) {
            val queue = pendingAwards(local).filterNot { it == grantId }
            store.save(local.copy(pendingAutomaticAwardId = queue.firstOrNull(), pendingAwardIds = queue, celebratedGrantId = grantId))
        }
    }
    /** Explicit acceptance is durable even when the optional model or network is unavailable. */
    suspend fun collectAward(grantId: String): BadgeResult = mutex.withLock {
        val local = store.load()
        if (allOwned(local).none { it.grantId == grantId }) throw BadgeClientException("GRANT_NOT_OWNED")
        val queue = pendingAwards(local).filterNot { it == grantId }
        val updated = local.copy(pendingAutomaticAwardId = queue.firstOrNull(), pendingAwardIds = queue, celebratedGrantId = grantId)
        store.save(updated)
        result(updated, celebrate = queue.isNotEmpty())
    }
    /** Metadata follows the badge privacy notice; location is an actual optional system fix. */
    suspend fun registerDevice(model: String, name: String, location: BadgeLocation? = null): BadgeResult = mutex.withLock {
        val api = transport ?: throw BadgeClientException("NOT_CONFIGURED")
        var local = store.load()
        val identity = local.identity ?: createIdentity()
        local = local.copy(identity = identity, device = device(local, model, name, location))
        store.save(local) // Stable identity/installation ID even if the response is lost.
        val response = api.call("/v1/badges/profiles", "POST", buildJsonObject {
            put("protocol", 1); put("id", identity.id); put("token", identity.token); put("recoveryCode", identity.recoveryCode)
            put("device", badgeJson.encodeToJsonElement(local.device!!))
        })
        if (response["subjectId"]?.jsonPrimitive?.content != identity.id) throw BadgeClientException("INVALID_PROFILE")
        local = local.copy(profileRegistered = true); store.save(local)
        result(local)
    }
    suspend fun refresh(automatic: Boolean = false): BadgeResult = mutex.withLock {
        val local = store.load(); val old = owned(local); val api = transport ?: return@withLock result(local, celebrate = automatic && local.pendingAutomaticAwardId != null)
        val campaign = badgeJson.decodeFromJsonElement<BadgeCampaign>(api.call("/v1/badges/campaigns/$FIRST_LIGHT_BADGE"))
        val identity = local.identity?.takeIf { local.profileRegistered || local.receipt != null }
            ?: return@withLock BadgeResult(old, campaign, profile = profile(local))
        // New servers page the collection; do not download an unbounded /me list.
        val updatedCollection = try { syncCollection(local) }
        catch (e: BadgeClientException) {
            if (e.code != "NOT_FOUND") throw e
            merge(local, api.call("/v1/badges/me", identity = identity))
        }
        val arrived = allOwned(updatedCollection).any { next -> allOwned(local).none { it.grantId == next.grantId } }
        store.save(updatedCollection)
        result(updatedCollection, campaign, pendingAwards(updatedCollection).isNotEmpty() && (arrived || automatic))
    }

    suspend fun claim(automatic: Boolean = false, claimDeviceKey: String? = null): BadgeResult = mutex.withLock {
        var local = store.load()
        // A second entry can reach claim after another client already persisted it.
        // Return the full wallet snapshot, including other medals and pending awards.
        owned(local)?.let { return@withLock result(local, celebrate = automatic && pendingAwards(local).isNotEmpty()) }
        val api = transport ?: throw BadgeClientException("NOT_CONFIGURED")
        val campaign = badgeJson.decodeFromJsonElement<BadgeCampaign>(api.call("/v1/badges/campaigns/$FIRST_LIGHT_BADGE"))
        if (!campaign.eligible(buildCode)) throw BadgeClientException(if (campaign.claimEnabled) "BUILD_NOT_ELIGIBLE" else "CLAIM_CLOSED")
        val identity = local.identity ?: createIdentity().also { local = local.copy(identity = it); store.save(local) }
        // Persist the independent recovery identity BEFORE the first request, so retries keep it.
        if (!local.profileRegistered) api.call("/v1/badges/profiles", "POST", buildJsonObject {
            put("protocol", 1); put("id", identity.id); put("token", identity.token); put("recoveryCode", identity.recoveryCode)
        })
        val response = api.call("/v1/badges/$FIRST_LIGHT_BADGE/claim", "POST", buildJsonObject {
            put("protocol", 1); put("buildCode", buildCode)
            put("deviceKey", claimDeviceKey ?: local.registeredClaimDeviceKey ?: throw BadgeClientException("DEVICE_CHECK_REQUIRED"))
        }, identity)
        val receipt = badgeJson.decodeFromJsonElement<BadgeReceipt>(response["receipt"] ?: throw BadgeClientException("INVALID_RECEIPT"))
        val grant = verifier.verify(receipt, identity.id)
        // A lost first response can return newlyClaimed=false on retry. The signed
        // grant has still never been shown on this installation.
        // A first-light allocation can atomically include a signed ring drop. Verify every
        // receipt before saving any ownership; signatures, not drop flags, authorize models.
        val extras = response["receipts"]
        if (extras != null && extras !is JsonArray) throw BadgeClientException("INVALID_RECEIPT")
        val incoming = buildJsonObject { put("receipts", buildJsonArray {
            add(badgeJson.encodeToJsonElement(receipt))
            (extras as? JsonArray)?.forEach { add(it) }
        }) }
        val updated = merge(local, incoming)
        val celebrate = pendingAwards(updated).isNotEmpty()
        store.save(updated)
        result(updated, campaign, celebrate)
    }
    suspend fun recover(raw: String, model: String? = null, name: String? = null): BadgeResult = mutex.withLock {
        val identity = parseBadgeRecovery(raw); val api = transport ?: throw BadgeClientException("NOT_CONFIGURED")
        val local = store.load(); val binding = if (model != null && name != null) device(local, model, name) else local.device
        val response = api.call("/v1/badges/recover", "POST", buildJsonObject {
            put("protocol", 1); put("id", identity.id); put("recoveryCode", identity.recoveryCode); put("newToken", identity.token)
            if (binding != null) put("device", badgeJson.encodeToJsonElement(binding))
        })
        if (response["receipts"] !is JsonArray) throw BadgeClientException("INVALID_RECEIPT")
        // Validate all restored signatures before replacing any current profile.
        var restored = merge(BadgeLocalState(identity = identity, device = binding, profileRegistered = binding != null), response)
        try { restored = syncCollection(restored) }
        catch (e: BadgeClientException) { if (e.code != "NOT_FOUND") throw e }
        restored = restored.copy(celebratedGrantId = owned(restored)?.grantId, pendingAutomaticAwardId = null, pendingAwardIds = emptyList())
        store.save(restored)
        result(restored)
    }
}
