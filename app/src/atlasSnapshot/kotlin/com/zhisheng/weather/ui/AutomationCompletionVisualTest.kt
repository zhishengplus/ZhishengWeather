package com.zhisheng.weather.ui

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.zhisheng.weather.data.*
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.theme.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Actual production content; window/system-bar behavior is verified separately on Android. */
class AutomationCompletionVisualTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5,
        theme = "android:Theme.Material.Light.NoActionBar")

    private fun capture(name: String, style: InterfaceStyle, light: Boolean, width: Int, height: Int,
        fontScale: Float = 1.5f,
        check: ((SemanticsNode) -> Unit)? = null,
        progressiveCheck: ((SemanticsNode) -> Boolean)? = null,
        content: @androidx.compose.runtime.Composable () -> Unit): ComposeView {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(screenWidth = width * 3,
            screenHeight = height * 3, fontScale = fontScale,
            orientation = if (width > height) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT))
        var checked = check == null && progressiveCheck == null
        return ComposeView(paparazzi.context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setContent { CompositionLocalProvider(LocalInspectionMode provides true) {
                ZhishengWeatherTheme(isLight = light, interfaceStyle = style, softGlow = false,
                    homeSurfaceStyle = HomeSurfaceStyle.FRAGRANCE_GLASS) { content() }
            } }
            if (check != null || progressiveCheck != null) viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!checked) {
                        val root = descendants(this@apply).firstNotNullOfOrNull { view ->
                            view.javaClass.methods.firstOrNull { it.name == "getSemanticsOwner" && it.parameterCount == 0 }
                                ?.invoke(view) as? SemanticsOwner
                        }?.unmergedRootSemanticsNode ?: error("Missing actual Compose semantics")
                        check?.invoke(root)
                        checked = progressiveCheck?.invoke(root) ?: true
                    }
                    if (progressiveCheck != null && !checked) this@apply.postInvalidateOnAnimation()
                    return true
                }
            })
            if (progressiveCheck == null) paparazzi.snapshot(this, name)
            else paparazzi.gif(this, name, end = 2_000, fps = 8)
            assertTrue("Native check must run before Paparazzi disposes the composition", checked)
        }
    }

    @Test fun allReminderStatesUseReadableTypeInBothStylesAndLightLevels() {
        for (style in InterfaceStyle.entries) for (light in listOf(true, false)) {
            capture("automation_${style.key}_${light}_320_150", style, light, 320, 800) {
                Column(Modifier.fillMaxSize().background(LocalZhishengPalette.current.bg)
                    .verticalScroll(rememberScrollState())) {
                    AutomationSettingsContent(AutomationSettings(backgroundEnabled = true, morningEnabled = true,
                        eveningEnabled = true, rainEnabled = true, backgroundMinutes = 120),
                        ReminderNotificationAccess.CHANNEL_BLOCKED, onChange = {}, onNotificationAction = {})
                }
            }
            capture("automation_landscape_${style.key}_${light}_150", style, light, 640, 300) {
                Column(Modifier.fillMaxSize().background(LocalZhishengPalette.current.bg)
                    .verticalScroll(rememberScrollState())) {
                    AutomationSettingsContent(AutomationSettings(), ReminderNotificationAccess.ALLOWED,
                        onChange = {}, onNotificationAction = {})
                }
            }
        }
    }

    @Test fun allowedReminderStatusAndTestFeedbackRemainReachableWithLargeType() {
        val settings = AutomationSettings(backgroundEnabled = true, morningEnabled = true,
            eveningEnabled = true, rainEnabled = true, foregroundMinutes = 5, backgroundMinutes = 120)
        val checkedAt = Instant.parse("2026-10-10T04:10:00Z").toEpochMilli()
        val today = Instant.parse("2026-10-09T16:00:00Z").toEpochMilli()
        val weather = WeatherData(fetchedAt = checkedAt, utcOffsetSeconds = 28800,
            daily = listOf(DailyWeather(today, high = 24.0, low = 12.0)))
        val status = ReminderPanelStatus(cityName = "镇原县·临泾镇", utcOffsetSeconds = 28800,
            result = evaluateWeatherReminders("fixture", weather, settings,
                mapOf("fixture:morning:2026-10-10" to checkedAt - 4 * 3_600_000L), checkedAt),
            lastCheckedAt = checkedAt, quietNotifications = true)
        val feedback = "测试通知已提交，请查看通知栏；横幅和声音跟随系统设置"
        val footerLabels = listOf("立即检查", "测试通知", "通知显示设置")

        for (style in InterfaceStyle.entries) for (light in listOf(true, false)) {
            for ((width, height) in listOf(320 to 640, 640 to 240)) {
                var contentChecked = false
                var footerIndex = 0
                var pendingScrollTo: Float? = null
                capture("automation_allowed_${style.key}_${light}_${width}_${height}_200",
                    style, light, width, height, fontScale = 2f, progressiveCheck = { root ->
                    val all = nodes(root)
                    val scroller = all.firstOrNull {
                        it.config.getOrNull(SemanticsActions.ScrollBy) != null &&
                            (it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke() ?: 0f) > 0f
                    } ?: error("Enabled reminders must have an actual scrollable viewport")
                    val range = scroller.config[SemanticsProperties.VerticalScrollAxisRange]
                    if (!contentChecked) {
                        val choices = all.filter { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }
                        if (style == InterfaceStyle.PHOSPHOR_VISTA) {
                            assertTrue("Vista settings must show compact values instead of inline interval grids", choices.isEmpty())
                            for ((label, value) in listOf("天气刷新间隔" to "5分钟", "后台刷新间隔" to "120分钟",
                                    "早间提醒时间" to "07:00", "晚间提醒时间" to "19:00")) {
                                val row = button(root, label)
                                assertTrue("$label must expose its saved value in the same clickable row", nodes(row).any {
                                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == value } == true
                                })
                                assertTrue("Setting rows retain 48dp touch targets", row.size.height >= 48 * 3)
                            }
                        } else {
                        assertEquals("Both refresh groups must expose all interval choices", 8, choices.size)
                        assertEquals("Each refresh group must expose exactly one selected interval", 2,
                            choices.count { it.config.getOrNull(SemanticsProperties.Selected) == true })
                        val selectedLabels = choices.filter { it.config.getOrNull(SemanticsProperties.Selected) == true }
                            .flatMap { choice -> nodes(choice).flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() } }
                            .map { it.text }.toSet()
                        assertEquals("The rendered selection must match both saved refresh intervals",
                            setOf("5分钟", "120分钟"), selectedLabels)
                        assertTrue("Interval touch targets must retain 48dp at density 3",
                            choices.all { it.size.height >= 48 * 3 && it.size.width >= 48 * 3 })
                        }
                        val intervalTexts = all.filter { node ->
                            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.matches(Regex("\\d+分钟")) }
                        }
                        assertEquals("Every shown interval must keep its actual rendered label", if (style == InterfaceStyle.PHOSPHOR_VISTA) 2 else 8, intervalTexts.size)
                        intervalTexts.forEach { node ->
                            val layouts = mutableListOf<TextLayoutResult>()
                            assertTrue(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts) == true)
                            assertTrue("Interval labels must have actual text layouts", layouts.isNotEmpty())
                            val measured = layouts.joinToString { result ->
                                "${result.layoutInput.text.text}: ${result.size}, widthOverflow=${result.didOverflowWidth}, heightOverflow=${result.didOverflowHeight}, lines=${result.lineCount}, constraints=${result.layoutInput.constraints}"
                            }
                            assertTrue("$style / light=$light / ${width}x$height interval text must fit: node=${node.size}, layouts=$measured",
                                layouts.none { it.hasVisualOverflow })
                        }
                        val labels = all.flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
                        assertTrue("The current reminder city must be visible in the content",
                            labels.any { it.startsWith("提醒地点：镇原县·临泾镇") })
                        assertTrue("The actual already-sent state must be explained", labels.any { it.startsWith("今天已发送到通知栏") })
                        assertTrue("The actual waiting state must be explained", labels.any { it.startsWith("等待提醒时间，到时检查预报") })
                        assertTrue("Missing short-term precipitation must retain its actual reason",
                            labels.contains("当前来源未返回可用短时降水数据，更新后再检查"))
                        assertTrue("The actual test-notification feedback must appear", feedback in labels)
                        footerLabels.forEach { label ->
                            assertFalse("$label must be usable when no action is running",
                                button(root, label).config.contains(SemanticsProperties.Disabled))
                        }
                        contentChecked = true
                        false
                    } else if (pendingScrollTo?.let { kotlin.math.abs(range.value() - it) > 1f } == true) {
                        false
                    } else {
                        pendingScrollTo = null
                        val viewport = scroller.boundsInRoot
                        val actions = footerLabels.map { it to button(root, it) }
                        // Layout positions retain off-screen geometry; boundsInRoot is
                        // clipped and cannot tell us how far a hidden button must move.
                        val groupTop = actions.minOf { it.second.positionInRoot.y }
                        val groupBottom = actions.maxOf { it.second.positionInRoot.y + it.second.size.height }
                        val together = groupBottom - groupTop <= viewport.height + 1f
                        val targets = if (together) actions else listOf(actions[footerIndex])
                        val top = targets.minOf { it.second.positionInRoot.y }
                        val bottom = targets.maxOf { it.second.positionInRoot.y + it.second.size.height }
                        val delta = when {
                            top < viewport.top - 1f -> top - viewport.top
                            bottom > viewport.bottom + 1f -> bottom - viewport.bottom
                            else -> 0f
                        }
                        if (kotlin.math.abs(delta) > 1f) {
                            val nextScroll = (range.value() + delta).coerceIn(0f, range.maxValue())
                            assertTrue("The footer target must fit within the real scroll range",
                                kotlin.math.abs(nextScroll - range.value()) > 1f)
                            pendingScrollTo = nextScroll
                            assertTrue("Scroll must be accepted by the actual viewport",
                                scroller.config[SemanticsActions.ScrollBy].action?.invoke(0f, nextScroll - range.value()) == true)
                            false
                        } else {
                            targets.forEach { (label, action) ->
                                val bounds = action.boundsInRoot
                                assertTrue("$label must actually enter the viewport after scrolling",
                                    bounds.top >= viewport.top - 1f && bounds.bottom <= viewport.bottom + 1f &&
                                        bounds.left >= viewport.left - 1f && bounds.right <= viewport.right + 1f &&
                                        bounds.height + 1f >= action.size.height && bounds.width + 1f >= action.size.width)
                            }
                            if (together) true else {
                                footerIndex++
                                footerIndex == actions.size
                            }
                        }
                    }
                }) {
                    Column(Modifier.fillMaxSize().background(LocalZhishengPalette.current.bg)
                        .verticalScroll(rememberScrollState())) {
                        AutomationSettingsContent(settings, ReminderNotificationAccess.ALLOWED,
                            onChange = {}, onNotificationAction = {}, reminderStatus = status,
                            checking = false, testing = false, testFeedback = feedback,
                            onCheckNow = {}, onTestNotification = {}, onBackgroundAction = {})
                    }
                }
            }
        }
    }

    @Test fun completeChoiceListScrollsAndCancelNeverChangesSelection() {
        val options = AppIconStyle.entries.map { it.cn to it.key }
        for (light in listOf(true, false)) for ((width, height, font) in listOf(
            Triple(320, 480, 2f), Triple(320, 640, 1f), Triple(640, 160, 2f))) {
            var index = 0
            var checkedContent = false
            var pendingScroll: Float? = null
            val picked = mutableListOf<String>()
            var cancelled = 0
            capture("setting_choices_${light}_${width}_${height}_$font", InterfaceStyle.PHOSPHOR_VISTA,
                light, width, height, font, progressiveCheck = { root ->
                val all = nodes(root)
                val choices = all.filter { it.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton }
                val scroller = all.first { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                val range = scroller.config[SemanticsProperties.VerticalScrollAxisRange]
                val cancel = button(root, "取消")
                assertTrue("Cancel remains fully visible while the list scrolls", cancel.boundsInRoot.height + 1f >= cancel.size.height)
                assertTrue("Cancel remains a 48dp touch target", cancel.size.height >= 144)
                if (!checkedContent) {
                    assertEquals("All four icon styles must be present", 4, choices.size)
                    assertEquals("Only the saved icon is selected", 1, choices.count { it.config.getOrNull(SemanticsProperties.Selected) == true })
                    assertTrue("The last option must keep the same full row width", choices.map { it.size.width }.distinct().size == 1)
                    assertTrue("Every choice retains 48dp", choices.all { it.size.height >= 144 })
                    val selected = choices.single { it.config.getOrNull(SemanticsProperties.Selected) == true }
                    assertTrue(nodes(selected).any { it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "简约" } == true })
                    all.forEach { node ->
                        val layouts = mutableListOf<TextLayoutResult>()
                        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                        assertTrue("Actual list text must fit ${width}x$height/font$font: ${layouts.map { it.layoutInput.text.text }}",
                            layouts.none { it.hasVisualOverflow })
                    }
                    assertTrue(cancel.config[SemanticsActions.OnClick].action?.invoke() == true)
                    assertEquals(1, cancelled)
                    assertTrue("Cancelling never selects a different icon", picked.isEmpty())
                    checkedContent = true
                    false
                } else if (pendingScroll?.let { kotlin.math.abs(range.value() - it) > 1f } == true) false
                else {
                    pendingScroll = null
                    val choice = choices[index]
                    val viewport = scroller.boundsInRoot
                    val top = choice.positionInRoot.y
                    val bottom = top + choice.size.height
                    val delta = when {
                        top < viewport.top - 1f -> top - viewport.top
                        bottom > viewport.bottom + 1f -> bottom - viewport.bottom
                        else -> 0f
                    }
                    if (kotlin.math.abs(delta) > 1f) {
                        val next = (range.value() + delta).coerceIn(0f, range.maxValue())
                        assertTrue("Every choice can enter the actual viewport", kotlin.math.abs(next - range.value()) > 1f)
                        pendingScroll = next
                        assertTrue(scroller.config[SemanticsActions.ScrollBy].action?.invoke(0f, next - range.value()) == true)
                        false
                    } else {
                        assertTrue("The complete choice must be visible before selection", choice.boundsInRoot.height + 1f >= choice.size.height)
                        assertTrue(choice.config[SemanticsActions.OnClick].action?.invoke() == true)
                        assertEquals(options[index].second, picked.last())
                        index++
                        index == options.size
                    }
                }
            }) {
                Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    SettingsChoicePanel("应用图标", options, "minimal",
                        "深色与浅色随主题联动；天气娘和简约图标独立保持", onDismiss = { cancelled++ },
                        onSelect = { picked += it }, modifier = Modifier.fillMaxWidth().heightIn(max = if (height == 160) 90.dp else height.dp - 24.dp))
                }
            }
        }
    }

    private fun button(root: SemanticsNode, label: String): SemanticsNode = nodes(root).first { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null && nodes(node).any {
            it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == label } == true
        }
    }
    private fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
