package com.zhisheng.weather.data

import org.junit.Assert.*
import org.junit.Test

class UpdateNoticeTest {
    @Test fun snoozeWaitsUntilItsDeadlineAndDismissAlwaysWins() {
        assertFalse(shouldOfferUpdate(release, emptySet(), 300L, 299L))
        assertTrue(shouldOfferUpdate(release, emptySet(), 300L, 300L))
        assertFalse(shouldOfferUpdate(release, setOf("code:100"), 300L, 301L))
    }
    private val release = AppUpdateInfo(versionCode = 100, versionName = "1.0", apkUrl = "https://example.com/app.apk")
    @Test fun aNewReleaseCanBeOfferedButClosingItPreventsRepeatOffers() {
        assertTrue(shouldOfferUpdate(release, emptySet()))
        assertFalse(shouldOfferUpdate(release, setOf(requireNotNull(updateNoticeKey(release)))))
    }
    @Test fun noteOrDownloadMirrorChangesNeverRepeatTheSameRelease() {
        val seen = setOf(requireNotNull(updateNoticeKey(release)))
        assertFalse(shouldOfferUpdate(release.copy(notes = "updated text", apkUrl = "https://mirror.example.com/app.apk"), seen))
        assertTrue(shouldOfferUpdate(release.copy(versionCode = 101, versionName = "1.1"), seen))
    }
    @Test fun olderSeenVersionsRemainSuppressedAfterAnEvenNewerNotice() {
        val newer = release.copy(versionCode = 101, versionName = "1.1")
        val seen = setOf(requireNotNull(updateNoticeKey(release)), requireNotNull(updateNoticeKey(newer)))
        assertFalse(shouldOfferUpdate(release, seen))
        assertFalse(shouldOfferUpdate(null, seen))
    }
}
