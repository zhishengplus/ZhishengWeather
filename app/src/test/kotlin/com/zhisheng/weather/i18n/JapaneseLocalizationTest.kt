package com.zhisheng.weather.i18n

import com.zhisheng.weather.data.AppLanguage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseLocalizationTest {
    @Test
    fun `Japanese alert details retain place severity time and quantities`() {
        val source = "金昌市发布暴雨红色预警，预计未来 3 小时降雨 100 毫米"
        val localized = uiText(source, AppLanguage.JAPANESE)
        listOf("金昌", "3", "100").forEach { assertTrue("Lost alert detail: $localized", localized.contains(it)) }
        assertTrue("Lost red warning severity: $localized", localized.contains("赤") || localized.contains("红"))
    }

    @Test
    fun `Japanese metric labels retain UV and visibility values`() {
        assertTrue(uiText("紫外线指数 0", AppLanguage.JAPANESE).contains("0"))
        assertTrue(uiText("能见度 12 km", AppLanguage.JAPANESE).contains("12 km"))
    }

    @Test
    fun `Japanese daily digest retains dates temperatures and rain probability`() {
        val localized = uiText("明天 10月1日：高温 28°，低温 19°，降雨概率 40%", AppLanguage.JAPANESE)
        listOf("10", "1", "28", "19", "40%").forEach { assertTrue("Lost forecast value: $localized", localized.contains(it)) }
    }

    @Test
    fun `primary navigation uses native Japanese wording`() {
        assertEquals("設定", uiText("设置", AppLanguage.JAPANESE))
        assertEquals("データと現在地", uiText("数据与定位", AppLanguage.JAPANESE))
        assertEquals("雨雲レーダー", uiText("雷达回波", AppLanguage.JAPANESE))
        assertEquals("お天気ガールのひとこと", uiText("天气娘简报", AppLanguage.JAPANESE))
    }

    @Test
    fun `dynamic forecast labels retain values`() {
        assertEquals("15日間天気予報", uiText("15日天气预报", AppLanguage.JAPANESE))
        assertEquals("15日分の予報を受信 · °C", uiText("已接收 15 日预报 · °C", AppLanguage.JAPANESE))
        assertTrue(weatherBriefingText("明天会比今天低 7°，今晚把外套备好。", AppLanguage.JAPANESE).contains("7℃"))
    }

    @Test
    fun `Chinese mode never rewrites provider or user text`() {
        val source = "当前城市 · 彩云天气"
        assertEquals(source, uiText(source, AppLanguage.CHINESE))
    }

    @Test
    fun `all Compose pages pass visible text through the localization gate`() {
        val root = File(requireNotNull(System.getProperty("user.dir")))
        val uiRoot = File(root, "src/main/kotlin/com/zhisheng/weather/ui")
        val offenders = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "LocalizedText.kt" }
            .filter { it.readText().contains("import androidx.compose.material3.Text") }
            .map(File::getName)
            .toList()

        assertTrue("Direct Material Text imports bypass localization: $offenders", offenders.isEmpty())
        assertTrue(File(uiRoot, "home/HomeScreen.kt").readText().contains("import com.zhisheng.weather.ui.Text"))
    }

    @Test
    fun `Japanese Android resources mirror every base string key`() {
        val root = File(requireNotNull(System.getProperty("user.dir")))
        fun keys(path: String): Set<String> = Regex("name=\"([^\"]+)\"")
            .findAll(File(root, path).readText())
            .map { it.groupValues[1] }
            .toSet()

        val base = keys("src/main/res/values/strings.xml")
        val japanese = keys("src/main/res/values-ja/strings.xml")
        assertFalse(base.isEmpty())
        assertEquals(base, japanese)
    }
}
