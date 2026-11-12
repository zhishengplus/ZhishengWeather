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

class BadgeCollectionTest {
    private val key = Ed25519PrivateKeyParameters(SecureRandom())
    private val publicKey = Base64.getEncoder().encodeToString(SubjectPublicKeyInfo(
        AlgorithmIdentifier(ASN1ObjectIdentifier("1.3.101.112")), key.generatePublicKey().encoded).encoded)
    private val verifier = BadgeReceiptVerifier(mapOf("test" to publicKey))
    private val identity = newBadgeIdentity()
    private fun signed(value: String): BadgeReceipt {
        val bytes = value.toByteArray()
        val signer = Ed25519Signer().apply { init(true, key); update(bytes, 0, bytes.size) }
        return BadgeReceipt("test", Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
            Base64.getUrlEncoder().withoutPadding().encodeToString(signer.generateSignature()))
    }
    private fun receipt(type: String = FIRST_LIGHT_BADGE, serial: Long = 66): BadgeReceipt = signed(badgeJson.encodeToString(
        BadgeGrant(1, type, if (type == FIRST_LIGHT_BADGE) FIRST_LIGHT_EDITION else "晴空冠冕", UUID.randomUUID().toString(), identity.id, serial, 1000)))
    private class Store(var value: BadgeLocalState) : BadgeWalletStore {
        override suspend fun load() = value
        override suspend fun save(value: BadgeLocalState) { this.value = value }
    }
    private fun wallet(store: Store, api: BadgeTransport? = null) = BadgeWallet(store, api, verifier, 20261119)
    @Test fun oldEncryptedPayloadSchemaAndAllReceiptsSurviveMigration() = runBlocking {
        val old = BadgeLocalState(identity, receipt())
        val encoded = badgeJson.encodeToString(old)
        val store = Store(badgeJson.decodeFromString(encoded))
        assertEquals(1, wallet(store).cached().collection.size)
        assertEquals(identity, store.value.identity)
    }
    @Test fun collectionIsUnlimitedButOnlyThreeFeaturedSelectionsPersist() = runBlocking {
        val receipts = (11L..85L).map { receipt("clear-sky-crown", it) }
        val store = Store(BadgeLocalState(identity = identity, receipts = receipts))
        val client = wallet(store)
        val grants = client.cached().collection
        assertEquals(75, grants.size)
        for (grant in grants.take(3)) client.feature(grant.grantId)
        try { client.feature(grants[3].grantId); fail() } catch (e: BadgeClientException) { assertEquals("FEATURED_LIMIT", e.code) }
        assertEquals(3, wallet(store).cached().featuredGrantIds.size)
        assertEquals(75, wallet(store).cached().collection.size)
    }
    @Test fun redeemAndSignedRevocationSyncCannotResurrectOfflineOwnership() = runBlocking {
        val receipt = receipt(); val grant = verifier.verify(receipt, identity.id)
        var revoke = false
        val tombstone = signed(buildJsonObject { put("protocol", 1); put("kind", "badge-revocation"); put("grantId", grant.grantId)
            put("subjectId", identity.id); put("badgeId", FIRST_LIGHT_BADGE); put("revokedAt", 2000) }.toString())
        val api = object : BadgeTransport {
            override suspend fun call(path: String, method: String, body: JsonObject?, identity: BadgeIdentity?): JsonObject = buildJsonObject {
                put("protocol", 1)
                if (path.endsWith("/redeem")) put("receipt", badgeJson.encodeToJsonElement(receipt))
                else { put("items", buildJsonArray {}); put("revocations", buildJsonArray { if (revoke) add(badgeJson.encodeToJsonElement(tombstone)) }) }
            }
        }
        val store = Store(BadgeLocalState(identity = identity, profileRegistered = true))
        val client = wallet(store, api)
        assertEquals(66, client.redeem("ZG1." + "A".repeat(43)).grant?.serial?.toInt())
        client.feature(grant.grantId); revoke = true
        assertTrue(client.collectionPage().collection.isEmpty())
        assertTrue(wallet(store).cached().collection.isEmpty())
        assertTrue(store.value.featuredGrantIds.isEmpty())
        // A stale server/cache receipt cannot remove the verified tombstone.
        store.value = store.value.copy(receipt = receipt)
        assertNull(wallet(store).cached().grant)
    }
    @Test fun forgedTombstoneNeverOverwritesWallet() = runBlocking {
        val receipt = receipt(); val store = Store(BadgeLocalState(identity = identity, receipt = receipt, profileRegistered = true))
        val api = object : BadgeTransport {
            override suspend fun call(path: String, method: String, body: JsonObject?, identity: BadgeIdentity?) = buildJsonObject {
                put("revocations", buildJsonArray { add(badgeJson.encodeToJsonElement(receipt.copy(signature = "bad"))) })
            }
        }
        try { wallet(store, api).collectionPage(); fail() } catch (e: BadgeClientException) { assertEquals("INVALID_RECEIPT", e.code) }
        assertNotNull(wallet(store).cached().grant)
    }

    @Test fun stableEmptySlotsSurviveRestartAndReplacement() = runBlocking {
        val receipts = (11L..14L).map { receipt("clear-sky-crown", it) }
        val store = Store(BadgeLocalState(identity = identity, receipts = receipts))
        val client = wallet(store); val grants = client.cached().collection
        client.place(grants[0].grantId, 1)
        assertEquals(listOf(null, grants[0].grantId, null), wallet(store).cached().displaySlots)
        client.place(grants[1].grantId, 1)
        assertEquals(4, client.cached().collection.size)
        assertEquals(listOf(null, grants[1].grantId, null), client.cached().displaySlots)
        client.place(grants[1].grantId, 2)
        assertEquals(listOf(null, null, grants[1].grantId), client.cached().displaySlots)
        client.place(null, 2)
        assertEquals(listOf(null, null, null), client.cached().displaySlots)
        assertEquals(4, client.cached().collection.size)
    }
    @Test fun legacyFeaturedSelectionMigratesWithoutInventingOwnership() = runBlocking {
        val item = receipt(); val grant = verifier.verify(item, identity.id)
        val store = Store(BadgeLocalState(identity = identity, receipt = item, featuredGrantIds = listOf(grant.grantId)))
        assertEquals(listOf(grant.grantId, null, null), wallet(store).cached().displaySlots)
        try { wallet(store).place(UUID.randomUUID().toString(), 0); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(listOf(grant.grantId, null, null), wallet(store).cached().displaySlots)
    }
}
