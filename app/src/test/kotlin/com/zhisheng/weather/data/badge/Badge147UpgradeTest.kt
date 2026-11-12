package com.zhisheng.weather.data.badge

import com.zhisheng.weather.BuildConfig
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.*
import org.junit.Test

/** Keep the pre-backup 147 wire fields fixed; this is synthetic data, never a real wallet. */
class Badge147UpgradeTest {
    @Serializable private data class Legacy147State(
        val identity: BadgeIdentity? = null, val receipt: BadgeReceipt? = null,
        val celebratedGrantId: String? = null, val device: BadgeDevice? = null,
        val profileRegistered: Boolean = false, val pendingAutomaticAwardId: String? = null,
        val pendingAwardIds: List<String> = emptyList(), val receipts: List<BadgeReceipt> = emptyList(),
        val revocations: List<BadgeReceipt> = emptyList(), val featuredGrantIds: List<String> = emptyList(),
        val collectionCursor: String? = null, val displaySlots: List<String?> = emptyList(),
        val instanceCodes: Map<String, BadgeInstanceCode> = emptyMap(),
        val pendingTransfer: BadgePendingTransfer? = null, val automaticClaimExhausted: Boolean = false,
        val registeredClaimDeviceKey: String? = null)

    private val key = Ed25519PrivateKeyParameters(SecureRandom())
    private val verifier = BadgeReceiptVerifier(mapOf("upgrade-test" to Base64.getEncoder().encodeToString(
        SubjectPublicKeyInfo(AlgorithmIdentifier(ASN1ObjectIdentifier("1.3.101.112")), key.generatePublicKey().encoded).encoded)))
    private val identity = BadgeIdentity(UUID.randomUUID().toString(), "b".repeat(64), "c".repeat(64))
    private val first = BadgeGrant(1, FIRST_LIGHT_BADGE, FIRST_LIGHT_EDITION, UUID.randomUUID().toString(),
        identity.id, 565, 1000, engravingPublicId = "旧署名甲", metadataRevision = 1)
    private val ring = BadgeGrant(2, "clear-sky-ring", "澄空之戒", UUID.randomUUID().toString(),
        identity.id, 1, 1000, allocation = "owner-reserved", kind = "badge-grant", definitionRevision = 1, definitionHash = "a".repeat(64),
        engravingPublicId = "秋", metadataRevision = 2, modelAssetId = "clear-sky-ring-standard-v1")

    private fun sign(grant: BadgeGrant): BadgeReceipt {
        val bytes = badgeJson.encodeToString(grant).toByteArray()
        val signer = Ed25519Signer().apply { init(true, key); update(bytes, 0, bytes.size) }
        return BadgeReceipt("upgrade-test", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
            Base64.getUrlEncoder().withoutPadding().encodeToString(signer.generateSignature()))
    }
    private class Store(var value: BadgeLocalState) : BadgeWalletStore {
        var writes = 0
        override suspend fun load() = value
        override suspend fun save(value: BadgeLocalState) { this.value = value; writes++ }
    }
    private fun oldState(pending: Boolean = false) = Legacy147State(identity = identity,
        receipt = sign(first), receipts = listOf(sign(first), sign(ring)), celebratedGrantId = first.grantId,
        device = BadgeDevice(UUID.randomUUID().toString(), "fixture-model", "fixture", "1"), profileRegistered = true,
        pendingAutomaticAwardId = ring.grantId.takeIf { pending }, pendingAwardIds = if (pending) listOf(ring.grantId) else emptyList(),
        featuredGrantIds = listOf(first.grantId, ring.grantId), displaySlots = listOf(first.grantId, null, ring.grantId),
        instanceCodes = mapOf(first.grantId to BadgeInstanceCode("ZB1." + "d".repeat(64), 0),
            ring.grantId to BadgeInstanceCode("ZB1." + "e".repeat(64), 0)),
        pendingTransfer = BadgePendingTransfer(UUID.randomUUID().toString(), "ZB1." + "f".repeat(64), "ZB1." + "1".repeat(64)),
        registeredClaimDeviceKey = "2".repeat(64))
    private fun decode(legacy: Legacy147State): Store {
        val raw = badgeJson.encodeToString(legacy)
        assertFalse(raw.contains("backedUpCodeDigests"))
        return Store(badgeJson.decodeFromString<BadgeLocalState>(raw))
    }
    private fun client(store: Store) = BadgeWallet(store, null, verifier, BuildConfig.VERSION_CODE) {
        error("An update must not create another profile for an existing 147 wallet")
    }

    @Test fun direct147PayloadUpgradeAndOfflineReopenPreserveEveryExistingValue() = runBlocking {
        val store = decode(oldState()); val before = store.value
        for (wallet in listOf(client(store), client(store))) {
            for (result in listOf(wallet.cached(), wallet.refresh())) {
                assertEquals(listOf(first, ring), result.collection)
                assertEquals(listOf(first.grantId, null, ring.grantId), result.displaySlots)
                assertFalse(result.celebrate)
            }
        }
        assertEquals(before, store.value); assertEquals(0, store.writes)
        assertEquals(identity, store.value.identity); assertTrue(store.value.backedUpCodeDigests.isEmpty())
    }

    @Test fun unfinished147AwardCompletesOnceWithoutReplacingCodesOrPendingTransfer() = runBlocking {
        val store = decode(oldState(pending = true)); val before = store.value
        assertTrue(client(store).cached().celebrate)
        client(store).markAutomaticAwardPresented(ring.grantId)
        assertFalse(client(store).cached().celebrate)
        assertEquals(listOf(first, ring), client(store).cached().collection)
        assertEquals(identity, store.value.identity)
        assertEquals(before.instanceCodes, store.value.instanceCodes)
        assertEquals(before.pendingTransfer, store.value.pendingTransfer)
        assertEquals(before.displaySlots, store.value.displaySlots)
        assertEquals(ring.grantId, store.value.celebratedGrantId)
    }
}
