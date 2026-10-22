package com.zhisheng.weather.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.BuildConfig
import com.zhisheng.weather.data.*
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.zhishengDialogPanel
import com.zhisheng.weather.widget.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import java.time.Instant

internal data class WeatherDiagnosticsReport(
    val generalLines: List<String>,
    val device: String,
    val location: String,
    val source: SourcePref,
    val cached: CachedWeather?,
    val cacheCompatible: Boolean,
) {
    fun text(includeLocationAndDevice: Boolean = false): String = buildString {
        generalLines.forEach { appendLine(it) }
        if (includeLocationAndDevice) {
            appendLine("设备 $device")
            appendLine("地点 $location")
        } else appendLine("地点与设备型号：已隐藏")
        appendLine("主源 ${source.cn}")
        val usable = cached?.takeIf { cacheCompatible && it.data.error == null }
        if (usable == null) {
            appendLine("当前配置暂无可用天气数据，请返回天气页刷新后重新读取。")
            if (cached != null) {
                val reason = if (!cacheCompatible) "与当前来源或配置不匹配" else "不可用于当前诊断"
                appendLine("历史缓存（$reason）取得 ${Instant.ofEpochMilli(cached.data.fetchedAt ?: cached.savedAtMillis)}")
            }
        } else {
            val data = usable.data
            appendLine("当前配置兼容缓存 · 实际返回 ${diagnosticSourceName(data.dataSource)}")
            appendLine("缓存取得 ${Instant.ofEpochMilli(data.fetchedAt ?: usable.savedAtMillis)} · 数据发布 ${data.updateTime?.let(Instant::ofEpochMilli) ?: "未知"}")
            appendLine("模块来源 ${data.blockSources.entries.filter { it.key in diagnosticBlockNames }.joinToString { "${it.key}=${diagnosticSourceName(it.value)}" }.ifBlank { "未知" }}")
            appendLine("来源说明 ${diagnosticSourceNotes(data.sourceDiagnostics)}")
            appendLine("字段覆盖：实况 ${data.current != null} · 小时 ${data.hourly.size} · 每日 ${data.daily.size} · 短时降水 ${data.rainMinutes.size}")
        }
        appendLine("诊断不包含密钥、凭据、坐标或完整配置。")
    }
}

private val diagnosticBlockNames = WeatherModule.entries.flatMap { it.blocks }.toSet() +
    setOf("yesterday", "typhoon", "nowcast")
private val diagnosticSourceNames = SourcePref.entries.map { it.en }.toSet() +
    setOf("TENCENT", "WEATHER-COM-CN", "WEATHER.COM.CN", "WCN")

private fun diagnosticSourceName(raw: String?): String = raw.orEmpty().split(" + ")
    .filter { it in diagnosticSourceNames }.joinToString(" + ").ifBlank { "未知" }

/** Report controlled summaries, never provider error strings which may echo request details. */
private fun diagnosticSourceNotes(notes: Map<String, String>): String {
    if (notes.isEmpty()) return "无"
    return notes.entries.joinToString("；") { (key, raw) ->
        val module = WeatherModule.entries.firstOrNull { it.key == key }?.cn
        val label = module ?: if (key == "capabilities") "来源能力" else "其它来源说明"
        val note = when {
            key == "capabilities" -> "部分字段受该来源能力或授权限制，未返回的字段保持缺失"
            raw.contains("未配置") || raw.contains("开发者模式已关闭") -> "指定来源不可用，保留主源模块"
            raw.contains("不提供") -> "指定来源不提供该模块，保留主源模块"
            raw.contains("请求失败") || raw.contains("服务未返回该字段") -> "指定来源未取得该字段，保留主源模块"
            else -> "存在来源说明，服务原始信息已省略"
        }
        "$label：$note"
    }
}

internal sealed interface WeatherDiagnosticsState {
    data object Loading : WeatherDiagnosticsState
    data object Failed : WeatherDiagnosticsState
    data class Ready(val report: WeatherDiagnosticsReport) : WeatherDiagnosticsState
}

internal suspend fun readWeatherDiagnostics(load: suspend () -> WeatherDiagnosticsReport): WeatherDiagnosticsState =
    try { WeatherDiagnosticsState.Ready(load()) }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { WeatherDiagnosticsState.Failed }

private suspend fun loadWeatherDiagnostics(context: Context): WeatherDiagnosticsReport {
    val city = CityRepository.selectedCity.first()
    val cached = city?.let { WeatherCache.load(context, it) }
    val source = SettingsRepository.sourcePref.first()
    val compatible = cached?.data?.let { WeatherRepository.cacheCompatible(it, source) } ?: false
    val lines = mutableListOf(
        "枳生天气 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}",
        "显示 ${context.resources.configuration.screenWidthDp}×${context.resources.configuration.screenHeightDp}dp · 字体比例 ${context.resources.configuration.fontScale}",
        "通知权限 ${androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()}",
    )
    val auto = AutomationSettingsRepository.read(context)
    lines += "前台 ${auto.foregroundMinutes}分钟 · 独立后台 ${auto.backgroundEnabled} / ${auto.backgroundMinutes}分钟 · 早晚/雨提醒 ${auto.morningEnabled}/${auto.eveningEnabled}/${auto.rainEnabled}"
    if (auto.remindersEnabled) {
        val status = WeatherReminderNotification.readStatus(context)
        lines += "早间设定 ${minuteLabel(auto.morningMinute)} · 晚间设定 ${minuteLabel(auto.eveningMinute)} · 降水检查最长15分钟"
        lines += "提醒状态 " + status.result.checks.joinToString { check ->
            val kind = when (check.kind) { "morning" -> "早间"; "evening" -> "晚间"; else -> "降水" }
            "$kind=${check.state.name}"
        }
        lines += "最近提醒检查 ${status.lastCheckedAt?.let(Instant::ofEpochMilli) ?: "尚未记录"}"
    }
    val manager = android.appwidget.AppWidgetManager.getInstance(context)
    val instances = WidgetRuntime.instances(context)
    lines += "桌面组件 ${instances.size} 个"
    instances.forEach { instance ->
        val options = manager.getAppWidgetOptions(instance.id)
        val config = WidgetStore.config(context, instance.id, instance.style)
        val locationMode = when (config.locationMode) {
            "selected" -> "跟随主页地点"
            "fixed" -> "固定地点"
            "located" -> "定位地点"
            else -> "其它地点模式"
        }
        lines += "#${instance.id} ${instance.style.key}/${instance.kind} · 宿主范围 ${options.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)}–${options.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)} × ${options.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)}–${options.getInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)}dp · $locationMode · 透明度 ${config.opacity}% · 数字 ${config.textScale}"
    }
    return WeatherDiagnosticsReport(lines, "${Build.MANUFACTURER} ${Build.MODEL}",
        "${city?.displayName ?: "未选定"} · ${city?.contextLabel.orEmpty()}", source, cached, compatible)
}

suspend fun weatherDiagnostics(context: Context): String = loadWeatherDiagnostics(context).text()

@Composable
internal fun WeatherDiagnosticsCopyAction(state: WeatherDiagnosticsState, copied: Boolean = false,
    copyFailed: Boolean = false, onCopy: () -> Unit) {
    TextButton(onClick = { if (state is WeatherDiagnosticsState.Ready) onCopy() },
        enabled = state is WeatherDiagnosticsState.Ready, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(if (copied) "已复制" else if (copyFailed) "重试复制" else "复制诊断",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp))
    }
}

@Composable
internal fun WeatherDiagnosticsPanel(state: WeatherDiagnosticsState, includeLocationAndDevice: Boolean,
    copied: Boolean, copyFailed: Boolean, onPrivacyChange: (Boolean) -> Unit,
    onCopy: () -> Unit, onRetry: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalZhishengPalette.current
    Column(modifier.zhishengDialogPanel(glass = true)) {
        Text("天气与桌面诊断", Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = palette.text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                WeatherDiagnosticsState.Loading -> Text("正在读取天气与桌面信息…",
                    color = palette.textSecondary, style = MaterialTheme.typography.bodyMedium)
                WeatherDiagnosticsState.Failed -> Text("暂时无法读取诊断信息，请重试。",
                    color = palette.textSecondary, style = MaterialTheme.typography.bodyMedium)
                is WeatherDiagnosticsState.Ready -> {
                    Row(Modifier.fillMaxWidth().toggleable(includeLocationAndDevice, role = Role.Checkbox,
                        onValueChange = onPrivacyChange).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(includeLocationAndDevice, onCheckedChange = null)
                        Text("包含地点与设备型号", Modifier.weight(1f), color = palette.text,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp))
                    }
                    Text("默认隐藏地点与设备型号，勾选后才会显示并复制。", color = palette.textSecondary,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp))
                    SelectionContainer {
                        Text(state.report.text(includeLocationAndDevice), color = palette.text,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp))
                    }
                }
            }
            if (state != WeatherDiagnosticsState.Loading) {
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("重新读取", color = palette.mint,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("关闭", color = palette.textSecondary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp))
            }
            Spacer(Modifier.weight(1f))
            WeatherDiagnosticsCopyAction(state, copied, copyFailed, onCopy)
        }
    }
}

@Composable
fun WeatherDiagnosticsButton() {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    var includeLocationAndDevice by rememberSaveable { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var copied by remember { mutableStateOf(false) }
    var copyFailed by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<WeatherDiagnosticsState>(WeatherDiagnosticsState.Loading) }
    TextButton(onClick = {
        state = WeatherDiagnosticsState.Loading
        copied = false
        copyFailed = false
        open = true
    }) { Text("查看天气与桌面诊断") }
    if (open) {
        LaunchedEffect(attempt) {
            state = WeatherDiagnosticsState.Loading
            copied = false
            copyFailed = false
            state = readWeatherDiagnostics { loadWeatherDiagnostics(context) }
        }
        WeatherOverlayDialog(onDismiss = { open = false }, maxWidth = 560.dp) { modifier ->
            WeatherDiagnosticsPanel(state, includeLocationAndDevice, copied, copyFailed,
                onPrivacyChange = { includeLocationAndDevice = it; copied = false; copyFailed = false },
                onCopy = copy@{
                    val report = (state as? WeatherDiagnosticsState.Ready)?.report ?: return@copy
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    val result = runCatching {
                        val manager = checkNotNull(clipboard)
                        val text = report.text(includeLocationAndDevice)
                        manager.setPrimaryClip(android.content.ClipData.newPlainText("天气诊断", text))
                        // Some ROMs silently reject writes instead of throwing.
                        check(manager.primaryClip?.getItemAt(0)?.text?.toString() == text)
                    }
                    copied = result.isSuccess
                    copyFailed = result.isFailure
                    if (copyFailed) android.widget.Toast.makeText(context,
                        "未能写入剪贴板，请允许剪贴板访问，或长按诊断文字复制", android.widget.Toast.LENGTH_LONG).show()
                }, onRetry = { attempt++; state = WeatherDiagnosticsState.Loading; copied = false; copyFailed = false },
                onDismiss = { open = false }, modifier = modifier)
        }
    }
}
