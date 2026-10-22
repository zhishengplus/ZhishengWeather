package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitColdStartRegressionTest {
    private val root = File(requireNotNull(System.getProperty("user.dir")))

    @Test fun systemRotationPolicyAppliesFromColdStartWithoutPreferenceOrSensorOverrides() {
        val activity = File(root, "src/main/kotlin/com/zhisheng/weather/MainActivity.kt").readText()
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .newDocumentBuilder().parse(File(root, "src/main/AndroidManifest.xml"))
        val activities = document.getElementsByTagName("activity")
        val main = (0 until activities.length).map { activities.item(it) as org.w3c.dom.Element }
            .single { it.getAttribute("android:name") == ".MainActivity" }
        assertTrue(main.getAttribute("android:screenOrientation") == "fullUser")
        assertTrue(main.getAttribute("android:configChanges").split('|').containsAll(listOf("orientation", "screenSize")))
        assertTrue(activity.contains("landscapeStandby.collectAsState(initial = false)"))
        assertFalse(activity.contains("landscapeStandby.collectAsState(initial = true)"))
        assertFalse(activity.contains("requestedOrientation"))
        assertFalse(activity.contains("OrientationEventListener"))
        assertFalse(activity.contains("StandbyTiltGate"))
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
