package com.zhisheng.weather.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

private const val DISCLOSURE_KEY = "rainviewer_community_use_v1"
private fun radarDisclosure(context: Context) = context.getSharedPreferences("radar_disclosure", Context.MODE_PRIVATE)

@Composable
internal fun rememberRainviewerDisclosureAccepted(): State<Boolean> {
    val context = LocalContext.current
    return produceState(initialValue = radarDisclosure(context).getBoolean(DISCLOSURE_KEY, false), context) {
        val preferences = radarDisclosure(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == DISCLOSURE_KEY) value = preferences.getBoolean(DISCLOSURE_KEY, false)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

internal fun acceptRainviewerDisclosure(context: Context) {
    radarDisclosure(context).edit().putBoolean(DISCLOSURE_KEY, true).apply()
}
