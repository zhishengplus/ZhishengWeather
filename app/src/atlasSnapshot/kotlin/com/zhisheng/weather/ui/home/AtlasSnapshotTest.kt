package com.zhisheng.weather.ui.home

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.zhisheng.weather.model.*
import com.zhisheng.weather.ui.DisplayPrefs
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.SponsorBoard
import com.zhisheng.weather.ui.SponsorBoardRow
import com.zhisheng.weather.ui.SponsorEntranceLight
import com.android.ide.common.rendering.api.SessionParams.RenderingMode
import com.zhisheng.weather.ui.theme.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Instant

/** Layout-only fixture. Never bundled in the APK and never requests user locations or providers. */
@RunWith(Parameterized::class)
class AtlasSnapshotTest(private val width: Int, private val scale: Float) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}dp_{1}x")
        fun configurations() = listOf(arrayOf<Any>(320, 1f), arrayOf<Any>(375, 1f), arrayOf<Any>(414, 1f), arrayOf<Any>(768, 1f), arrayOf<Any>(375, 1.5f))
    }
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width * 3, screenHeight = maxOf(width * 3 + 1, 1920), fontScale = scale),
        theme = "android:Theme.Material.Light.NoActionBar", renderingMode = RenderingMode.V_SCROLL)
    private val city = City("青岛 · 布局示例", "山东", 36.07, 120.38, "fixture")
    private val sunset = Instant.parse("2026-09-04T10:20:00Z").toEpochMilli()
    private val sky = SkyPhotographyForecast(
        GlowForecast(GlowKind.DUSK, "值得留意", sunset - 20 * 60_000, sunset + 20 * 60_000,
            listOf("高云有利于映照晚霞", "低层云可能遮住部分天空")),
        StarForecast(72, "适合留意", sunset + 3 * 3_600_000, sunset + 6 * 3_600_000,
            listOf("窗口内云量较少", "月光仍可能影响暗弱星体"), "优先选择远离路灯、视野开阔的位置。", .38),
        updatedAtMillis = sunset - 3_600_000, zoneId = "Asia/Shanghai")
    private val levels = listOf(.2,.5,.8,1.0,1.1,.9,.6,.2,-.1,-.3,-.4,-.3,0.0,.3,.7,1.0,1.2,1.1,.8,.4,.1,-.2,-.3,-.1,.2)
    private val coast = CoastalForecast(TideTrend.RISING, levels.mapIndexed { i, h -> MarinePoint(sunset + i * 3_600_000, h) },
        TideTurningPoint(sunset + 4 * 3_600_000, 1.1), TideTurningPoint(sunset + 10 * 3_600_000, -.4),
        .8, 135.0, 5.6, .5, 7.8, 24.6, .4, 70.0, 8.2, updatedAtMillis = sunset, zoneId = "Asia/Shanghai", windWaveHeightM = .3)
    private val scene = SceneWeatherData(sky, coast)

    private fun snapshot(light: Boolean, content: @Composable () -> Unit) {
        val host = ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setContent {
                ZhishengWeatherTheme(isLight = light) {
                    Column(Modifier.fillMaxWidth().background(ZhishengBg).padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("布局示例 · 非实时天气", Modifier.padding(horizontal = 20.dp), color = ZhishengTextSecondary)
                        content()
                    }
                }
            }
        }
        paparazzi.snapshot(host)
    }

    @Test fun atlasLight() = snapshot(true) { AtlasBody() }
    @Test fun atlasDark() = snapshot(false) { AtlasBody() }
    @Composable private fun AtlasBody() {
        AtlasSectionHeading(6)
        WeatherAtlas(WeatherData(yesterday = YesterdayInfo(29.0, 23.0)), scene, city, "c", DisplayPrefs(),
            onHistoryClick = {}, onRadarClick = {}, radarContent = {
                AtlasUnavailableGraphic("无回波示例数据", 92.dp)
                AtlasCaption("点击打开地图")
            })
    }
    @Composable private fun DetailBody(page: AtlasPage, content: @Composable () -> Unit) {
        AtlasPageHeader(page, {})
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            AtlasCaption(city.displayName)
            content()
        }
    }
    @Test fun glowLight() = snapshot(true) { DetailBody(AtlasPage.GLOW) { AtlasGlowDetail(scene, city) } }
    @Test fun starsDark() = snapshot(false) { DetailBody(AtlasPage.STARS) { AtlasStarsDetail(scene, city) } }
    @Test fun coastLight() = snapshot(true) { DetailBody(AtlasPage.COAST) { AtlasCoastDetail(scene, "c") } }
    @Test fun missingLight() = snapshot(true) { DetailBody(AtlasPage.COAST) { AtlasCoastDetail(SceneWeatherData(coastError = "此地点暂无海域覆盖"), "c"); AtlasDarkDetail() } }
    @Test fun sponsorLight() = snapshot(true) { SponsorBody(false) }
    @Test fun sponsorEntranceDark() = snapshot(false) { SponsorBody(true) }
    @Composable private fun SponsorBody(entrance: Boolean) {
        Box {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SPONSOR BOARD / 赞助榜单", color = ZhishengCyan)
                SponsorBoard.forEachIndexed { i, name -> SponsorBoardRow(i + 1, name, true, null) }
            }
            if (entrance) SponsorEntranceLight(.15f, Modifier.matchParentSize())
        }
    }
}
