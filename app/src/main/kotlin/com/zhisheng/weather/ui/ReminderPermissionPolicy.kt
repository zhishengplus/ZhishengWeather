package com.zhisheng.weather.ui

import com.zhisheng.weather.data.AutomationSettings

internal object ReminderPermissionPolicy {
    fun requestOnChange(previous: AutomationSettings, next: AutomationSettings, notificationsAllowed: Boolean) =
        !previous.remindersEnabled && next.remindersEnabled && !notificationsAllowed

    fun refreshOnRestore(previousAllowed: Boolean?, nowAllowed: Boolean, remindersEnabled: Boolean) =
        previousAllowed == false && nowAllowed && remindersEnabled
}
