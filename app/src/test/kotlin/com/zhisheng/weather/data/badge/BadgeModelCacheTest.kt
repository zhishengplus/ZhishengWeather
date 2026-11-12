package com.zhisheng.weather.data.badge

import com.zhisheng.weather.ui.badge.badgeModelCacheHash
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.encodeToString
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException

class BadgeModelCacheTest {
    @Test fun stalledDownloadDoesNotBlockOtherReadyModelsOrIndependentDownloads() = cache { _, c ->
        val ready = grant(); val bytes = glb(); c.commit(ready, sign(proof(ready, bytes)), ByteArrayInputStream(bytes))
        val slow = grant(); val slowBytes = glb("""{"asset":{"version":"2.0"},"extras":{"fixture":1}}""")
        val other = grant(); val otherBytes = glb("""{"asset":{"version":"2.0"},"extras":{"fixture":2}}""")
        val entered = java.util.concurrent.CountDownLatch(1); val release = java.util.concurrent.CountDownLatch(1)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val slowResult = pool.submit<CachedBadgeModel> {
                c.commit(slow, sign(proof(slow, slowBytes)), object : ByteArrayInputStream(slowBytes) {
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        entered.countDown(); check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        return super.read(b, off, len)
                    }
                })
            }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            pool.submit {
                assertNotNull(c.cached(ready))
                c.commit(other, sign(proof(other, otherBytes)), ByteArrayInputStream(otherBytes))
                assertNotNull(c.cached(other))
            }.get(2, java.util.concurrent.TimeUnit.SECONDS)
            release.countDown(); slowResult.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertNotNull(c.cached(slow))
        } finally { release.countDown(); pool.shutdownNow() }
    }
    @Test fun concurrentSameModelSerialsDownloadOnceAndRetainSeparateSignedOwnership() = runBlocking {
        cache { _, c ->
            val a = grant(); val b = a.copy(grantId = UUID.randomUUID().toString(), serial = 89)
            val bytes = glb(); var downloads = 0
            val results = listOf(a, b).map { g -> async {
                val signed = sign(proof(g, bytes))
                withBadgeModelAsset(sha(bytes)) {
                    c.reuseVerifiedAsset(g, signed) ?: run {
                        downloads++; delay(30); c.commit(g, signed, ByteArrayInputStream(bytes))
                    }
                }
            } }.awaitAll()
            assertEquals(1, downloads); assertEquals(results[0].file, results[1].file)
            assertEquals(a.grantId, c.cached(a)!!.manifest.grantId)
            assertEquals(b.grantId, c.cached(b)!!.manifest.grantId)
        }
    }
    @Test fun sharedBinaryStillRejectsCorruptionAndAnotherGrantsProof() = cache { _, c ->
        val a = grant(); val bytes = glb(); val signed = sign(proof(a, bytes))
        val first = c.commit(a, signed, ByteArrayInputStream(bytes))
        rejected { c.reuseVerifiedAsset(a.copy(grantId = UUID.randomUUID().toString()), signed) }
        first.file.writeBytes(bytes.copyOf().also { it[it.lastIndex] = 42 })
        assertNull(c.reuseVerifiedAsset(a, signed))
    }
    @Test fun transientModelFailureRetriesOnceButCancellationAndInvalidProofNeverRetry() = runBlocking {
        var attempts = 0
        assertEquals("ready", retryBadgeModelTransfer { if (++attempts == 1) throw BadgeClientException("NETWORK_UNAVAILABLE"); "ready" })
        assertEquals(2, attempts)
        for (error in listOf(BadgeClientException("INVALID_MODEL_MANIFEST"), BadgeClientException("MODEL_FORBIDDEN"), CancellationException("left page"))) {
            attempts = 0
            try { retryBadgeModelTransfer<String> { attempts++; throw error }; fail() }
            catch (caught: Exception) { assertSame(error, caught) }
            assertEquals(1, attempts)
        }
        attempts = 0
        try { retryBadgeModelTransfer<String> { attempts++; throw BadgeClientException("SERVICE_UNAVAILABLE") }; fail() }
        catch (error: BadgeClientException) { assertEquals("SERVICE_UNAVAILABLE", error.code) }
        assertEquals(2, attempts)
    }
    @Test fun signedAssetReplacementRejectsBakedCacheButTextOnlyUpdateReusesBinary()=cache{_,c->
        val g=grant();val bytes=glb();c.commit(g,sign(proof(g,bytes)),ByteArrayInputStream(bytes))
        val renamed=g.copy(engravingPublicId="枳生-测试",metadataRevision=1,modelAssetId="fixture-crown-v1")
        assertNotNull(c.cached(renamed))
        val rebound=renamed.copy(modelAssetId="crown-generic-v1",metadataRevision=2)
        assertNull(c.cached(rebound))
        rejected{verifier.verifyModel(sign(proof(g,bytes)),rebound)}
        c.commit(rebound,sign(proof(g,bytes).copy(assetId="crown-generic-v1")),ByteArrayInputStream(bytes))
        assertNotNull(c.cached(rebound))
    }
    private val key=Ed25519PrivateKeyParameters(SecureRandom())
    private val publicKey=Base64.getEncoder().encodeToString(SubjectPublicKeyInfo(AlgorithmIdentifier(ASN1ObjectIdentifier("1.3.101.112")),key.generatePublicKey().encoded).encoded)
    private val verifier=BadgeReceiptVerifier(mapOf("fixture" to publicKey))
    private fun grant()=BadgeGrant(1,"clear-sky-crown","晴空冠冕",UUID.randomUUID().toString(),UUID.randomUUID().toString(),88,1000)
    private fun sha(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){ "%02x".format(it) }
    private fun glb(json:String="""{"asset":{"version":"2.0"}}""",binary:ByteArray?=null):ByteArray {
        val encoded=json.toByteArray();val padded=encoded+ByteArray((4-encoded.size%4)%4){32};val bin=binary?.let{it+ByteArray((4-it.size%4)%4)}
        val size=20+padded.size+(bin?.let{8+it.size}?:0);return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply{putInt(0x46546C67);putInt(2);putInt(size);putInt(padded.size);putInt(0x4E4F534A);put(padded);if(bin!=null){putInt(bin.size);putInt(0x004E4942);put(bin)}}.array()
    }
    private fun proof(g:BadgeGrant,b:ByteArray)=BadgeModelManifest(1,"badge-model-v1",g.subjectId,g.grantId,g.badgeId,g.serial,"fixture-crown-v1","a".repeat(64),"clear_sky_crown",null,sha(b),b.size.toLong(),"model/gltf-binary")
    private fun sign(value:BadgeModelManifest):BadgeReceipt {val bytes=badgeJson.encodeToString(value).toByteArray();val signer=Ed25519Signer().apply{init(true,key);update(bytes,0,bytes.size)};return BadgeReceipt("fixture",Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),Base64.getUrlEncoder().withoutPadding().encodeToString(signer.generateSignature()))}
    private inline fun cache(block:(File,BadgeModelCache)->Unit){val dir=Files.createTempDirectory("badge-model-test-").toFile();try{block(dir,BadgeModelCache(dir,verifier))}finally{dir.deleteRecursively()}}
    private fun rejected(block:()->Unit){try{block();fail("Expected validation failure")}catch(e:AssertionError){throw e}catch(_:Exception){}}
    @Test fun validSignedModelCommitsAtomicallyAndWorksOffline()=cache{dir,c->val g=grant();val bytes=glb();val signed=sign(proof(g,bytes));val ready=c.commit(g,signed,ByteArrayInputStream(bytes));assertArrayEquals(bytes,ready.file.readBytes());assertNotNull(c.cached(g));assertTrue(dir.listFiles().orEmpty().none{it.name.endsWith(".part")})}
    @Test fun modelProofCannotBeReusedAcrossSubjectGrantTypeOrSerial() {val g=grant();val p=proof(g,glb());for(bad in listOf(p.copy(subjectId=UUID.randomUUID().toString()),p.copy(grantId=UUID.randomUUID().toString()),p.copy(badgeId="together-with-you"),p.copy(serial=89),p.copy(purpose="other-purpose")))rejected{verifier.verifyModel(sign(bad),g)}}
    @Test fun badSignatureAndPathAreRejected() {val g=grant();val p=proof(g,glb());rejected{verifier.verifyModel(sign(p).copy(signature="invalid"),g)};rejected{verifier.verifyModel(sign(p.copy(assetId="../../private")),g)}}
    @Test fun corruptCachedBytesNeverBecomeReady()=cache{_,c->val g=grant();val bytes=glb();val model=c.commit(g,sign(proof(g,bytes)),ByteArrayInputStream(bytes));model.file.writeBytes(bytes.copyOf().also{it[it.lastIndex]=42});assertNull(c.cached(g));rejected{validateCachedModelFile(model)}}
    @Test fun shortLongCancelledAndInterruptedWritesLeaveNoReadyCache()=cache{dir,c->val g=grant();val bytes=glb();val signed=sign(proof(g,bytes));for(input in listOf(bytes.dropLast(1).toByteArray(),bytes+byteArrayOf(1)))rejected{c.commit(g,signed,ByteArrayInputStream(input))};rejected{c.commit(g,signed,ByteArrayInputStream(bytes),{false})};assertNull(c.cached(g));assertTrue(dir.listFiles().orEmpty().none{it.name.endsWith(".part")||it.name.endsWith(".glb")})}
    @Test fun diskWriteFailureDoesNotPublishMetadataOrBinary()=cache{dir,_->val broken=BadgeModelCache(dir,verifier){throw IOException("fixture disk full")};val g=grant();val bytes=glb();rejected{broken.commit(g,sign(proof(g,bytes)),ByteArrayInputStream(bytes))};assertTrue(dir.listFiles().orEmpty().isEmpty())}
    @Test fun externalUriAndZipInputFailBeforeGpu()=cache{_,c->val g=grant();for(bytes in listOf(glb("""{"asset":{"version":"2.0"},"buffers":[{"uri":"https://example.invalid/x","byteLength":2}]}"""),ByteArray(100){80})){rejected{c.commit(g,sign(proof(g,bytes)),ByteArrayInputStream(bytes))};assertNull(c.cached(g))}}
    @Test fun hugeTextureAndAccessorAllocationAreRejected()=cache{_,c->val g=grant();val png=ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN).apply{put(byteArrayOf(-119,80,78,71,13,10,26,10));putInt(13);putInt(0x49484452);putInt(9000);putInt(9000)}.array();val image=glb("""{"asset":{"version":"2.0"},"buffers":[{"byteLength":24}],"bufferViews":[{"buffer":0,"byteLength":24}],"images":[{"mimeType":"image/png","bufferView":0}]}""",png);val access=glb("""{"asset":{"version":"2.0"},"accessors":[{"count":3000000}]}""");for(bytes in listOf(image,access))rejected{c.commit(g,sign(proof(g,bytes)),ByteArrayInputStream(bytes))}}
    @Test fun revokedMetadataRemovalCannotResolveOldCachedFile()=cache{_,c->val g=grant();val bytes=glb();c.commit(g,sign(proof(g,bytes)),ByteArrayInputStream(bytes));c.forget(g.grantId);assertNull(c.cached(g))}
    @Test fun webviewCacheRouteIsStrictOfflineHashOnly() {val hash="a".repeat(64);assertEquals(hash,badgeModelCacheHash("https://badge.local.invalid/models/$hash.glb"));for(url in listOf("https://evil.invalid/models/$hash.glb","http://badge.local.invalid/models/$hash.glb","https://badge.local.invalid/models/$hash.glb?token=x","https://badge.local.invalid/models/../model.json","file:///models/$hash.glb"))assertNull(badgeModelCacheHash(url));assertNull(badgeModelCacheHash("https://badge.local.invalid/models/$hash.glb","POST"))}
}
