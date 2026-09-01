/* Hallmark · pre-emit critique: P5 H5 E5 S5 R5 V5 */
/* Hallmark · macrostructure: single-page grouped control ledger · genre: atmospheric · theme: existing Zhisheng terminal
 * states: default · focus · active · disabled · loading · error · success
 * contrast: pass
 */
package com.zhisheng.weather.ui

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.data.AccentTone
import com.zhisheng.weather.data.AppLanguage
import com.zhisheng.weather.data.AppIconManager
import com.zhisheng.weather.data.AppIconStyle
import com.zhisheng.weather.data.AppUpdateInfo
import com.zhisheng.weather.data.WidgetBackgroundMode
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.data.CaiyunApi
import com.zhisheng.weather.data.HomeModule
import com.zhisheng.weather.data.HomeBriefingStyle
import com.zhisheng.weather.data.LocationSource
import com.zhisheng.weather.data.LifeIndexMetric
import com.zhisheng.weather.data.LandscapeStandbyStyle
import com.zhisheng.weather.data.QWeatherApi
import com.zhisheng.weather.data.SecretStore
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.widget.ZhishengWidgetProvider
import com.zhisheng.weather.data.SourcePref
import com.zhisheng.weather.data.TelemetryMetric
import com.zhisheng.weather.data.ThemeMode
import com.zhisheng.weather.i18n.AppLanguageState
import com.zhisheng.weather.i18n.uiText
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCard
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengRed
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════

// 设置（v0.1.0）
// 数据与位置 → 首页内容 → 外观与设备 → 关于枳生
// ═══════════════════════════════════════════════════════════

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    landscapePortraitLocked: Boolean,
    onRestoreLandscapeAuto: () -> Unit,
    onLocate: () -> Unit,
    locating: Boolean,
    locateMessage: String?,
    onClearLocateMessage: () -> Unit,
    activeSource: String?,
    activeSupplementSources: List<String>,
    activeCityName: String?,
    sourceLoading: Boolean,
    onAtmosphereLab: () -> Unit,
    onShowWhatsNew: () -> Unit,
    availableUpdate: AppUpdateInfo?,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val tempUnit by SettingsRepository.tempUnit.collectAsState(initial = "c")
    val windUnit by SettingsRepository.windUnit.collectAsState(initial = "kmh")
    val pressureUnit by SettingsRepository.pressureUnit.collectAsState(initial = "hpa")
    val showTyphoon by SettingsRepository.showTyphoon.collectAsState(initial = true)
    val source by SettingsRepository.sourcePref.collectAsState(initial = SourcePref.AUTO)
    val developerMode by SettingsRepository.developerMode.collectAsState(initial = false)
    val ambience by SettingsRepository.ambience.collectAsState(initial = AmbienceLevel.VIVID)
    val scanlines by SettingsRepository.scanlines.collectAsState(initial = true)
    val locationEnabled by SettingsRepository.locationEnabled.collectAsState(initial = false)
    val preciseLocationEnabled by SettingsRepository.preciseLocationEnabled.collectAsState(initial = false)
    val preciseLocationPermissionAsked by SettingsRepository.preciseLocationPermissionAsked.collectAsState(initial = false)
    val showAqi by SettingsRepository.showAqi.collectAsState(initial = true)
    val showIndices by SettingsRepository.showIndices.collectAsState(initial = true)
    val showYesterday by SettingsRepository.showYesterday.collectAsState(initial = true)
    val showPrecip by SettingsRepository.showPrecip.collectAsState(initial = true)
    val showTelemetry by SettingsRepository.showTelemetry.collectAsState(initial = true)
    val showSpacetime by SettingsRepository.showSpacetime.collectAsState(initial = true)
    val bootAnim by SettingsRepository.bootAnim.collectAsState(initial = true)
    val keepScreenOn by SettingsRepository.keepScreenOn.collectAsState(initial = false)
    val landscapeStandby by SettingsRepository.landscapeStandby.collectAsState(initial = true)
    val landscapeStandbyStyle by SettingsRepository.landscapeStandbyStyle.collectAsState(
        initial = LandscapeStandbyStyle.WEATHER_CORE,
    )
    val telemetryMetrics by SettingsRepository.telemetryMetrics.collectAsState(initial = TelemetryMetric.defaultSelection)
    val lifeIndexMetrics by SettingsRepository.lifeIndexMetrics.collectAsState(initial = LifeIndexMetric.defaultSelection)
    val themeMode by SettingsRepository.themeMode.collectAsState(initial = ThemeMode.DARK)
    val accentTone by SettingsRepository.accentTone.collectAsState(initial = AccentTone.STANDARD)
    val appIconStyle by SettingsRepository.appIconStyle.collectAsState(initial = AppIconStyle.CHARACTER)
    val homeBriefingStyle by SettingsRepository.homeBriefingStyle.collectAsState(
        initial = HomeBriefingStyle.WEATHER_GIRL,
    )
    val widgetBackgroundMode by SettingsRepository.widgetBackgroundMode.collectAsState(initial = WidgetBackgroundMode.GLASS)
    val appLanguage by SettingsRepository.appLanguage.collectAsState(initial = AppLanguage.CHINESE)
    val moduleOrder by SettingsRepository.moduleOrder.collectAsState(initial = HomeModule.defaultOrder)
    val qwRt by SecretStore.qwRuntimeFlow.collectAsState(initial = SecretStore.qwRuntime)
    val caiyunRt by SecretStore.caiyunRuntimeFlow.collectAsState(initial = SecretStore.caiyunRuntime)
    val amapRt by SecretStore.amapRuntimeFlow.collectAsState(initial = SecretStore.amapRuntime)

    var permDenied by remember { mutableStateOf(false) }
    // 0.0.9-debug 修复：原为普通 remember，Activity 配置变更重建时向导弹窗
    // 静默消失，而保留的 ProviderSetupViewModel 仍停在中间步骤——重开后旧步骤
    // 残留、验证态悬空。saveable 让弹窗随重建恢复，配合向导内的 DisposableEffect
    // 取消验证与 FocusRequester 步进守卫，重建路径闭环。
    var wizard by rememberSaveable { mutableStateOf<ProviderWizardKind?>(null) }
    var showContributors by remember { mutableStateOf(false) }
    var showCommunityGroup by remember { mutableStateOf(false) }
    var showAppUpdate by remember { mutableStateOf(false) }
    var developerToolsExpanded by rememberSaveable { mutableStateOf(false) }
    var moduleOrderExpanded by rememberSaveable { mutableStateOf(false) }
    var telemetryItemsExpanded by rememberSaveable { mutableStateOf(false) }
    var lifeIndexItemsExpanded by rememberSaveable { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val landscapeLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var landscapeSection by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(landscapeSection, landscapeLayout) {
        if (landscapeLayout) scrollState.scrollTo(0)
    }

    // 权限申请器：只在用户点「定位当前城市」时触发，App 启动/刷新绝不调用
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        scope.launch {
            if (preciseLocationEnabled) SettingsRepository.setPreciseLocationPermissionAsked()
            if (LocationSource.hasPermission(context)) {
                permDenied = false
                onLocate()
            } else {
                permDenied = true
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(ZhishengBg)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        if (!landscapeLayout) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = uiText("返回"), tint = ZhishengText)
                }
                Column {
                    Text(
                        "设置",
                        style = MaterialTheme.typography.titleMedium,
                        color = ZhishengOrange,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "SYSTEM CONFIG",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextTertiary,
                        letterSpacing = 1.5.sp,
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            if (landscapeLayout) {
                Column(
                    modifier = Modifier.fillMaxHeight().width(190.dp)
                        .background(ZhishengSurface.copy(alpha = 0.72f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = uiText("返回"),
                                tint = ZhishengText,
                            )
                        }
                        Text(
                            "设置",
                            style = MaterialTheme.typography.titleSmall,
                            color = ZhishengOrange,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LandscapeSettingsRail(
                        selected = landscapeSection,
                        onSelected = { landscapeSection = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
                Box(Modifier.fillMaxHeight().width(1.dp).background(ZhishengCardBorder))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .padding(horizontal = if (landscapeLayout) 24.dp else 16.dp),
            ) {
            if (!landscapeLayout || landscapeSection == 0) {
            SectionTitle(
                1,
                "数据与位置",
                "DATA / LOCATION",
                sourceHint(source, activeSource, activeSupplementSources, activeCityName, sourceLoading),
            )
            InlineGroupLabel("天气来源", "自动优选或指定服务")
            CardBox {
                listOf(SourcePref.AUTO, SourcePref.XIAOMI, SourcePref.OPEN_METEO).forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    SourceRow(
                        pref = p,
                        description = sourceDescription(p),
                        selected = source == p,
                        status = sourceStatus(p, source == p, activeSource, sourceLoading),
                        onClick = { scope.launch { SettingsRepository.setSourcePref(p) } },
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                // 开关前后保持同一棵组件树：只切换可用状态，杜绝重排导致的页面跳动。
                ToggleRow(
                    "开发者模式",
                    if (developerMode) "已开启·可使用彩云、和风、高德与氛围实验室" else "开启彩云、和风、高德接入与天气效果预览",
                    developerMode,
                ) {
                    if (developerMode) developerToolsExpanded = false
                    scope.launch { SettingsRepository.setDeveloperMode(!developerMode) }
                }
                listOf(SourcePref.CAIYUN, SourcePref.QWEATHER).forEach { p ->
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    SourceRow(
                        pref = p,
                        description = sourceDescription(p),
                        selected = source == p,
                        status = if (developerMode) {
                            sourceStatus(p, source == p, activeSource, sourceLoading)
                        } else {
                            "需开启" to false
                        },
                        enabled = developerMode,
                        onClick = { scope.launch { SettingsRepository.setSourcePref(p) } },
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                ActionRow(
                    label = if (developerToolsExpanded) {
                        "> 收起开发者工具"
                    } else {
                        "> 数据源接入 / 氛围实验室"
                    },
                    enabled = developerMode,
                    color = ZhishengCyan,
                ) { developerToolsExpanded = !developerToolsExpanded }
                if (developerToolsExpanded && developerMode) {
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    InlineGroupLabel("接入管理", "凭据只保存在本机")
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    ActionRow(
                        label = if (CaiyunApi.enabled) "> 彩云天气 · 已配置 · 重新配置" else "> 彩云天气 · 接入",
                        enabled = true,
                        color = ZhishengCyan,
                    ) { wizard = ProviderWizardKind.CAIYUN }
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    ActionRow(
                        label = "> 清除本机彩云 Token",
                        enabled = caiyunRt.ready,
                        color = ZhishengOrange,
                    ) { scope.launch { SecretStore.clearCaiyun() } }
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    ActionRow(
                        label = if (QWeatherApi.enabled) "> 和风天气 · 已配置 · 重新配置" else "> 和风天气 · 接入",
                        enabled = true,
                        color = ZhishengCyan,
                    ) { wizard = ProviderWizardKind.QWEATHER }
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    ActionRow(
                        label = "> 清除本机和风凭据",
                        enabled = qwRt.ready,
                        color = ZhishengOrange,
                    ) { scope.launch { SecretStore.clearQw() } }
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    ActionRow(
                        label = if (amapRt.ready) "> 高德街道定位 · 已配置 · 重新配置" else "> 高德街道定位 · 接入",
                        enabled = true,
                        color = ZhishengCyan,
                    ) { wizard = ProviderWizardKind.AMAP }
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    ActionRow(
                        label = "> 清除本机高德 Key",
                        enabled = amapRt.ready,
                        color = ZhishengOrange,
                    ) { scope.launch { SecretStore.clearAmap() } }
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    InlineGroupLabel("效果预览", "模拟数据不会写入主页")
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    ActionRow(
                        label = "> 氛围实验室 · 预览全部天气效果",
                        enabled = true,
                        color = ZhishengMint,
                    ) { onAtmosphereLab() }
                }
            }

            Spacer(Modifier.height(8.dp))
            InlineGroupLabel(
                "位置服务",
                if (locationEnabled) "已开启 · 仅在打开时复核" else "已关闭 · 不读取位置权限",
            )
            CardBox {
                ToggleRow(
                    "自动跟随所在城市",
                    if (locationEnabled) "开启·打开 App 时自动更新" else "关闭·不申请任何位置权限",
                    locationEnabled,
                ) {
                    scope.launch {
                        SettingsRepository.setLocationEnabled(!locationEnabled)
                        if (locationEnabled) onClearLocateMessage()
                    }
                }
                if (locationEnabled) {
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    ToggleRow(
                        "街道级精确定位",
                        if (preciseLocationEnabled) {
                            "开启·定位时可选择精确位置；识别失败自动回退到城市"
                        } else {
                            "关闭·仅使用城市级大致位置"
                        },
                        preciseLocationEnabled,
                    ) {
                        onClearLocateMessage()
                        permDenied = false
                        scope.launch { SettingsRepository.setPreciseLocationEnabled(!preciseLocationEnabled) }
                    }
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    ActionRow(
                        label = if (locating) "定位中 ..." else "⌖ 立即重新定位",
                        enabled = !locating,
                        color = ZhishengMint,
                    ) {
                        onClearLocateMessage()
                        val permissionReady = if (preciseLocationEnabled) {
                            LocationSource.hasPrecisePermission(context) ||
                                (LocationSource.hasPermission(context) && preciseLocationPermissionAsked)
                        } else {
                            LocationSource.hasPermission(context)
                        }
                        if (permissionReady) onLocate()
                        else permLauncher.launch(LocationSource.requestedPermissions(preciseLocationEnabled))
                    }
                    locateMessage?.let { msg ->
                        HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                        Text(
                            "> $msg",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (msg.startsWith("已")) {
                                ZhishengMint
                            } else {
                                ZhishengOrange
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                    if (permDenied) {
                        HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                "> 已拒绝位置权限，定位不可用（手动搜索城市不受影响）",
                                style = MaterialTheme.typography.labelMedium,
                                color = ZhishengOrange,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "[ 去系统设置授权 ]",
                                style = MaterialTheme.typography.labelMedium,
                                color = ZhishengCyan,
                                modifier = Modifier
                                    .clickable(role = Role.Button) { openAppSettings(context) }
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            }
            if (!landscapeLayout || landscapeSection == 1) {
            SectionTitle(2, "首页内容", "HOME CONTENT", "调整单位、显示模块与主页顺序。")
            InlineGroupLabel("主页播报")
            CardBox {
                SegmentRow(
                    "播报样式",
                    listOf("天气娘" to "weather_girl", "简洁 Tips" to "tips"),
                    homeBriefingStyle.key,
                    hint = "天气内容完全相同；简洁 Tips 不显示人物形象",
                ) { value ->
                    scope.launch {
                        SettingsRepository.setHomeBriefingStyle(HomeBriefingStyle.from(value))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            InlineGroupLabel("单位")
            CardBox {
                SegmentRow(
                    "温度", listOf("摄氏 °C" to "c", "华氏 °F" to "f"), tempUnit,
                ) { scope.launch { SettingsRepository.setTempUnit(it) } }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                SegmentRow(
                    "风速", listOf("km/h" to "kmh", "m/s" to "ms", "级" to "bft"), windUnit,
                ) { scope.launch { SettingsRepository.setWindUnit(it) } }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                SegmentRow(
                    "气压", listOf("hPa" to "hpa", "mmHg" to "mmhg", "inHg" to "inhg"), pressureUnit,
                ) { scope.launch { SettingsRepository.setPressureUnit(it) } }
            }

            Spacer(Modifier.height(8.dp))
            InlineGroupLabel("模块")
            CardBox {
                ToggleRow("时空观测", "过去7天、往年同日与近两小时雷达；作为一个模块排序", showSpacetime) {
                    scope.launch { SettingsRepository.setShowSpacetime(!showSpacetime) }
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                ToggleRow("短时降水", "未来两小时开始、停止与强度趋势", showPrecip) {
                    scope.launch { SettingsRepository.setShowPrecip(!showPrecip) }
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                ToggleRow("遥测数据", "湿度/风/气压/能见度等", showTelemetry) {
                    scope.launch { SettingsRepository.setShowTelemetry(!showTelemetry) }
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                ToggleRow("空气质量", "AQI 与六项污染物", showAqi) {
                    scope.launch { SettingsRepository.setShowAqi(!showAqi) }
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                ToggleRow("生活指数", "洗车/运动/穿衣/感冒", showIndices) {
                    scope.launch { SettingsRepository.setShowIndices(!showIndices) }
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                ToggleRow("昨日复盘", "昨日高低温与温差", showYesterday) {
                    scope.launch { SettingsRepository.setShowYesterday(!showYesterday) }
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                ToggleRow("台风路径", "实况路径、强度变化与多机构预报", showTyphoon) {
                    scope.launch { SettingsRepository.setShowTyphoon(!showTyphoon) }
                }
            }

            if (developerMode) {
                Spacer(Modifier.height(8.dp))
                InlineGroupLabel("遥测项目", "开发者模式 · 自由选择显示内容")
                CardBox {
                    ActionRow(
                        label = if (telemetryItemsExpanded) {
                            "> 收起遥测项目 · ${telemetryMetrics.size}/${TelemetryMetric.entries.size}"
                        } else {
                            "> 选择遥测项目 · ${telemetryMetrics.size}/${TelemetryMetric.entries.size}"
                        },
                        enabled = showTelemetry,
                        color = ZhishengCyan,
                    ) { telemetryItemsExpanded = !telemetryItemsExpanded }
                    if (telemetryItemsExpanded && showTelemetry) {
                        HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                        Row(Modifier.fillMaxWidth()) {
                            ActionRow(
                                label = "> 全选",
                                enabled = telemetryMetrics.size != TelemetryMetric.entries.size,
                                color = ZhishengMint,
                                modifier = Modifier.weight(1f),
                            ) { scope.launch { SettingsRepository.setTelemetryMetrics(TelemetryMetric.defaultSelection) } }
                            ActionRow(
                                label = "> 清空",
                                enabled = telemetryMetrics.isNotEmpty(),
                                color = ZhishengOrange,
                                modifier = Modifier.weight(1f),
                            ) { scope.launch { SettingsRepository.setTelemetryMetrics(emptySet()) } }
                        }
                        TelemetryMetric.entries.forEach { metric ->
                            HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                            ToggleRow(metric.cn, metric.en, metric in telemetryMetrics) {
                                val next = telemetryMetrics.toMutableSet().apply {
                                    if (!add(metric)) remove(metric)
                                }
                                scope.launch { SettingsRepository.setTelemetryMetrics(next) }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                InlineGroupLabel("生活指数项目", "开发者模式 · 自由选择显示内容")
                CardBox {
                    ActionRow(
                        label = if (lifeIndexItemsExpanded) {
                            "> 收起生活指数项目 · ${lifeIndexMetrics.size}/${LifeIndexMetric.entries.size}"
                        } else {
                            "> 选择生活指数项目 · ${lifeIndexMetrics.size}/${LifeIndexMetric.entries.size}"
                        },
                        enabled = showIndices,
                        color = ZhishengCyan,
                    ) { lifeIndexItemsExpanded = !lifeIndexItemsExpanded }
                    if (lifeIndexItemsExpanded && showIndices) {
                        HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                        Row(Modifier.fillMaxWidth()) {
                            ActionRow(
                                label = "> 全选",
                                enabled = lifeIndexMetrics.size != LifeIndexMetric.entries.size,
                                color = ZhishengMint,
                                modifier = Modifier.weight(1f),
                            ) { scope.launch { SettingsRepository.setLifeIndexMetrics(LifeIndexMetric.defaultSelection) } }
                            ActionRow(
                                label = "> 清空",
                                enabled = lifeIndexMetrics.isNotEmpty(),
                                color = ZhishengOrange,
                                modifier = Modifier.weight(1f),
                            ) { scope.launch { SettingsRepository.setLifeIndexMetrics(emptySet()) } }
                        }
                        LifeIndexMetric.entries.forEach { metric ->
                            HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                            ToggleRow(metric.cn, metric.en, metric in lifeIndexMetrics) {
                                val next = lifeIndexMetrics.toMutableSet().apply {
                                    if (!add(metric)) remove(metric)
                                }
                                scope.launch { SettingsRepository.setLifeIndexMetrics(next) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            InlineGroupLabel("模块布局", "排序工具默认收起，减少设置页干扰")
            CardBox {
                ActionRow(
                    label = if (moduleOrderExpanded) "> 收起模块排序" else "> 调整主页模块顺序",
                    enabled = true,
                    color = ZhishengCyan,
                ) { moduleOrderExpanded = !moduleOrderExpanded }
                if (moduleOrderExpanded) {
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    ActionRow(
                        label = "> 恢复默认顺序",
                        enabled = moduleOrder != HomeModule.defaultOrder,
                        color = ZhishengOrange,
                    ) {
                        scope.launch { SettingsRepository.setModuleOrder(HomeModule.defaultOrder) }
                    }
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    ModuleOrderEditor(moduleOrder) { from, to ->
                        val next = moduleOrder.toMutableList().apply {
                            add(to, removeAt(from))
                        }
                        scope.launch { SettingsRepository.setModuleOrder(next) }
                    }
                }
            }

            }
            if (!landscapeLayout || landscapeSection == 2) {
            SectionTitle(3, "外观与设备", "DISPLAY / DEVICE", "统一管理语言、主题、动效与桌面显示。")
            InlineGroupLabel("显示风格")
            CardBox {
                SegmentRow(
                    "显示语言",
                    listOf("简体中文" to "zh", "日本語" to "ja"),
                    appLanguage.key,
                    hint = "切换后立即生效；天气数值和数据来源不会改变",
                ) { value ->
                    scope.launch {
                        val selected = AppLanguage.from(value)
                        SettingsRepository.setAppLanguage(selected)
                        AppLanguageState.current = selected
                        ZhishengWidgetProvider.refreshAll(context)
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                SegmentRow(
                    "主题模式",
                    listOf("深色" to "dark", "浅色" to "light", "跟随系统" to "system"),
                    themeMode.key,
                    hint = "深色是磷光终端，浅色是纸面终端",
                ) { v -> scope.launch { SettingsRepository.setThemeMode(ThemeMode.from(v)) } }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                SegmentRow(
                    "强调色亮度",
                    listOf("标准" to "standard", "柔和" to "soft"),
                    accentTone.key,
                    hint = "同时调整数据绿与线框蓝；柔和档降低发光亮度",
                ) { v -> scope.launch { SettingsRepository.setAccentTone(AccentTone.from(v)) } }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                SegmentRow(
                    "天气氛围层",
                    listOf("关闭" to "off", "克制" to "subtle", "明显" to "vivid", "强烈" to "intense"),
                    ambience.key,
                    hint = "强烈档增加粒子密度、移动速度与磷光亮度",
                ) { v -> scope.launch { SettingsRepository.setAmbience(AmbienceLevel.from(v)) } }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                ToggleRow("CRT 扫描线", "整屏细横纹，终端质感", scanlines) {
                    scope.launch { SettingsRepository.setScanlines(!scanlines) }
                }
            }

            Spacer(Modifier.height(8.dp))
            InlineGroupLabel("设备与桌面")
            CardBox {
                ToggleRow("横屏待机界面", "开启后旋转显示桌面时钟；关闭后锁定竖屏", landscapeStandby) {
                    scope.launch { SettingsRepository.setLandscapeStandby(!landscapeStandby) }
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                SegmentRow(
                    "横屏样式",
                    listOf("经典终端" to "classic", "气象中枢" to "weather_core"),
                    landscapeStandbyStyle.key,
                    hint = if (landscapeStandby) {
                        "经典保留现版；气象中枢强化日照轨迹、天气趋势与沉浸光感"
                    } else {
                        "开启横屏待机界面后生效"
                    },
                ) { value ->
                    scope.launch {
                        SettingsRepository.setLandscapeStandbyStyle(LandscapeStandbyStyle.from(value))
                    }
                }
                if (landscapePortraitLocked) {
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    ActionRow(
                        label = "> 恢复自动旋转",
                        enabled = landscapeStandby,
                        color = ZhishengMint,
                        onClick = onRestoreLandscapeAuto,
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                SegmentRow(
                    "应用图标",
                    listOf("天气娘" to "character", "经典" to "classic"),
                    appIconStyle.key,
                    hint = "选择桌面显示的图标；切换后可能需要片刻刷新",
                ) { value ->
                    val selected = AppIconStyle.from(value)
                    scope.launch {
                        if (AppIconManager.apply(context, selected)) {
                            SettingsRepository.setAppIconStyle(selected)
                        }
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                SegmentRow(
                    "桌面组件底色",
                    listOf("全透明" to "transparent", "玻璃" to "glass", "不透明" to "opaque"),
                    widgetBackgroundMode.key,
                    hint = "玻璃为当前效果；全透明融入壁纸，不透明优先保证文字清晰",
                ) { value ->
                    scope.launch {
                        SettingsRepository.setWidgetBackgroundMode(WidgetBackgroundMode.from(value))
                        ZhishengWidgetProvider.refreshAll(context)
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                ToggleRow("开机自检动画", "启动时的终端打字序列", bootAnim) {
                    scope.launch { SettingsRepository.setBootAnim(!bootAnim) }
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                ToggleRow("常亮屏幕", "看天气时不自动息屏", keepScreenOn) {
                    scope.launch { SettingsRepository.setKeepScreenOn(!keepScreenOn) }
                }
            }

            }
            if (!landscapeLayout || landscapeSection == 3) {
            SectionTitle(4, "关于枳生", "ABOUT", "版本更新、官网、社区与开源信息。")
            CardBox {
                InfoRow(
                    "版本",
                    "v${com.zhisheng.weather.BuildConfig.VERSION_NAME} · 更新说明",
                    onClick = onShowWhatsNew,
                )
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                InfoRow(
                    "检查更新",
                    availableUpdate?.let { "发现新版本 v${it.versionName} · 点此查看" }
                        ?: "自动检测更新 · 不弹窗、不自动下载",
                    onClick = { showAppUpdate = true },
                    attention = availableUpdate != null,
                )
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                InfoRow("权限", "网络；位置可选；安装更新时才调用系统安装")
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                ActionRow(
                    label = "> 社区贡献者名单 · ${CommunityContributors.size} 位",
                    enabled = true,
                    color = ZhishengMint,
                ) { showContributors = true }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                LinkRow(
                    "用户交流 QQ 群",
                    "$CommunityQqGroup · 点开群二维码",
                ) { showCommunityGroup = true }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                LinkRow(
                    "枳生天气官网",
                    "zhishengweather.site · 官方网站",
                ) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://zhishengweather.site/"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                LinkRow(
                    "GitHub 仓库",
                    "开源主页 · 欢迎 star",
                ) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/zhishengplus/ZhishengWeather"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "枳生天气 · 数据终端",
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                "数据来源：和风 / 彩云 / 小米公开接口 / Open-Meteo / RainViewer",
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 28.dp),
            )
            }
            }
        }
    }

    wizard?.let { kind ->
        ProviderWizard(kind = kind, onClose = { wizard = null })
    }
    if (showContributors) {
        ContributorsDialog(onClose = { showContributors = false })
    }
    if (showCommunityGroup) {
        CommunityGroupDialog(onClose = { showCommunityGroup = false })
    }
    if (showAppUpdate) {
        AppUpdateDialog(
            initialInfo = availableUpdate,
            onClose = { showAppUpdate = false },
        )
    }
}

/** 横屏专用索引：常规横屏完整露出四类，极矮窗口仍可滚动访问，右侧只滚当前类别。 */
@Composable
private fun LandscapeSettingsRail(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = listOf(
        Triple("01//", "数据与位置", "DATA / LOCATION"),
        Triple("02//", "首页内容", "HOME CONTENT"),
        Triple("03//", "外观与设备", "DISPLAY / DEVICE"),
        Triple("04//", "关于枳生", "ABOUT"),
    )
    LazyColumn(
        modifier = modifier.background(ZhishengSurface.copy(alpha = 0.72f)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(sections) { index, (number, title, english) ->
            val active = selected == index
            Row(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .background(if (active) ZhishengCard else Color.Transparent)
                    .border(1.dp, if (active) ZhishengOrange else ZhishengCardBorder, RectangleShape)
                    .clickable(role = Role.Tab) { onSelected(index) }
                    .semantics { this.selected = active }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    number,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) ZhishengOrange else ZhishengTextTertiary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) ZhishengText else ZhishengTextSecondary,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                    Text(
                        english,
                        style = MaterialTheme.typography.labelSmall,
                        color = ZhishengTextTertiary,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun sourceHint(
    selected: SourcePref,
    activeSource: String?,
    supplementSources: List<String>,
    cityName: String?,
    loading: Boolean,
): String {
    val city = cityName ?: "当前城市"
    if (loading) return "正在为 $city 连接 ${selected.cn}，完成后这里会显示实际返回数据的来源。"
    val active = sourceName(activeSource)
        ?: return "$city 还没有成功返回天气数据；选择数据源后可直接看到连接结果。"
    val supplements = supplementSources.mapNotNull(::sourceName).filter { it != active }.distinct()
    val activeSummary = if (supplements.isEmpty()) active else "$active + ${supplements.joinToString("/")}（分项）"
    return if (selected == SourcePref.AUTO) {
        "$city 当前实际使用：$activeSummary。自动优选会按功能选源，并在首选源不可用时降级。"
    } else {
        "$city 当前实际使用：$activeSummary；设置已锁定为 ${selected.cn}。"
    }
}

private fun sourceDescription(p: SourcePref): String = when (p) {
    SourcePref.AUTO -> "小米为主；实况与短时冲突时按完整区块优选"
    SourcePref.QWEATHER -> if (QWeatherApi.enabled) "凭据已配置·完整数据" else "在下方接入"
    SourcePref.CAIYUN -> if (CaiyunApi.enabled) "Token 已配置·本机接入" else "在下方填写 Token"
    SourcePref.XIAOMI -> "免配置·国内覆盖"
    SourcePref.OPEN_METEO -> "免配置·全球覆盖"
}

private fun sourceStatus(
    pref: SourcePref,
    selected: Boolean,
    activeSource: String?,
    loading: Boolean,
): Pair<String, Boolean> {
    if (selected && loading) return "连接中" to true
    if (pref != SourcePref.AUTO && sourceMatches(pref, activeSource)) return "使用中" to true
    return when (pref) {
        SourcePref.AUTO -> if (selected && activeSource != null) "使用中" to true else "可用" to true
        SourcePref.QWEATHER -> if (QWeatherApi.enabled) "已配置" to true else "未配置" to false
        SourcePref.CAIYUN -> if (CaiyunApi.enabled) "已配置" to true else "未配置" to false
        SourcePref.XIAOMI -> "可用" to true
        SourcePref.OPEN_METEO -> "可用" to true
    }
}

private fun sourceMatches(pref: SourcePref, activeSource: String?): Boolean = when (pref) {
    SourcePref.QWEATHER -> activeSource == "QWEATHER"
    SourcePref.CAIYUN -> activeSource == "CAIYUN"
    SourcePref.XIAOMI -> activeSource == "XIAOMI"
    SourcePref.OPEN_METEO -> activeSource == "OPEN-METEO"
    SourcePref.AUTO -> false
}

private fun sourceName(activeSource: String?): String? = when (activeSource) {
    "QWEATHER" -> "和风天气"
    "CAIYUN" -> "彩云天气"
    "XIAOMI" -> "小米公开接口"
    "OPEN-METEO" -> "Open-Meteo"
    else -> activeSource
}

private fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun SectionTitle(index: Int, title: String, en: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(start = 2.dp, top = 24.dp, bottom = 8.dp, end = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%02d//".format(index),
                style = MaterialTheme.typography.titleSmall,
                color = ZhishengOrange,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = ZhishengTextSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(en, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, letterSpacing = 1.5.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(description, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
    }
}

@Composable
private fun InlineGroupLabel(label: String, detail: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ZhishengOrange, fontWeight = FontWeight.Bold)
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.weight(1f))
            Text(detail, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        }
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .background(ZhishengCard)
            .border(1.dp, ZhishengCardBorder, RectangleShape),
    ) {
        content()
    }
}

@Composable
private fun SourceRow(
    pref: SourcePref,
    description: String,
    selected: Boolean,
    status: Pair<String, Boolean>,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = enabled, role = Role.RadioButton) { onClick() }
            // v0.0.4：TalkBack 播报选中状态
            .semantics {
                this.selected = selected
                if (!enabled) disabled()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 3.dp, height = 22.dp)
                .background(if (selected && enabled) ZhishengMint else ZhishengCardBorder)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pref.cn,
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        !enabled -> ZhishengTextTertiary
                        selected -> ZhishengMint
                        else -> ZhishengText
                    },
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.width(8.dp))
                Text(pref.en, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, letterSpacing = 1.sp)
            }
            Text(description, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        }
        Text(
            status.first,
            style = MaterialTheme.typography.labelMedium,
            color = if (!enabled) ZhishengTextTertiary else if (status.second) ZhishengCyan else ZhishengOrange,
        )
        if (selected && enabled) {
            Spacer(Modifier.width(8.dp))
            Text("[✓]", style = MaterialTheme.typography.labelMedium, color = ZhishengMint)
        }
    }
}

// 分段选择器：一行内 2-3 个互斥选项
@Composable
private fun SegmentRow(
    label: String,
    options: List<Pair<String, String>>,
    current: String,
    hint: String? = null,
    onPick: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = ZhishengText)
        // 分档选项也该有一句说明：开关行一直有，分档行原来没有，
        // 用户只能靠猜「克制」和「明显」差在哪（v0.0.9）。
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // v0.0.4：互斥选项组语义，TalkBack 正确播报单选关系
            modifier = Modifier.selectableGroup(),
        ) {
            options.forEach { (text, value) ->
                val on = current == value
                Box(
                    Modifier.weight(1f)
                        .background(if (on) ZhishengMint.copy(alpha = 0.14f) else ZhishengSurface)
                        .border(1.dp, if (on) ZhishengMint else ZhishengCardBorder, RectangleShape)
                        .clickable(role = Role.RadioButton) { onPick(value) }
                        .semantics { this.selected = on }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) ZhishengMint else ZhishengTextSecondary,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, hint: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            // 单一 toggleable 事件源：Switch 只负责绘制，避免父子点击同时触发。
            .toggleable(value = checked, role = Role.Switch) { onToggle() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = ZhishengText)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ZhishengBg,
                checkedTrackColor = ZhishengMint,
                uncheckedThumbColor = ZhishengTextTertiary,
                uncheckedTrackColor = ZhishengCardBorder,
                uncheckedBorderColor = ZhishengCardBorder,
            ),
        )
    }
}

@Composable
private fun ModuleOrderEditor(
    order: List<HomeModule>,
    onMove: (from: Int, to: Int) -> Unit,
) {
    order.forEachIndexed { index, module ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "%02d".format(index + 1),
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengOrange,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(module.cn, style = MaterialTheme.typography.titleSmall, color = ZhishengText)
                Text(module.en, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, letterSpacing = 1.sp)
            }
            Text(
                "[↑]",
                style = MaterialTheme.typography.titleSmall,
                color = if (index > 0) ZhishengCyan else ZhishengCardBorder,
                modifier = Modifier
                    .clickable(enabled = index > 0, role = Role.Button, onClickLabel = "${module.cn}上移") {
                        onMove(index, index - 1)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Text(
                "[↓]",
                style = MaterialTheme.typography.titleSmall,
                color = if (index < order.lastIndex) ZhishengMint else ZhishengCardBorder,
                modifier = Modifier
                    .clickable(enabled = index < order.lastIndex, role = Role.Button, onClickLabel = "${module.cn}下移") {
                        onMove(index, index + 1)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        if (index < order.lastIndex) HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
    }
}

@Composable
private fun ActionRow(
    label: String,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) color else ZhishengTextTertiary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    attention: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (attention) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(ZhishengRed, CircleShape),
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (attention) ZhishengRed else ZhishengTextSecondary,
            fontWeight = if (attention) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = if (attention) ZhishengRed else ZhishengText,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

// 可点击外链行：跳浏览器打开 URL（v0.0.5 GitHub 引流入口）
@Composable
private fun LinkRow(label: String, sub: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, color = ZhishengCyan)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        }
        Spacer(Modifier.weight(1f))
        Text("↗", style = MaterialTheme.typography.titleMedium, color = ZhishengCyan)
    }
}
