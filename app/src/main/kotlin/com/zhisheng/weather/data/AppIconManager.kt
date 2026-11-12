package com.zhisheng.weather.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Switches launcher aliases without killing the app. */
object AppIconManager {
    // The original default component name is retained so an existing pinned
    // launcher entry keeps its component identity after an upgrade.
    private const val DEFAULT_ALIAS = "com.zhisheng.weather.IconCharacter"
    private const val CHARACTER_ALIAS = "com.zhisheng.weather.IconWeatherGirl"
    private val LEGACY_ALIASES = listOf(
        "com.zhisheng.weather.IconMinimal", "com.zhisheng.weather.IconClassic", "com.zhisheng.weather.IconLight",
    )

    internal fun aliasFor(style: AppIconStyle, enabledLegacyAliases: Set<String>): String = when (style) {
        AppIconStyle.CHARACTER -> CHARACTER_ALIAS
        // Old explicitly selected entries keep their component while showing the
        // new default icon, so upgrading need not discard a pinned shortcut.
        AppIconStyle.DEFAULT -> LEGACY_ALIASES.firstOrNull { it in enabledLegacyAliases } ?: DEFAULT_ALIAS
    }

    @Synchronized
    fun apply(context: Context, style: AppIconStyle): Boolean = runCatching {
        val app = context.applicationContext
        val pm = app.packageManager
        fun component(name: String) = ComponentName(app.packageName, name)
        val enabledLegacy = LEGACY_ALIASES.filter { name ->
            pm.getComponentEnabledSetting(component(name)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }.toSet()
        val target = component(aliasFor(style, enabledLegacy))
        val components = (listOf(DEFAULT_ALIAS, CHARACTER_ALIAS) + LEGACY_ALIASES).map(::component)
        // Always enable the replacement before disabling the former entry.
        setState(pm, target, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        components.filterNot { it == target }.forEach {
            setState(pm, it, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        }
    }.isSuccess

    private fun setState(pm: PackageManager, component: ComponentName, state: Int) {
        if (pm.getComponentEnabledSetting(component) == state) return
        pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}

/** Reconcile persisted icon selection after a package upgrade, before opening the app. */
class AppIconUpgradeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8_000) {
                    AppIconManager.apply(context.applicationContext, SettingsRepository.appIconStyle.first())
                }
            } catch (_: Exception) {
                // The next ordinary app launch retries reconciliation.
            } finally { pending.finish() }
        }
    }
}
