package com.zhisheng.weather.ui

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UiRefreshRateTest {
    @Test fun oldAndroidRequestsOnlyAReportedRefreshRate() {
        assertEquals(60f, motionRefreshRate(33, listOf(60f)), .01f)
        assertEquals(90f, motionRefreshRate(33, listOf(60f, 90f)), .01f)
        assertEquals(120f, motionRefreshRate(33, listOf(60f, 90f, 120f, 144f)), .01f)
        assertEquals(144f, motionRefreshRate(33, listOf(60f, 144f)), .01f)
        assertEquals(120.00001f, motionRefreshRate(33, listOf(60f, 120.00001f)), .01f)
    }
    @Test fun unavailableOrMalformedLegacyDisplayDoesNotInventAMode() {
        assertEquals(0f, motionRefreshRate(26, emptyList()), 0f)
        assertEquals(0f, motionRefreshRate(33, listOf(Float.NaN, Float.POSITIVE_INFINITY, -60f, 0f)), 0f)
    }
    @Test fun modernAndroidNegotiates120WithoutSelectingAResolutionOrDisplayMode() {
        assertEquals(120f, motionRefreshRate(34, listOf(60f)), 0f)
        assertEquals(120f, motionRefreshRate(36, listOf(90f, 144f)), 0f)
    }

    @Test fun popupFallbackOnlyAcceptsAnAttachedApplicationSubPanel() {
        assertTrue(isHighRefreshPopupRoot(true, WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL))
        assertFalse(isHighRefreshPopupRoot(false, WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL))
        assertFalse(isHighRefreshPopupRoot(true, WindowManager.LayoutParams.TYPE_APPLICATION))
        assertFalse(isHighRefreshPopupRoot(true, WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG))
        assertFalse(isHighRefreshPopupRoot(true, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY))
    }

    @Test fun popupRateUpdateKeepsItsParamsIdentityAndAllOtherWindowAttributes() {
        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
            width = 345; height = 678; x = 12; y = 34
            flags = WindowManager.LayoutParams.FLAG_SECURE
            gravity = 53; softInputMode = 32; format = -3; alpha = .75f
            preferredDisplayModeId = 7
            preferredRefreshRate = 90f
        }
        var updates = 0
        repeat(2) {
            assertTrue(updatePopupRefreshRate(params, 120f) { updated ->
                updates++
                assertSame(params, updated)
                assertEquals(120f, updated.preferredRefreshRate, 0f)
            })
        }
        assertEquals(1, updates)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL, params.type)
        assertEquals(345, params.width); assertEquals(678, params.height)
        assertEquals(12, params.x); assertEquals(34, params.y)
        assertEquals(WindowManager.LayoutParams.FLAG_SECURE, params.flags)
        assertEquals(53, params.gravity); assertEquals(32, params.softInputMode)
        assertEquals(-3, params.format); assertEquals(.75f, params.alpha, 0f)
        assertEquals(7, params.preferredDisplayModeId)
    }

    @Test fun popupDetachFailureRestoresThePreferenceAndAllowsALaterRetry() {
        val params = WindowManager.LayoutParams().apply { preferredRefreshRate = 90f; width = 345 }
        assertFalse(updatePopupRefreshRate(params, 120f) { throw IllegalArgumentException("View detached") })
        assertEquals(90f, params.preferredRefreshRate, 0f)
        assertEquals(345, params.width)
        assertTrue(updatePopupRefreshRate(params, 120f) { })
        assertEquals(120f, params.preferredRefreshRate, 0f)
    }
}
