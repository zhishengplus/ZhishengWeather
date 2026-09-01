/* Hallmark · pre-emit critique: P5 H5 E5 S5 R5 V4 */
/* Hallmark · component: provider setup modal · genre: atmospheric · theme: existing Zhisheng terminal
 * states: default · hover · focus · active · disabled · loading · error · success
 * contrast: pass
 */
package com.zhisheng.weather.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhisheng.weather.data.ProviderTestStage
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCard
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengMono
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengReading
import com.zhisheng.weather.ui.theme.ZhishengRed
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import kotlinx.coroutines.delay

private val EnterEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val ExitEase = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)
private const val CAIYUN_APPLICATION_MANAGE_URL = "https://platform.caiyunapp.com/application/manage"
private const val AMAP_APPLICATION_MANAGE_URL = "https://console.amap.com/dev/key/app"

@Composable
fun ProviderWizard(kind: ProviderWizardKind, onClose: () -> Unit) {
    val factory = remember(kind) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProviderSetupViewModel(kind) as T
        }
    }
    val model: ProviderSetupViewModel = viewModel(
        key = "provider-setup-${kind.name}",
        factory = factory,
    )
    val state = model.state
    val reducedMotion = rememberReducedMotion()
    val currentOnClose by rememberUpdatedState(onClose)
    var shown by remember(kind) { mutableStateOf(true) }
    var closing by remember(kind) { mutableStateOf(false) }

    fun requestClose() {
        if (!closing) {
            model.cancelVerification()
            closing = true
            shown = false
        }
    }

    LaunchedEffect(closing) {
        if (closing) {
            delay(if (reducedMotion) 120 else 220)
            model.reset(kind)
            currentOnClose()
        }
    }

    // 0.0.9-debug 修复：Activity 因配置变更重建时，弹窗组合被直接销毁，
    // 不经过 requestClose。原实现验证协程挂在 viewModelScope 上继续跑完并
    // 静默写入凭据（用户以为已放弃）。组合销毁即取消未完成的验证。
    DisposableEffect(Unit) {
        onDispose { model.cancelVerification() }
    }

    fun requestBackOrClose() {
        when (providerBackAction(state)) {
            ProviderBackAction.IGNORE -> Unit
            ProviderBackAction.PREVIOUS -> model.previous()
            ProviderBackAction.CLOSE -> requestClose()
        }
    }

    Dialog(
        onDismissRequest = ::requestBackOrClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(ZhishengBg.copy(alpha = 0.78f))
                .safeDrawingPadding()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val panelMaxHeight = minOf(maxHeight - 24.dp, 680.dp)
            val panelEnter: EnterTransition = if (reducedMotion) {
                fadeIn(tween(120, easing = EnterEase))
            } else {
                fadeIn(tween(280, easing = EnterEase)) +
                    scaleIn(tween(280, easing = EnterEase), initialScale = 0.96f)
            }
            val panelExit: ExitTransition = if (reducedMotion) {
                fadeOut(tween(120, easing = ExitEase))
            } else {
                fadeOut(tween(220, easing = ExitEase)) +
                    scaleOut(tween(220, easing = ExitEase), targetScale = 0.98f)
            }
            AnimatedVisibility(visible = shown, enter = panelEnter, exit = panelExit) {
                ProviderSetupPanel(
                    state = state,
                    model = model,
                    compact = maxWidth < 360.dp,
                    reducedMotion = reducedMotion,
                    onBack = ::requestBackOrClose,
                    onClose = ::requestClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .heightIn(max = panelMaxHeight),
                )
            }
        }
    }
}

@Composable
private fun ProviderSetupPanel(
    state: ProviderSetupUiState,
    model: ProviderSetupViewModel,
    compact: Boolean,
    reducedMotion: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val firstAction = remember { FocusRequester() }
    LaunchedEffect(state.step) { scroll.scrollTo(0) }
    // 0.0.9-debug 修复：firstAction 只在 step==0 时挂到页脚按钮上。
    // 原实现 LaunchedEffect(Unit) 无条件 80ms 后 requestFocus，若面板首组合时
    // step>=1（Activity 重建后保留的 ViewModel 停在中间步骤），请求器从未初始化，
    // requestFocus() 直接抛 IllegalStateException 崩溃。改为仅在 step==0 时请求，
    // 且 key 上 step：返回第 0 步时重新聚焦，步骤前进时协程随取消终止。
    LaunchedEffect(state.step == 0) {
        if (state.step == 0) {
            delay(80)
            firstAction.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .background(ZhishengSurface, RectangleShape)
            .border(1.dp, ZhishengCardBorder, RectangleShape)
            .imePadding(),
    ) {
        ProviderHeader(state = state, onBack = onBack, onClose = onClose)
        StepRail(state)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 20.dp),
        ) {
            // 步骤切换立即替换内容，不让上一页和下一页同时参与绘制。
            // 弹窗本体、按钮按压和结果状态仍保留动效。
            ProviderStepContent(state = state, model = model)
        }
        ProviderFooter(
            state = state,
            model = model,
            compact = compact,
            onBack = onBack,
            onClose = onClose,
            modifier = if (state.step == 0) Modifier.focusRequester(firstAction) else Modifier,
        )
    }
}

@Composable
private fun ProviderHeader(state: ProviderSetupUiState, onBack: () -> Unit, onClose: () -> Unit) {
    val (title, provider) = when (state.kind) {
        ProviderWizardKind.QWEATHER -> "接入和风天气" to "QWEATHER"
        ProviderWizardKind.CAIYUN -> "接入彩云天气" to "CAIYUN"
        ProviderWizardKind.AMAP -> "接入高德街道定位" to "AMAP GEO"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (providerBackAction(state) == ProviderBackAction.PREVIOUS) {
            TerminalButton(
                label = "←",
                onClick = onBack,
                tone = ButtonTone.NEUTRAL,
                accessibilityLabel = "返回上一步",
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = ZhishengText,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "$provider · PROVIDER LINK",
                style = MaterialTheme.typography.labelSmall,
                color = ZhishengTextTertiary,
                letterSpacing = 1.2.sp,
            )
        }
        Text(
            "%02d/%02d".format(state.step + 1, state.lastStep + 1),
            style = MaterialTheme.typography.labelMedium,
            color = ZhishengOrange,
        )
        Spacer(Modifier.width(8.dp))
        TerminalButton(
            label = "×",
            onClick = onClose,
            tone = ButtonTone.NEUTRAL,
            accessibilityLabel = "关闭接入向导",
            modifier = Modifier.size(52.dp),
        )
    }
}

@Composable
private fun StepRail(state: ProviderSetupUiState) {
    val labels = when (state.kind) {
        ProviderWizardKind.QWEATHER ->
            listOf("选择路线", "创建项目", "获取凭据", "获取 Host", "真实验证", "接入完成")
        ProviderWizardKind.CAIYUN ->
            listOf("准备", "进入应用管理", "打开访问控制", "复制并验证", "接入完成")
        ProviderWizardKind.AMAP ->
            listOf("准备", "创建 Web 应用", "复制并验证", "接入完成")
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(labels.size) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(if (index <= state.step) ZhishengCyan else ZhishengCardBorder),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("CURRENT TASK", color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(
                labels.getOrElse(state.step) { labels.last() },
                color = ZhishengCyan,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun ProviderStepContent(state: ProviderSetupUiState, model: ProviderSetupViewModel) {
    when (state.kind) {
        ProviderWizardKind.QWEATHER -> QweatherStep(state, model)
        ProviderWizardKind.CAIYUN -> CaiyunStep(state, model)
        ProviderWizardKind.AMAP -> AmapStep(state, model)
    }
}

@Composable
private fun QweatherStep(state: ProviderSetupUiState, model: ProviderSetupViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    when (state.step) {
        0 -> {
            StepIntro(
                title = "先选一条接入路线",
                body = "第一次配置请直接用“快速 API KEY”。全程只要复制两项：API KEY 和 API Host；高级 JWT 留给熟悉密钥管理的用户。",
            )
            TerminalCommand(
                if (state.authMode == QweatherAuthMode.API_KEY) {
                    "provider add qweather --quick"
                } else {
                    "provider add qweather --auth jwt"
                },
            )
            AuthChoice(
                title = "快速接入 · API KEY",
                subtitle = "推荐新手 · 只复制 Key 与 Host · 约 3 分钟",
                selected = state.authMode == QweatherAuthMode.API_KEY,
                onClick = { model.setAuthMode(QweatherAuthMode.API_KEY) },
            )
            Spacer(Modifier.height(8.dp))
            AuthChoice(
                title = "高级接入 · JWT",
                subtitle = "更安全 · 需要公钥、项目 ID 与凭据 ID",
                selected = state.authMode == QweatherAuthMode.JWT,
                onClick = { model.setAuthMode(QweatherAuthMode.JWT) },
            )
            Spacer(Modifier.height(12.dp))
            FactRow("存储", "no_backup 私密目录 · 不进入 APK 和系统备份")
        }
        1 -> {
            StepIntro(
                title = "登录后先创建项目",
                body = "网页打开后，你会先看到项目列表。本步不要找 API：先创建并打开一个名为“枳生天气”的项目。已有项目可以直接打开。",
            )
            ProviderLink("打开和风项目管理", "登录 → 左侧“项目管理” → 右上角“创建项目”") {
                openUrl(context, "https://console.qweather.com/project")
            }
            InstructionList(
                "在项目列表右上角点“创建项目”",
                "项目名称填写“枳生天气”，然后点“保存”",
                "保存后点击项目名称“枳生天气”，进入项目详情",
            )
            FactRow("你应该看到", "项目详情页中有“项目 ID”和“凭据”两个区域")
        }
        2 -> {
            StepIntro(
                title = if (state.authMode == QweatherAuthMode.JWT) "创建 JWT 凭据" else "创建并复制 API KEY",
                body = if (state.authMode == QweatherAuthMode.JWT) {
                    "在项目的“凭据”区域添加 JSON Web Token，把下方公钥完整粘贴进去；保存后复制项目 ID 和凭据 ID。"
                } else {
                    "你现在应该位于“枳生天气”的项目详情页。API KEY 不在账户首页，而是在这个项目的“凭据”区域里创建。"
                },
            )
            ProviderLink("返回和风项目管理", "点击“枳生天气” → 凭据 → 添加凭据") {
                openUrl(context, "https://console.qweather.com/project")
            }
            if (state.authMode == QweatherAuthMode.JWT) {
                InstructionList(
                    "点击项目名称“枳生天气”，进入项目详情",
                    "在“凭据”区域右侧点“添加凭据”",
                    "认证方式选择“JSON Web Token”，粘贴下方完整公钥并保存",
                    "复制项目 ID 与新凭据的 ID，返回这里填写",
                )
                val keys = state.keys
                if (keys == null) {
                    TerminalButton(label = "生成 Ed25519 密钥", onClick = model::regenerateKeys)
                } else {
                    PublicKeyBlock(keys.publicPem)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CopyPublicKeyButton(text = keys.publicPem, modifier = Modifier.weight(1f))
                        TerminalButton(
                            label = "重新生成",
                            onClick = model::regenerateKeys,
                            tone = ButtonTone.NEUTRAL,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TerminalField(
                    label = "项目 ID",
                    value = state.projectId,
                    onValueChange = model::setProjectId,
                    helper = "项目详情中显示的 Project ID",
                    error = state.fieldErrors[ProviderField.PROJECT_ID],
                    enabled = !state.testing,
                )
                TerminalField(
                    label = "凭据 ID · kid",
                    value = state.kid,
                    onValueChange = model::setKid,
                    helper = "JWT 凭据保存后显示的 Credential ID",
                    error = state.fieldErrors[ProviderField.KID],
                    enabled = !state.testing,
                )
            } else {
                InstructionList(
                    "点击项目名称“枳生天气”，进入项目详情",
                    "在“凭据”区域右侧点“添加凭据”",
                    "认证方式选择“API KEY”，名称填写“枳生天气”，然后保存",
                    "打开刚创建的凭据，复制完整 API KEY，返回这里粘贴",
                )
                TerminalField(
                    label = "API KEY",
                    value = state.apiKey,
                    onValueChange = model::setApiKey,
                    helper = "只在验证请求与本机私密存储中使用",
                    error = state.fieldErrors[ProviderField.API_KEY],
                    sensitive = true,
                    keyboardType = KeyboardType.Password,
                    enabled = !state.testing,
                )
                ClipboardPasteButton("从剪贴板粘贴 API KEY", model::setApiKey)
            }
            FactRow("完成标志", if (state.authMode == QweatherAuthMode.JWT) "项目 ID 与凭据 ID 都已填入" else "上方输入框已经显示一串完整 Key")
        }
        3 -> {
            StepIntro(
                title = "复制账户专属 API Host",
                body = "API Host 不在项目详情里。打开控制台“设置”，直接找到“API Host”这一项并复制域名。",
            )
            ProviderLink("打开控制台设置", "设置 → API Host → 复制") {
                openUrl(context, "https://console.qweather.com/setting")
            }
            InstructionList(
                "打开“设置”页面后找到“API Host”",
                "复制类似 abc123.qweatherapi.com 的完整域名",
                "不要复制 /v7/weather 等接口路径",
            )
            TerminalField(
                label = "API Host",
                value = state.host,
                onValueChange = model::setHost,
                helper = "例如 abc123.qweatherapi.com；https:// 可省略",
                error = state.fieldErrors[ProviderField.HOST],
                keyboardType = KeyboardType.Uri,
            )
            ClipboardPasteButton("从剪贴板粘贴 API Host", model::setHost)
            FactRow("完成标志", "输入框里只有 qweatherapi.com 域名，没有 /v7 等路径")
        }
        4 -> {
            StepIntro(
                title = "最后一步：真实验证",
                body = "枳生天气会请求一次北京城市数据。成功才覆盖本机配置；失败会指出 Host 或凭据哪一项需要改。",
            )
            TerminalCommand("provider test qweather --city beijing")
            FactRow("认证", if (state.authMode == QweatherAuthMode.API_KEY) "快速 API KEY" else "高级 JWT")
            FactRow("Host", state.host.ifBlank { "尚未填写，请返回上一步" })
            ConnectionTrace(state)
            ResultBanner(state)
        }
        5 -> SuccessStep(state)
    }
}

@Composable
private fun CaiyunStep(state: ProviderSetupUiState, model: ProviderSetupViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    when (state.step) {
        0 -> {
            StepIntro(
                title = "建立彩云 v2.6 兼容链路",
                body = "使用 Token 接入彩云稳定版 v2.6。验证会真实请求一次北京实况，成功后才保存；官方新接入更推荐 App Key 与 App Secret。",
            )
            TerminalCommand("provider add caiyun --api v2.6 --auth token")
            FactRow("调用", "Token 只发往 api.caiyunapp.com")
            FactRow("标注", "应用内保留“数据来自彩云天气”")
            FactRow("存储", "凭据仅在本机 no_backup 私密目录")
        }
        1 -> {
            StepIntro(
                title = "登录彩云开放平台",
                body = "注册或登录开发者账户。账户审核、免费额度和套餐以开放平台当前页面为准。",
            )
            ProviderLink("打开彩云开放平台", "登录后进入应用管理") {
                openUrl(context, CAIYUN_APPLICATION_MANAGE_URL)
            }
            ProviderLink("查看官方开发文档", "v2.6 · 认证与鉴权") {
                openUrl(context, "https://docs.caiyunapp.com/weather-api/v2/v2.6/auth.html")
            }
        }
        2 -> {
            StepIntro(
                title = "完成认证并创建天气应用",
                body = "按平台要求完成个人或企业开发者认证，然后在应用管理中新建天气应用。审核状态由彩云开放平台决定。",
            )
            InstructionList(
                "提交开发者认证资料",
                "在应用管理中新建天气应用",
                "在访问控制中生成或查看 Token",
                "发布时按平台要求标注天气数据来源",
            )
            ProviderLink("返回开放平台", "认证与应用管理") {
                openUrl(context, CAIYUN_APPLICATION_MANAGE_URL)
            }
        }
        3 -> {
            StepIntro(
                title = "验证 Token",
                body = "粘贴完整 Token。验证请求完成前，当前已保存的彩云配置不会被替换。",
            )
            TerminalField(
                label = "彩云 Token",
                value = state.caiyunToken,
                onValueChange = model::setCaiyunToken,
                helper = "用于 v2.6 路径鉴权，仅发送到彩云官方 API",
                error = state.fieldErrors[ProviderField.CAIYUN_TOKEN],
                sensitive = true,
                keyboardType = KeyboardType.Password,
                enabled = !state.testing,
            )
            ClipboardPasteButton("从剪贴板粘贴彩云 Token", model::setCaiyunToken)
            ConnectionTrace(state)
            ResultBanner(state)
        }
        4 -> SuccessStep(state)
    }
}

@Composable
private fun AmapStep(state: ProviderSetupUiState, model: ProviderSetupViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    when (state.step) {
        0 -> {
            StepIntro(
                title = "增强国内街道名称",
                body = "高德只负责把精确坐标转换成街道名称，不接管系统定位，也不改变天气数据源。未配置、额度不足或请求失败时会自动退回系统识别。",
            )
            TerminalCommand("location enhance amap --web-service")
            FactRow("调用时机", "仅在开启“街道级精确定位”并实际定位时请求")
            FactRow("请求次数", "GPS 坐标转换 + 逆地理编码；不会随天气刷新重复调用")
            FactRow("存储", "Web 服务 Key 仅保存在本机 no_backup 私密目录")
        }
        1 -> {
            StepIntro(
                title = "创建 Web 服务类型 Key",
                body = "在高德开放平台创建应用，再添加“Web 服务”类型的 Key。不要填写 Android SDK Key：两种 Key 的用途和校验方式不同。",
            )
            ProviderLink("打开高德应用管理", "控制台 → 我的应用 → 创建新应用") {
                openUrl(context, AMAP_APPLICATION_MANAGE_URL)
            }
            InstructionList(
                "创建或打开名为“枳生天气”的应用",
                "添加 Key，服务平台选择“Web 服务”",
                "复制生成的 Key，回到下一步粘贴",
                "免费额度和超额计费以高德控制台当前规则为准",
            )
        }
        2 -> {
            StepIntro(
                title = "验证高德 Web 服务 Key",
                body = "验证会真实请求一次北京测试点的逆地理编码。成功后才替换本机旧配置，Key 不会写入 APK 或日志。",
            )
            TerminalField(
                label = "高德 Web 服务 API Key",
                value = state.amapKey,
                onValueChange = model::setAmapKey,
                helper = "只接受应用中“Web 服务”类型的 Key",
                error = state.fieldErrors[ProviderField.AMAP_KEY],
                sensitive = true,
                keyboardType = KeyboardType.Password,
                enabled = !state.testing,
            )
            ClipboardPasteButton("从剪贴板粘贴高德 Key", model::setAmapKey)
            ConnectionTrace(state)
            ResultBanner(state)
        }
        3 -> SuccessStep(state)
    }
}

@Composable
private fun StepIntro(title: String, body: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = ZhishengReading),
        color = ZhishengText,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = ZhishengReading, lineHeight = 22.sp),
        color = ZhishengTextSecondary,
    )
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun TerminalCommand(command: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ZhishengCard)
            .border(1.dp, ZhishengCardBorder)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("$", color = ZhishengMint, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            command,
            color = ZhishengText,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = ZhishengMono),
        )
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("[$label]", color = ZhishengOrange, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = ZhishengTextSecondary,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = ZhishengReading, lineHeight = 19.sp),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun InstructionList(vararg lines: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        lines.forEachIndexed { index, line ->
            Row(verticalAlignment = Alignment.Top) {
                Text("%02d".format(index + 1), color = ZhishengOrange, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(12.dp))
                Text(
                    line,
                    color = ZhishengTextSecondary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = ZhishengReading),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ProviderLink(label: String, detail: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .defaultMinSize(minHeight = 52.dp)
            .scale(if (pressed) 0.99f else 1f)
            .border(2.dp, if (focused) ZhishengCyan else Color.Transparent)
            .padding(2.dp)
            .background(if (hovered) ZhishengCyan.copy(alpha = 0.08f) else ZhishengCard)
            .border(1.dp, ZhishengCardBorder)
            .hoverable(interaction)
            .focusable(interactionSource = interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = ZhishengCyan, style = MaterialTheme.typography.titleSmall)
            Text(detail, color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall)
        }
        Text("↗", color = ZhishengCyan, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AuthChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .scale(if (pressed) 0.99f else 1f)
            .border(2.dp, if (focused) ZhishengCyan else Color.Transparent)
            .padding(2.dp)
            .background(
                when {
                    selected -> ZhishengMint.copy(alpha = 0.09f)
                    hovered -> ZhishengCyan.copy(alpha = 0.08f)
                    else -> ZhishengCard
                },
            )
            .border(1.dp, if (selected) ZhishengMint else ZhishengCardBorder)
            .hoverable(interaction)
            .focusable(interactionSource = interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) ZhishengMint else ZhishengText, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                color = ZhishengTextTertiary,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = ZhishengReading),
            )
        }
        Text(if (selected) "[●]" else "[ ]", color = if (selected) ZhishengMint else ZhishengTextTertiary)
    }
}

@Composable
private fun PublicKeyBlock(text: String) {
    Text(
        text,
        color = ZhishengTextSecondary,
        style = MaterialTheme.typography.labelSmall.copy(fontFamily = ZhishengMono, lineHeight = 16.sp),
        modifier = Modifier
            .fillMaxWidth()
            .background(ZhishengCard)
            .border(1.dp, ZhishengCardBorder)
            .padding(12.dp),
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun CopyPublicKeyButton(text: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var copied by remember(text) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_500)
            copied = false
        }
    }
    TerminalButton(
        label = if (copied) "已复制" else "复制公钥",
        onClick = {
            copyToClipboard(context, "qweather-public-key", text)
            copied = true
        },
        tone = if (copied) ButtonTone.SUCCESS else ButtonTone.PRIMARY,
        modifier = modifier,
    )
}

@Composable
private fun ClipboardPasteButton(label: String, onPaste: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var feedback by remember { mutableStateOf<ClipboardFeedback?>(null) }
    LaunchedEffect(feedback) {
        if (feedback != null) {
            delay(2_500)
            feedback = null
        }
    }
    TerminalButton(
        label = when (feedback) {
            ClipboardFeedback.PASTED -> "已从剪贴板粘贴"
            ClipboardFeedback.EMPTY -> "剪贴板没有可用文字"
            null -> label
        },
        onClick = {
            val text = readClipboardText(context)
            if (text == null) {
                feedback = ClipboardFeedback.EMPTY
            } else {
                onPaste(text)
                feedback = ClipboardFeedback.PASTED
            }
        },
        tone = when (feedback) {
            ClipboardFeedback.PASTED -> ButtonTone.SUCCESS
            ClipboardFeedback.EMPTY -> ButtonTone.ERROR
            null -> ButtonTone.NEUTRAL
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
}

private enum class ClipboardFeedback { PASTED, EMPTY }

@Composable
internal fun TerminalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    helper: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    sensitive: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var reveal by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val outline = when {
        error != null -> ZhishengRed
        focused -> ZhishengCyan
        else -> Color.Transparent
    }
    val border = if (error != null) ZhishengRed else ZhishengCardBorder

    Column(modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(label, color = ZhishengTextSecondary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            interactionSource = interaction,
            visualTransformation = if (sensitive && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(ZhishengMint),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = ZhishengText, fontFamily = ZhishengMono),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .alpha(if (enabled) 1f else 0.55f)
                .border(2.dp, outline)
                .padding(2.dp)
                .background(ZhishengCard)
                .border(1.dp, border)
                .semantics { if (error != null) this.error(error) },
            decorationBox = { inner ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) { inner() }
                    if (sensitive) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable(role = Role.Button) { reveal = !reveal },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (reveal) "隐藏" else "显示",
                                color = ZhishengCyan,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            },
        )
        Text(
            error ?: helper,
            color = if (error != null) ZhishengRed else ZhishengTextTertiary,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = ZhishengReading),
            modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp).padding(top = 4.dp),
        )
    }
}

@Composable
private fun ConnectionTrace(state: ProviderSetupUiState) {
    if (state.status == ProviderSetupStatus.IDLE && state.result == null) return
    val stages = if (state.kind == ProviderWizardKind.QWEATHER && state.authMode == QweatherAuthMode.JWT) {
        ProviderTestStage.entries
    } else {
        ProviderTestStage.entries.filterNot { it == ProviderTestStage.SIGN }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(ZhishengCard)
            .border(1.dp, ZhishengCardBorder)
            .padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("CONNECTION TRACE", color = ZhishengTextTertiary, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
        stages.forEach { stage ->
            val marker: String
            val color: Color
            when {
                stage in state.completedStages -> {
                    marker = "[✓]"
                    color = ZhishengMint
                }
                stage == state.activeStage && state.status == ProviderSetupStatus.ERROR -> {
                    marker = "[!]"
                    color = ZhishengRed
                }
                stage == state.activeStage && state.testing -> {
                    marker = "[..]"
                    color = ZhishengCyan
                }
                else -> {
                    marker = "[ ]"
                    color = ZhishengTextTertiary
                }
            }
            Row {
                Text(marker, color = color, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(12.dp))
                Text(stage.label, color = color, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ResultBanner(state: ProviderSetupUiState) {
    val result = state.result ?: return
    val success = result.ok
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .border(1.dp, if (success) ZhishengMint else ZhishengRed)
            .padding(12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            (if (success) "[✓] " else "[!] ") + result.title,
            color = if (success) ZhishengMint else ZhishengRed,
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            result.detail,
            color = ZhishengTextSecondary,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = ZhishengReading),
        )
    }
}

@Composable
private fun SuccessStep(state: ProviderSetupUiState) {
    val (provider, command, body) = when (state.kind) {
        ProviderWizardKind.QWEATHER -> Triple(
            "和风天气", "qweather",
            "连接验证和本机保存都已完成。关闭弹窗后，可以在天气来源中锁定和风天气。",
        )
        ProviderWizardKind.CAIYUN -> Triple(
            "彩云天气", "caiyun",
            "连接验证和本机保存都已完成。关闭弹窗后，可以在天气来源中锁定彩云天气。",
        )
        ProviderWizardKind.AMAP -> Triple(
            "高德街道定位", "amap-geo",
            "连接验证和本机保存都已完成。开启街道级精确定位后，高德会增强国内街道名称；失败时仍会自动回退。",
        )
    }
    StepIntro(
        title = "$provider 已接入",
        body = body,
    )
    TerminalCommand("provider status $command --ready")
    ConnectionTrace(state)
    ResultBanner(state)
}

@Composable
private fun ProviderFooter(
    state: ProviderSetupUiState,
    model: ProviderSetupViewModel,
    compact: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finalInput = (state.kind == ProviderWizardKind.QWEATHER && state.step == 4) ||
        (state.kind == ProviderWizardKind.CAIYUN && state.step == 3) ||
        (state.kind == ProviderWizardKind.AMAP && state.step == 2)
    val finished = state.status == ProviderSetupStatus.SUCCESS || state.step == state.lastStep
    val primaryLabel = when {
        finished -> "完成"
        finalInput -> "开始真实验证并保存"
        state.step == 0 -> when {
            state.kind == ProviderWizardKind.QWEATHER && state.authMode == QweatherAuthMode.API_KEY ->
                "使用 API KEY 快速接入"
            state.kind == ProviderWizardKind.CAIYUN -> "开始接入"
            state.kind == ProviderWizardKind.AMAP -> "开始接入"
            else -> "开始高级配置"
        }
        state.kind == ProviderWizardKind.QWEATHER && state.step == 1 -> "项目已打开，去创建凭据"
        state.kind == ProviderWizardKind.QWEATHER && state.step == 2 -> if (
            state.authMode == QweatherAuthMode.API_KEY
        ) "Key 已粘贴，去获取 Host" else "JWT 凭据已填好，去获取 Host"
        state.kind == ProviderWizardKind.QWEATHER && state.step == 3 -> "Host 已粘贴，进入验证"
        state.kind == ProviderWizardKind.CAIYUN && state.step == 1 -> "已登录，去创建天气应用"
        state.kind == ProviderWizardKind.CAIYUN && state.step == 2 -> "应用已创建，去复制 Token"
        state.kind == ProviderWizardKind.AMAP && state.step == 1 -> "Web 服务应用已创建，去粘贴 Key"
        else -> "进入下一步"
    }
    val primaryAction = {
        when {
            finished -> onClose()
            finalInput -> model.testAndSave()
            else -> model.next()
        }
    }

    val primaryTone = when (state.status) {
        ProviderSetupStatus.ERROR -> ButtonTone.ERROR
        ProviderSetupStatus.SUCCESS -> ButtonTone.SUCCESS
        else -> ButtonTone.PRIMARY
    }

    if (compact) {
        Column(modifier = Modifier.fillMaxWidth().border(1.dp, ZhishengCardBorder).padding(12.dp)) {
            TerminalButton(
                label = if (state.step == 0) "取消" else "返回",
                onClick = onBack,
                enabled = !state.testing,
                tone = ButtonTone.NEUTRAL,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            TerminalButton(
                label = primaryLabel,
                onClick = primaryAction,
                enabled = !state.testing,
                loading = state.testing,
                tone = primaryTone,
                modifier = modifier.fillMaxWidth(),
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().border(1.dp, ZhishengCardBorder).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TerminalButton(
                label = if (state.step == 0) "取消" else "返回",
                onClick = onBack,
                enabled = !state.testing,
                tone = ButtonTone.NEUTRAL,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TerminalButton(
                label = primaryLabel,
                onClick = primaryAction,
                enabled = !state.testing,
                loading = state.testing,
                tone = primaryTone,
                modifier = modifier.weight(1f),
            )
        }
    }
}

internal enum class ButtonTone { PRIMARY, NEUTRAL, ERROR, SUCCESS }

internal enum class ButtonVisualState {
    DEFAULT, HOVER, FOCUS, ACTIVE, DISABLED, LOADING, ERROR, SUCCESS,
}

@Composable
internal fun TerminalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    tone: ButtonTone = ButtonTone.PRIMARY,
    previewState: ButtonVisualState? = null,
    accessibilityLabel: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val visual = previewState ?: when {
        !enabled -> ButtonVisualState.DISABLED
        loading -> ButtonVisualState.LOADING
        tone == ButtonTone.ERROR -> ButtonVisualState.ERROR
        tone == ButtonTone.SUCCESS -> ButtonVisualState.SUCCESS
        pressed -> ButtonVisualState.ACTIVE
        focused -> ButtonVisualState.FOCUS
        hovered -> ButtonVisualState.HOVER
        else -> ButtonVisualState.DEFAULT
    }
    val signal = when (visual) {
        ButtonVisualState.ERROR -> ZhishengRed
        ButtonVisualState.SUCCESS -> ZhishengMint
        ButtonVisualState.DISABLED -> ZhishengTextTertiary
        else -> when (tone) {
            ButtonTone.PRIMARY -> ZhishengCyan
            ButtonTone.NEUTRAL -> ZhishengTextSecondary
            ButtonTone.ERROR -> ZhishengRed
            ButtonTone.SUCCESS -> ZhishengMint
        }
    }
    val outer = if (visual == ButtonVisualState.FOCUS) signal else Color.Transparent
    val background = when (visual) {
        ButtonVisualState.HOVER, ButtonVisualState.ACTIVE, ButtonVisualState.LOADING -> signal.copy(alpha = 0.12f)
        ButtonVisualState.ERROR, ButtonVisualState.SUCCESS -> signal.copy(alpha = 0.09f)
        else -> ZhishengCard
    }
    val displayLabel = when (visual) {
        ButtonVisualState.LOADING -> "[..] 正在验证"
        ButtonVisualState.ERROR -> "[!] $label"
        ButtonVisualState.SUCCESS -> "[✓] $label"
        else -> label
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .scale(if (visual == ButtonVisualState.ACTIVE) 0.99f else 1f)
            .border(2.dp, outer)
            .padding(2.dp)
            .background(background)
            .border(1.dp, signal)
            .hoverable(interaction)
            .focusable(enabled, interaction)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                if (accessibilityLabel != null) contentDescription = accessibilityLabel
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            displayLabel,
            color = signal,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun readClipboardText(context: Context): String? = runCatching {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}.getOrNull()
