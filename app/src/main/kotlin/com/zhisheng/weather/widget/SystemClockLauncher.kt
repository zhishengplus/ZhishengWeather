package com.zhisheng.weather.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.provider.AlarmClock
import android.os.Build
import java.util.Locale

internal val SystemClockPackages = listOf("com.android.deskclock", "com.sec.android.app.clockpackage",
    "com.huawei.deskclock", "com.hihonor.deskclock", "com.coloros.alarmclock", "com.oplus.alarmclock",
    "com.android.BBKClock", "com.vivo.clock", "com.google.android.deskclock")

internal fun romClockPackages(manufacturer: String, brand: String): List<String> {
    val device = "$manufacturer $brand".lowercase(Locale.ROOT)
    return when {
        listOf("xiaomi", "redmi", "poco").any(device::contains) -> listOf("com.android.deskclock")
        "samsung" in device -> listOf("com.sec.android.app.clockpackage")
        "honor" in device -> listOf("com.hihonor.deskclock", "com.android.deskclock")
        "huawei" in device -> listOf("com.huawei.deskclock", "com.android.deskclock")
        listOf("oppo", "realme", "oneplus").any(device::contains) -> listOf("com.coloros.alarmclock", "com.oplus.alarmclock", "com.android.deskclock")
        listOf("vivo", "iqoo").any(device::contains) -> listOf("com.android.BBKClock", "com.vivo.clock")
        "google" in device -> listOf("com.google.android.deskclock")
        else -> emptyList()
    }
}

internal fun isUsableSystemClock(packageName: String, flags: Int, enabled: Boolean, exported: Boolean,
    romPackages: Set<String> = emptySet()): Boolean =
    enabled && exported && packageName !in setOf("android", "com.android.intentresolver") &&
        (packageName in romPackages || flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)

/** Limited clock queries, never an installed-app scan or a hard-coded vendor activity name. */
@Suppress("DEPRECATION")
internal fun systemClockLaunchCandidates(context: Context): List<Intent> {
    val pm = context.packageManager
    val preferred = romClockPackages(Build.MANUFACTURER, Build.BRAND)
    fun system(info: ActivityInfo) = info.packageName != context.packageName &&
        isUsableSystemClock(info.packageName, info.applicationInfo.flags, info.enabled && info.applicationInfo.enabled, info.exported, preferred.toSet())
    val entry = Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.APP_CLOCK")
    val entries = runCatching { pm.queryIntentActivities(entry, 0) }.getOrDefault(emptyList()).filter { system(it.activityInfo) }
    val alarms = runCatching { pm.queryIntentActivities(Intent(AlarmClock.ACTION_SHOW_ALARMS), 0) }.getOrDefault(emptyList()).filter { system(it.activityInfo) }
    // Prefer the ROM's known system clock when another system clock also claims APP_CLOCK.
    val discovered = (entries + alarms).map { it.activityInfo.packageName }.filterNot { it == "com.google.android.deskclock" }
    val packages = (preferred + SystemClockPackages.filterNot { it == "com.google.android.deskclock" } +
        discovered + "com.google.android.deskclock").distinct()
    val result = mutableListOf<Intent>()
    for (pkg in packages) {
        val intent = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull() ?: continue
        val info = runCatching { intent.resolveActivityInfo(pm, 0) }.getOrNull() ?: continue
        if (system(info)) result += Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
    for (match in entries) result += Intent(entry).setComponent(ComponentName(match.activityInfo.packageName, match.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    for (match in alarms) result += Intent(AlarmClock.ACTION_SHOW_ALARMS).setComponent(ComponentName(match.activityInfo.packageName, match.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return result.distinctBy { it.component?.flattenToString() to it.action }
}
