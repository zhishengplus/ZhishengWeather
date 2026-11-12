package com.zhisheng.weather.data.badge

import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.*
import org.junit.Test

class BadgeTransferTest {
    @Test fun backupIncludesFirstLightHonorAndDynamicRingWithSeparateCodes() = runBlocking {
        val first = grant()
        val crown = grant().copy(badgeId = "clear-sky-crown", edition = "晴空冠冕", engravingPublicId = "晨光")
        val contributor = grant().copy(badgeId = "together-with-you", edition = "与君同行", engravingPublicId = "同行者")
        val ring = grant().copy(protocol = 2, kind = "badge-grant", badgeId = "clear-sky-ring", edition = "澄空之戒",
            definitionRevision = 1, definitionHash = "a".repeat(64), modelAssetId = "clear-sky-ring-standard-v2", engravingPublicId = "晴朗")
        val backup = wallet(store(first, crown, contributor, ring), Api()).recoveryBackup()
        assertEquals(setOf("初光", "晴空冠冕", "与君同行", "澄空之戒"), backup.entries.map { it.name }.toSet())
        assertEquals(4, backup.entries.map { it.code }.toSet().size)
        assertEquals("晴朗", backup.entries.single { it.name == "澄空之戒" }.publicId)
    }
    @Test fun oneBackupIncludesEveryInstanceAndReusesCodesAcrossRepeatedExports() = runBlocking {
        val grants = (11L..85L).map { grant(it) }; val local = store(*grants.toTypedArray()); val api = Api()
        val client = wallet(local, api); val first = client.recoveryBackup()
        assertEquals(75, first.entries.size); assertEquals(75, first.entries.map { it.code }.toSet().size)
        val second = client.recoveryBackup()
        assertEquals(first.entries.map { it.code }, second.entries.map { it.code })
        assertEquals(grants.toSet(), client.cached().collection.toSet())
        assertEquals(identity, local.value.identity)
    }
    @Test fun interruptedBackupReturnsNoPartialResultAndRetriesThePersistedCodes() = runBlocking {
        val local = store(grant(66), grant(67), grant(68)); val api = Api().apply { failCodeAt = 2 }; val client = wallet(local, api)
        try { client.recoveryBackup(); fail("Must not return a partial backup") }
        catch (e: BadgeClientException) { assertEquals("NETWORK_UNAVAILABLE", e.code) }
        val pending = local.value.instanceCodes.mapValues { it.value.code }
        assertEquals(2, pending.size); api.failCodeAt = -1
        val completed = client.recoveryBackup()
        assertEquals(3, completed.entries.size)
        for ((id, code) in pending) assertEquals(code, completed.entries.single { it.code.split('.')[1] == id }.code)
    }
    @Test fun transferDuringBackupInvalidatesTheWholeExportAndEmptyCollectionIsRejected() = runBlocking {
        val original = grant(); val local = store(original); val api = Api()
        api.afterCode = { api.departures = listOf(departure(original)) }
        try { wallet(local, api).recoveryBackup(); fail("Transferred badge must not be exported") }
        catch (e: BadgeClientException) { assertEquals("BACKUP_COLLECTION_CHANGED", e.code) }
        try { wallet(local, api).recoveryBackup(); fail("Empty backup must not be exported") }
        catch (e: BadgeClientException) { assertEquals("NO_BADGES_TO_BACKUP", e.code) }
    }
    @Test fun administratorMetadataRefreshesOfflineCopyWithoutChangingOwnershipOrRecoveryCode() = runBlocking {
        val original = grant().copy(badgeId = "together-with-you", edition = "与君同行")
        val local = store(original)
        val update = original.copy(engravingPublicId = "枳生-测试", metadataRevision = 1, modelAssetId = "contributor-generic-v1")
        val api = Api().apply { incoming = listOf(receipt(update)) }
        assertEquals(update, wallet(local, api).collectionPage().collection.single())
        api.incoming = listOf(receipt(original))
        assertEquals(update, wallet(local, api).collectionPage().collection.single())
        assertEquals(update, wallet(local).cached().collection.single())
        val next = update.copy(engravingPublicId = "新的纪念 ID", metadataRevision = 2)
        api.incoming = listOf(receipt(next))
        assertEquals(next, wallet(local, api).collectionPage().collection.single())
    }
    @Test fun ownershipEpochTakesPriorityOverOlderMetadataAndMalformedIdsAreRejected() = runBlocking {
        val original = grant().copy(badgeId = "clear-sky-crown", edition = "晴空冠冕", engravingPublicId = "枳生-测试", metadataRevision = 3)
        val returned = original.copy(ownershipRevision = 2)
        val local = store(original)
        val api = Api().apply { incoming = listOf(receipt(returned)); departures = listOf(departure(original)) }
        assertEquals(returned, wallet(local, api).collectionPage().collection.single())
        for (id in listOf("", "a".repeat(33), "冒\u202e名", "换\n行")) {
            assertThrows(BadgeClientException::class.java) { verifier.verify(receipt(returned.copy(engravingPublicId = id)), identity.id) }
        }
    }
    private val key = Ed25519PrivateKeyParameters(SecureRandom())
    private val verifier = BadgeReceiptVerifier(mapOf("fixture" to Base64.getEncoder().encodeToString(
        SubjectPublicKeyInfo(AlgorithmIdentifier(ASN1ObjectIdentifier("1.3.101.112")), key.generatePublicKey().encoded).encoded)))
    private val identity = newBadgeIdentity()
    private fun signed(raw: String): BadgeReceipt {
        val bytes = raw.toByteArray(); val signer = Ed25519Signer().apply { init(true, key); update(bytes, 0, bytes.size) }
        return BadgeReceipt("fixture", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
            Base64.getUrlEncoder().withoutPadding().encodeToString(signer.generateSignature()))
    }
    private fun grant(serial: Long = 66, epoch: Long = 0, id: String = UUID.randomUUID().toString()) =
        BadgeGrant(1, FIRST_LIGHT_BADGE, FIRST_LIGHT_EDITION, id, identity.id, serial, 1000, ownershipRevision = epoch)
    private fun receipt(grant: BadgeGrant) = signed(badgeJson.encodeToString(grant))
    private fun departure(grant: BadgeGrant, epoch: Long? = grant.ownershipRevision) = signed(buildJsonObject {
        put("protocol", 1); put("kind", "badge-revocation"); put("grantId", grant.grantId); put("subjectId", identity.id)
        put("badgeId", grant.badgeId); put("revokedAt", 2000); if (epoch != null) put("ownershipRevision", epoch)
    }.toString())
    private class Store(var value: BadgeLocalState) : BadgeWalletStore {
        override suspend fun load() = value
        override suspend fun save(value: BadgeLocalState) { this.value = value }
    }
    private fun store(vararg grants: BadgeGrant) = Store(BadgeLocalState(identity = identity, profileRegistered = true,
        receipts = grants.map(::receipt), device = BadgeDevice(UUID.randomUUID().toString(), "test", "test", "test")))
    private fun wallet(store: Store, api: BadgeTransport? = null) = BadgeWallet(store, api, verifier, 20261134)
    private inner class Api : BadgeTransport {
        var incoming: List<BadgeReceipt> = emptyList()
        var departures: List<BadgeReceipt> = emptyList()
        var fail = false
        var transfer: BadgeReceipt? = null
        var lostReply = false
        var firstInput: JsonObject? = null
        var savedCode: String? = null
        val backupAttempts = mutableListOf<String>()
        var failCodeAt = -1
        var afterCode: (() -> Unit)? = null
        var claimCalls = 0
        var open = false
        override suspend fun call(path: String, method: String, body: JsonObject?, identity: BadgeIdentity?): JsonObject {
            if (fail) throw BadgeClientException("NETWORK_UNAVAILABLE")
            return when {
                path.contains("/campaigns/") -> badgeJson.encodeToJsonElement(BadgeCampaign(state = if (open) "open" else "closed", claimEnabled = open, claimMode = "manual")).jsonObject
                path.endsWith("/claim") -> { claimCalls++; throw BadgeClientException("AUTOMATIC_ALREADY_CLAIMED") }
                path.endsWith("/instance-code") -> {
                    savedCode = body!!.getValue("code").jsonPrimitive.content
                    backupAttempts += savedCode!!
                    if (backupAttempts.size == failCodeAt) throw BadgeClientException("NETWORK_UNAVAILABLE")
                    afterCode?.invoke()
                    buildJsonObject { put("protocol", 1) }
                }
                path.endsWith("/instance-recover") -> {
                    if (firstInput == null) firstInput = body else assertEquals(firstInput, body)
                    if (lostReply) { lostReply = false; throw BadgeClientException("NETWORK_UNAVAILABLE") }
                    buildJsonObject { put("receipt", badgeJson.encodeToJsonElement(transfer!!)) }
                }
                path.endsWith("/collection") -> buildJsonObject {
                    put("receipts", badgeJson.encodeToJsonElement(incoming)); put("revocations", badgeJson.encodeToJsonElement(departures))
                }
                else -> error("Unexpected request $path")
            }
        }
    }
    @Test fun multipleFirstLightsStayDistinctAndOnlyDepartedInstanceIsRemoved() = runBlocking {
        val a = grant(); val b = grant(67); val store = store(a, b); val api = Api().apply { departures = listOf(departure(a)) }
        val client = wallet(store, api)
        assertEquals(2, client.cached().collection.size)
        assertEquals(listOf(b), client.refresh().collection)
        assertEquals(listOf(b), wallet(store).cached().collection)
    }
    @Test fun returnToPreviousOwnerKeepsNewEpochAndRejectsStaleReceipts() = runBlocking {
        val a = grant(); val returned = a.copy(ownershipRevision = 2); val store = store(a)
        val api = Api().apply { incoming = listOf(receipt(returned)); departures = listOf(departure(a)) }
        assertEquals(returned, wallet(store, api).collectionPage().grant)
        api.incoming = listOf(receipt(a))
        assertEquals(returned, wallet(store, api).collectionPage().grant)
        api.departures = listOf(departure(returned, null))
        assertTrue(wallet(store, api).collectionPage().collection.isEmpty())
    }
    @Test fun offlinePlaySurvivesUntilSuccessfulOnlineSync() = runBlocking {
        val a = grant(); val store = store(a); val api = Api().apply { fail = true; departures = listOf(departure(a)) }
        assertEquals(a, wallet(store, api).automaticAward("test", "test") { error("No location needed") }.grant)
        api.fail = false
        assertNull(wallet(store, api).automaticAward("test", "test") { error("Closed campaign") }.grant)
        assertNull(wallet(store).cached().grant)
    }
    @Test fun lostTransferReplyRetriesPersistedRequestAndSecretWithoutReplacingCollection() = runBlocking {
        val existing = grant(); val incoming = grant(67, 1); val store = store(existing)
        val api = Api().apply { transfer = receipt(incoming); lostReply = true }
        val code = "ZB1.${incoming.grantId}.${badgeSecret()}"
        try { wallet(store, api).recoverInstance(code); fail() } catch (e: BadgeClientException) { assertEquals("NETWORK_UNAVAILABLE", e.code) }
        assertNotNull(store.value.pendingTransfer); assertEquals(listOf(existing), wallet(store).cached().collection)
        val retry = wallet(store, api).recoverInstance(code)
        assertEquals(identity, store.value.identity); assertEquals(setOf(existing, incoming), retry.collection.toSet())
        assertNull(store.value.pendingTransfer)
        assertEquals(api.firstInput!!.getValue("newCode").jsonPrimitive.content, store.value.instanceCodes[incoming.grantId]!!.code)
    }
    @Test fun generatedCodeSurvivesLostReplyAndIsDiscardedAfterDeparture() = runBlocking {
        val a = grant(); val store = store(a); val api = Api().apply { fail = true }
        try { wallet(store, api).instanceRecoveryText(a.grantId); fail() } catch (_: BadgeClientException) { }
        val pendingCode = store.value.instanceCodes[a.grantId]!!.code; api.fail = false
        assertEquals(pendingCode, wallet(store, api).instanceRecoveryText(a.grantId)); assertEquals(pendingCode, api.savedCode)
        api.departures = listOf(departure(a)); wallet(store, api).collectionPage()
        assertTrue(store.value.instanceCodes.isEmpty())
    }
    @Test fun invalidTransferReceiptNeverReplacesExistingCollection() = runBlocking {
        val a = grant(); val incoming = grant(67, 1); val store = store(a)
        val api = Api().apply { transfer = receipt(incoming).copy(signature = "bad") }
        try { wallet(store, api).recoverInstance("ZB1.${incoming.grantId}.${badgeSecret()}"); fail() }
        catch (e: BadgeClientException) { assertEquals("INVALID_RECEIPT", e.code) }
        assertEquals(listOf(a), wallet(store).cached().collection); assertEquals(identity, store.value.identity)
    }
    @Test fun explicitCollectWorksOfflineAndDoesNotReplayAfterRestart() = runBlocking {
        val a = grant(); val local = store(); val api = Api().apply { incoming = listOf(receipt(a)) }
        val client = wallet(local, api); client.refresh(); api.fail = true
        assertTrue(canCollectBadgeAward(a.grantId, client.cached().collection, null))
        assertFalse(canCollectBadgeAward(a.grantId, client.cached().collection, a.grantId))
        assertFalse(canCollectBadgeAward(java.util.UUID.randomUUID().toString(), client.cached().collection, null))
        val collected = client.collectAward(a.grantId)
        assertEquals(listOf(a), collected.collection); assertFalse(collected.celebrate)
        assertFalse(wallet(local, api).automaticAward("test", "test") { error("No location") }.celebrate)
        assertFalse(client.collectAward(a.grantId).celebrate)
    }
    @Test fun collectingOnePreservesOtherQueuedAwardsAndRejectsUnknownReceipt() = runBlocking {
        val a = grant(); val b = grant(67); val local = store(); val api = Api().apply { incoming = listOf(receipt(a), receipt(b)) }
        val client = wallet(local, api); client.refresh()
        val next = client.collectAward(a.grantId)
        assertTrue(next.celebrate); assertEquals(b.grantId, next.grant?.grantId)
        assertEquals(listOf(b.grantId), local.value.pendingAwardIds)
        try { client.collectAward(java.util.UUID.randomUUID().toString()); fail("Unowned receipt cannot be accepted") }
        catch (e: BadgeClientException) { assertEquals("GRANT_NOT_OWNED", e.code) }
        assertEquals(listOf(b.grantId), local.value.pendingAwardIds)
        client.collectAward(b.grantId)
        assertFalse(wallet(local).cached().celebrate)
    }
    @Test fun collectionVisitDoesNotConsumeFirstAwardBeforePresentation() = runBlocking {
        val a = grant(); val store = store(); val api = Api().apply { incoming = listOf(receipt(a)) }
        wallet(store, api).refresh()
        assertEquals(a.grantId, store.value.pendingAutomaticAwardId); assertNull(store.value.celebratedGrantId)
        val restarted = wallet(store, api)
        assertTrue(restarted.automaticAward("test", "test") { error("Already owned") }.celebrate)
        restarted.markAutomaticAwardPresented(a.grantId)
        assertFalse(restarted.automaticAward("test", "test") { error("Already owned") }.celebrate)
    }
    @Test fun previouslyPresentedUpgradeDoesNotReplayAndTransferDoesNotAutoRefill() = runBlocking {
        val a = grant(); val store = store(a); store.value = store.value.copy(celebratedGrantId = a.grantId, registeredClaimDeviceKey = "a".repeat(64))
        val api = Api().apply { open = true }
        assertFalse(wallet(store, api).automaticAward("test", "test", "a".repeat(64)) { error("No location") }.celebrate)
        api.departures = listOf(departure(a))
        repeat(3) { assertNull(wallet(store, api).automaticAward("test", "test", "a".repeat(64)) { error("Registered already") }.grant) }
        assertEquals(1, api.claimCalls); assertTrue(store.value.automaticClaimExhausted)
    }
    @Test fun archiveCodeCannotSilentlyEnterIndividualRecovery() {
        try { parseBadgeInstanceCode(badgeRecoveryText(identity)); fail() } catch (e: BadgeClientException) { assertEquals("INVALID_INSTANCE_CODE", e.code) }
    }
}
