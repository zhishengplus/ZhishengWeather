package com.zhisheng.weather.ui

// Automatic startup notices belong to genuine icon entries, not widgets or restored tasks.
internal fun isStartupIconEntry(action: String?, launcherCategory: Boolean, taskRoot: Boolean,
                                restored: Boolean, fromHistory: Boolean): Boolean =
    // Package installers also open MAIN, sometimes without CATEGORY_LAUNCHER.
    action == "android.intent.action.MAIN" && taskRoot && !restored && !fromHistory

internal fun suppressStartupPrompts(iconEntry: Boolean, action: String?, unseenVersion: Boolean): Boolean =
    !iconEntry && !(unseenVersion && action == "android.intent.action.MAIN")
