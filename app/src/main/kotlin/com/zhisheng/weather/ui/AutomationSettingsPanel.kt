package com.zhisheng.weather.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.zhisheng.weather.data.AutomationSettings
import com.zhisheng.weather.data.AutomationSettingsRepository
import com.zhisheng.weather.data.WeatherReminderNotification
import com.zhisheng.weather.data.WeatherReminderSchedule
import com.zhisheng.weather.data.ReminderPanelStatus
import com.zhisheng.weather.data.CityRepository
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.ui.theme.LocalHomeSurfaceStyle
import com.zhisheng.weather.ui.theme.isPhosphorVista
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import com.zhisheng.weather.ui.theme.LocalZhishengChrome
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.zhishengCompactPanel
import com.zhisheng.weather.ui.theme.zhishengDialogPanel
import com.zhisheng.weather.widget.WidgetRefresh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.zhisheng.weather.model.cityZone
import com.zhisheng.weather.model.cityDate

internal enum class ReminderNotificationAccess { ALLOWED, APP_BLOCKED, CHANNEL_BLOCKED }

internal fun reminderNotificationSettingsIntent(access: ReminderNotificationAccess, packageName: String): Intent =
    Intent(if (access == ReminderNotificationAccess.APP_BLOCKED)
        Settings.ACTION_APP_NOTIFICATION_SETTINGS else Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .apply {
            if (access != ReminderNotificationAccess.APP_BLOCKED)
                putExtra(Settings.EXTRA_CHANNEL_ID, WeatherReminderNotification.CHANNEL)
        }

@Composable
fun AutomationSettingsPanel(showHeader: Boolean = true) {
    val context = LocalContext.current
    val settings by AutomationSettingsRepository.settings.collectAsState()
    fun access(): ReminderNotificationAccess {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return ReminderNotificationAccess.APP_BLOCKED
        val manager = context.getSystemService(NotificationManager::class.java)
        return if (manager?.getNotificationChannel(WeatherReminderNotification.CHANNEL)?.importance ==
            NotificationManager.IMPORTANCE_NONE) ReminderNotificationAccess.CHANNEL_BLOCKED else ReminderNotificationAccess.ALLOWED
    }
    var notificationAccess by rememberSaveable { mutableStateOf(access()) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var statusError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val city by CityRepository.selectedCity.collectAsState(initial = null)
    val source by SettingsRepository.sourcePref.collectAsState(initial = com.zhisheng.weather.data.SourcePref.AUTO)
    val revision by WeatherReminderNotification.revision.collectAsState()
    var status by remember { mutableStateOf<ReminderPanelStatus?>(null) }
    var checking by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testFeedback by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(settings, city, source, revision, notificationAccess) {
        try {
            status = WeatherReminderNotification.readStatus(context.applicationContext)
            statusError = null
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            status = null
            statusError = "暂时无法读取提醒状态，请点击立即检查重试"
        }
    }
    fun checkAccess() {
        val next = access()
        if (ReminderPermissionPolicy.refreshOnRestore(notificationAccess == ReminderNotificationAccess.ALLOWED,
                next == ReminderNotificationAccess.ALLOWED, AutomationSettingsRepository.read(context).remindersEnabled)) {
            WidgetRefresh.request(context.applicationContext)
            WeatherReminderSchedule.request(context.applicationContext)
            WeatherReminderSchedule.schedule(context.applicationContext)
        }
        notificationAccess = next
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { checkAccess() }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { checkAccess() }
    AutomationSettingsContent(settings, notificationAccess, actionError ?: statusError,
        onChange = { next ->
            val previous = AutomationSettingsRepository.read(context)
            AutomationSettingsRepository.save(context, next)
            if (Build.VERSION.SDK_INT >= 33 && ReminderPermissionPolicy.requestOnChange(previous, next,
                    NotificationManagerCompat.from(context).areNotificationsEnabled())) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }, onNotificationAction = {
            actionError = null
            // A denied or manually disabled permission may no longer show a request sheet.
            // The explicit recovery action always opens the actual system controls.
            val intent = reminderNotificationSettingsIntent(access(), context.packageName)
            if (runCatching { context.startActivity(intent) }.isFailure && runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")))
                }.isFailure) actionError = "暂时无法打开系统设置，请在系统设置中允许天气通知"
        }, showHeader = showHeader, reminderStatus = status, checking = checking, testing = testing,
        testFeedback = testFeedback, onCheckNow = {
            scope.launch {
                checking = true
                actionError = null
                try {
                    WeatherReminderNotification.checkNow(context.applicationContext, forceRefresh = true)
                    WeatherReminderSchedule.schedule(context.applicationContext)
                    status = WeatherReminderNotification.readStatus(context.applicationContext)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { actionError = "这次检查未完成，请重试" }
                finally { checking = false }
            }
        }, onTestNotification = {
            scope.launch {
                testing = true
                testFeedback = null
                try { testFeedback = withContext(Dispatchers.IO) { WeatherReminderNotification.testNotification(context.applicationContext) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { testFeedback = "测试通知未发出，请重试或检查系统通知设置" }
                finally { testing = false; checkAccess() }
            }
        }, onBackgroundAction = {
            if (runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"))) }.isFailure) actionError = "请在系统应用设置中允许枳生天气后台运行"
        })
}

/** Production content accepts state so enabled and blocked layouts can be reviewed offline. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AutomationSettingsContent(settings: AutomationSettings, notificationAccess: ReminderNotificationAccess,
    actionError: String? = null, onChange: (AutomationSettings) -> Unit, onNotificationAction: () -> Unit,
    showHeader: Boolean = true, reminderStatus: ReminderPanelStatus? = null,
    checking: Boolean = false, testing: Boolean = false, testFeedback: String? = null,
    onCheckNow: () -> Unit = {}, onTestNotification: () -> Unit = {}, onBackgroundAction: () -> Unit = {}) {
    val palette = LocalZhishengPalette.current
    val body = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp)
    val caption = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp)
    var details by rememberSaveable { mutableStateOf(false) }
    var editingTime by rememberSaveable { mutableStateOf<String?>(null) }
    val actionInk = if (isPhosphorVista) palette.textSecondary else palette.mint
    CompositionLocalProvider(LocalContentColor provides palette.text, LocalTextStyle provides body) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (showHeader) Text("刷新与天气提醒", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 25.sp))
                Text("自动更新当前地点，提醒按需开启", style = caption, color = palette.textSecondary)
            }
            Spacer(Modifier.height(16.dp))
            if (isPhosphorVista) {
                SettingsChoiceRow("天气刷新间隔", listOf(5, 10, 15, 30).map { "${it}分钟" to it.toString() },
                    settings.foregroundMinutes.toString(), hint = "使用应用时，每隔一段时间自动更新") {
                    onChange(settings.copy(foregroundMinutes = it.toInt()))
                }
            } else {
                Text("天气刷新", Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Medium)
                Text("使用应用时，每隔一段时间自动更新", Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = caption, color = palette.textSecondary)
                IntervalChoices(settings.foregroundMinutes, listOf(5, 10, 15, 30)) {
                    onChange(settings.copy(foregroundMinutes = it))
                }
            }
            AutomationToggle("后台刷新", "离开应用后也更新已保存的当前地点", settings.backgroundEnabled) {
                onChange(settings.copy(backgroundEnabled = it))
            }
            if (settings.needsBackground) {
                val label = if (settings.backgroundEnabled) "后台刷新间隔" else "提醒检查间隔"
                if (isPhosphorVista) {
                    SettingsChoiceRow(label, listOf(15, 30, 60, 120).map { "${it}分钟" to it.toString() },
                        settings.backgroundMinutes.toString(), hint = "实际执行时间受系统省电影响") {
                        onChange(settings.copy(backgroundMinutes = it.toInt()))
                    }
                } else {
                    Text(label, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = caption, color = palette.textSecondary)
                    IntervalChoices(settings.backgroundMinutes, listOf(15, 30, 60, 120)) {
                        onChange(settings.copy(backgroundMinutes = it))
                    }
                }
                Text(if (settings.remindersEnabled && settings.checkMinutes < settings.backgroundMinutes)
                    "提醒开启后，最长每${settings.checkMinutes}分钟检查；系统省电仍可能延后"
                    else "实际执行时间受系统省电影响", Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = caption, color = palette.textSecondary)
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = palette.cardBorder.copy(alpha = .35f))
            Text("天气提醒", Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Medium)
            Text(if (settings.remindersEnabled && reminderStatus?.cityName != null)
                "提醒地点：${reminderStatus.cityName} · 当地时间" else "时间以当前地点的当地时间为准",
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = caption, color = palette.textSecondary)
            AutomationToggle("早间今日天气", if (settings.morningEnabled)
                reminderHint(reminderStatus, "morning", "${minuteLabel(settings.morningMinute)} 后检查今天的预报") else "早上收到今天的天气简报",
                settings.morningEnabled) { onChange(settings.copy(morningEnabled = it)) }
            if (settings.morningEnabled) ReminderTimeAction("早间提醒时间", settings.morningMinute) { editingTime = "morning" }
            AutomationToggle("晚间明日天气", if (settings.eveningEnabled)
                reminderHint(reminderStatus, "evening", "${minuteLabel(settings.eveningMinute)} 后检查明天的预报") else "提前了解明天的天气",
                settings.eveningEnabled) { onChange(settings.copy(eveningEnabled = it)) }
            if (settings.eveningEnabled) ReminderTimeAction("晚间提醒时间", settings.eveningMinute) { editingTime = "evening" }
            AutomationToggle("短时降水提醒", if (settings.rainEnabled)
                reminderHint(reminderStatus, "rain", "未来30分钟有降水时提醒，需来源提供短时预报")
                else "未来30分钟有降水时提醒，需来源提供短时预报", settings.rainEnabled) {
                onChange(settings.copy(rainEnabled = it))
            }
            if (settings.remindersEnabled) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(reminderStatus?.lastCheckedAt?.let {
                        "上次检查 ${Instant.ofEpochMilli(it).atZone(cityZone(reminderStatus.utcOffsetSeconds)).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))}"
                    } ?: "开启后会检查预报，早晚提醒按设定时间发送", style = caption, color = palette.textSecondary)
                    if (reminderStatus?.quietNotifications == true && notificationAccess == ReminderNotificationAccess.ALLOWED)
                        Text("通知已允许；横幅和声音跟随系统设置，可先测试一条", style = caption, color = palette.textSecondary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = onCheckNow, enabled = !checking && !testing) {
                            Text(if (checking) "正在检查…" else "立即检查", color = if (checking) palette.textTertiary else actionInk)
                        }
                        TextButton(onClick = onTestNotification, enabled = !testing) {
                            Text(if (testing) "正在测试…" else "测试通知", color = if (testing) palette.textTertiary else actionInk)
                        }
                        if (notificationAccess == ReminderNotificationAccess.ALLOWED) TextButton(onClick = onNotificationAction) {
                            Text("通知显示设置", color = actionInk)
                        }
                    }
                    testFeedback?.let { Text(it, style = caption, color = palette.text) }
                    if (reminderStatus?.backgroundRestricted == true) {
                        Text("系统限制了后台运行，离开应用后可能无法检查提醒", style = caption, color = palette.orange)
                        TextButton(onClick = onBackgroundAction) { Text("允许后台运行", color = actionInk) }
                    }
                }
            }
            if (settings.remindersEnabled && notificationAccess != ReminderNotificationAccess.ALLOWED) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    .zhishengCompactPanel().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("提醒已保存，通知尚未开启", color = palette.orange, fontWeight = FontWeight.Medium)
                    Text(if (notificationAccess == ReminderNotificationAccess.CHANNEL_BLOCKED)
                        "系统关闭了天气提醒通知，请允许这个通知类别" else "允许天气通知后，才能收到已开启的提醒",
                        style = caption, color = palette.textSecondary)
                    TextButton(onClick = onNotificationAction) { Text(if (notificationAccess == ReminderNotificationAccess.APP_BLOCKED)
                        "开启通知" else "打开提醒通知设置", color = actionInk) }
                }
            }
            actionError?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = caption, color = palette.red) }
            TextButton(onClick = { details = !details }, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(if (details) "收起刷新与提醒说明" else "了解刷新与提醒", style = caption, color = actionInk)
            }
            if (details) Text("后台只使用已保存的地点，不申请后台定位。早晚提醒会按地点时区安排到时检查，90分钟内使用有效预报，每种提醒每天最多一次；缺数据时会重试，错过时段不补发旧简报。降水开启后最长每15分钟刷新检查，需要有效短时预报，同一地点4小时内不重复提醒。通知测试不占用天气提醒次数。系统省电、后台限制或断网可能延后检查，横幅和声音由系统通知设置决定。",
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = caption, color = palette.textSecondary)
        }
    }
    editingTime?.let { kind ->
        val minute = if (kind == "morning") settings.morningMinute else settings.eveningMinute
        WeatherOverlayDialog(onDismiss = { editingTime = null }, maxWidth = 420.dp) { modifier ->
            ReminderTimePanel(if (kind == "morning") "早间提醒时间" else "晚间提醒时间", minute,
                onDismiss = { editingTime = null }, onSelect = {
                    onChange(if (kind == "morning") settings.copy(morningMinute = it) else settings.copy(eveningMinute = it))
                    editingTime = null
                }, modifier = modifier)
        }
    }
}

private fun reminderHint(status: ReminderPanelStatus?, kind: String, fallback: String): String {
    val check = status?.result?.checks?.firstOrNull { it.kind == kind } ?: return fallback
    val next = check.nextAt ?: return check.detail
    val day = cityDate(System.currentTimeMillis(), status.utcOffsetSeconds)
    val date = cityDate(next, status.utcOffsetSeconds)
    val dayLabel = when (date) { day -> "今天"; day.plusDays(1) -> "明天"; else -> "${date.monthValue}月${date.dayOfMonth}日" }
    val time = Instant.ofEpochMilli(next).atZone(cityZone(status.utcOffsetSeconds)).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
    return check.detail + "\n" + if (kind == "rain") "重复提醒最早 $dayLabel $time" else "下次 $dayLabel $time"
}

@Composable
private fun AutomationToggle(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    if (isPhosphorVista) {
        VistaSettingToggle(title, hint, checked, onInfo = null) { onChange(!checked) }
        return
    }
    val palette = LocalZhishengPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 22.sp), color = palette.text)
            Text(hint, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp), color = palette.textSecondary)
        }
        Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(checkedThumbColor = palette.bg,
            checkedTrackColor = palette.mint, uncheckedThumbColor = palette.textTertiary,
            uncheckedTrackColor = palette.cardBorder, uncheckedBorderColor = palette.cardBorder))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntervalChoices(selected: Int, options: List<Int>, onSelect: (Int) -> Unit) {
    val palette = LocalZhishengPalette.current
    val shape = LocalZhishengChrome.current.compactShape
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val columns = if ((maxWidth - 40.dp) / 2 < (100 * fontScale).dp) 1 else 2
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = columns) {
        options.forEach { minute ->
            val chosen = minute == selected
            Row(Modifier.weight(1f).zhishengCompactPanel(selected = chosen)
                .background(if (chosen) palette.mint.copy(alpha = if (palette.isLight) .12f else .18f) else Color.Transparent, shape)
                .border(1.5.dp, if (chosen) palette.mint else Color.Transparent, shape)
                .selectable(chosen, role = Role.RadioButton) { onSelect(minute) }
                .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(20.dp)
                    .background(if (chosen) palette.mint else Color.Transparent, CircleShape)
                    .border(1.dp, if (chosen) palette.mint else palette.textTertiary, CircleShape), contentAlignment = Alignment.Center) {
                    if (chosen) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp), tint = palette.bg)
                }
                Text("${minute}分钟", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
                    color = palette.text, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            }
        }
    }
    }
}

@Composable
private fun ReminderTimeAction(title: String, minute: Int, onClick: () -> Unit) {
    if (isPhosphorVista) {
        VistaSettingInfo(title, minuteLabel(minute), onClick, attention = false)
        return
    }
    TextButton(onClick = onClick, modifier = Modifier.padding(start = 8.dp).heightIn(min = 48.dp)) {
        Text("$title · ${minuteLabel(minute)}", color = LocalZhishengPalette.current.mint,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp))
    }
}

internal fun minuteLabel(minute: Int) = "%02d:%02d".format(java.util.Locale.ROOT, minute / 60, minute % 60)

@Composable
internal fun ReminderTimePanel(title: String, minute: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier) {
    // A different reminder gets a different list state, including its exact minute.
    key(minute) {
        val initial = minute.coerceIn(0, 1439)
        val palette = LocalZhishengPalette.current
        val density = LocalDensity.current
        val hourState = rememberLazyListState(initialFirstVisibleItemIndex = initial / 60)
        val minuteState = rememberLazyListState(initialFirstVisibleItemIndex = initial % 60)
        val hour by remember(hourState) { derivedStateOf { reminderWheelValue(hourState, initial / 60) } }
        val part by remember(minuteState) { derivedStateOf { reminderWheelValue(minuteState, initial % 60) } }
        var hourTarget by remember { mutableStateOf<Int?>(null) }
        var minuteTarget by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(hourTarget) {
            val target = hourTarget ?: return@LaunchedEffect
            try { hourState.animateScrollToItem(target) }
            finally { if (hourTarget == target) hourTarget = null }
        }
        LaunchedEffect(minuteTarget) {
            val target = minuteTarget ?: return@LaunchedEffect
            try { minuteState.animateScrollToItem(target) }
            finally { if (minuteTarget == target) minuteTarget = null }
        }
        val settled = hourTarget == null && minuteTarget == null &&
            !hourState.isScrollInProgress && !minuteState.isScrollInProgress &&
            reminderWheelAligned(hourState) && reminderWheelAligned(minuteState)
        BoxWithConstraints(modifier.zhishengDialogPanel(glass = true)) {
            val panelHeight = minOf(maxHeight, 420.dp)
            val compact = panelHeight < 260.dp
            val numberSize = if (compact) 16.sp else 20.sp
            val numberLine = if (compact) 20.sp else 26.sp
            // Reserve the full scaled line height even when a platform's nonlinear
            // sp conversion compresses large text. Digits and Chinese units share a row.
            val rowHeight = maxOf(48.dp, (numberLine.value * density.fontScale).dp + 8.dp)
            Surface(Modifier.fillMaxWidth().height(panelHeight), color = Color.Transparent, contentColor = palette.text) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 6.dp else 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = if (compact) 14.sp else 18.sp, lineHeight = if (compact) 18.sp else 25.sp))
                        if (!compact) Text("当地时间 · 24小时制 · 上下滑动或点选",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                            color = palette.textSecondary)
                    }
                    Row(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ReminderTimeWheel("小时", "时", 24, hourState, hour, rowHeight, numberSize, numberLine,
                            onRequest = { hourTarget = it }, modifier = Modifier.weight(1f).fillMaxHeight())
                        ReminderTimeWheel("分钟", "分", 60, minuteState, part, rowHeight, numberSize, numberLine,
                            onRequest = { minuteTarget = it }, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                    HorizontalDivider(Modifier.padding(top = if (compact) 0.dp else 10.dp),
                        color = palette.cardBorder.copy(alpha = .35f))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = if (compact) 0.dp else 2.dp),
                        horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("取消", color = palette.textSecondary,
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp))
                        }
                        TextButton(onClick = {
                            onSelect(reminderWheelValue(hourState, hour) * 60 + reminderWheelValue(minuteState, part))
                        }, enabled = settled, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("保存时间", color = if (settled) palette.mint else palette.textTertiary,
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp))
                        }
                    }
                }
            }
        }
    }
}

private fun reminderWheelValue(state: LazyListState, fallback: Int): Int {
    val layout = state.layoutInfo
    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
    return layout.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - center) }?.index ?: fallback
}

private fun reminderWheelAligned(state: LazyListState): Boolean {
    val layout = state.layoutInfo
    val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
    val item = layout.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2f - center) } ?: return false
    return kotlin.math.abs(item.offset + item.size / 2f - center) <= 1f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReminderTimeWheel(label: String, unit: String, count: Int, state: LazyListState, selected: Int,
    rowHeight: Dp, fontSize: TextUnit, lineHeight: TextUnit, onRequest: (Int) -> Unit,
    modifier: Modifier = Modifier) {
    val palette = LocalZhishengPalette.current
    val shape = LocalZhishengChrome.current.compactShape
    val latestSelected by rememberUpdatedState(selected)
    // Fling snapping handles touch input. Also center non-fling accessibility scrolls
    // after they settle, without competing with an active drag or an item-click animation.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collectLatest { moving ->
            if (!moving) {
                delay(80)
                if (!state.isScrollInProgress && !reminderWheelAligned(state))
                    state.scrollToItem(reminderWheelValue(state, latestSelected))
            }
        }
    }
    BoxWithConstraints(modifier.clip(shape)) {
        val padding = ((maxHeight - rowHeight) / 2).coerceAtLeast(0.dp)
        val vista = isPhosphorVista
        val glass = LocalHomeSurfaceStyle.current == HomeSurfaceStyle.FRAGRANCE_GLASS
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(rowHeight)
            .background(if (vista && glass) Color.White.copy(alpha = if (palette.isLight) .46f else .12f)
                else palette.mint.copy(alpha = if (palette.isLight) .12f else .18f), shape)
            .then(if (vista) Modifier else Modifier.border(1.dp, palette.mint.copy(alpha = .75f), shape)))
        LazyColumn(Modifier.fillMaxSize().selectableGroup().semantics {
            contentDescription = label
            stateDescription = "%02d%s".format(Locale.ROOT, selected, label)
            customActions = listOf(
                CustomAccessibilityAction("前一$label") { if (selected > 0) { onRequest(selected - 1); true } else false },
                CustomAccessibilityAction("后一$label") { if (selected < count - 1) { onRequest(selected + 1); true } else false },
            )
        }, state = state,
            contentPadding = PaddingValues(vertical = padding),
            flingBehavior = rememberSnapFlingBehavior(state, snapPosition = SnapPosition.Center)) {
            items(count, key = { it }) { value ->
                Box(Modifier.fillMaxWidth().height(rowHeight)
                    .selectable(value == selected, role = Role.RadioButton) { onRequest(value) }
                    .semantics { contentDescription = "%02d%s".format(Locale.ROOT, value, label) },
                    contentAlignment = Alignment.Center) {
                    Text("%02d %s".format(Locale.ROOT, value, unit), Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = fontSize, lineHeight = lineHeight),
                        maxLines = 1, softWrap = false,
                        color = if (value == selected && vista) palette.mint else if (value == selected) palette.text else palette.textSecondary,
                        fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}
