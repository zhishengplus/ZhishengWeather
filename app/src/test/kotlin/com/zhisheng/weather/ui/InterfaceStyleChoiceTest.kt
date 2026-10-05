package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceStyleChoiceTest {
    private val projectDir = File(requireNotNull(System.getProperty("user.dir")))

    @Test
    fun updateFlowOffersBothInterfaceStylesExactlyOnce() {
        val activity = File(projectDir, "src/main/kotlin/com/zhisheng/weather/MainActivity.kt").readText()
        val chooser = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/InterfaceStyleChoiceDialog.kt").readText()

        assertTrue(activity.contains("shouldShowInterfaceStyleChoice()"))
        assertTrue(activity.contains("markInterfaceStyleChoiceSeen()"))
        assertTrue(activity.contains("InterfaceStyleChoiceDialog("))
        assertTrue(activity.contains("showStyleChoiceAfterWhatsNew"))
        assertTrue(chooser.contains("InterfaceStyle.PHOSPHOR_VISTA"))
        assertTrue(chooser.contains("InterfaceStyle.CLASSIC_TERMINAL"))
        assertTrue(chooser.contains("澄空终端"))
        assertTrue(chooser.contains("经典终端"))
        assertTrue(chooser.contains("选择后仍可在设置中切换"))
    }
}
