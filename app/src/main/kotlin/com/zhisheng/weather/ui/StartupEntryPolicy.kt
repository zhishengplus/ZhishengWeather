package com.zhisheng.weather.ui

// Automatic startup notices belong to genuine icon entries, not widgets or restored tasks.
internal fun isStartupIconEntry(action: String?, launcherCategory: Boolean, taskRoot: Boolean,
                                restored: Boolean, fromHistory: Boolean): Boolean =
    action == "android.intent.action.MAIN" && launcherCategory && taskRoot && !restored && !fromHistory
