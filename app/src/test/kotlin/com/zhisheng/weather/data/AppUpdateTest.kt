package com.zhisheng.weather.data

import com.zhisheng.weather.BuildConfig
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
    fun giteeManifestKeepsChannelIdentityAndUsesGiteeAsDefaultPage() {
        val info = AppUpdate.parseManifest(
            """{"versionName":"0.1.5-beta5","apkUrl":"https://example.test/app.apk"}""",
            UpdateChannel.GITEE,
        )

        assertEquals(UpdateChannel.GITEE, info.channel)
        assertEquals(AppUpdate.GITEE_RELEASES_PAGE, info.pageUrl)
    }

    @Test
    fun githubHostedManifestStillSelectsGiteeWhenItsApkIsOnGitee() {
        val info = AppUpdate.parseManifest(
            """{"versionCode":20260974,"versionName":"0.1.5-beta10.1-public","apkUrl":"https://gitee.com/zhisheng8888/ZhishengWeather/releases/download/v0.1.5-beta10.1-public/ZhishengWeather-v0.1.5-beta10.1-public.apk"}""",
        )
        assertEquals(UpdateChannel.GITEE, info.channel)
        assertEquals(AppUpdate.GITEE_RELEASES_PAGE, info.pageUrl)
        assertTrue(AppUpdate.isNewer(info, 20260973, "0.1.5-beta10-public"))
        assertTrue(AppUpdate.isNewer(info, 20260912, "0.1.5-beta7"))
    }

    @Test
    fun giteeFallbackUsesSameVersionTagAndApkFileName() {
        val info = AppUpdate.asGiteeRelease(
            AppUpdateInfo(
                versionName = "0.1.5-beta5",
                apkUrl = "https://github.com/example/releases/download/v0.1.5-beta5/ZhishengWeather-v0.1.5-beta5-public.apk",
            ),
        )

        assertEquals(UpdateChannel.GITEE, info.channel)
        assertEquals(
            "https://gitee.com/zhisheng8888/ZhishengWeather/releases/download/v0.1.5-beta5/ZhishengWeather-v0.1.5-beta5-public.apk",
            info.apkUrl,
        )
        assertEquals(
            "https://gitee.com/zhisheng8888/ZhishengWeather/releases/tag/v0.1.5-beta5",
            info.pageUrl,
        )
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
    fun releaseFeedCanDiscoverPublicBetaThatLatestStableEndpointOmits() {
        val feed = """[
            {"tag_name":"v0.1.5-beta9-public","draft":false,"prerelease":true,
             "assets":[{"name":"ZhishengWeather-v0.1.5-beta9-public.apk","state":"uploaded",
                         "browser_download_url":"https://example.test/beta9.apk"}]},
            {"tag_name":"v0.1.5-beta7","draft":false,"prerelease":false,"assets":[]}
        ]""".trimIndent()
        assertEquals("0.1.5-beta9-public", AppUpdate.parseGithubFeed(feed)?.versionName)
        assertEquals("https://example.test/beta9.apk", AppUpdate.parseGithubFeed(feed)?.apkUrl)
    }

    @Test
    fun staleManifestCannotMaskNewerPublicRelease() {
        val stale = AppUpdateInfo(versionCode = 20260912, versionName = "0.1.5-beta7", apkUrl = "https://example.test/old.apk")
        val public = AppUpdateInfo(versionName = "0.1.5-beta9-public", apkUrl = "https://example.test/new.apk")
        assertEquals(public, AppUpdate.newestOf(stale, public))
        assertEquals(stale, AppUpdate.newestOf(stale, public.copy(versionName = "0.1.5-beta6")))
    }

    @Test
    fun githubLatestSkipsDraftAndUnknownAssetsButAcceptsPublicBeta() {
        assertNull(
            AppUpdate.parseGithubLatest(
                """{"tag_name":"v0.1.4","draft":true,"prerelease":false,"html_url":"https://example.test","assets":[{"name":"ZhishengWeather-v0.1.4-public.apk","state":"uploaded","browser_download_url":"https://example.test/public.apk"}]}""",
            ),
        )
        assertEquals("0.1.5-beta9-public", AppUpdate.parseGithubLatest(
            """{"tag_name":"v0.1.5-beta9-public","draft":false,"prerelease":true,"html_url":"https://example.test","assets":[{"name":"zhisheng-weather-v0.1.5-beta9-public.apk","state":"uploaded","browser_download_url":"https://example.test/public.apk"}]}""",
        )?.versionName)
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
    fun releaseAssetMustBelongToTheRequestedVersion() {
        val assets = listOf(
            GithubAssetDto(
                name = "ZhishengWeather-v0.1.5-beta5-public.apk",
                browserDownloadUrl = "https://example.test/beta5.apk",
            ),
            GithubAssetDto(
                name = "ZhishengWeather-v0.1.5-beta6-public.apk",
                browserDownloadUrl = "https://example.test/beta6.apk",
            ),
        )
        assertEquals(
            "https://example.test/beta6.apk",
            AppUpdate.pickPublicApk(assets, "0.1.5-beta6")?.browserDownloadUrl,
        )
        assertNull(AppUpdate.pickPublicApk(assets, "0.1.5-beta7"))
        assertFalse(AppUpdate.apkNameMatchesVersion("ZhishengWeather-v0.1.5-beta60-public.apk", "0.1.5-beta6"))
    }

    @Test
    fun checkedInReleaseRemainsValidWithoutDowngradingDevelopmentBuild() {
        val manifest = sequenceOf(
            File("update.json"),
            File("../update.json"),
        ).first { it.isFile }.readText()
        val info = AppUpdate.parseManifest(manifest)
        // Locally prepared release metadata must match the shareable APK name.
        assertTrue(info.versionCode != null && info.versionCode!! > 0)
        val apkName = if (info.versionName.endsWith("-public"))
            "ZhishengWeather-v${info.versionName}.apk" else "ZhishengWeather-v${info.versionName}-public.apk"
        assertTrue(info.apkUrl.endsWith(apkName))
        assertTrue(info.sha256?.matches(Regex("[0-9a-fA-F]{64}")) == true)
        assertTrue(info.notes.contains("小组件"))
        assertTrue(AppUpdate.isNewer(info, 20260831, "0.1.3"))
        assertTrue(AppUpdate.isNewer(info, 20260901, "0.1.5-beta3"))
        assertTrue(AppUpdate.isNewer(info, 20260912, "0.1.5-beta7"))
        assertTrue(AppUpdate.isNewer(info, 20260973, "0.1.5-beta10-public"))
        assertFalse(AppUpdate.isNewer(info, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME))
    }

    @Test
    fun semverPrereleaseOrderingMatchesReleaseChannels() {
        // 排序：beta5 < beta5-2 < beta6 < beta10 < 同版本正式版
        fun newer(remote: String, local: String) = AppUpdate.compareSemver(remote, local) > 0
        assertTrue(newer("0.1.5-beta6", "0.1.5-beta5"))
        assertTrue(newer("0.1.5-beta5-2", "0.1.5-beta5"))
        assertTrue(newer("0.1.5-beta6", "0.1.5-beta5-2"))
        assertTrue(newer("0.1.5-beta10", "0.1.5-beta6"))
        assertTrue(newer("0.1.5", "0.1.5-beta6"))
        assertFalse(newer("0.1.5-beta6", "0.1.5"))
        assertFalse(newer("0.1.5-beta5", "0.1.5-beta6"))
        assertEquals(0, AppUpdate.compareSemver("0.1.5-beta6", "v0.1.5-beta6"))
    }

    @Test
    fun manifestRejectsNonHttpsApkUrl() {
        val raw = """
            {"versionCode":20260906,"versionName":"0.1.5-beta6","apkUrl":"http://example.com/a.apk","sha256":"abc"}
        """.trimIndent()
        val err = runCatching { AppUpdate.parseManifest(raw) }.exceptionOrNull()
        assertTrue("非 https 下载地址必须被拒绝：$err", err is IllegalArgumentException)
    }

    @Test
    fun updateCheckCacheRulesShortTermSuccessOnly() {
        val available = AppUpdateCheck.Available(
            AppUpdateInfo(versionCode = 2, versionName = "0.2", apkUrl = "https://example.com/a.apk"),
        )
        val failed: AppUpdateCheck = AppUpdateCheck.Failed("offline")
        assertTrue(AppUpdate.shouldReuseCache(available, refresh = false, elapsedMs = 60_000L))
        assertFalse(AppUpdate.shouldReuseCache(available, refresh = true, elapsedMs = 0L))
        assertFalse(AppUpdate.shouldReuseCache(available, refresh = false, elapsedMs = 10 * 60_000L))
        assertFalse(AppUpdate.shouldReuseCache(failed, refresh = false, elapsedMs = 1_000L))
        assertFalse(AppUpdate.shouldReuseCache(null, refresh = false, elapsedMs = 0L))
    }

    @Test
    fun updateCheckOffersVersionChoicesWithoutAutomaticDownload() {
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
        assertTrue(settings.contains("可忽略此版本或延后 3 天"))
        assertTrue(dialog.contains("不会自动下载"))
        assertTrue(activity.contains("for (attempt in 0..2)"))
        assertTrue(activity.contains("AppUpdate.check(refresh = true)"))
        assertTrue(!activity.contains("showAppUpdate = true"))
        assertTrue(manifest.contains("REQUEST_INSTALL_PACKAGES"))
        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
        assertTrue(manifest.contains("@xml/file_provider_paths"))
        assertTrue(dialog.contains("AppUpdate.canSelfUpdate()"))
        assertTrue(dialog.contains("不能由公共版直接覆盖"))
        assertTrue(dialog.contains("pageActionLabel"))
        assertTrue(settings.contains("downloadOnOpen = true"))
        assertTrue(dialog.contains("[ 立即更新 ]"))
        assertTrue(activity.contains("AppUpdate.check()"))
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

    @Test
    fun downloadProgressIsLimitedByPercentOrTime() {
        assertTrue(AppUpdate.shouldEmitDownloadProgress(-1, 0L, 0, 1L))
        assertFalse(AppUpdate.shouldEmitDownloadProgress(20, 1_000L, 20, 1_050L))
        assertTrue(AppUpdate.shouldEmitDownloadProgress(20, 1_000L, 21, 1_010L))
        assertTrue(AppUpdate.shouldEmitDownloadProgress(20, 1_000L, 20, 1_100L))
        assertTrue(AppUpdate.shouldEmitDownloadProgress(99, 1_000L, 100, 1_001L))
    }
}
