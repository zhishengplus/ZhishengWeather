package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewDialogTest {
    @Test
    fun `release notes show current changes without forced empty page height`() {
        assertEquals("0.1.5-beta10.3-public", WhatsNewVersion)
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val dialog = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/WhatsNewDialog.kt").readText()
        val activity = File(projectDir, "src/main/kotlin/com/zhisheng/weather/MainActivity.kt").readText()
        val settings = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/SettingsScreen.kt").readText()

        assertTrue(dialog.contains(".verticalScroll(scroll)"))
        assertFalse(dialog.contains("Modifier.weight(1f),\n                ) { currentPage"))
        val copy = WhatsNewSections.flatMap { it.notes }.joinToString { it.title + it.detail }
        listOf("收藏", "紫外线", "更新于", "两行", "系统日历", "开屏动画", "烧屏风险").forEach {
            assertTrue("missing current change $it", copy.contains(it))
        }
        listOf("全机型已适配", "彻底防止烧屏", "三星自动更新已修复", "霞光时刻", "历史天气").forEach {
            assertFalse("unverified release claim $it", copy.contains(it))
        }
        assertTrue(activity.contains("shouldShowWhatsNew()"))
        assertTrue(activity.contains("markWhatsNewSeen()"))
        assertTrue(settings.contains("更新说明"))
        assertTrue(settings.contains("检查更新"))
    }

    @Test fun `categories preserve every release note without duplication`() {
        val categories = ReleaseNotesCategory.entries.filter { it != ReleaseNotesCategory.ALL }
        val filtered = categories.flatMap(::releaseNotesFor)
        assertEquals(WhatsNewSections.toSet(), filtered.toSet())
        assertEquals(filtered.size, filtered.distinctBy { it.id }.size)
        assertEquals(WhatsNewSections, releaseNotesFor(ReleaseNotesCategory.ALL))
        categories.forEach { category ->
            val sections = releaseNotesFor(category)
            assertTrue(sections.isNotEmpty())
            assertTrue(sections.all { it.category == category && it.notes.isNotEmpty() })
            assertEquals(WhatsNewSections.filter { it.category == category }, sections)
        }
    }
}
