package com.zhisheng.weather.data.badge

import android.content.Context
import com.zhisheng.weather.BuildConfig
import java.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/** Offline fixture for the separately named experience APK. Never trusts a production key or endpoint. */
internal class BadgeExperienceTransport(private val context: Context) : BadgeTransport {
    init { check(BuildConfig.BADGE_EXPERIENCE && context.packageName == "com.zhisheng.weather.badgeexperience") }
    private val fixture = badgeJson.parseToJsonElement(context.assets.open("badges/experience.json").bufferedReader().use { it.readText() }).jsonObject
    private val prefs = context.getSharedPreferences("badge_experience_only", Context.MODE_PRIVATE)
    private val idPolicy = badgeJson.parseToJsonElement(context.assets.open("badges/id-policy.json").bufferedReader().use { it.readText() }).jsonObject
    val identity = BadgeIdentity(fixture.getValue("subjectId").jsonPrimitive.content, "a".repeat(64), "b".repeat(64))
    val verifierKeys = mapOf("experience-only" to fixture.getValue("publicKey").jsonPrimitive.content)
    private val ringId = "00000000-0000-4000-8000-000000147002"
    private val ringDefinition = badgeJson.parseToJsonElement(context.assets.open("badges/ring-definition.json").bufferedReader().use { it.readText() }).jsonObject
    private val definitionPayload = buildJsonObject {
        put("protocol", 1); put("purpose", "badge-definition-v1"); put("definition", ringDefinition)
    }.toString().toByteArray()
    private val definitionHash = java.security.MessageDigest.getInstance("SHA-256").digest(definitionPayload).joinToString("") { "%02x".format(it) }
    private fun signed(payload: ByteArray): BadgeReceipt {
        val key = Ed25519PrivateKeyParameters(Base64.getDecoder().decode(fixture.getValue("testSeed").jsonPrimitive.content), 0)
        val signer = Ed25519Signer().apply { init(true, key); update(payload, 0, payload.size) }
        return BadgeReceipt("experience-only", Base64.getUrlEncoder().withoutPadding().encodeToString(payload),
            Base64.getUrlEncoder().withoutPadding().encodeToString(signer.generateSignature()))
    }
    private fun ringReceipt() = signed(badgeJson.encodeToString(BadgeGrant(2, "clear-sky-ring", "澄空之戒", ringId,
        identity.id, 900002, 1793232000000, kind = "badge-grant", definitionRevision = 1,
        definitionHash = definitionHash, modelAssetId = "clear-sky-ring-standard-v2",
        engravingPublicId = prefs.getString("ringPublicId", null), metadataRevision = prefs.getLong("ringRevision", 0))).toByteArray())
    fun model(grant: BadgeGrant, cache: BadgeModelCache): CachedBadgeModel {
        check(grant.subjectId == identity.id && grant.grantId == ringId && grant.badgeId == "clear-sky-ring")
        val asset = ringDefinition.getValue("asset").jsonObject
        val manifest = BadgeModelManifest(1, "badge-model-v1", identity.id, ringId, "clear-sky-ring", 900002,
            asset.getValue("assetId").jsonPrimitive.content, asset.getValue("version").jsonPrimitive.content,
            asset.getValue("assetId").jsonPrimitive.content, sha256 = asset.getValue("sha256").jsonPrimitive.content,
            bytes = asset.getValue("bytes").jsonPrimitive.long, mime = "model/gltf-binary", definitionProof = signed(definitionPayload))
        val proof = signed(badgeJson.encodeToString(manifest).toByteArray())
        return context.assets.open("badges/clear-sky-ring.glb").use { cache.commit(grant, proof, it) }
    }
    private fun receipt(): BadgeReceipt {
        val metadata = prefs.getLong("revision", 0)
        val payload = badgeJson.encodeToString(BadgeGrant(1, FIRST_LIGHT_BADGE, FIRST_LIGHT_EDITION,
            fixture.getValue("grantId").jsonPrimitive.content, identity.id, 900001, 1793232000000,
            engravingPublicId = prefs.getString("publicId", null), metadataRevision = metadata)).toByteArray()
        // Publicly reproducible test key; accepted only by this fixture's verifier.
        return signed(payload)
    }
    override suspend fun call(path: String, method: String, body: JsonObject?, identity: BadgeIdentity?): JsonObject {
        fun collection() = buildJsonObject {
            put("protocol", 1); put("receipts", buildJsonArray { if (prefs.getBoolean("issued", false)) {
                add(badgeJson.encodeToJsonElement(receipt())); add(badgeJson.encodeToJsonElement(ringReceipt()))
            } })
        }
        return when (path) {
            "/v1/badges/campaigns/first-light-016" -> badgeJson.encodeToJsonElement(BadgeCampaign(
                state = "open", claimEnabled = true, claimMode = "manual")).jsonObject
            "/v1/badges/profiles" -> buildJsonObject { put("protocol", 1); put("subjectId", this@BadgeExperienceTransport.identity.id) }
            "/v1/badges/claim-device" -> buildJsonObject { put("protocol", 1); put("alreadyClaimed", prefs.getBoolean("issued", false)) }
            "/v1/badges/first-light-016/claim" -> {
                prefs.edit().putBoolean("issued", true).commit()
                buildJsonObject {
                    put("protocol", 1); put("receipt", badgeJson.encodeToJsonElement(receipt()))
                    put("receipts", buildJsonArray { add(badgeJson.encodeToJsonElement(ringReceipt())) })
                }
            }
            "/v1/badges/collection", "/v1/badges/me" -> collection()
            "/v1/badges/engraving" -> {
                val isRing = body?.get("grantId")?.jsonPrimitive?.content == ringId
                if (!isRing && body?.get("grantId")?.jsonPrimitive?.content != fixture.getValue("grantId").jsonPrimitive.content) throw BadgeClientException("GRANT_NOT_OWNED")
                val revisionKey = if (isRing) "ringRevision" else "revision"
                val idKey = if (isRing) "ringPublicId" else "publicId"
                if (body?.get("expectedMetadataRevision")?.jsonPrimitive?.longOrNull != prefs.getLong(revisionKey, 0)) throw BadgeClientException("METADATA_CHANGED")
                val value = body["engravingPublicId"]?.jsonPrimitive?.content?.takeIf { it.length in 1..32 } ?: throw BadgeClientException("INVALID_ENGRAVING")
                fun compact(text: String) = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
                    .lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
                val raw = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
                val normalized = compact(raw)
                if (Regex("(?:https?\\s*:|www\\s*\\.)", RegexOption.IGNORE_CASE).containsMatchIn(raw) ||
                    idPolicy.getValue("fragments").jsonArray.any { normalized.contains(compact(it.jsonPrimitive.content)) } ||
                    idPolicy.getValue("reserved").jsonArray.any { normalized.trimEnd { it in '0'..'9' } == compact(it.jsonPrimitive.content) })
                    throw BadgeClientException("ID_NOT_ALLOWED")
                prefs.edit().putString(idKey, value).putLong(revisionKey, prefs.getLong(revisionKey, 0) + 1).commit()
                buildJsonObject { put("protocol", 1); put("receipt", badgeJson.encodeToJsonElement(if (isRing) ringReceipt() else receipt())) }
            }
            else -> throw BadgeClientException("NOT_CONFIGURED")
        }
    }
}
