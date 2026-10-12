package com.zhisheng.weather.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateOpeningPolicyTest {
    @Test fun autoPlaysOncePerVersionAlongsidePendingReleaseNotes() {
        assertTrue(shouldAutoPlayUpdateOpening(20261029, 20261028, whatsNewPending = true, motionEnabled = true))
        assertFalse(shouldAutoPlayUpdateOpening(20261029, 20261029, whatsNewPending = true, motionEnabled = true))
        assertFalse(shouldAutoPlayUpdateOpening(20261029, 0, whatsNewPending = false, motionEnabled = true))
        assertFalse(shouldAutoPlayUpdateOpening(20261029, 0, whatsNewPending = true, motionEnabled = false))
    }

    @Test fun versionLabelReadsTheBetaNumber() {
        assertEquals("10.3", openingVersionLabel("0.1.5-beta10.3-public"))
        assertEquals("10.4", openingVersionLabel("0.1.5-beta10.4-public"))
        assertEquals("10", openingVersionLabel("0.1.5-beta10-public"))
        assertEquals("0.2.0", openingVersionLabel("0.2.0"))
        assertNull(openingVersionLabel(null))
    }

    @Test fun onlyAGenuineUpgradeRollsFromThePreviousVersion() {
        val upgrade = updateOpeningSpec("0.1.5-beta10.4-public", "0.1.5-beta10.3-public", upgrade = true)
        assertEquals("10.4", upgrade.version)
        assertEquals("10.3", upgrade.previousVersion)
        assertEquals("BETA", upgrade.channel)

        assertNull(updateOpeningSpec("0.1.5-beta10.4-public", "0.1.5-beta10.3-public", upgrade = false).previousVersion)
        assertNull(updateOpeningSpec("0.1.5-beta10.4-public", "0.1.5-beta10.4-public", upgrade = true).previousVersion)
        assertNull(updateOpeningSpec("0.1.5-beta10.4-public", null, upgrade = true).previousVersion)
    }

    @Test fun countsComeFromTheShippedReleaseNotes() {
        val spec = updateOpeningSpec("0.1.5-beta10.3-public", null, upgrade = false)
        assertEquals(WhatsNewSections.size, spec.sectionCount)
        assertEquals(WhatsNewSections.sumOf { it.notes.size }, spec.noteCount)
    }

    @Test fun timelineEndsWithTheBackdropGoneAndStaysWithinFiveSeconds() {
        assertEquals(1f, openingBackdropAlpha(0f), 0f)
        assertEquals(1f, openingBackdropAlpha(OpeningHandoffMs - 200f), 0f)
        assertEquals(0f, openingBackdropAlpha(UpdateOpeningDurationMs), 0f)
        assertTrue(OpeningHandoffMs < UpdateOpeningDurationMs)
        assertEquals(5000f, UpdateOpeningDurationMs, 0f)
    }
}
