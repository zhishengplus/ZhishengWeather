package com.zhisheng.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhisheng.weather.data.*
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.components.WeatherDialogWindow
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.zhishengDialogPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class WeatherSourceNavigation(val modules: Boolean = false, val editing: WeatherModule? = null) {
    fun edit(module: WeatherModule) = WeatherSourceNavigation(true, module)
    fun back(): WeatherSourceNavigation? = if (editing != null) copy(editing = null) else null
}

internal data class WeatherSourceChoice(val source: SourcePref?, val enabled: Boolean)

internal fun weatherSourceChoices(module: WeatherModule?, visible: Set<SourcePref>, available: Set<SourcePref>,
    selected: SourcePref?): List<WeatherSourceChoice> = buildList {
    if (module != null) add(WeatherSourceChoice(null, true))
    (visible.toList() + listOfNotNull(selected)).distinct().filter { source -> source == selected ||
        (source in visible && (module == null || WeatherSourceCapabilities.supports(source, module))) }.forEach { source ->
        add(WeatherSourceChoice(source, source in available &&
            (module == null || WeatherSourceCapabilities.supports(source, module))))
    }
}

@Composable
internal fun WeatherSourceManager(primary: SourcePref, weather: WeatherData?, onDismiss: () -> Unit,
    onPrimarySelected: suspend (SourcePref) -> Unit, onCompare: () -> Unit, onManageProviders: () -> Unit,
    comparisonAvailable: Boolean = true, initialModules: Boolean = false) {
    val policy by SettingsRepository.moduleSources.collectAsState(initial = ModuleSourcePolicy())
    val developer by SettingsRepository.developerMode.collectAsState(initial = false)
    val qw by SecretStore.qwRuntimeFlow.collectAsState(initial = QwRuntimeCreds())
    val cy by SecretStore.caiyunRuntimeFlow.collectAsState(initial = CaiyunRuntimeCreds())
    val amap by SecretStore.amapRuntimeFlow.collectAsState(initial = AmapRuntimeCreds())
    val baidu by SecretStore.baiduRuntimeFlow.collectAsState(initial = BaiduRuntimeCreds())
    val seniverse by SecretStore.seniverseRuntimeFlow.collectAsState(initial = SeniverseRuntimeCreds())
    val moji by SecretStore.mojiRuntimeFlow.collectAsState(initial = MojiRuntimeCreds())
    var available by remember { mutableStateOf(SourcePref.visible(false).toSet()) }
    LaunchedEffect(developer, qw, cy, amap, baidu, seniverse, moji) { available = WeatherRepository.configuredSources() }
    var modules by rememberSaveable { mutableStateOf(initialModules) }
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val navigation = WeatherSourceNavigation(modules, WeatherModule.entries.firstOrNull { it.key == editingKey })
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun back() {
        if (saving) return
        if (navigation.back() != null) editingKey = null else onDismiss()
    }
    // The window helper owns full-screen sizing. Compose 1.7's false-width mode instead
    // caps both dimensions to Configuration, whose height excludes system bars.
    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = true,
        decorFitsSystemWindows = false)) {
        WeatherDialogWindow(overlay = true)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .38f)).clickable { back() })
            val panelHeight = ((maxHeight - 48.dp) * .92f).coerceAtMost(700.dp)
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center) {
                WeatherSourceManagerPanel(primary, policy, weather, SourcePref.visible(developer).toSet(), available,
                    navigation, saving, error,
                    onNavigate = { next -> if (!saving) { modules = next.modules; editingKey = next.editing?.key; error = null } },
                    onSelect = { selected ->
                        if (!saving) scope.launch {
                            saving = true; error = null
                            try {
                                navigation.editing?.let { module -> SettingsRepository.setModuleSource(module, selected); editingKey = null }
                                    ?: onPrimarySelected(requireNotNull(selected))
                            } catch (cancel: CancellationException) { throw cancel }
                            catch (_: Exception) { error = "没有保存成功，请重试" }
                            finally { saving = false }
                        }
                    }, onBack = ::back, onDone = { if (!saving) onDismiss() },
                    onCompare = { if (!saving) {
                        if (comparisonAvailable) onCompare() else error = "请先返回主页选择一个城市，再比较天气来源"
                    } }, onManageProviders = { if (!saving) onManageProviders() },
                    supplementSettings = { SourcePrioritySettings(primary, available, onSaving = { saving = it }) },
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                        .heightIn(max = panelHeight))
            }
        }
    }
}

/** The dialog keeps one window and one header while moving between the list and a module's choices. */
@Composable
internal fun WeatherSourceManagerPanel(primary: SourcePref, policy: ModuleSourcePolicy, weather: WeatherData?,
    visible: Set<SourcePref>, available: Set<SourcePref>, navigation: WeatherSourceNavigation,
    saving: Boolean = false, error: String? = null, onNavigate: (WeatherSourceNavigation) -> Unit,
    onSelect: (SourcePref?) -> Unit, onBack: () -> Unit, onDone: () -> Unit, onCompare: () -> Unit,
    onManageProviders: () -> Unit, modifier: Modifier = Modifier,
    supplementSettings: @Composable () -> Unit = {}) {
    val palette = LocalZhishengPalette.current
    val compact = LocalConfiguration.current.screenHeightDp < 420
    val scroll = rememberScrollState()
    LaunchedEffect(navigation.modules, navigation.editing) { scroll.scrollTo(0) }
    val title = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 25.sp)
    val body = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp)
    val caption = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp)
    Surface(modifier.zhishengDialogPanel(glass = true), color = Color.Transparent, contentColor = palette.text) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = if (compact) 4.dp else 8.dp, bottom = if (compact) 0.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(navigation.editing?.let { "${it.cn}来源" } ?: "天气来源", style = title, fontWeight = FontWeight.Medium)
                    Text(navigation.editing?.let { "当前选择：${policy[it]?.cn ?: "跟随主源"}" } ?: "当前主源：${primary.cn}",
                        style = caption, color = palette.textSecondary)
                }
                IconButton(onClick = onDone, enabled = !saving) { Icon(Icons.Default.Close, "关闭天气来源", tint = palette.textSecondary) }
            }
            if (navigation.editing != null) {
                TextButton(onClick = onBack, enabled = !saving, modifier = Modifier.padding(start = 8.dp)) {
                    Text("‹ 返回模块设置", color = palette.mint, style = body)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = if (compact) 4.dp else 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "主天气源", true to "按模块设置").forEach { (isModules, name) ->
                        val selected = navigation.modules == isModules
                        Surface(Modifier.weight(1f).selectable(selected, enabled = !saving, role = Role.Tab) {
                            onNavigate(WeatherSourceNavigation(isModules))
                        }, shape = RoundedCornerShape(12.dp), color = if (selected) palette.mint.copy(alpha = .14f) else palette.surface.copy(alpha = .36f)) {
                            Text(name, Modifier.padding(horizontal = 10.dp, vertical = if (compact) 8.dp else 12.dp), color = if (selected) palette.mint else palette.textSecondary,
                                style = body, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                        }
                    }
                }
            }
            HorizontalDivider(color = palette.cardBorder.copy(alpha = .35f))
            Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)) {
                if (navigation.modules && navigation.editing == null) {
                    Text(if (policy.followsPrimary) "默认跟随主源，点某个模块即可单独调整" else "单独设置的模块会保留自己的来源",
                        Modifier.padding(horizontal = 18.dp, vertical = 14.dp), style = caption, color = palette.textSecondary)
                    WeatherModuleSettingsContent(policy, available, weather, onCompare,
                        onChoose = { onNavigate(navigation.edit(it)) }, showHeader = false)
                    supplementSettings()
                } else {
                    val module = navigation.editing
                    val selected = if (module == null) primary else policy[module]
                    Column(Modifier.padding(horizontal = 18.dp, vertical = if (compact) 8.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (module != null) "跟随主源：${primary.cn}" else if (compact && !policy.followsPrimary) "${policy.selections.size} 个模块已单独设置"
                            else "未单独设置的模块会跟随主源", style = caption, color = palette.textSecondary)
                        if (module == null && !compact && !policy.followsPrimary) Text("${policy.selections.size} 个模块已单独设置，切换主源不会改变它们", style = caption, color = palette.textSecondary)
                    }
                    weatherSourceChoices(module, visible, available, selected).forEach { choice ->
                        val source = choice.source
                        val chosen = source == selected
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp)
                            .selectable(chosen, enabled = choice.enabled && !saving, role = Role.RadioButton) { onSelect(source) }
                            .background(if (chosen) palette.mint.copy(alpha = .10f) else Color.Transparent, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(source?.cn ?: "跟随主源", style = body, color = if (choice.enabled) palette.text else palette.textSecondary,
                                    fontWeight = if (chosen) FontWeight.Medium else FontWeight.Normal)
                                val hint = if (!choice.enabled) {
                                    if (module != null && source != null && !WeatherSourceCapabilities.supports(source, module)) "这个来源不提供该模块"
                                    else "当前不可用，可在接入设置中检查"
                                } else if (source == null) "使用当前主源 ${primary.cn}"
                                else sourceChoiceHint(source, module)
                                Text(hint, style = caption, color = palette.textSecondary)
                            }
                            RadioButton(chosen, onClick = null, enabled = choice.enabled && !saving,
                                colors = RadioButtonDefaults.colors(selectedColor = palette.mint, unselectedColor = palette.textSecondary))
                        }
                    }
                    TextButton(onClick = onManageProviders, enabled = !saving, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text("管理自有服务接入", style = body, color = palette.mint)
                    }
                }
            }
            if (error != null) Text(error, Modifier.padding(horizontal = 18.dp, vertical = 6.dp), color = palette.red, style = caption)
            HorizontalDivider(color = palette.cardBorder.copy(alpha = .35f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = if (compact) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (saving) "正在保存…" else "选择会自动保存", Modifier.weight(1f), color = palette.textSecondary, style = caption)
                Button(onClick = if (navigation.editing == null) onDone else onBack, enabled = !saving,
                    shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = palette.mint, contentColor = palette.bg)) {
                    Text(if (navigation.editing == null) "完成" else "返回模块", style = body)
                }
            }
        }
    }
}

private fun sourceChoiceHint(source: SourcePref, module: WeatherModule?): String {
    if (module == WeatherModule.FORECAST && !WeatherSourceCapabilities.map.getValue(source).hourly) return "仅提供每日预报"
    return when (source) {
        SourcePref.AUTO -> "自动选择可用来源，失败时换用备用数据"
        SourcePref.ZHISHENG -> "综合多个来源，补齐天气信息"
        SourcePref.XIAOMI -> "国内天气 · 实况、预报与短时降水"
        SourcePref.NMC -> "官方实况、预报与预警"
        SourcePref.OPEN_METEO -> "全球覆盖 · 模型预报"
        SourcePref.QWEATHER -> "使用你已接入的和风服务"
        SourcePref.CAIYUN -> "使用你已接入的彩云服务"
        SourcePref.AMAP -> "区县实况和每日预报"
        SourcePref.BAIDU -> "数据范围取决于服务授权"
        SourcePref.TENCENT -> "城市日预报 · 网站通道"
        SourcePref.WEATHERCN -> "城市实况与当日预报 · 网站通道"
        SourcePref.SENIVERSE -> "个人免费版城市天气 · 3天日预报"
        SourcePref.MOJI -> "个人订阅 · 实况与日预报"
    }
}
