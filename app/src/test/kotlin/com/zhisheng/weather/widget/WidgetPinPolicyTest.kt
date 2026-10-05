package com.zhisheng.weather.widget

import org.junit.Assert.*
import org.junit.Test

class WidgetPinPolicyTest {
    @Test fun acceptingARequestIsNotProofThatTheWidgetWasAdded() {
        assertEquals(WidgetPinState.WAITING, pinRequestResult(true))
        assertEquals(WidgetPinState.REJECTED, pinRequestResult(false))
    }

    @Test fun missingCallbackIsUnconfirmedRatherThanDeniedOrSuccessful() {
        assertEquals(WidgetPinState.WAITING, pinAwaitResult(500, null))
        assertEquals(WidgetPinState.UNCONFIRMED, pinAwaitResult(PIN_FEEDBACK_DELAY_MS, null))
    }

    @Test fun lateSuccessOverridesTheFeedbackDeadline() {
        assertEquals(WidgetPinState.ADDED, pinAwaitResult(PIN_FEEDBACK_DELAY_MS * 3, true))
        assertEquals(WidgetPinState.CONFIG_FAILED, pinAwaitResult(0, false))
    }

    @Test fun systemGuidesCoverBrandsAndUnknownDevicesWithoutInventingPermissionFailures() {
        assertEquals(WidgetHomeGuide.XIAOMI, widgetHomeGuide("Redmi"))
        assertEquals(WidgetHomeGuide.VIVO, widgetHomeGuide("iQOO"))
        assertEquals(WidgetHomeGuide.OPPO, widgetHomeGuide("OnePlus"))
        assertEquals(WidgetHomeGuide.OPPO, widgetHomeGuide("realme"))
        assertEquals(WidgetHomeGuide.HONOR, widgetHomeGuide("HONOR"))
        assertEquals(WidgetHomeGuide.HUAWEI, widgetHomeGuide("HUAWEI"))
        assertEquals(WidgetHomeGuide.SAMSUNG, widgetHomeGuide("samsung"))
        assertEquals(WidgetHomeGuide.OTHER, widgetHomeGuide("unknown"))
    }
}
