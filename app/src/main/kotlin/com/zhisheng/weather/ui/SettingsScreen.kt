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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.data.AccentTone
import com.zhisheng.weather.data.AppLanguage
import com.zhisheng.weather.data.AppIconManager
import com.zhisheng.weather.data.AppIconStyle
import com.zhisheng.weather.data.AppUpdateInfo
import com.zhisheng.weather.data.AmbienceLevel
import com.zhisheng.weather.data.CaiyunApi
import com.zhisheng.weather.data.HomeModule
import com.zhisheng.weather.data.HomeBriefingStyle
import com.zhisheng.weather.data.HomeSurfaceStyle
import com.zhisheng.weather.data.LocationSource
import com.zhisheng.weather.data.LifeIndexMetric
import com.zhisheng.weather.data.LandscapeStandbyStyle
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.data.QWeatherApi
import com.zhisheng.weather.data.SecretStore
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.widget.WidgetConfigActivity
import com.zhisheng.weather.widget.WidgetStyle
import com.zhisheng.weather.data.ReleaseFeatures
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
import com.zhisheng.weather.ui.theme.zhishengPanel
import com.zhisheng.weather.ui.theme.zhishengScreen
import com.zhisheng.weather.ui.theme.zhishengCompactPanel
import com.zhisheng.weather.ui.theme.zhishengOverlayColor
import com.zhisheng.weather.ui.theme.LocalZhishengChrome
import com.zhisheng.weather.ui.theme.LocalHomeSurfaceStyle
import com.zhisheng.weather.ui.theme.glassScrollHeader
import com.zhisheng.weather.ui.theme.isPhosphorVista
import com.zhisheng.weather.ui.components.HomeBackdrop
import com.zhisheng.weather.ui.components.LocalHomeBackdrop
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
    initialSection: Int = 0,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showStatisticsInfo by remember { mutableStateOf(false) }
    var showLivingSkyPreview by rememberSaveable { mutableStateOf(false) }
    if (showStatisticsInfo) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showStatisticsInfo = false },
            containerColor = zhishengOverlayColor(),
            title = { Text("设备与版本统计说明", color = ZhishengText) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("首次使用或版本变更时，向枳生天气自有服务器 zhishengweather.site 发送本应用专用的设备标识摘要与版本号，用于设备去重及版本分布，帮助改进使用体验、安排后续更新。\n\n摘要由 Android ID 在本地生成，不上传原值；无法获取时使用随机安装编号。不包含机型、位置、天气密钥或页面操作。摘要可关联同一设备，不属于完全匿名数据。\n\n服务器保存首次登记时间与最后登记版本，登记记录在统计服务存续期间保留。关闭“设备与版本统计”后停止发送，已有记录保留。\n\n卸载重装通常可去重，恢复出厂设置或系统用户变化仍可能算作新设备。统计不代表实时在线或当前仍安装的数量。", color = ZhishengTextSecondary)
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { showStatisticsInfo = false }) { Text("知道了", color = ZhishengMint) } },
        )
    }

    val tempUnit by SettingsRepository.tempUnit.collectAsState(initial = "c")
    val windUnit by SettingsRepository.windUnit.collectAsState(initial = "kmh")
    val pressureUnit by SettingsRepository.pressureUnit.collectAsState(initial = "hpa")
    val showTyphoon by SettingsRepository.showTyphoon.collectAsState(initial = true)
    val source by SettingsRepository.sourcePref.collectAsState(initial = SourcePref.AUTO)
    val developerMode by SettingsRepository.developerMode.collectAsState(initial = false)
    val ambience by SettingsRepository.ambience.collectAsState(initial = AmbienceLevel.VIVID)
    val livingSky by SettingsRepository.livingSky.collectAsState(initial = false)
    val scanlines by SettingsRepository.scanlines.collectAsState(initial = true)
    val softGlow by SettingsRepository.softGlow.collectAsState(initial = false)
    val softGlowLevel by SettingsRepository.softGlowLevel.collectAsState(initial = com.zhisheng.weather.data.SoftGlowLevel.SUBTLE)
    val usageStatistics by SettingsRepository.usageStatistics.collectAsState(initial = false)
    val locationEnabled by SettingsRepository.locationEnabled.collectAsState(initial = false)
    val preciseLocationEnabled by SettingsRepository.preciseLocationEnabled.collectAsState(initial = false)
    val preciseLocationPermissionAsked by SettingsRepository.preciseLocationPermissionAsked.collectAsState(initial = false)
    val showAqi by SettingsRepository.showAqi.collectAsState(initial = true)
    val showIndices by SettingsRepository.showIndices.collectAsState(initial = true)
    val showYesterday by SettingsRepository.showYesterday.collectAsState(initial = true)
    val showPrecip by SettingsRepository.showPrecip.collectAsState(initial = true)
    val showTelemetry by SettingsRepository.showTelemetry.collectAsState(initial = true)
    val showSpacetime by SettingsRepository.showSpacetime.collectAsState(initial = true)
    val showSkyPhotography by SettingsRepository.showSkyPhotography.collectAsState(initial = true)
    val showCoastWeather by SettingsRepository.showCoastWeather.collectAsState(initial = true)
    val keepScreenOn by SettingsRepository.keepScreenOn.collectAsState(initial = false)
    val standbyBurnInProtection by SettingsRepository.standbyBurnInProtection.collectAsState(initial = true)
    val landscapeStandby by SettingsRepository.landscapeStandby.collectAsState(initial = true)
    val landscapeStandbyStyle by SettingsRepository.landscapeStandbyStyle.collectAsState(
        initial = LandscapeStandbyStyle.WEATHER_CORE,
    )
    val telemetryMetrics by SettingsRepository.telemetryMetrics.collectAsState(initial = TelemetryMetric.defaultSelection)
    val lifeIndexMetrics by SettingsRepository.lifeIndexMetrics.collectAsState(initial = LifeIndexMetric.defaultSelection)
    val themeMode by SettingsRepository.themeMode.collectAsState(initial = ThemeMode.LIGHT)
    val interfaceStyle by SettingsRepository.interfaceStyle.collectAsState(initial = InterfaceStyle.PHOSPHOR_VISTA)
    val accentTone by SettingsRepository.accentTone.collectAsState(initial = AccentTone.STANDARD)
    val appIconStyle by SettingsRepository.appIconStyle.collectAsState(initial = AppIconStyle.CHARACTER)
    val homeBriefingStyle by SettingsRepository.homeBriefingStyle.collectAsState(
        initial = HomeBriefingStyle.TIPS,
    )
    val homeSurfaceStyle by SettingsRepository.homeSurfaceStyle.collectAsState(initial = HomeSurfaceStyle.FRAGRANCE_GLASS)
    val appLanguage by SettingsRepository.appLanguage.collectAsState(initial = AppLanguage.CHINESE)
    val moduleOrder by SettingsRepository.moduleOrder.collectAsState(initial = HomeModule.defaultOrder)
    val qwRt by SecretStore.qwRuntimeFlow.collectAsState(initial = SecretStore.qwRuntime)
    val caiyunRt by SecretStore.caiyunRuntimeFlow.collectAsState(initial = SecretStore.caiyunRuntime)
    val amapRt by SecretStore.amapRuntimeFlow.collectAsState(initial = SecretStore.amapRuntime)
    val baiduRt by SecretStore.baiduRuntimeFlow.collectAsState(initial = SecretStore.baiduRuntime)

    var permDenied by remember { mutableStateOf(false) }
    // 0.0.9-debug 修复：原为普通 remember，Activity 配置变更重建时向导弹窗
    // 静默消失，而保留的 ProviderSetupViewModel 仍停在中间步骤——重开后旧步骤
    // 残留、验证态悬空。saveable 让弹窗随重建恢复，配合向导内的 DisposableEffect
    // 取消验证与 FocusRequester 步进守卫，重建路径闭环。
    var wizard by rememberSaveable { mutableStateOf<ProviderWizardKind?>(null) }
    var showContributors by remember { mutableStateOf(false) }
    var showSponsor by remember { mutableStateOf(false) }
    var showSponsorBoard by remember { mutableStateOf(false) }
    var showCommunityGroup by remember { mutableStateOf(false) }
    var showAppUpdate by remember { mutableStateOf(false) }
    var developerToolsExpanded by rememberSaveable { mutableStateOf(false) }
    var sourcePickerExpanded by rememberSaveable { mutableStateOf(false) }
    var moduleOrderExpanded by rememberSaveable { mutableStateOf(false) }
    var telemetryItemsExpanded by rememberSaveable { mutableStateOf(false) }
    var lifeIndexItemsExpanded by rememberSaveable { mutableStateOf(false) }
    val dataScrollState = rememberScrollState()
    val homeScrollState = rememberScrollState()
    val appearanceScrollState = rememberScrollState()
    val aboutScrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val landscapeLayout = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val chrome = LocalZhishengChrome.current
    var landscapeSection by rememberSaveable { mutableIntStateOf(initialSection.coerceIn(0, 3)) }
    // 两主题都使用四页分栏：经典终端此前是单页长滚动，与新版设置岛相比
    // 找不到内容、也拿不到底部切换；现在统一分页，外观按主题各自适配。
    val scrollState = when (landscapeSection) {
        0 -> dataScrollState
        1 -> homeScrollState
        2 -> appearanceScrollState
        else -> aboutScrollState
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

    val isFloatingGlass = isPhosphorVista && !landscapeLayout
    val glassHeader = isFloatingGlass && LocalHomeSurfaceStyle.current == HomeSurfaceStyle.FRAGRANCE_GLASS
    val glassBlendDistance = with(LocalDensity.current) { 48.dp.toPx() }
    val sampleSettingsBackground = isPhosphorVista && LocalHomeSurfaceStyle.current == HomeSurfaceStyle.FRAGRANCE_GLASS
    val floatingContentInset = maxOf(112.dp,
        82.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
    val glassSource = rememberGraphicsLayer()
    val settingsBackground = rememberGraphicsLayer()
    var glassOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { glassOrigin = it.positionInRoot() }) {
    if (sampleSettingsBackground) Box(Modifier.fillMaxSize()
        .drawWithContent {
            settingsBackground.record { this@drawWithContent.drawContent() }
            drawLayer(settingsBackground)
        }.zhishengScreen())
    CompositionLocalProvider(LocalHomeBackdrop provides if (sampleSettingsBackground)
        HomeBackdrop(settingsBackground, glassOrigin, androidx.compose.ui.platform.LocalView.current) else null) {
    Column(
        modifier = Modifier.fillMaxSize().drawWithContent {
                if (isFloatingGlass) {
                    glassSource.record { this@drawWithContent.drawContent() }
                    drawLayer(glassSource)
                } else drawContent()
            }.zhishengScreen()
            .statusBarsPadding()
            .then(if (!landscapeLayout) Modifier else Modifier.navigationBarsPadding()),
    ) {
        if (!landscapeLayout && !glassHeader) SettingsTopBar(onBack)

        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.Center,
        ) {
            if (landscapeLayout) {
                Column(
                    modifier = Modifier.fillMaxHeight().width(190.dp)
                        .background(ZhishengSurface.copy(alpha = 0.72f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(if (isPhosphorVista) 48.dp else 40.dp),
                        ) {
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
                modifier = (when {
                    landscapeLayout -> Modifier.weight(1f)
                    isPhosphorVista -> Modifier.fillMaxWidth().widthIn(max = chrome.contentMaxWidth)
                    else -> Modifier.fillMaxWidth()
                })
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .padding(horizontal = if (landscapeLayout) 24.dp else chrome.pagePadding)
                    .padding(bottom = if (isFloatingGlass) floatingContentInset else 24.dp),
            ) {
            if (glassHeader) Spacer(Modifier.height(64.dp))
            if (landscapeSection == 0) {
            SectionTitle(
                1,
                "数据与位置",
                "DATA / LOCATION",
                if (isPhosphorVista) "天气来源、定位方式与数据接入。" else sourceHint(source, activeSource, activeSupplementSources, activeCityName, sourceLoading),
            )
            InlineGroupLabel("天气数据")
            CardBox {
                if (isPhosphorVista) {
                    InfoRow("天气来源", if (source == SourcePref.AUTO) "自动选择" else source.cn,
                        onClick = { sourcePickerExpanded = !sourcePickerExpanded })
                    Text("${activeCityName ?: "当前城市"} · ${if (sourceLoading) "天气更新中" else sourceName(activeSource)?.let { "当前使用 $it" } ?: "等待天气数据"}",
                        fontSize = 12.sp, lineHeight = 18.sp, color = ZhishengTextSecondary,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp))
                }
                if (!isPhosphorVista || sourcePickerExpanded) {
                if (isPhosphorVista) {
                    SettingsDivider()
                    Text(sourceHint(source, activeSource, activeSupplementSources, activeCityName, sourceLoading),
                        fontSize = 12.sp, lineHeight = 18.sp, color = ZhishengTextSecondary,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp))
                }
                listOf(SourcePref.AUTO, SourcePref.ZHISHENG, SourcePref.XIAOMI, SourcePref.NMC, SourcePref.OPEN_METEO).forEachIndexed { i, p ->
                    if (i > 0) SettingsDivider()
                    SourceRow(
                        pref = p,
                        description = sourceDescription(p),
                        selected = source == p,
                        status = sourceStatus(p, source == p, activeSource, sourceLoading),
                        onClick = { scope.launch { SettingsRepository.setSourcePref(p) } },
                    )
                }
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
                    SettingsDivider()
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
                    SettingsDivider()
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
                        SettingsDivider()
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
                        SettingsDivider()
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

            Spacer(Modifier.height(8.dp))
            InlineGroupLabel("高级接入", "自有数据源与效果预览")
            CardBox {
                // 高级选项只在启用时展开，常用设置保持在前。
                ToggleRow(
                    "开发者模式",
                    if (developerMode) "已开启 · 可配置彩云、和风及地理服务" else "配置自有天气与地理服务凭据",
                    developerMode,
                ) {
                    if (developerMode) developerToolsExpanded = false
                    scope.launch { SettingsRepository.setDeveloperMode(!developerMode) }
                }
                if (!isPhosphorVista || developerMode) {
                listOf(SourcePref.CAIYUN, SourcePref.QWEATHER).forEach { p ->
                    SettingsDivider()
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
                SettingsDivider()
                ActionRow(
                    label = if (developerToolsExpanded) {
                        "> 收起开发者工具"
                    } else {
                        if (ReleaseFeatures.weatherAtmosphere) "> 数据源接入 / 氛围实验室" else "> 数据源接入"
                    },
                    enabled = developerMode,
                    color = ZhishengCyan,
                ) { developerToolsExpanded = !developerToolsExpanded }
                if (developerToolsExpanded && developerMode) {
                    SettingsDivider()
                    InlineGroupLabel("接入管理", "凭据只保存在本机")
                    SettingsDivider()
                    ActionRow(
                        label = if (caiyunRt.ready) "> 彩云天气 · 已配置 · 重新配置" else "> 彩云天气 · 接入",
                        enabled = true,
                        color = ZhishengCyan,
                    ) { wizard = ProviderWizardKind.CAIYUN }
                    SettingsDivider()
                    ActionRow(
                        label = "> 清除本机彩云 Token",
                        enabled = caiyunRt.ready,
                        color = ZhishengOrange,
                    ) { scope.launch { SecretStore.clearCaiyun() } }
                    SettingsDivider()
                    ActionRow(
                        label = if (qwRt.ready) "> 和风天气 · 已配置 · 重新配置" else "> 和风天气 · 接入",
                        enabled = true,
                        color = ZhishengCyan,
                    ) { wizard = ProviderWizardKind.QWEATHER }
                    SettingsDivider()
                    ActionRow(
                        label = "> 清除本机和风凭据",
                        enabled = qwRt.ready,
                        color = ZhishengOrange,
                    ) { scope.launch { SecretStore.clearQw() } }
                    SettingsDivider()
                    ActionRow(
                        label = if (amapRt.ready) "> 高德地理服务 · 已配置 · 重新配置" else "> 高德地理服务 · 接入",
                        enabled = true,
                        color = ZhishengCyan,
                    ) { wizard = ProviderWizardKind.AMAP }
                    SettingsDivider()
                    ActionRow(
                        label = "> 清除本机高德 Key",
                        enabled = amapRt.ready,
                        color = ZhishengOrange,
                    ) { scope.launch { SecretStore.clearAmap() } }
                    SettingsDivider()
                    ActionRow(
                        label = if (baiduRt.ready) "> 百度地理服务 · 已配置 · 重新配置" else "> 百度地理服务 · 接入",
                        enabled = true,
                        color = ZhishengCyan,
                    ) { wizard = ProviderWizardKind.BAIDU }
                    SettingsDivider()
                    ActionRow(
                        label = "> 清除本机百度 AK",
                        enabled = baiduRt.ready,
                        color = ZhishengOrange,
                    ) { scope.launch { SecretStore.clearBaidu() } }
                    if (ReleaseFeatures.weatherAtmosphere) {
                    SettingsDivider()
                    InlineGroupLabel("效果预览", "模拟数据不会写入主页")
                    SettingsDivider()
                    ActionRow(
                        label = "> 氛围实验室 · 预览全部天气效果",
                        enabled = true,
                        color = ZhishengMint,
                    ) { onAtmosphereLab() }
                    }

                }
                }
            }

            }
            if (landscapeSection == 1) {
            SectionTitle(2, "首页内容", "HOME CONTENT", if (isPhosphorVista) "选择你想看的天气信息。" else "调整单位、显示模块与主页顺序。")
            InlineGroupLabel("主页播报")
            CardBox {
                SegmentRow(
                    "播报样式",
                    listOf("天气娘" to "weather_girl", (if (isPhosphorVista) "文字提示" else "简洁 Tips") to "tips", "关闭" to "off"),
                    homeBriefingStyle.key,
                    hint = "天气娘与文字提示内容一致；关闭后主界面会完整收起播报区域",
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
                    compact = true,
                ) { scope.launch { SettingsRepository.setTempUnit(it) } }
                SettingsDivider()
                SegmentRow(
                    "风速", listOf("km/h" to "kmh", "m/s" to "ms", "级" to "bft"), windUnit,
                    compact = true,
                ) { scope.launch { SettingsRepository.setWindUnit(it) } }
                SettingsDivider()
                SegmentRow(
                    "气压", listOf("hPa" to "hpa", "mmHg" to "mmhg", "inHg" to "inhg"), pressureUnit,
                    compact = true,
                ) { scope.launch { SettingsRepository.setPressureUnit(it) } }
            }

            Spacer(Modifier.height(8.dp))
            InlineGroupLabel("模块")
            CardBox {
                ToggleRow(if (ReleaseFeatures.starPhotography) "气象视界 · 天空" else "霞光时刻", if (ReleaseFeatures.starPhotography) "查看霞光和星空拍摄时段" else "查看晨昏霞光参考时段；使用 Open-Meteo 当地预报", showSkyPhotography) {
                    scope.launch { SettingsRepository.setShowSkyPhotography(!showSkyPhotography) }
                }
                SettingsDivider()
                if (ReleaseFeatures.coastalWeather) {
                    ToggleRow("气象视界 · 海岸", "潮位、海浪与海温预览；开启后把地点坐标发送给 Open-Meteo", showCoastWeather) {
                    scope.launch { SettingsRepository.setShowCoastWeather(!showCoastWeather) }
                }
                SettingsDivider()
                }
                ToggleRow(if (ReleaseFeatures.radar) "气象视界 · 回看与雷达" else "天气回看", if (ReleaseFeatures.radar) "查看过去天气和降雨变化" else "查看历史天气与往年同期", showSpacetime) {
                    scope.launch { SettingsRepository.setShowSpacetime(!showSpacetime) }
                }
                SettingsDivider()
                ToggleRow("短时降水", "未来两小时开始、停止与强度趋势", showPrecip) {
                    scope.launch { SettingsRepository.setShowPrecip(!showPrecip) }
                }
                SettingsDivider()
                ToggleRow("遥测数据", "湿度/风/气压/能见度等", showTelemetry) {
                    scope.launch { SettingsRepository.setShowTelemetry(!showTelemetry) }
                }
                SettingsDivider()
                ToggleRow("空气质量", "AQI 与六项污染物", showAqi) {
                    scope.launch { SettingsRepository.setShowAqi(!showAqi) }
                }
                SettingsDivider()
                ToggleRow("生活指数", "当天指数与未来三天建议，项目随数据源而定", showIndices) {
                    scope.launch { SettingsRepository.setShowIndices(!showIndices) }
                }
                SettingsDivider()
                ToggleRow("昨日复盘", "昨日高低温与温差", showYesterday) {
                    scope.launch { SettingsRepository.setShowYesterday(!showYesterday) }
                }
                if (ReleaseFeatures.typhoon) {
                SettingsDivider()
                ToggleRow("台风路径", "实况路径、强度变化与多机构预报", showTyphoon) {
                    scope.launch { SettingsRepository.setShowTyphoon(!showTyphoon) }
                }
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
                        SettingsDivider()
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
                            SettingsDivider()
                            ToggleRow(metric.cn, if (isPhosphorVista) "" else metric.en, metric in telemetryMetrics) {
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
                        SettingsDivider()
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
                            SettingsDivider()
                            ToggleRow(metric.cn, if (isPhosphorVista) "" else metric.en, metric in lifeIndexMetrics) {
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
            InlineGroupLabel("模块布局", "按自己的习惯排列")
            CardBox {
                ActionRow(
                    label = if (moduleOrderExpanded) "> 收起模块排序" else "> 调整主页模块顺序",
                    enabled = true,
                    color = ZhishengCyan,
                ) { moduleOrderExpanded = !moduleOrderExpanded }
                if (moduleOrderExpanded) {
                    SettingsDivider()
                    ActionRow(
                        label = "> 恢复默认顺序",
                        enabled = moduleOrder != HomeModule.defaultOrder,
                        color = ZhishengOrange,
                    ) {
                        scope.launch { SettingsRepository.setModuleOrder(HomeModule.defaultOrder) }
                    }
                    SettingsDivider()
                    ModuleOrderEditor(moduleOrder) { from, to ->
                        val next = moduleOrder.toMutableList().apply {
                            add(to, removeAt(from))
                        }
                        scope.launch { SettingsRepository.setModuleOrder(next) }
                    }
                }
            }

            }
            if (landscapeSection == 2) {
            SectionTitle(3, "外观与体验", "DISPLAY / DEVICE", "主题、自然天光与屏幕显示。")
            if (isPhosphorVista) {
                InlineGroupLabel("首页材质")
                CardBox {
                SegmentRow(
                    "模块外观",
                    HomeSurfaceStyle.entries.map { it.cn to it.key },
                    homeSurfaceStyle.key,
                    hint = "切换首页与设置页的玻璃质感；天气文字和图标保持清晰",
                ) { value -> scope.launch { SettingsRepository.setHomeSurfaceStyle(HomeSurfaceStyle.from(value)) } }
                }
            }
            InlineGroupLabel("界面与语言")
            CardBox {
                if (ReleaseFeatures.classicTheme) {
                    SegmentRow(
                    "界面主题",
                    listOf("澄空终端" to "phosphor_vista", "经典终端" to "classic_terminal"),
                    interfaceStyle.key,
                    hint = if (interfaceStyle == InterfaceStyle.PHOSPHOR_VISTA) {
                        "无框天气概览，逐时、五日与多日预报依次展开"
                    } else {
                        "经典终端美术风格，与澄空共用天气逻辑和模块排序"
                    },
                ) { value -> scope.launch { SettingsRepository.setInterfaceStyle(InterfaceStyle.from(value)) } }
                SettingsDivider()
                }
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
                    }
                }
                SettingsDivider()
                SegmentRow(
                    "主题模式",
                    listOf("深色" to "dark", "浅色" to "light", "跟随系统" to "system"),
                    themeMode.key,
                    hint = "浅色清透，深色柔和，也可跟随系统",
                ) { v -> scope.launch { SettingsRepository.setThemeMode(ThemeMode.from(v)) } }
            }
            InlineGroupLabel("天光与动效")
            CardBox {
                SegmentRow(
                    "强调色亮度",
                    listOf("标准" to "standard", "柔和" to "soft"),
                    accentTone.key,
                    hint = "调整界面强调色的亮度",
                ) { v -> scope.launch { SettingsRepository.setAccentTone(AccentTone.from(v)) } }
                if (isPhosphorVista) {
                    SettingsDivider()
                    ToggleRow("柔雾辉光", "柔和色泽缓慢流动，贯穿所有页面", softGlow) {
                        scope.launch { SettingsRepository.setSoftGlow(!softGlow) }
                    }
                    if (softGlow) SegmentRow(
                        "辉光变化",
                        com.zhisheng.weather.data.SoftGlowLevel.entries.map { it.cn to it.key },
                        softGlowLevel.key,
                        hint = "柔和档轻缓流动；流光档色彩聚散和移动更明显",
                    ) { value -> scope.launch { SettingsRepository.setSoftGlowLevel(com.zhisheng.weather.data.SoftGlowLevel.from(value)) } }
                }
                if (ReleaseFeatures.weatherAtmosphere) {
                SettingsDivider()
                SegmentRow(
                    if (isPhosphorVista) "自然天光" else "天气氛围层",
                    listOf("关闭" to "off", "克制" to "subtle", "明显" to "vivid", "强烈" to "intense"),
                    ambience.key,
                    hint = if (isPhosphorVista) "天光、云雾与雨雪随天气变化，向下阅读时渐隐；省电模式下静止显示" else "强烈档增加粒子密度、移动速度与磷光亮度",
                    singleRow = true,
                ) { v -> scope.launch { SettingsRepository.setAmbience(AmbienceLevel.from(v)) } }
                if (isPhosphorVista) {
                    SettingsDivider()
                    ToggleRow("随时间变化的天空",
                        if (ambience == AmbienceLevel.OFF) "打开自然天光后生效；关闭此开关恢复原来的天空"
                        else "主页与澄空小组件跟随城市日照变化；关闭恢复原来的天空", livingSky) {
                        scope.launch { SettingsRepository.setLivingSky(!livingSky) }
                    }
                    ActionRow(label = "预览一天的天空", enabled = true, color = ZhishengCyan) {
                        showLivingSkyPreview = true
                    }
                }
                SettingsDivider()
                if (!isPhosphorVista) ToggleRow("CRT 扫描线", "整屏细横纹，终端质感", scanlines) {
                    scope.launch { SettingsRepository.setScanlines(!scanlines) }
                }
                }
            }

            Spacer(Modifier.height(8.dp))
            InlineGroupLabel("设备与桌面")
            InlineGroupLabel("横屏待机")
            CardBox {
                ToggleRow("横屏待机界面", "开启后旋转显示桌面时钟；关闭后锁定竖屏", landscapeStandby) {
                    scope.launch { SettingsRepository.setLandscapeStandby(!landscapeStandby) }
                }
                SettingsDivider()
                ToggleRow("防烧屏保护", "横屏内容每 15 秒轻移 1 个像素，减少固定位置长期显示；只能降低烧屏风险", standbyBurnInProtection) {
                    scope.launch { SettingsRepository.setStandbyBurnInProtection(!standbyBurnInProtection) }
                }
                if (ReleaseFeatures.classicTheme) {
                SettingsDivider()
                SegmentRow(
                    "横屏样式",
                    listOf("经典终端" to "classic", "气象中枢" to "weather_core"),
                    landscapeStandbyStyle.key,
                    hint = if (landscapeStandby) {
                        if (isPhosphorVista) "经典时钟，或同时显示天气趋势的天气时钟" else "经典保留现版；气象中枢强化日照轨迹、天气趋势与沉浸光感"
                    } else {
                        "开启横屏待机界面后生效"
                    },
                ) { value ->
                    scope.launch {
                        SettingsRepository.setLandscapeStandbyStyle(LandscapeStandbyStyle.from(value))
                    }
                }
                }
                if (landscapePortraitLocked) {
                    SettingsDivider()
                    ActionRow(
                        label = "> 恢复自动旋转",
                        enabled = landscapeStandby,
                        color = ZhishengMint,
                        onClick = onRestoreLandscapeAuto,
                    )
                }
            }
            InlineGroupLabel("小组件与通知栏")
            CardBox {
                ActionRow(
                    label = if (isPhosphorVista) "澄空小组件" else "> 澄空小组件",
                    enabled = true,
                    color = if (isPhosphorVista) ZhishengCyan else ZhishengMint,
                ) {
                    context.startActivity(Intent(context, WidgetConfigActivity::class.java).putExtra("widget_style", WidgetStyle.VISTA.key))
                }
                SettingsDivider()
                ActionRow(
                    label = if (isPhosphorVista) "经典小组件" else "> 经典小组件",
                    enabled = true,
                    color = if (isPhosphorVista) ZhishengCyan else ZhishengOrange,
                ) {
                    context.startActivity(Intent(context, WidgetConfigActivity::class.java).putExtra("widget_style", WidgetStyle.CLASSIC.key))
                }
                SettingsDivider()
                ActionRow(
                    label = if (isPhosphorVista) "通知栏天气" else "> 通知栏天气",
                    enabled = true,
                    color = if (isPhosphorVista) ZhishengCyan else ZhishengMint,
                ) {
                    context.startActivity(Intent(context, WidgetConfigActivity::class.java).putExtra("notification_settings", true))
                }
            }
            InlineGroupLabel("图标与屏幕")
            CardBox {
                SegmentRow(
                    "应用图标",
                    listOf("天气娘" to "character", "深色" to "dark", "浅色" to "light"),
                    appIconStyle.key,
                    hint = "深色与浅色随主题联动；天气娘始终保持，不跟随主题",
                ) { value ->
                    val selected = AppIconStyle.from(value)
                    scope.launch {
                        if (AppIconManager.apply(context, selected)) {
                            SettingsRepository.setAppIconStyle(selected)
                        }
                    }
                }
                SettingsDivider()
                ToggleRow("常亮屏幕", "看天气时不自动息屏", keepScreenOn) {
                    scope.launch { SettingsRepository.setKeepScreenOn(!keepScreenOn) }
                }
            }

            }
            if (landscapeSection == 3) {
            SectionTitle(4, "关于枳生", "ABOUT", "版本更新、官网、社区与开源信息。")
            CardBox {
                InfoRow(
                    "版本",
                    "v${com.zhisheng.weather.BuildConfig.VERSION_NAME} · 更新说明",
                    onClick = onShowWhatsNew,
                )
                SettingsDivider()
                InfoRow(
                    "检查更新",
                    availableUpdate?.let { "发现新版本 v${it.versionName} · 点此查看" }
                        ?: "自动检测更新 · 可忽略此版本或延后 3 天",
                    onClick = { showAppUpdate = true },
                    attention = availableUpdate != null,
                )
            }
            InlineGroupLabel("社区与支持")
            CardBox {
                LinkRow(
                    "赞助榜单",
                    "${SponsorBoard.size} 位支持者",
                    accent = ZhishengOrange,
                    forceSingleLine = true,
                ) { showSponsorBoard = true }
                SettingsDivider()
                LinkRow(
                    "社区贡献者名单",
                    "${CommunityContributors.size} 位贡献者",
                    accent = ZhishengMint,
                    forceSingleLine = true,
                ) { showContributors = true }
                SettingsDivider()
                LinkRow(
                    "支持作者",
                    "请杯咖啡",
                    accent = ZhishengOrange,
                    forceSingleLine = true,
                ) { showSponsor = true }
                SettingsDivider()
                LinkRow(
                    "用户交流 QQ 群",
                    CommunityQqGroup,
                    accent = ZhishengMint,
                    forceSingleLine = true,
                ) { showCommunityGroup = true }
                SettingsDivider()
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
                SettingsDivider()
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
            InlineGroupLabel("隐私与权限")
            CardBox {
                ToggleRow("设备与版本统计", "", usageStatistics, onInfo = { showStatisticsInfo = true }) {
                    scope.launch { SettingsRepository.setUsageStatistics(!usageStatistics) }
                }
                SettingsDivider()
                InfoRow("权限", "网络；位置可选；安装更新时才调用系统安装")
            }
            Spacer(Modifier.height(20.dp))
            Text(
                if (isPhosphorVista) "枳生天气" else "枳生天气 · 数据终端",
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                "数据来源：和风 / 彩云 / 小米公开接口 / 中央气象台 / 腾讯天气 / 中国天气网 / Open-Meteo / RainViewer / 枳生天气源（多源融合）",
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 12.dp),
            )
            }
            }
        }
        if (!landscapeLayout && !isPhosphorVista) {
            ClassicSettingsBar(selected = landscapeSection, onSelected = { landscapeSection = it },
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
    if (glassHeader) {
        SettingsTopBar(onBack, Modifier.align(Alignment.TopCenter)
            .glassScrollHeader(glassSource, glassOrigin,
                underlay = settingsBackground, underlayOrigin = glassOrigin,
                strength = { scrollState.value / glassBlendDistance })
            .statusBarsPadding())
    }
    if (isFloatingGlass) {
        VistaSettingsIsland(selected = landscapeSection, onSelected = { landscapeSection = it },
            backdrop = glassSource, backdropOrigin = glassOrigin,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 12.dp))
    }
    }
    }

    if (showLivingSkyPreview) LivingSkyPreviewDialog { showLivingSkyPreview = false }
    wizard?.let { kind ->
        ProviderWizard(kind = kind, onClose = { wizard = null })
    }
    if (showContributors) {
        ContributorsDialog(onClose = { showContributors = false })
    }
    if (showSponsor) {
        SponsorDialog(onClose = { showSponsor = false })
    }
    if (showSponsorBoard) {
        SponsorBoardDialog(onClose = { showSponsorBoard = false })
    }
    if (showCommunityGroup) {
        CommunityGroupDialog(onClose = { showCommunityGroup = false })
    }
    if (showAppUpdate) {
        AppUpdateDialog(
            initialInfo = availableUpdate,
            downloadOnOpen = true,
            onClose = { showAppUpdate = false },
        )
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = uiText("返回"), tint = ZhishengText)
        }
        Column {
            Text("设置", style = MaterialTheme.typography.titleMedium,
                color = if (isPhosphorVista) ZhishengTextSecondary else ZhishengOrange,
                fontWeight = if (isPhosphorVista) FontWeight.Medium else FontWeight.Bold)
            if (!isPhosphorVista) Text("SYSTEM CONFIG", style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary, letterSpacing = 1.5.sp)
        }
    }
}

/** 经典终端的底部分页条：与新主题设置岛同一职责（四页切换），保持方角与终端编号。 */
@Composable
private fun ClassicSettingsBar(
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = listOf("数据" to "数据与位置", "首页" to "首页内容", "外观" to "外观与设备", "关于" to "关于枳生")
    Row(
        modifier = modifier.fillMaxWidth()
            .background(ZhishengSurface.copy(alpha = 0.94f))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        sections.forEachIndexed { index, (short, full) ->
            val active = selected == index
            Column(
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .zhishengCompactPanel(
                        containerColor = if (active) ZhishengCard else Color.Transparent,
                        borderColor = if (active) ZhishengOrange else ZhishengCardBorder,
                    )
                    .clickable(role = Role.Tab, onClickLabel = full) { onSelected(index) }
                    .semantics { this.selected = active }
                    .padding(vertical = 7.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "%02d//".format(index + 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) ZhishengOrange else ZhishengTextTertiary,
                )
                Text(
                    short,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) ZhishengText else ZhishengTextSecondary,
                    maxLines = 1,
                )
            }
        }
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
                    .zhishengCompactPanel(
                        containerColor = if (active) ZhishengCard else Color.Transparent,
                        borderColor = if (active) ZhishengOrange else ZhishengCardBorder,
                    )
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
                    if (!isPhosphorVista) Text(
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
    SourcePref.ZHISHENG -> "自研·多源共识（配置和风/彩云后自动加入）"
    SourcePref.NMC -> "免配置·中央气象台官方预报"
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
        SourcePref.ZHISHENG -> "可用" to true
        SourcePref.NMC -> "可用" to true
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
    SourcePref.NMC -> activeSource == "NMC"
    SourcePref.OPEN_METEO -> activeSource == "OPEN-METEO"
    SourcePref.ZHISHENG -> activeSource == "ZHISHENG"
    SourcePref.AUTO -> false
}

private fun sourceName(activeSource: String?): String? = when (activeSource) {
    "QWEATHER" -> "和风天气"
    "CAIYUN" -> "彩云天气"
    "XIAOMI" -> "小米公开接口"
    "OPEN-METEO" -> "Open-Meteo"
    "NMC" -> "中央气象台"
    "ZHISHENG" -> "枳生天气源"
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
    if (isPhosphorVista) {
        Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 23.sp, lineHeight = 30.sp, color = ZhishengText, fontWeight = FontWeight.Medium)
            Text(description, fontSize = 13.sp, lineHeight = 20.sp, color = ZhishengTextSecondary)
        }
        return
    }
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
    if (isPhosphorVista) {
        Column(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = ZhishengTextSecondary)
            if (!detail.isNullOrBlank()) Text(detail, fontSize = 11.sp, lineHeight = 16.sp, color = ZhishengTextTertiary)
        }
        return
    }
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
    if (isPhosphorVista) {
        VistaSettingsCard(content)
        return
    }
    Column(
        Modifier.fillMaxWidth()
            .zhishengPanel(),
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
    if (isPhosphorVista) {
        VistaSettingSource(pref, description, selected, status, enabled, onClick)
        return
    }
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
                if (!isPhosphorVista) Text(pref.en, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, letterSpacing = 1.sp)
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
            Text(if (isPhosphorVista) "已选" else "[OK]", style = MaterialTheme.typography.labelMedium, color = ZhishengMint)
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
    compact: Boolean = false,
    singleRow: Boolean = false,
    onPick: (String) -> Unit,
) {
    if (compact && hint.isNullOrBlank()) {
        Row(
            Modifier.fillMaxWidth().padding(
                horizontal = if (isPhosphorVista) 18.dp else 16.dp,
                vertical = if (isPhosphorVista) 8.dp else 7.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                label,
                modifier = Modifier.width(52.dp),
                style = MaterialTheme.typography.titleSmall,
                fontSize = if (isPhosphorVista) 15.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                fontWeight = if (isPhosphorVista) FontWeight.Medium else FontWeight.Normal,
                color = ZhishengText,
                maxLines = 1,
            )
            SegmentOptions(options, current, true, onPick, Modifier.weight(1f))
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = if (isPhosphorVista) 18.dp else 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontSize = if (isPhosphorVista) 15.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
            fontWeight = if (isPhosphorVista) FontWeight.Medium else FontWeight.Normal, color = ZhishengText)
        // 分档选项也该有一句说明：开关行一直有，分档行原来没有，
        // 用户只能靠猜「克制」和「明显」差在哪（v0.0.9）。
        if (hint != null) {
            if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.labelSmall,
                fontSize = if (isPhosphorVista) 12.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                lineHeight = if (isPhosphorVista) 18.sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                color = ZhishengTextTertiary, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        SegmentOptions(options, current, singleRow, onPick)
    }
}

@Composable
private fun SegmentOptions(
    options: List<Pair<String, String>>,
    current: String,
    singleRow: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        if (isPhosphorVista) VistaSettingsChoices(options, current, onPick, singleRow) else Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // v0.0.4：互斥选项组语义，TalkBack 正确播报单选关系
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { (text, value) ->
                val on = current == value
                Box(
                    Modifier.weight(1f)
                        .zhishengCompactPanel(
                            selected = on,
                            containerColor = if (on) ZhishengMint.copy(alpha = 0.14f) else ZhishengSurface,
                            borderColor = if (on) ZhishengMint else ZhishengCardBorder,
                        )
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
private fun ToggleRow(label: String, hint: String, checked: Boolean, onInfo: (() -> Unit)? = null, onToggle: () -> Unit) {
    if (isPhosphorVista) {
        VistaSettingToggle(label, hint, checked, onInfo, onToggle)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            // 单一 toggleable 事件源：Switch 只负责绘制，避免父子点击同时触发。
            .toggleable(value = checked, role = Role.Switch) { onToggle() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = ZhishengText)
            if (hint.isNotBlank()) Text(hint, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
        }
        if (onInfo != null) IconButton(onClick = onInfo, modifier = Modifier.size(40.dp)) {
            com.zhisheng.weather.ui.components.PhosphorIcon(com.zhisheng.weather.R.drawable.ph_info, "查看设备与版本统计说明",
                Modifier.size(17.dp), ZhishengTextTertiary)
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
    val vista = isPhosphorVista
    val movable = order.withIndex().filterNot { !ReleaseFeatures.typhoon && it.value == HomeModule.TYPHOON }
    if (vista) Text("点箭头调整顺序", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
    movable.forEachIndexed { displayIndex, entry ->
        val index = entry.index
        val module = entry.value
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "%02d".format(displayIndex + 1),
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengOrange,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(module.cn, style = MaterialTheme.typography.titleSmall, color = ZhishengText)
                if (!isPhosphorVista) Text(module.en, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary, letterSpacing = 1.sp)
            }
            Text(
                "[↑]",
                style = MaterialTheme.typography.titleSmall,
                color = if (displayIndex > 0) ZhishengCyan else ZhishengCardBorder,
                modifier = Modifier
                    .clickable(enabled = displayIndex > 0, role = Role.Button, onClickLabel = "${module.cn}上移") {
                        onMove(index, movable[displayIndex - 1].index)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Text(
                "[↓]",
                style = MaterialTheme.typography.titleSmall,
                color = if (displayIndex < movable.lastIndex) ZhishengMint else ZhishengCardBorder,
                modifier = Modifier
                    .clickable(enabled = displayIndex < movable.lastIndex, role = Role.Button, onClickLabel = "${module.cn}下移") {
                        onMove(index, movable[displayIndex + 1].index)
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        if (displayIndex < movable.lastIndex) SettingsDivider()
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
    if (isPhosphorVista) {
        Box(modifier) { VistaSettingAction(label, enabled, color, onClick) }
        return
    }
    Row(
        modifier = modifier.fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button) { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (isPhosphorVista) settingsActionLabel(label) else label,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) color else ZhishengTextTertiary,
            fontWeight = FontWeight.Bold,
            letterSpacing = if (isPhosphorVista) 0.sp else 1.sp,
            maxLines = if (isPhosphorVista) 3 else 1,
            modifier = Modifier.weight(1f),
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        if (isPhosphorVista) {
            Spacer(Modifier.width(12.dp))
            com.zhisheng.weather.ui.components.PhosphorIcon(com.zhisheng.weather.R.drawable.ph_arrow_right,
                null, Modifier.size(18.dp), if (enabled) ZhishengTextSecondary else ZhishengTextTertiary)
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    attention: Boolean = false,
) {
    if (isPhosphorVista) {
        VistaSettingInfo(label, value, onClick, attention)
        return
    }
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
private fun LinkRow(
    label: String,
    sub: String,
    accent: Color? = null,
    forceSingleLine: Boolean = false,
    onClick: () -> Unit,
) {
    if (isPhosphorVista) {
        VistaSettingInfo(label, sub, onClick, false, accent, forceSingleLine)
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, color = accent ?: ZhishengCyan, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = accent?.copy(alpha = 0.82f) ?: ZhishengTextTertiary, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Text("↗", style = MaterialTheme.typography.titleMedium, color = accent ?: ZhishengCyan)
    }
}




@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = if (isPhosphorVista) Modifier.padding(horizontal = 18.dp) else Modifier,
        thickness = if (isPhosphorVista) 0.5.dp else 1.dp,
        color = ZhishengCardBorder.copy(alpha = if (isPhosphorVista) 0.32f else 1f),
    )
}
