package com.zhisheng.weather.ui
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.AppAnnouncement
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme
import org.junit.Rule
import org.junit.Test

class AnnouncementVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5.copy(density = Density.MEDIUM), theme = "android:Theme.Material.Light.NoActionBar")
    private fun capture(name: String, light: Boolean, width: Int, height: Int, scale: Float, long: Boolean) {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width, screenHeight = height,
            density = Density.MEDIUM, fontScale = scale, orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        val notice = AppAnnouncement("12345678-1234-1234-1234-123456789abc", if (long) "服务维护安排与使用说明：长标题也应完整阅读并能够关闭" else "感谢一路同行",
            if (long) "这是一条原生排版验证公告，正文保留换行。\n".repeat(50).take(2000) else "新的天气体验正在慢慢变好。\n\n感谢你分享遇到的问题，也谢谢每一份支持。", 1792368000000, 1794960000000, 20261086)
        val view = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent { ZhishengWeatherTheme(isLight = light, interfaceStyle = InterfaceStyle.PHOSPHOR_VISTA) {
                Box(Modifier.fillMaxSize().background(if (light) Color(0xFFCDD6DD) else Color(0xFF080D14))) { AnnouncementDialog(notice) { } }
            } }
        }
        paparazzi.snapshot(view, name)
    }
    @Test fun readableLightAndDarkGlass() { for (light in listOf(true, false)) capture("notice_${if (light) "light" else "dark"}", light, 390, 844, 1f, false) }
    @Test fun longContentLargeFontsAndShortLandscapeKeepCloseAction() {
        for (light in listOf(true, false)) {
            capture("notice_large_${if (light) "light" else "dark"}", light, 320, 640, 2f, true)
            capture("notice_landscape_${if (light) "light" else "dark"}", light, 640, 360, 1.3f, true)
        }
    }
}
