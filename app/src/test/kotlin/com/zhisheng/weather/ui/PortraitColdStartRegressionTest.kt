package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitColdStartRegressionTest {
    private val root = File(requireNotNull(System.getProperty("user.dir")))

    @Test fun disabledLandscapeCannotBeTemporarilyEnabledDuringPreferenceLoading() {
        val activity = File(root, "src/main/kotlin/com/zhisheng/weather/MainActivity.kt").readText()
        val manifest = File(root, "src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:screenOrientation=\"portrait\""))
        assertTrue(activity.contains("landscapeStandby.collectAsState(initial = false)"))
        assertFalse(activity.contains("landscapeStandby.collectAsState(initial = true)"))
        assertTrue(activity.indexOf("requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT") < activity.indexOf("setContent {"))
    }

    @Test fun taskRestoreDoesNotReplaySearchShortcut() {
        val activity = File(root, "src/main/kotlin/com/zhisheng/weather/MainActivity.kt").readText()
        assertTrue(activity.contains("if (savedInstanceState == null) dispatchShortcut(intent)"))
        assertTrue(activity.contains("Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY"))
        assertTrue(activity.contains("setIntent(Intent(intent).setAction(Intent.ACTION_MAIN))"))
        assertTrue(activity.contains("var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }"))
        assertFalse(activity.contains("shortcutCommand.value = ShortcutCommand(Intent.ACTION_MAIN"))
        assertTrue(activity.contains("if (screen == AppScreen.HOME) WeatherContinuity"))
    }
}
