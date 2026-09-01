package com.zhisheng.weather.ui.theme

import com.zhisheng.weather.model.AlertLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlertColorsTest {
    @Test
    fun unknownAlertIsNeutralInsteadOfBeingPromotedToRed() {
        assertEquals(AlertVisualTone.NEUTRAL, alertVisualTone(AlertLevel.UNKNOWN))
        assertNotEquals(AlertVisualTone.RED, alertVisualTone(AlertLevel.UNKNOWN))
        assertEquals(AlertVisualTone.RED, alertVisualTone(AlertLevel.RED))
    }
}
