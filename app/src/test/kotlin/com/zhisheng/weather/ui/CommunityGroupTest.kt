package com.zhisheng.weather.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityGroupTest {
    @Test
    fun `community contact is built into every distribution`() {
        val projectDir = File(requireNotNull(System.getProperty("user.dir")))
        val settings = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/SettingsScreen.kt").readText()
        val dialog = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/CommunityGroupDialog.kt").readText()
        val groups = File(projectDir, "src/main/kotlin/com/zhisheng/weather/ui/CommunityGroups.kt").readText()
        val build = File(projectDir, "build.gradle.kts").readText()
        assertTrue(settings.contains("用户交流 QQ 群"))
        assertFalse(settings.contains("CommunityQqGroup.isNotBlank()"))
        assertTrue(settings.contains("CommunityGroupDialog"))
        assertTrue(groups.contains("R.drawable.qq_group_2_qr"))
        assertTrue(groups.contains("R.drawable.qq_group_1_qr"))
        assertTrue(build.contains("COMMUNITY_QQ_GROUP"))
        assertTrue(build.contains("1106284779"))
        assertTrue(build.contains("562847192"))
        assertFalse(Regex("\\d{9,12}").containsMatchIn(dialog))
    }

    @Test fun `join fallback retains the selected group`() {
        val selected = CommunityGroups.last()
        val attempts = mutableListOf<Pair<String, String?>>()
        assertTrue(openCommunityGroupLink(selected) { url, target ->
            attempts += url to target
            target == null
        })
        assertEquals(listOf(selected.joinUrl to "com.tencent.mobileqq", selected.joinUrl to null), attempts)
    }

    @Test fun `successful QQ launch does not also open a browser`() {
        var attempts = 0
        assertTrue(openCommunityGroupLink(CommunityGroups.first()) { _, _ -> attempts++; true })
        assertEquals(1, attempts)
    }

    @Test fun `unavailable handlers report a failed launch`() {
        assertFalse(openCommunityGroupLink(CommunityGroups.first()) { _, _ -> false })
    }
}
