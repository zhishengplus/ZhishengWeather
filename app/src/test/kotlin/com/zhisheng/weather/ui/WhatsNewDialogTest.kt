package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewDialogTest {
    @Test
    fun `beta3 update guide is written for users upgrading from 013`() {
        assertEquals("0.1.5-beta3", WhatsNewVersion)

        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val dialog = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/WhatsNewDialog.kt").readText()
        val activity = File(projectDir, "src/main/kotlin/com/zhisheng/weather/MainActivity.kt").readText()
        val settings = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/SettingsScreen.kt").readText()

        assertTrue(dialog.contains("这次更新了什么"))
        assertTrue(dialog.contains("WHAT'S NEW"))
        assertTrue(dialog.contains("过去7日"))
        assertFalse(dialog.contains("0.1.0"))
        assertFalse(dialog.contains("Preview"))
        assertTrue(dialog.contains("前后查日期"))
        assertTrue(dialog.contains("温度对比"))
        assertTrue(dialog.contains("相邻画面平滑衔接"))
        assertFalse(dialog.contains("天地图底图"))
        assertFalse(dialog.contains("本机矢量底图"))
        assertTrue(dialog.contains("时空观测"))
        assertTrue(dialog.contains("五日预报"))
        assertTrue(dialog.contains("近15日天气"))
        assertTrue(dialog.contains("三星、真我"))
        assertTrue(dialog.contains("组件底色"))
        assertTrue(dialog.contains("全透明、玻璃和不透明"))
        assertTrue(dialog.contains("缺少的项目宁可留空"))
        assertTrue(dialog.contains("城市收藏"))
        assertTrue(!dialog.contains("QQ"))
        assertTrue(!dialog.contains("群号"))
        assertTrue(!dialog.contains("本机凭据"))
        assertTrue(dialog.contains("横向冷启动"))
        assertTrue(!dialog.contains("缺路"))
        assertTrue(!dialog.contains("公共源补齐"))
        assertTrue(!dialog.contains("阻断构建"))
        assertTrue(!dialog.contains("实验室"))
        assertTrue(!dialog.contains("开发中"))
        assertTrue(!dialog.contains("试错"))
        assertTrue(dialog.contains("\"更新说明\""))
        assertTrue(!dialog.contains("0.0.8"))
        assertTrue(!dialog.contains("这次不只是修补"))
        assertTrue(activity.contains("shouldShowWhatsNew()"))
        assertTrue(activity.contains("markWhatsNewSeen()"))
        assertTrue(activity.contains("AppUpdate.check"))
        assertTrue(!activity.contains("showAppUpdate = true"))
        assertTrue(settings.contains("v\${com.zhisheng.weather.BuildConfig.VERSION_NAME} · 更新说明"))
        assertTrue(settings.contains("检查更新"))
        assertTrue(settings.contains("自动检测更新 · 不弹窗、不自动下载"))
        assertTrue(settings.contains("AppUpdateDialog"))
    }
}
