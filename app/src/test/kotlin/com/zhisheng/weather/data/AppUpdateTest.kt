package com.zhisheng.weather.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test
    fun manifestPrefersVersionCodeAndKeepsPublicApkUrl() {
        val info = AppUpdate.parseManifest(
            """
            {
              "versionCode": 20260901,
              "versionName": "v0.1.4",
              "apkUrl": "https://example.test/ZhishengWeather-v0.1.4-public.apk",
              "sha256": "abc",
              "notes": "修复小组件",
              "pageUrl": "https://github.com/zhishengplus/ZhishengWeather/releases/tag/v0.1.4"
            }
            """.trimIndent(),
        )
        assertEquals(20260901, info.versionCode)
        assertEquals("0.1.4", info.versionName)
        assertEquals("https://example.test/ZhishengWeather-v0.1.4-public.apk", info.apkUrl)
        assertEquals("abc", info.sha256)
        assertTrue(AppUpdate.isNewer(info, 20260831, "0.1.3"))
        assertFalse(AppUpdate.isNewer(info, 20260901, "0.1.4"))
    }

    @Test
    fun sameOrOlderVersionIsNotAnUpdate() {
        val same = AppUpdateInfo(versionCode = 20260831, versionName = "0.1.3", apkUrl = "https://example.test/app.apk")
        val olderName = AppUpdateInfo(versionName = "0.1.0", apkUrl = "https://example.test/app.apk")
        assertFalse(AppUpdate.isNewer(same, 20260831, "0.1.3"))
        assertFalse(AppUpdate.isNewer(olderName, 20260831, "0.1.3"))
        assertTrue(AppUpdate.isNewer(AppUpdateInfo(versionName = "0.1.4", apkUrl = "https://example.test/app.apk"), 20260831, "0.1.3"))
    }

    @Test
    fun githubLatestIgnoresPrivateAndPreviewApks() {
        val info = AppUpdate.parseGithubLatest(
            """
            {
              "tag_name": "v0.1.3",
              "draft": false,
              "prerelease": false,
              "html_url": "https://github.com/zhishengplus/ZhishengWeather/releases/tag/v0.1.3",
              "body": "正式版",
              "assets": [
                {"name": "ZhishengWeather-v0.1.3-full-private.apk", "state": "uploaded", "browser_download_url": "https://example.test/private.apk"},
                {"name": "ZhishengWeather-v0.1.3-public.apk", "state": "uploaded", "digest": "sha256:deadbeef", "browser_download_url": "https://example.test/public.apk"}
              ]
            }
            """.trimIndent(),
        )
        assertEquals("0.1.3", info?.versionName)
        assertEquals("https://example.test/public.apk", info?.apkUrl)
        assertEquals("deadbeef", info?.sha256)
    }

    @Test
    fun githubLatestSkipsDraftPrereleaseAndUnknownAssets() {
        assertNull(
            AppUpdate.parseGithubLatest(
                """{"tag_name":"v0.1.4","draft":true,"prerelease":false,"html_url":"https://example.test","assets":[{"name":"ZhishengWeather-v0.1.4-public.apk","state":"uploaded","browser_download_url":"https://example.test/public.apk"}]}""",
            ),
        )
        assertNull(
            AppUpdate.parseGithubLatest(
                """{"tag_name":"v0.1.4-preview","draft":false,"prerelease":true,"html_url":"https://example.test","assets":[{"name":"zhisheng-weather-v0.1.4.apk","state":"uploaded","browser_download_url":"https://example.test/public.apk"}]}""",
            ),
        )
        assertNull(
            AppUpdate.parseGithubLatest(
                """{"tag_name":"v0.1.4","draft":false,"prerelease":false,"html_url":"https://example.test","assets":[{"name":"notes.txt","state":"uploaded","browser_download_url":"https://example.test/notes.txt"}]}""",
            ),
        )
    }

    @Test
    fun publicApkNameAcceptsLegacyAndCurrentReleaseFiles() {
        assertTrue(AppUpdate.isPublicApkName("zhisheng-weather-v0.1.0.apk"))
        assertTrue(AppUpdate.isPublicApkName("ZhishengWeather-v0.1.3-public.apk"))
        assertFalse(AppUpdate.isPublicApkName("ZhishengWeather-v0.1.3-full-private.apk"))
        assertFalse(AppUpdate.isPublicApkName("ZhishengWeather-v0.1.3-public-parallel.apk"))
        assertFalse(AppUpdate.isPublicApkName("ZhishengWeather-v0.1.3-owner-upgrade-private.apk"))
    }

    @Test
    fun checkedInManifestMatchesCurrentPublicRelease() {
        val manifest = sequenceOf(
            File("update.json"),
            File("../update.json"),
        ).first { it.isFile }.readText()
        val info = AppUpdate.parseManifest(manifest)
        assertEquals(20260901, info.versionCode)
        assertEquals("0.1.5-beta3", info.versionName)
        assertTrue(info.apkUrl.endsWith("ZhishengWeather-v0.1.5-beta3-public.apk"))
        assertTrue(info.sha256?.matches(Regex("[0-9a-fA-F]{64}")) == true)
        assertTrue(AppUpdate.isNewer(info, 20260831, "0.1.3"))
        assertFalse(AppUpdate.isNewer(info, 20260901, "0.1.5-beta3"))
    }

    @Test
    fun silentUpdateCheckOnlyMarksSettingsAndNeverOpensADialog() {
        val settings = sequenceOf(
            File("src/main/kotlin/com/zhisheng/weather/ui/SettingsScreen.kt"),
            File("app/src/main/kotlin/com/zhisheng/weather/ui/SettingsScreen.kt"),
        ).first { it.isFile }.readText()
        val activity = sequenceOf(
            File("src/main/kotlin/com/zhisheng/weather/MainActivity.kt"),
            File("app/src/main/kotlin/com/zhisheng/weather/MainActivity.kt"),
        ).first { it.isFile }.readText()
        val dialog = sequenceOf(
            File("src/main/kotlin/com/zhisheng/weather/ui/AppUpdateDialog.kt"),
            File("app/src/main/kotlin/com/zhisheng/weather/ui/AppUpdateDialog.kt"),
        ).first { it.isFile }.readText()
        val manifest = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
        ).first { it.isFile }.readText()

        assertTrue(settings.contains("检查更新"))
        assertTrue(settings.contains("attention = availableUpdate != null"))
        assertTrue(settings.contains("不弹窗、不自动下载"))
        assertTrue(dialog.contains("不会自动下载"))
        assertTrue(activity.contains("availableUpdate = when (val result = AppUpdate.check())"))
        assertTrue(!activity.contains("showAppUpdate = true"))
        assertTrue(manifest.contains("REQUEST_INSTALL_PACKAGES"))
        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
        assertTrue(manifest.contains("@xml/file_provider_paths"))
        assertTrue(dialog.contains("AppUpdate.canSelfUpdate()"))
        assertTrue(dialog.contains("不能由公共版直接覆盖"))
    }

    @Test
    fun buildTypesOnlyAllowTheFormalPublicPackageToSelfUpdate() {
        val gradle = sequenceOf(
            File("build.gradle.kts"),
            File("app/build.gradle.kts"),
        ).first { it.isFile }.readText()

        assertTrue(gradle.contains("buildConfigField(\"boolean\", \"CAN_SELF_UPDATE\", \"true\")"))
        assertTrue(gradle.contains("buildConfigField(\"boolean\", \"CAN_SELF_UPDATE\", \"false\")"))
    }
}
