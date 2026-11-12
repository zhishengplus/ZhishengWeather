package com.zhisheng.weather.data.badge

import com.zhisheng.weather.BuildConfig

import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.*
import org.junit.Test

class BadgeWalletTest {
    private val testDeviceKey = "a".repeat(64)
    private val key = Ed25519PrivateKeyParameters(SecureRandom())
    private val publicKey = Base64.getEncoder().encodeToString(SubjectPublicKeyInfo(
        AlgorithmIdentifier(ASN1ObjectIdentifier("1.3.101.112")), key.generatePublicKey().encoded).encoded)
    private val verifier = BadgeReceiptVerifier(mapOf("test-only" to publicKey))
    private class MemoryStore(var value: BadgeLocalState = BadgeLocalState()) : BadgeWalletStore {
        var writes = 0
        override suspend fun load() = value
        override suspend fun save(value: BadgeLocalState) { this.value = value; writes++ }
    }
    private fun receipt(id: String, serial: Long = 11, edition: String = FIRST_LIGHT_EDITION, allocation: String = "standard", badgeId: String = FIRST_LIGHT_BADGE): BadgeReceipt {
        val payload = badgeJson.encodeToString(BadgeGrant(1, badgeId, edition, UUID.randomUUID().toString(), id, serial, 1000, allocation)).toByteArray()
        val signer = Ed25519Signer().apply { init(true, key); update(payload, 0, payload.size) }
        return BadgeReceipt("test-only", Base64.getUrlEncoder().withoutPadding().encodeToString(payload),
            Base64.getUrlEncoder().withoutPadding().encodeToString(signer.generateSignature()))
    }
    private fun ringReceipt(id: String): BadgeReceipt {
        val grant = BadgeGrant(2, "clear-sky-ring", "Ring fixture", UUID.randomUUID().toString(), id, 11, 1000,
            "standard", "badge-grant", 1, "a".repeat(64), modelAssetId = "clear-sky-ring-standard-v1")
        val bytes = badgeJson.encodeToString(grant).toByteArray()
        val signer = Ed25519Signer().apply { init(true, key); update(bytes, 0, bytes.size) }
        return BadgeReceipt("test-only", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
            Base64.getUrlEncoder().withoutPadding().encodeToString(signer.generateSignature()))
    }
    @Test fun ringIdUpdateKeepsSignedDefinitionAndDoesNotQueueAnotherAward() = runBlocking {
        val identity = newBadgeIdentity(); val original = ringReceipt(identity.id)
        val before = verifier.verify(original, identity.id)
        val changed = before.copy(engravingPublicId = "晨光", metadataRevision = 1)
        val bytes = badgeJson.encodeToString(changed).toByteArray()
        val signer = Ed25519Signer().apply { init(true, key); update(bytes, 0, bytes.size) }
        val signed = BadgeReceipt("test-only", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
            Base64.getUrlEncoder().withoutPadding().encodeToString(signer.generateSignature()))
        val store = MemoryStore(BadgeLocalState(identity = identity, receipts = listOf(original), profileRegistered = true))
        val api = object : BadgeTransport {
            override suspend fun call(path: String, method: String, body: JsonObject?, identity: BadgeIdentity?): JsonObject {
                assertEquals("/v1/badges/engraving", path); assertEquals(before.grantId, body?.get("grantId")?.jsonPrimitive?.content)
                assertEquals(0L, body?.get("expectedMetadataRevision")?.jsonPrimitive?.long)
                return buildJsonObject { put("receipt", badgeJson.encodeToJsonElement(signed)) }
            }
        }
        val result = wallet(store, api).updateFirstLightId(before.grantId, "晨光")
        assertEquals(changed, result.collection.single()); assertEquals(before.definitionHash, result.collection.single().definitionHash)
        assertTrue(store.value.pendingAwardIds.isEmpty()); assertFalse(result.celebrate)
    }
    @Test fun contributionDisplayRenameKeepsVerifiedOldAndNewReceipts() {
        val id = newBadgeIdentity().id
        for (edition in listOf("与君同行", "与你同行")) {
            val signed = receipt(id, edition = edition, badgeId = "together-with-you")
            assertEquals("together-with-you", verifier.verify(signed, id).badgeId)
        }
    }
    @Test fun contributionRenameDoesNotTrustUnknownEditionsOrBadSignatures() {
        val id = newBadgeIdentity().id
        expectCode("INVALID_RECEIPT") { verifier.verify(receipt(id, edition = "unknown", badgeId = "together-with-you"), id) }
        val signed = receipt(id, edition = "与君同行", badgeId = "together-with-you")
        expectCode("INVALID_RECEIPT") { verifier.verify(signed.copy(signature = "invalid"), id) }
    }
    private inner class Api : BadgeTransport {
        var campaign = BadgeCampaign(state = "open", claimEnabled = true, allowedBuildCodes = listOf(20261106), opensAt = 1, closesAt = 2000)
        var saved: BadgeReceipt? = null
        var claimCalls = 0
        var fail = false
        var loseClaimReply = false
        var badReceipt = false
        var extraFactory: ((String) -> BadgeReceipt)? = null
        var extra: BadgeReceipt? = null
        var profileCalls = 0
        var loseProfileReply = false
        var deviceAlreadyClaimed = false
        var deviceBindCalls = 0
        var lastDevice: JsonObject? = null
        var registerSawPersistedIdentity: (() -> Boolean)? = null
        override suspend fun call(path: String, method: String, body: JsonObject?, identity: BadgeIdentity?): JsonObject {
            if (fail) throw BadgeClientException("NETWORK_UNAVAILABLE")
            return when {
                path.contains("/campaigns/") -> badgeJson.encodeToJsonElement(campaign).jsonObject
                path.endsWith("/profiles") -> { profileCalls++; lastDevice = body?.get("device")?.jsonObject; assertTrue(registerSawPersistedIdentity?.invoke() ?: true)
                    if (loseProfileReply) { loseProfileReply = false; throw BadgeClientException("NETWORK_UNAVAILABLE") }
                    buildJsonObject { put("protocol", 1); put("subjectId", body!!.getValue("id")) } }
                path.endsWith("/claim-device") -> { deviceBindCalls++; assertEquals(testDeviceKey, body!!.getValue("deviceKey").jsonPrimitive.content); buildJsonObject { put("alreadyClaimed", deviceAlreadyClaimed) } }
                path.endsWith("/claim") -> {
                    claimCalls++
                    val isNew = saved == null
                    if (saved == null) saved = receipt(identity!!.id)
                    if (extra == null) extra = extraFactory?.invoke(identity!!.id)
                    if (loseClaimReply) { loseClaimReply = false; throw BadgeClientException("NETWORK_UNAVAILABLE") }
                    buildJsonObject { put("protocol", 1); put("newlyClaimed", isNew); put("receipt", badgeJson.encodeToJsonElement(
                        if (badReceipt) saved!!.copy(signature = "invalid") else saved!!))
                        extra?.let { ring -> putJsonArray("receipts") {
                            add(badgeJson.encodeToJsonElement(saved!!)); add(badgeJson.encodeToJsonElement(ring))
                        } }
                    }
                }
                path.endsWith("/me") || path.endsWith("/recover") -> buildJsonObject {
                    put("protocol", 1); putJsonArray("receipts") { saved?.let { add(badgeJson.encodeToJsonElement(it)) } }
                }
                path.endsWith("/collection") -> throw BadgeClientException("NOT_FOUND") // Simulate a legacy server.
                else -> error("Unexpected test path")
            }
        }
    }
    @Test fun reinstallCannotAutomaticallyClaimAgainAndNeverUsesDeviceKeyToRecover() = runBlocking {
        val api = Api().apply { deviceAlreadyClaimed = true }
        val store = MemoryStore()
        val result = wallet(store, api).automaticAward("test", "test", testDeviceKey) { null }
        assertNull(result.grant); assertEquals(0, api.claimCalls); assertTrue(store.value.automaticClaimExhausted)
        wallet(store, api).automaticAward("test", "test", testDeviceKey) { error("No second registration") }
        assertEquals(1, api.deviceBindCalls); assertEquals(1, api.profileCalls)
    }
    @Test fun unavailableDeviceIdentityFailsClosedWithoutCreatingAnAutomaticProfile() = runBlocking {
        val api = Api(); val store = MemoryStore()
        expectCode("DEVICE_CHECK_REQUIRED") { runBlocking { wallet(store, api).automaticAward("test", "test") { error("No location") } } }
        assertEquals(0, api.claimCalls); assertEquals(0, api.profileCalls); assertNull(store.value.identity)
    }
    @Test fun legacyOwnerUpgradeBindsDeviceWithoutReplacingReceiptOrGrantingAgain() = runBlocking {
        val store = MemoryStore(); val api = Api(); val client = wallet(store, api)
        val original = client.claim(claimDeviceKey = testDeviceKey).grant
        assertFalse(store.value.profileRegistered)
        api.campaign = BadgeCampaign(state = "closed")
        assertEquals(original, client.automaticAward("test", "test", testDeviceKey) { error("Existing owner") }.grant)
        assertEquals(1, api.claimCalls); assertEquals(1, api.deviceBindCalls)
        assertEquals(original, wallet(store).cached().grant)
    }
    private fun wallet(store: MemoryStore, api: BadgeTransport? = Api(), build: Int = 20261106) = BadgeWallet(store, api, verifier, build)
    @Test fun shippingBuildClaimsForNewAndPreviouslyRegisteredUnclaimedInstallsWithoutLosingOfflineOwnership() = runBlocking {
        for (registeredBeforeUpgrade in listOf(false, true)) {
            val api = Api().apply { campaign = BadgeCampaign(state = "open", claimEnabled = true,
                claimMode = "manual", allowedBuildCodes = listOf(20261108, 20261109, 20261110, 20261111)) }
            val store = MemoryStore()
            if (registeredBeforeUpgrade) wallet(store, api, 20261147).registerDevice("test", "test", null)
            val previousIdentity = store.value.identity
            val current = wallet(store, api, BuildConfig.VERSION_CODE)
            val result = current.automaticAward("test", "test", testDeviceKey) { null }
            val grant = result.grant!!
            assertEquals(1, result.collection.size); assertTrue(result.celebrate)
            if (previousIdentity != null) assertEquals(previousIdentity, store.value.identity)
            current.collectAward(grant.grantId)
            api.fail = true
            val offline = current.automaticAward("test", "test", testDeviceKey) { error("No new registration") }
            assertEquals(grant, offline.grant); assertFalse(offline.celebrate)
            api.fail = false
            assertEquals(grant, current.automaticAward("test", "test", testDeviceKey) { error("No new registration") }.grant)
            assertEquals(1, api.claimCalls); assertEquals(1, api.profileCalls)
        }
    }
    @Test fun oldPublicVersionUpgradeWithNoWalletAutomaticallyClaimsAndKeepsTheSameGrantOnLaterUpdates() = runBlocking {
        val api = Api().apply { campaign = BadgeCampaign(state = "open", claimEnabled = true, claimMode = "manual") }
        val store = MemoryStore()
        val upgraded = wallet(store, api, 20261151).automaticAward("old phone", "old phone", testDeviceKey) { null }
        assertTrue(upgraded.celebrate); assertEquals(1, upgraded.collection.size)
        val identity = store.value.identity; val original = upgraded.grant!!
        wallet(store, api, 20261151).collectAward(original.grantId)
        val later = wallet(store, api, 20261152).automaticAward("old phone", "old phone", testDeviceKey) { error("Already registered") }
        assertEquals(identity, store.value.identity); assertEquals(original, later.grant)
        assertFalse(later.celebrate); assertEquals(1, api.profileCalls); assertEquals(1, api.claimCalls)
    }
    @Test fun firstInstallRegistrationTimeoutRetriesTheSameProfileWithoutWaitingForRestart() = runBlocking {
        val api = Api().apply { loseProfileReply = true }; val store = MemoryStore()
        var firstIdentity: BadgeIdentity? = null
        api.registerSawPersistedIdentity = {
            if (firstIdentity == null) firstIdentity = store.value.identity
            firstIdentity == store.value.identity
        }
        val first = wallet(store, api).automaticAward("test", "test", testDeviceKey) { null }
        val identity = store.value.identity; val device = store.value.device
        assertEquals(firstIdentity, identity); assertTrue(first.celebrate); assertTrue(store.value.profileRegistered)
        val retried = wallet(store, api).automaticAward("test", "test", testDeviceKey) { null }
        assertEquals(identity, store.value.identity); assertEquals(device, store.value.device)
        assertTrue(retried.celebrate); assertEquals(1, api.claimCalls); assertEquals(2, api.profileCalls)
    }
    @Test fun anOldRegisteredButUnclaimedProfileAutomaticallyResumesInsteadOfNeedingAManualGift() = runBlocking {
        val api = Api(); val store = MemoryStore(); wallet(store, api).registerDevice("test", "test")
        val before = store.value.identity
        val result = wallet(store, api).automaticAward("test", "test", testDeviceKey) { error("Metadata already persisted") }
        assertEquals(before, store.value.identity); assertNotNull(result.grant); assertTrue(result.celebrate)
        assertEquals(1, api.profileCalls); assertEquals(1, api.deviceBindCalls); assertEquals(1, api.claimCalls)
    }
    @Test fun lostRegistrationAndClaimRepliesRecoverTheSameProfilesAwardOnTheFirstRetry() = runBlocking {
        val api = Api(); val store = MemoryStore(); val client = wallet(store, api)
        api.loseClaimReply = true
        expectCode("NETWORK_UNAVAILABLE") { runBlocking { client.claim(claimDeviceKey = testDeviceKey) } }
        // Legacy/manual paths could persist the identity but not registration.
        assertFalse(store.value.profileRegistered); assertNull(store.value.receipt)
        api.deviceAlreadyClaimed = true
        val result = wallet(store, api).automaticAward("test", "test", testDeviceKey) { null }
        assertNotNull(result.grant); assertTrue(result.celebrate)
        assertEquals(api.saved, store.value.receipt); assertEquals(1, api.claimCalls)
    }
    @Test fun failedAutomaticCheckThenReconnectRetriesWithoutClearingWalletOrChangingIdentity() = runBlocking {
        val api = Api(); val store = MemoryStore(); wallet(store, api).registerDevice("test", "test")
        val before = store.value
        api.fail = true
        val deferred = wallet(store, api).automaticAward("test", "test", testDeviceKey) { null }
        assertNull(deferred.grant); assertTrue(deferred.syncDeferred)
        assertEquals(before, store.value)
        api.fail = false
        assertNotNull(wallet(store, api).automaticAward("test", "test", testDeviceKey) { error("Already registered") }.grant)
        assertEquals(before.identity, store.value.identity); assertEquals(1, api.claimCalls)
    }
    @Test fun concurrentAutomaticEntrypointsBothReturnTheWholeCollectionWithOneGrant() = runBlocking {
        val api = Api(); val store = MemoryStore(); val a = wallet(store, api); val b = wallet(store, api)
        val results = listOf(
            async { a.automaticAward("test", "test", testDeviceKey) { yield(); null } },
            async { b.automaticAward("test", "test", testDeviceKey) { yield(); null } },
        ).awaitAll()
        assertEquals(1, api.claimCalls)
        assertTrue(results.all { it.collection.size == 1 && it.grant == it.collection.single() })
        assertEquals(1, results.map { it.grant!!.grantId }.distinct().size)
        assertEquals(1, store.value.pendingAwardIds.size)
    }
    @Test fun idempotentClaimAndDeviceRegistrationKeepTheRingAndPendingInitialInTheirResults() = runBlocking {
        val store = MemoryStore(); val api = Api().apply { extraFactory = ::ringReceipt }; val client = wallet(store, api)
        val first = client.claim(claimDeviceKey = testDeviceKey)
        val retried = client.claim(automatic = true, claimDeviceKey = testDeviceKey)
        assertEquals(first.collection, retried.collection); assertTrue(retried.celebrate)
        val registered = client.registerDevice("test", "test")
        assertEquals(first.collection, registered.collection)
        assertEquals(2, store.value.pendingAwardIds.size); assertEquals(1, api.claimCalls)
    }
    @Test fun ordinaryClaimMergesSignedRingAndQueuesEachArrivalOnlyOnce() = runBlocking {
        val store = MemoryStore(); val api = Api().apply { extraFactory = ::ringReceipt }; val client = wallet(store, api)
        val reply = client.claim(claimDeviceKey = testDeviceKey)
        assertEquals(setOf(FIRST_LIGHT_BADGE, "clear-sky-ring"), reply.collection.map { it.badgeId }.toSet())
        assertEquals(2, store.value.pendingAwardIds.size); assertEquals(2, store.value.receipts.size)
        for (id in store.value.pendingAwardIds.toList()) client.markAutomaticAwardPresented(id)
        assertFalse(client.cached().celebrate); client.claim(claimDeviceKey = testDeviceKey)
        assertEquals(1, api.claimCalls); assertEquals(2, client.cached().collection.size)
        assertTrue(store.value.pendingAwardIds.isEmpty())
    }
    @Test fun invalidExtraRingCannotPartiallyPersistTheOrdinaryAward() = runBlocking {
        val store = MemoryStore(); val api = Api().apply { extraFactory = { ringReceipt(it).copy(signature = "invalid") } }
        expectCode("INVALID_RECEIPT") { runBlocking { wallet(store, api).claim(claimDeviceKey = testDeviceKey) } }
        assertNull(store.value.receipt); assertTrue(store.value.receipts.isEmpty()); assertTrue(store.value.pendingAwardIds.isEmpty())
        assertNotNull(store.value.identity) // Retry keeps its independently persisted profile.
    }
    @Test fun lostClaimReplyReturnsTheSamePrimaryAndRingAfterRetry() = runBlocking {
        val store = MemoryStore(); val api = Api().apply { extraFactory = ::ringReceipt; loseClaimReply = true }; val client = wallet(store, api)
        expectCode("NETWORK_UNAVAILABLE") { runBlocking { client.claim(claimDeviceKey = testDeviceKey) } }
        val identity = store.value.identity!!.id; val original = api.extra!!
        val result = client.claim(claimDeviceKey = testDeviceKey)
        assertEquals(identity, store.value.identity!!.id); assertEquals(2, result.collection.size)
        assertTrue(store.value.receipts.contains(original)); assertEquals(2, store.value.pendingAwardIds.size)
    }
    private fun expectCode(code: String, action: () -> Unit) {
        try { action(); fail("Expected a client error") } catch (e: BadgeClientException) { assertEquals(code, e.code) }
    }
    @Test fun serialNumbersNeverUseReservedOrFixedDemoValue() {
        assertEquals("NO.000011", badgeSerialText(11)); assertEquals("NO.000066", badgeSerialText(66));
        assertEquals("NO.1000000", badgeSerialText(1000000));
        try { badgeSerialText(0); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun manualSwitchAcceptsAnySupportedBuildButClosedAndUnknownModesStayClosed() {
        val campaign = BadgeCampaign(state = "open", claimEnabled = true, claimMode = "manual")
        assertTrue(campaign.eligible(20261112)); assertTrue(campaign.eligible(20271112))
        assertFalse(campaign.copy(state = "closed").eligible(20261112))
        assertFalse(campaign.copy(claimEnabled = false).eligible(20261112))
        assertFalse(campaign.copy(claimMode = "future-mode").eligible(20261112))
    }
    @Test fun automaticAwardWaitsForAnOpenCampaignBeforeDeviceOrLocationCollection() = runBlocking {
        val api = Api().apply { campaign = BadgeCampaign(state = "closed") }; val store = MemoryStore()
        val result = wallet(store, api).automaticAward("test", "test", testDeviceKey) { error("Must not request a fix") }
        assertNull(result.grant); assertNull(store.value.identity); assertEquals(0, api.profileCalls)
    }
    @Test fun automaticAwardRegistersMetadataAndPresentsOnlyOnceAfterAcknowledgement() = runBlocking {
        val api = Api().apply { campaign = BadgeCampaign(state = "open", claimEnabled = true, claimMode = "manual") }
        val store = MemoryStore(); val client = wallet(store, api, 20261112)
        val fix = BadgeLocation(0.0, 0.0, 100f, 1000)
        val first = client.automaticAward("test model", "test maker", testDeviceKey) { fix }
        assertNotNull(first.grant); assertTrue(first.celebrate)
        assertEquals("test model", api.lastDevice?.get("model")?.jsonPrimitive?.content)
        assertEquals(0.0, api.lastDevice?.get("location")?.jsonObject?.get("latitude")?.jsonPrimitive?.double ?: -1.0, 0.0)
        val restarted = wallet(store, api, 20261112)
        assertTrue(restarted.automaticAward("test", "test", testDeviceKey) { error("Owner must not collect again") }.celebrate)
        restarted.markAutomaticAwardPresented(first.grant!!.grantId)
        assertFalse(restarted.automaticAward("test", "test", testDeviceKey) { error("Owner must not collect again") }.celebrate)
        assertEquals(1, api.claimCalls)
    }
    @Test fun automaticAwardSucceedsWithoutLocationAndRetriesTheSameIdentity() = runBlocking {
        val api = Api(); val store = MemoryStore(); val client = wallet(store, api)
        api.loseClaimReply = true
        val recovered = client.automaticAward("test", "test", testDeviceKey) { null }
        assertNotNull(recovered.grant); assertFalse(recovered.syncDeferred)
        val identity = store.value.identity
        val retry = client.automaticAward("test", "test", testDeviceKey) { null }
        assertNotNull(retry.grant); assertTrue(retry.celebrate); assertEquals(identity, store.value.identity)
        assertEquals(1, api.claimCalls)
    }
    @Test fun explicitAwardRetryReportsNetworkFailureAndReusesRegisteredIdentity() = runBlocking {
        val api = Api(); val store = MemoryStore(); val client = wallet(store, api)
        client.registerDevice("test", "test")
        val identity = store.value.identity
        api.fail = true
        assertNull(client.automaticAward("test", "test", testDeviceKey) { null }.grant)
        expectCode("NETWORK_UNAVAILABLE") { runBlocking {
            client.automaticAward("test", "test", testDeviceKey, tolerateOffline = false) { null }
        } }
        api.fail = false
        val result = client.automaticAward("test", "test", testDeviceKey, tolerateOffline = false) { null }
        assertNotNull(result.grant); assertTrue(result.celebrate)
        assertEquals(identity, store.value.identity); assertEquals(1, api.profileCalls); assertEquals(1, api.claimCalls)
        client.automaticAward("test", "test", testDeviceKey, tolerateOffline = false) { null }
        assertEquals(1, api.claimCalls)
    }
    @Test fun validSignedReceiptIsBoundToSubjectAndFixedEdition() {
        val id = newBadgeIdentity().id; val signed = receipt(id)
        assertEquals(11L, verifier.verify(signed, id).serial)
        expectCode("INVALID_RECEIPT") { verifier.verify(signed, newBadgeIdentity().id) }
        expectCode("INVALID_RECEIPT") { verifier.verify(receipt(id, edition = "初光 · 0.1.7"), id) }
    }
    @Test fun tamperedPayloadSignatureAndUnknownKeysAreRejected() {
        val id = newBadgeIdentity().id; val signed = receipt(id)
        expectCode("INVALID_RECEIPT") { verifier.verify(signed.copy(payload = signed.payload.dropLast(8) + "AAAAAAAA"), id) }
        expectCode("INVALID_RECEIPT") { verifier.verify(signed.copy(signature = "invalid"), id) }
        expectCode("INVALID_RECEIPT") { verifier.verify(signed.copy(keyId = "untrusted"), id) }
        expectCode("INVALID_RECEIPT") { verifier.verify(receipt(id, serial = 1), id) }
    }
    @Test fun noConfiguredServiceCannotPretendToGrant() = runBlocking {
        val store = MemoryStore(); val client = wallet(store, null)
        assertNull(client.cached().grant)
        expectCode("NOT_CONFIGURED") { runBlocking { client.claim(claimDeviceKey = testDeviceKey) } }
        assertEquals(0, store.writes)
    }
    @Test fun claimPersistsRecoveryIdentityBeforeNetworkAndAwardsOnlyOnce() = runBlocking {
        val store = MemoryStore(); val api = Api(); api.registerSawPersistedIdentity = { store.value.identity != null }
        val client = wallet(store, api); val first = client.claim(claimDeviceKey = testDeviceKey); val second = client.claim(claimDeviceKey = testDeviceKey)
        assertNotNull(first.grant); assertTrue(first.celebrate); assertFalse(second.celebrate)
        assertEquals(first.grant, second.grant); assertEquals(1, api.claimCalls)
        assertNotNull(client.recoveryText())
    }
    @Test fun repeatedConcurrentTapsDoNotDuplicateGrantOrReward() = runBlocking {
        val api = Api(); val client = wallet(MemoryStore(), api)
        val results = (1..20).map { async { client.claim(claimDeviceKey = testDeviceKey) } }.awaitAll()
        assertEquals(1, api.claimCalls); assertEquals(1, results.count { it.celebrate })
        assertEquals(1, results.map { it.grant?.grantId }.distinct().size)
    }
    @Test fun twoActivityClientsShareOneWalletTransitionAndIdentity() = runBlocking {
        val api = Api(); val store = MemoryStore(); val a = wallet(store, api); val b = wallet(store, api)
        val results = listOf(async { a.claim(claimDeviceKey = testDeviceKey) }, async { b.claim(claimDeviceKey = testDeviceKey) }).awaitAll()
        assertEquals(1, api.claimCalls); assertEquals(1, results.count { it.celebrate })
        assertEquals(results[0].grant, results[1].grant)
    }
    @Test fun timeoutNeverShowsSuccessAndRetryUsesSameIdentityAndSerial() = runBlocking {
        val store = MemoryStore(); val api = Api().apply { loseClaimReply = true }; val client = wallet(store, api)
        expectCode("NETWORK_UNAVAILABLE") { runBlocking { client.claim(claimDeviceKey = testDeviceKey) } }
        assertNull(store.value.receipt); val id = store.value.identity!!.id
        val retried = client.claim(claimDeviceKey = testDeviceKey); assertEquals(id, retried.grant!!.subjectId)
        // The first reply was lost: this is the first verified presentation,
        // even though the server correctly reports an idempotent existing grant.
        assertEquals(11L, retried.grant.serial); assertTrue(retried.celebrate)
    }
    @Test fun invalidClaimReceiptNeverPersistsOwnershipOrReward() = runBlocking {
        val store = MemoryStore(); val api = Api().apply { badReceipt = true }; val client = wallet(store, api)
        expectCode("INVALID_RECEIPT") { runBlocking { client.claim(claimDeviceKey = testDeviceKey) } }
        assertNull(store.value.receipt); assertNull(store.value.celebratedGrantId)
    }
    @Test fun upgradeAndOfflineReopenKeepOwnershipWithoutAnyNetworkCall() = runBlocking {
        val store = MemoryStore(); val first = wallet(store).claim(claimDeviceKey = testDeviceKey).grant
        val upgraded = wallet(store, null, build = 20261107)
        assertEquals(first, upgraded.cached().grant); assertEquals(first, upgraded.refresh().grant)
        assertEquals(first, upgraded.claim(claimDeviceKey = testDeviceKey).grant)
    }
    @Test fun closedCampaignStillAllowsExistingOwnerAndRecovery() = runBlocking {
        val store = MemoryStore(); val api = Api(); val client = wallet(store, api); val owned = client.claim(claimDeviceKey = testDeviceKey).grant
        val code = client.recoveryText()!!; api.campaign = BadgeCampaign(state = "closed")
        assertEquals(owned, client.refresh().grant)
        val recoveredStore = MemoryStore(); val recovered = wallet(recoveredStore, api, 20261107).recover(code)
        assertEquals(owned, recovered.grant); assertFalse(recovered.celebrate)
        assertEquals(store.value.identity!!.id, recoveredStore.value.identity!!.id)
        assertNotEquals(store.value.identity!!.token, recoveredStore.value.identity!!.token)
    }
    @Test fun notStartedClosedAndWrongBuildNeverSendClaim() = runBlocking {
        for (state in listOf("closed", "not_started", "ended", "not_configured")) {
            val api = Api().apply { campaign = BadgeCampaign(state = state) }; val store = MemoryStore()
            expectCode("CLAIM_CLOSED") { runBlocking { wallet(store, api).claim(claimDeviceKey = testDeviceKey) } }
            assertEquals(0, api.claimCalls); assertNull(store.value.identity)
        }
        val api = Api(); expectCode("BUILD_NOT_ELIGIBLE") { runBlocking { wallet(MemoryStore(), api, 20261107).claim(claimDeviceKey = testDeviceKey) } }
        assertEquals(0, api.claimCalls)
    }
    @Test fun badRecoveryAndNetworkFailureLeaveExistingWalletUntouched() = runBlocking {
        val store = MemoryStore(); val api = Api(); val client = wallet(store, api); client.claim(claimDeviceKey = testDeviceKey); val before = store.value
        expectCode("INVALID_RECOVERY") { runBlocking { client.recover("000066") } }
        assertEquals(before, store.value)
        api.fail = true
        expectCode("NETWORK_UNAVAILABLE") { runBlocking { client.recover(badgeRecoveryText(before.identity!!)) } }
        assertEquals(before, store.value)
    }
    @Test fun freshInstallWithoutIdentityDoesNotInventExistingOwnership() = runBlocking {
        val client = wallet(MemoryStore()); assertNull(client.cached().grant); assertNull(client.refresh().grant)
    }
    @Test fun invalidRefreshedReceiptDoesNotSilentlyReplaceOrEraseValidOfflineReceipt() = runBlocking {
        val store = MemoryStore(); val api = Api(); val client = wallet(store, api); val grant = client.claim(claimDeviceKey = testDeviceKey).grant
        val before = store.value; api.saved = api.saved!!.copy(signature = "invalid")
        expectCode("INVALID_RECEIPT") { runBlocking { client.refresh() } }
        assertEquals(before, store.value); assertEquals(grant, client.cached().grant)
    }
    @Test fun recoveryCodeParserIsBoundedAndDoesNotTreatSerialAsCredential() {
        val identity = newBadgeIdentity(); val parsed = parseBadgeRecovery(badgeRecoveryText(identity))
        assertEquals(identity.id, parsed.id); assertEquals(identity.recoveryCode, parsed.recoveryCode)
        for (code in listOf("NO.000011", "FL1.bad.abc", "x".repeat(200))) expectCode("INVALID_RECOVERY") { parseBadgeRecovery(code) }
    }
    @Test fun actualNodeServerReceiptVerifiesWithAndroidCodec() {
        val raw = javaClass.classLoader!!.getResourceAsStream("badge/node-receipt.json")!!.bufferedReader().use { it.readText() }
        val fixture = badgeJson.parseToJsonElement(raw).jsonObject
        assertTrue(fixture.getValue("testOnly").jsonPrimitive.boolean)
        val receipt = badgeJson.decodeFromJsonElement<BadgeReceipt>(fixture.getValue("receipt"))
        val checked = BadgeReceiptVerifier(mapOf("node-fixture-only" to fixture.getValue("publicKey").jsonPrimitive.content))
            .verify(receipt, fixture.getValue("subjectId").jsonPrimitive.content)
        assertEquals(11L, checked.serial); assertEquals(FIRST_LIGHT_EDITION, checked.edition)
    }
    @Test fun ownerOneNeedsExplicitSignedAllocationAndOtherReservationsRemainInvalid() {
        val id = newBadgeIdentity().id
        assertEquals(1L, verifier.verify(receipt(id, 1, allocation = "owner-reserved"), id).serial)
        assertEquals("NO.000001", badgeSerialText(1))
        expectCode("INVALID_RECEIPT") { verifier.verify(receipt(id, 1), id) }
        for (n in 2L..10L) expectCode("INVALID_RECEIPT") { verifier.verify(receipt(id, n, allocation = "owner-reserved"), id) }
    }
    @Test fun administratorReservationsRequireRealSignatureSubjectAndAllocation() {
        val id = newBadgeIdentity().id
        for (n in 1L..10L) {
            val signed = receipt(id, n, allocation = "admin-reserved")
            assertEquals(n, verifier.verify(signed, id).serial)
            assertEquals("NO." + n.toString().padStart(6, '0'), badgeSerialText(n))
            expectCode("INVALID_RECEIPT") { verifier.verify(signed.copy(signature = "invalid"), id) }
            expectCode("INVALID_RECEIPT") { verifier.verify(signed, newBadgeIdentity().id) }
            expectCode("INVALID_RECEIPT") { verifier.verify(receipt(id, n), id) }
        }
        for (n in listOf(0L, 11L, 1000000000000L))
            expectCode("INVALID_RECEIPT") { verifier.verify(receipt(id, n, allocation = "admin-reserved"), id) }
    }
    @Test fun reservedTenKeepsSignedOwnershipAfterUpgradeOfflineAndRecovery() = runBlocking {
        val store = MemoryStore(); val api = Api().apply { campaign = BadgeCampaign(state = "closed") }
        val client = wallet(store, api); client.registerDevice("test-model", "test-name")
        api.saved = receipt(store.value.identity!!.id, 10, allocation = "admin-reserved")
        val first = client.refresh(); assertTrue(first.celebrate); assertFalse(client.refresh().celebrate)
        assertEquals(first.grant, wallet(store, null, 20261111).cached().grant)
        assertEquals(first.grant, wallet(MemoryStore(), api).recover(client.recoveryText()!!).grant)
    }
    @Test fun openingClosedPageDoesNotCreateProfileOrCollectDevice() = runBlocking {
        val store = MemoryStore(); val api = Api().apply { campaign = BadgeCampaign(state = "closed") }; val client = wallet(store, api)
        client.cached(); client.refresh(); assertEquals(0, api.profileCalls); assertEquals(0, store.writes); assertNull(store.value.device)
    }
    @Test fun explicitClosedRegistrationIsIdempotentAndPersistsPublicProofWithoutClaiming() = runBlocking {
        val store = MemoryStore(); val api = Api().apply { campaign = BadgeCampaign(state = "closed") }; val client = wallet(store, api)
        val first = client.registerDevice("test-model", "test-name"); val second = client.registerDevice("test-model", "test-name")
        assertEquals(first.profile, second.profile); assertTrue(store.value.profileRegistered); assertEquals(0, api.claimCalls)
        assertNotNull(first.profile); assertNull(first.grant); assertFalse(first.celebrate)
        assertEquals(setOf("installationId", "model", "name", "noticeVersion"), api.lastDevice!!.keys)
        assertEquals("badge-device-location-v2", api.lastDevice!!.getValue("noticeVersion").jsonPrimitive.content)
        assertEquals(first.profile, wallet(store, api, 20261111).cached().profile)
    }
    @Test fun failedRegistrationRetriesSameIdentityAndDeviceButNeverMarksRegisteredEarly() = runBlocking {
        val store = MemoryStore(); val api = Api().apply { fail = true }; val client = wallet(store, api)
        expectCode("NETWORK_UNAVAILABLE") { runBlocking { client.registerDevice("test-model", "test-name") } }
        val before = store.value; assertNotNull(before.identity); assertNotNull(before.device); assertFalse(before.profileRegistered)
        api.fail = false; client.registerDevice("test-model", "test-name")
        assertEquals(before.identity, store.value.identity); assertEquals(before.device, store.value.device); assertTrue(store.value.profileRegistered)
    }
    @Test fun newlyFetchedOwnerOneCelebratesOnceAndSurvivesOfflineUpgradeAndRecovery() = runBlocking {
        val store = MemoryStore(); val api = Api().apply { campaign = BadgeCampaign(state = "closed") }; val client = wallet(store, api)
        client.registerDevice("test-model", "test-name"); val id = store.value.identity!!.id
        api.saved = receipt(id, 1, allocation = "owner-reserved")
        val first = client.refresh(); assertEquals(1L, first.grant!!.serial); assertTrue(first.celebrate); assertFalse(client.refresh().celebrate)
        assertEquals(first.grant, wallet(store, null, 20261111).cached().grant)
        val restored = wallet(MemoryStore(), api).recover(client.recoveryText()!!, "new-model", "new-name")
        assertEquals(first.grant, restored.grant); assertFalse(restored.celebrate); assertEquals(id, restored.profile!!.subjectId)
    }
    @Test fun laterCollectionPagesDiscoverAwardsAndRefreshMetadata() = runBlocking {
        val identity = newBadgeIdentity()
        val first = receipt(identity.id, 31)
        val last = receipt(identity.id, 99)
        val store = MemoryStore(BadgeLocalState(identity = identity, receipt = first, profileRegistered = true,
            celebratedGrantId = verifier.verify(first, identity.id).grantId))
        var pages = 0
        val transport = object : BadgeTransport {
            override suspend fun call(path: String, method: String, body: JsonObject?, identity: BadgeIdentity?): JsonObject =
                if (path.contains("campaigns")) badgeJson.encodeToJsonElement(BadgeCampaign(state = "closed")).jsonObject
                else buildJsonObject {
                    pages++
                    val second = body?.get("cursor")?.jsonPrimitive?.contentOrNull == "second"
                    put("receipts", badgeJson.encodeToJsonElement(listOf(if (second) last else first)))
                    if (!second) put("nextCursor", "second")
                }
        }
        val result = wallet(store, transport).refresh()
        assertEquals(2, pages); assertEquals(2, result.collection.size)
        assertTrue(result.celebrate); assertEquals(99L, result.grant!!.serial); assertNull(result.nextCursor)
    }
    @Test fun everyNewBadgeQueuesOnceAndSurvivesRestartUntilPresented() = runBlocking {
        val identity = newBadgeIdentity()
        val existing = receipt(identity.id, 1, allocation = "admin-reserved")
        val second = receipt(identity.id, 98)
        val honor = receipt(identity.id, 173, "晴空冠冕", badgeId = "clear-sky-crown")
        var values = listOf(existing, second, honor)
        val store = MemoryStore(BadgeLocalState(identity = identity, receipt = existing, profileRegistered = true,
            celebratedGrantId = verifier.verify(existing, identity.id).grantId))
        val transport = object : BadgeTransport {
            override suspend fun call(path: String, method: String, body: JsonObject?, identity: BadgeIdentity?): JsonObject =
                if (path.contains("campaigns")) badgeJson.encodeToJsonElement(BadgeCampaign(state = "closed")).jsonObject
                else buildJsonObject { put("receipts", badgeJson.encodeToJsonElement(values)) }
        }
        val first = wallet(store, transport).refresh()
        assertTrue(first.celebrate); assertEquals(98L, first.grant!!.serial)
        assertEquals(2, store.value.pendingAwardIds.size)
        val reopened = wallet(store, transport)
        assertTrue(reopened.cached().celebrate)
        reopened.markAutomaticAwardPresented(first.grant.grantId)
        val next = reopened.cached(); assertTrue(next.celebrate); assertEquals("clear-sky-crown", next.grant!!.badgeId)
        reopened.markAutomaticAwardPresented(next.grant.grantId)
        assertFalse(reopened.cached().celebrate)
        assertFalse(reopened.refresh().celebrate)
        assertEquals(3, reopened.cached().collection.size)
    }

    @Test fun backupReminderOnlyClearsForWrittenCurrentCodesAndReturnsForNewOrRotatedCodes() = runBlocking {
        val identity = newBadgeIdentity()
        val signed = receipt(identity.id)
        val grant = verifier.verify(signed, identity.id)
        val code = "ZB1.${grant.grantId}." + "a".repeat(64)
        val store = MemoryStore(BadgeLocalState(identity = identity, receipt = signed, profileRegistered = true,
            instanceCodes = mapOf(grant.grantId to BadgeInstanceCode(code, grant.ownershipRevision))))
        val client = wallet(store, null)
        val backup = BadgeRecoveryBackup(listOf(BadgeRecoveryBackup.Entry("初光", grant.serial, null, code)))
        assertTrue(client.cached().backupNeeded)
        assertTrue(wallet(store, null).cached().backupNeeded) // Opening a picker/preparing a file is not success.
        assertFalse(client.confirmBackupSaved(backup).backupNeeded)
        assertFalse(wallet(store, null).cached().backupNeeded)
        val rotatedCode = "ZB1.${grant.grantId}." + "b".repeat(64)
        store.value = store.value.copy(instanceCodes = mapOf(grant.grantId to BadgeInstanceCode(rotatedCode, grant.ownershipRevision)))
        assertTrue(client.cached().backupNeeded)
        assertTrue(client.confirmBackupSaved(backup).backupNeeded) // An old completed export cannot acknowledge a new code.
        store.value = store.value.copy(receipts = listOf(signed, receipt(identity.id, 12)))
        assertTrue(client.cached().backupNeeded)
        assertFalse(store.value.backedUpCodeDigests.values.any { it == code })
    }
}
