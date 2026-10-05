package com.zhisheng.weather.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Switches between the three manifest launcher aliases without restarting the app. */
object AppIconManager {
    private const val CHARACTER_ALIAS = "com.zhisheng.weather.IconCharacter"
    // 保留旧 alias 名称，避免已选择经典图标的升级用户短暂丢失桌面入口。
    private const val DARK_ALIAS = "com.zhisheng.weather.IconClassic"
    private const val LIGHT_ALIAS = "com.zhisheng.weather.IconLight"

    fun apply(context: Context, style: AppIconStyle): Boolean = runCatching {
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        val aliases = mapOf(
            AppIconStyle.CHARACTER to ComponentName(appContext.packageName, CHARACTER_ALIAS),
            AppIconStyle.DARK to ComponentName(appContext.packageName, DARK_ALIAS),
            AppIconStyle.LIGHT to ComponentName(appContext.packageName, LIGHT_ALIAS),
        )
        val selected = aliases.getValue(style)

        // Always expose the new launcher entry before hiding the old one. Some OEM
        // launchers otherwise briefly remove the app from the desktop/app drawer.
        setState(
            packageManager = packageManager,
            component = selected,
            state = PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        )
        aliases.values.filterNot { it == selected }.forEach { previous ->
            setState(
                packageManager = packageManager,
                component = previous,
                state = PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            )
        }
    }.isSuccess

    private fun setState(
        packageManager: PackageManager,
        component: ComponentName,
        state: Int,
    ) {
        if (packageManager.getComponentEnabledSetting(component) == state) return
        packageManager.setComponentEnabledSetting(
            component,
            state,
            PackageManager.DONT_KILL_APP,
        )
    }
}
