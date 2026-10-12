package com.zhisheng.weather.ui

import com.zhisheng.weather.data.AutomationSettings
import org.junit.Assert.*
import org.junit.Test

class ReminderPermissionPolicyTest {
    @Test fun onlyTurningOnTheFirstReminderRequestsPermission() {
        val off = AutomationSettings()
        val on = off.copy(morningEnabled = true)
        assertTrue(ReminderPermissionPolicy.requestOnChange(off, on, false))
        assertFalse(ReminderPermissionPolicy.requestOnChange(on, on.copy(foregroundMinutes = 15), false))
        assertFalse(ReminderPermissionPolicy.requestOnChange(on, on.copy(morningMinute = 510), false))
        assertFalse(ReminderPermissionPolicy.requestOnChange(on, on.copy(rainEnabled = true), false))
        assertFalse(ReminderPermissionPolicy.requestOnChange(on, off, false))
        assertFalse(ReminderPermissionPolicy.requestOnChange(off, on, true))
    }

    @Test fun onlyRestoredDeliveryRequestsAnImmediateCheck() {
        assertTrue(ReminderPermissionPolicy.refreshOnRestore(false, true, true))
        assertFalse(ReminderPermissionPolicy.refreshOnRestore(null, true, true))
        assertFalse(ReminderPermissionPolicy.refreshOnRestore(true, true, true))
        assertFalse(ReminderPermissionPolicy.refreshOnRestore(false, false, true))
        assertFalse(ReminderPermissionPolicy.refreshOnRestore(false, true, false))
    }
}
