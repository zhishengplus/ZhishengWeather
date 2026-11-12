package com.zhisheng.weather.data.badge

import com.zhisheng.weather.ui.badge.BadgeViewLifetime
import com.zhisheng.weather.ui.badge.badgeAssetPath
import org.junit.Assert.*
import org.junit.Test

class BadgeWebPolicyTest {
    @Test fun onlyLocalPackagedViewerAndModelResourcesAreAllowed() {
        assertEquals("viewer/index.html", badgeAssetPath("https://badge.local.invalid/viewer/index.html"))
        assertEquals("viewer/vendor/three.core.js", badgeAssetPath("https://badge.local.invalid/viewer/vendor/three.core.js"))
        assertEquals("model.json", badgeAssetPath("https://badge.local.invalid/model.json"))
        assertEquals("first-light.glb", badgeAssetPath("https://badge.local.invalid/first-light.glb"))
        assertEquals("viewer/font.ttf", badgeAssetPath("https://badge.local.invalid/viewer/font.ttf"))
    }
    @Test fun approvedSceneAssetsAreAllowedWithoutOpeningGeneralJsonOrHdrAccess() {
        for (name in listOf("clearsky-scene.json", "clearsky-softboxes.hdr", "clearsky-atelier.glb", "clearsky-shadow.png"))
            assertEquals(name, badgeAssetPath("https://badge.local.invalid/$name"))
        for (name in listOf("service.json", "private.hdr", "studio/clearsky-scene.json", "clearsky-softboxes.hdr?x=1"))
            assertNull(badgeAssetPath("https://badge.local.invalid/$name"))
    }
    @Test fun externalFileContentJavascriptAndCredentialUrlsAreDenied() {
        for (url in listOf("https://example.com/a.js", "http://badge.local.invalid/viewer/index.html", "file:///data/local/a",
            "content://private/a", "javascript:alert(1)", "https://user:pass@badge.local.invalid/viewer/index.html",
            "https://badge.local.invalid:443/viewer/index.html", "https://badge.local.invalid.evil/viewer/index.html")) assertNull(badgeAssetPath(url))
    }
    @Test fun traversalEncodedTraversalQueriesAndUnexpectedResourcesAreDenied() {
        for (path in listOf("viewer/../service.json", "viewer/%2e%2e/service.json", "viewer//index.html", "viewer/./index.html",
            "viewer/a.js?token=anything", "viewer/index.html#x", "service.json", "viewer/secrets.enc", "../file", "viewer/a%2f.js"))
            assertNull(badgeAssetPath("https://badge.local.invalid/$path"))
        assertNull(badgeAssetPath("https://badge.local.invalid/viewer/index.html", "POST"))
        assertNull(badgeAssetPath("https://badge.local.invalid/" + "a".repeat(600)))
    }
    @Test fun lifecyclePauseAndDestroyAreIdempotentAndCannotResumeDisposedView() {
        val life = BadgeViewLifetime(); assertTrue(life.isAlive); assertFalse(life.canRender)
        assertTrue(life.resume()); assertFalse(life.resume()); assertTrue(life.canRender)
        assertTrue(life.pause()); assertFalse(life.pause()); assertFalse(life.canRender)
        assertTrue(life.resume()); assertTrue(life.destroy()); assertFalse(life.isAlive); assertFalse(life.canRender)
        assertFalse(life.destroy()); assertFalse(life.resume()); assertFalse(life.canRender)
    }
    @Test fun queuedCallbacksCannotCrossRetainedRendererHosts() {
        val life = BadgeViewLifetime()
        life.newHost(); life.resume()
        val previous = life.hostGeneration
        assertTrue(life.isCurrentHost(previous))
        life.newHost()
        assertFalse(life.canRender)
        assertFalse(life.isCurrentHost(previous))
        life.resume()
        assertFalse(life.isCurrentHost(previous))
        assertTrue(life.isCurrentHost(life.hostGeneration))
        life.destroy()
        assertFalse(life.isCurrentHost(life.hostGeneration))
    }
}
