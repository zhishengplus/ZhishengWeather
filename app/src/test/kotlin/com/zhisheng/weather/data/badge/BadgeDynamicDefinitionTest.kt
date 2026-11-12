package com.zhisheng.weather.data.badge

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.*
import org.junit.Test

class BadgeDynamicDefinitionTest {
    private val key=Ed25519PrivateKeyParameters(SecureRandom())
    private val publicKey=Base64.getEncoder().encodeToString(SubjectPublicKeyInfo(AlgorithmIdentifier(ASN1ObjectIdentifier("1.3.101.112")),key.generatePublicKey().encoded).encoded)
    private val verifier=BadgeReceiptVerifier(mapOf("fixture" to publicKey))
    private fun signed(text:String):BadgeReceipt {val bytes=text.toByteArray();val signer=Ed25519Signer().apply{init(true,key);update(bytes,0,bytes.size)};return BadgeReceipt("fixture",Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),Base64.getUrlEncoder().withoutPadding().encodeToString(signer.generateSignature()))}
    private fun definition(renderer:String="pbr-glb-v1")=signed("""{"protocol":1,"purpose":"badge-definition-v1","definition":{"schemaVersion":1,"badgeId":"future-moon","revision":1,"name":"测试月亮","state":"published","renderer":"$renderer","asset":{"assetId":"moon-r1","version":"${"a".repeat(64)}","sha256":"${"b".repeat(64)}","bytes":100,"mime":"model/gltf-binary"},"presentation":{"version":1,"orientation":[0,0,0,1],"occupancy":0.74,"maxZoom":3.2,"anchors":{},"materialPolicy":"preserve-gltf"}}}""")
    private fun grant(proof:BadgeReceipt)=BadgeGrant(2,"future-moon","测试月亮",UUID.randomUUID().toString(),UUID.randomUUID().toString(),88,1000,"standard","badge-grant",1,MessageDigest.getInstance("SHA-256").digest(Base64.getUrlDecoder().decode(proof.payload)).joinToString(""){"%02x".format(it)})
    private fun model(g:BadgeGrant,proof:BadgeReceipt)=BadgeModelManifest(1,"badge-model-v1",g.subjectId,g.grantId,g.badgeId,g.serial,"moon-r1","a".repeat(64),"moon-r1",null,"b".repeat(64),100,"model/gltf-binary",definitionProof=proof)
    private fun reject(block:()->Unit){try{block();fail("Expected validation failure")}catch(e:AssertionError){throw e}catch(_:Exception){}}
    @Test fun unknownNewTypeRequiresNoHardcodedIdWhenSignedGrantAndDefinitionMatch(){val proof=definition();val g=grant(proof);assertEquals(g,verifier.verify(signed(badgeJson.encodeToString(g)),g.subjectId));val result=verifier.verifyModel(signed(badgeJson.encodeToString(model(g,proof))),g);assertNotNull(result.presentation);assertEquals(.74,result.presentation!!.occupancy,0.0)}
    @Test fun tamperedDefinitionVersionAssetAndSignatureNeverSelectAnotherModel(){val proof=definition();val g=grant(proof);val m=model(g,proof);for(bad in listOf(g.copy(definitionHash="0".repeat(64)),g.copy(definitionRevision=2),g.copy(badgeId="another-moon")))reject{verifier.verifyModel(signed(badgeJson.encodeToString(m)),bad)};reject{verifier.verifyModel(signed(badgeJson.encodeToString(m.copy(sha256="c".repeat(64)))),g)};reject{verifier.verifyModel(signed(badgeJson.encodeToString(m.copy(definitionProof=proof.copy(signature="invalid")))),g)}}
    @Test fun unknownLegacyTypeAndUnsignedProtocolUpgradeRemainRejected(){val g=grant(definition());reject{verifier.verify(signed(badgeJson.encodeToString(g.copy(protocol=1))),g.subjectId)};reject{verifier.verify(signed(badgeJson.encodeToString(g.copy(kind=null))),g.subjectId)};reject{verifier.verify(signed(badgeJson.encodeToString(g)).copy(signature="invalid"),g.subjectId)}}
    @Test fun futureRendererKeepsVerifiedOwnershipButRequiresAppUpgradeForRendering(){val proof=definition("future-renderer-v2");val g=grant(proof);assertEquals(g,verifier.verify(signed(badgeJson.encodeToString(g)),g.subjectId));try{verifier.verifyModel(signed(badgeJson.encodeToString(model(g,proof))),g);fail("unsupported renderer")}catch(e:BadgeClientException){assertEquals("UNSUPPORTED_BADGE_RENDERER",e.code)}}
}
