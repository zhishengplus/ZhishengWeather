package com.zhisheng.weather.ui.badge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import android.animation.ValueAnimator
import android.os.Build
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhisheng.weather.BuildConfig
import com.zhisheng.weather.data.badge.*
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONObject

/** Entering and foreground refresh retry an eligible first claim using the same durable wallet. */
@Composable
internal fun MyBadgesContent(onBack: () -> Unit, hapticsEnabled: Boolean = true, vm: BadgeViewModel = viewModel(),
    celebrationGrantId: String? = null, onAwardPresented: (String) -> Unit = {}) {
    val state by vm.state.collectAsState()
    val modelStates by vm.models.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    var backupDialog by remember { mutableStateOf(false) }
    var pendingBackup by remember { mutableStateOf<BadgeRecoveryBackup?>(null) }
    var backupWriting by remember { mutableStateOf(false) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val backup = pendingBackup; pendingBackup = null
        if (uri != null) {
            if (backup == null) vm.backupSaveFailed()
            else scope.launch {
                backupWriting = true
                try {
                    withContext(Dispatchers.IO) { writeBadgeRecoveryBackup(backup) { context.contentResolver.openOutputStream(uri, "wt") } }
                    vm.backupSaved(backup)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { vm.backupSaveFailed() }
                finally { backupWriting = false }
            }
        }
    }
    var recover by remember { mutableStateOf(false) }
    var recovery by remember { mutableStateOf<String?>(null) }
    var recoveryGrant by remember { mutableStateOf<BadgeGrant?>(null) }
    var selectRecovery by remember { mutableStateOf(false) }
    var transferOpen by remember { mutableStateOf(false) }
    var resumeTransferRegistration by remember { mutableStateOf(false) }
    var registrationAction by remember { mutableStateOf<String?>(null) }
    var profileDetails by remember { mutableStateOf(false) }
    var redeemOpen by remember { mutableStateOf(false) }
    var cabinetInspection by remember { mutableStateOf<BadgeGrant?>(null) }
    var unavailableGrant by remember { mutableStateOf<BadgeGrant?>(null) }
    var activeAward by rememberSaveable { mutableStateOf<String?>(null) }
    var initialAwardConsumed by rememberSaveable { mutableStateOf(false) }
    var selectingFirstId by remember { mutableStateOf(false) }
    var editingFirstId by remember { mutableStateOf<BadgeGrant?>(null) }
    LaunchedEffect(state.reward, state.collection, celebrationGrantId, activeAward) {
        if (activeAward != null && state.collection.none { it.grantId == activeAward }) {
            activeAward = null
            cabinetInspection = null
        }
        if (activeAward == null) {
            val candidate = state.reward ?: celebrationGrantId?.takeUnless { initialAwardConsumed }
            if (state.collection.any { it.grantId == candidate }) activeAward = candidate
        }
    }
    BackHandler(enabled = cabinetInspection != null) { cabinetInspection = null }
    fun inspect(grant: BadgeGrant) {
        if (grant.protocol == 2 || grant.badgeId in setOf(FIRST_LIGHT_BADGE,"clear-sky-crown","together-with-you")) { cabinetInspection = grant;vm.prepareModel(grant.grantId) }
        else unavailableGrant = grant
    }
    LaunchedEffect(Unit) { vm.open() }
    LaunchedEffect(owner, celebrationGrantId) {
        if (celebrationGrantId == null) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { delay(30_000); vm.syncOwnership() }
        }
    }
    LaunchedEffect(state.profile) { if (state.profile != null && resumeTransferRegistration) { resumeTransferRegistration = false; transferOpen = true } }
    LaunchedEffect(state.collection) { cabinetInspection?.let { selected -> if (state.collection.none { it.grantId == selected.grantId }) cabinetInspection = null } }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) vm.stop()
            if (event == Lifecycle.Event.ON_RESUME) { vm.open();cabinetInspection?.let { vm.prepareModel(it.grantId) } }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); vm.stop(); recovery = null }
    }
    val badgeMenu: @Composable ColumnScope.(close: () -> Unit) -> Unit = { close ->
            if (state.collection.none { it.badgeId == FIRST_LIGHT_BADGE })
                BadgeMenuItem(text = { Text("领取初光") }, enabled = !state.busy,
                    onClick = { close(); vm.retryFirstLight() })
            if (state.collection.any { it.badgeId in OWNER_EDITABLE_BADGES })
                BadgeMenuItem(text = { Text("修改自定义 ID") }, enabled = !state.busy, onClick = {
                    close()
                    val target = cabinetInspection?.takeIf { it.badgeId in OWNER_EDITABLE_BADGES }
                        ?: state.collection.filter { it.badgeId in OWNER_EDITABLE_BADGES }.singleOrNull()
                    if (target != null) editingFirstId = target else selectingFirstId = true
                })
            BadgeMenuItem(text = { Text("输入赠送码") }, enabled = !state.busy,
                onClick = { close(); redeemOpen = true })
            BadgeMenuItem(text = { Text("刷新徽章") }, enabled = !state.busy, onClick = {
                close()
                if (state.collection.isEmpty()) vm.retryFirstLight() else {
                    vm.open(retryFailedModels = true)
                    cabinetInspection?.let { vm.prepareModel(it.grantId, retry = true) }
                }
            })
            BadgeMenuItem(text = { Text("转入一枚徽章") }, enabled = !state.busy, onClick = { close(); transferOpen = true })
            if (state.collection.isNotEmpty())
                BadgeMenuItem(text = { Text("一键保存恢复码") }, enabled = !state.busy && !backupWriting && pendingBackup == null,
                    onClick = { close(); backupDialog = true })
            if (state.collection.isNotEmpty())
                BadgeMenuItem(text = { Text("保存单枚恢复码") }, enabled = !state.busy, onClick = {
                    close()
                    val selected = cabinetInspection ?: state.collection.singleOrNull()
                    if (selected == null) selectRecovery = true
                    else vm.instanceRecoveryCode(selected.grantId) { recoveryGrant = selected; recovery = it }
                })
            if (state.profile != null)
                BadgeMenuItem(text = { Text("本机徽章档案") }, enabled = !state.busy, onClick = { close(); profileDetails = true })
            else BadgeMenuItem(text = { Text("登记本机徽章档案") }, enabled = !state.busy,
                onClick = { close(); registrationAction = "register" })
            BadgeMenuItem(text = { Text("旧版档案恢复") }, enabled = !state.busy, onClick = { close(); recover = true })
    }
    BadgeCabinetContent(state, onBack, { inspect(it) }, hapticsEnabled,
        inspection = cabinetInspection, onReturnToCabinet = { cabinetInspection = null }, models = modelStates,
        onRetryModel = { vm.prepareModel(it, true) }, onPrepareModel = { vm.prepareModel(it) },
        onMore = { vm.collectionPage(more = true) },
        awardGrantId = activeAward, onAwardPresented = { id -> vm.automaticAwardPresented(id); onAwardPresented(id) },
        onAwardCollected = {
            activeAward?.let { id -> vm.collectAward(id) {
                initialAwardConsumed = true; activeAward = null; cabinetInspection = null
                onAwardPresented(id)
            } }
        }, menu = badgeMenu)
        if (selectingFirstId) BadgeDialog(onDismissRequest = { selectingFirstId = false }, title = { Text("选择一枚收藏") },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                state.collection.filter { it.badgeId in OWNER_EDITABLE_BADGES }.forEach { item ->
                    BadgeAction(onClick = { selectingFirstId = false; editingFirstId = item }) { Text(badgeDisplayName(item) + " · " + badgeSerialText(item.serial) + "  " + item.engravingPublicId.orEmpty()) }
                }
            } }, confirmButton = { BadgeAction(onClick = { selectingFirstId = false }) { Text("取消") } })
        editingFirstId?.let { item ->
            var value by remember(item.grantId) { mutableStateOf(item.engravingPublicId.orEmpty()) }
            BadgeDialog(onDismissRequest = { if (!state.busy) editingFirstId = null }, title = { Text(badgeDisplayName(item) + " · 自定义 ID") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(badgeSerialText(item.serial))
                    BadgeIdField(value, { if (it.length <= 32) value = it }, label = "你的 ID", modifier = Modifier.fillMaxWidth())
                    Text(if (item.badgeId == "clear-sky-ring") "会刻在戒指内壁，并同步到后台。当前持有人和管理员可以修改，转赠时保留。" else "会显示在徽章上，并同步到后台。当前持有人和管理员可以修改，转赠时保留。", style = MaterialTheme.typography.bodySmall)
                    if (state.messageIsError) Text(state.message.orEmpty(), color = ZhishengTextSecondary)
                } }, confirmButton = { BadgeAction(enabled = !state.busy && value.isNotBlank(), onClick = {
                    vm.updateFirstLightId(item.grantId, value) { editingFirstId = null }
                }) { Text(if (state.busy) "正在保存" else "保存 ID") } },
                dismissButton = { BadgeAction(enabled = !state.busy, onClick = { editingFirstId = null }) { Text("取消") } })
        }
        if (redeemOpen) {
            var code by remember { mutableStateOf("") }
            BadgeDialog(onDismissRequest = { redeemOpen = false; code = "" },
                properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
                title = { Text("兑换管理员赠送徽章") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("赠送码仅兑换指定徽章及编号，不是恢复码。请先登记或恢复自己的档案；兑换需要联网。")
                    if (state.profile == null) BadgeAction(onClick = { redeemOpen = false; code = ""; registrationAction = "register" }) { Text("先登记本机档案") }
                    BadgeCodeField(code, { if (it.length <= 80) code = it }, label = "一次性赠送码 ZG1.…", modifier = Modifier.fillMaxWidth())
                } },
                confirmButton = { BadgeAction(enabled = !state.busy && state.profile != null && code.isNotBlank(), onClick = {
                    val value = code; code = ""; redeemOpen = false; vm.redeem(value)
                }) { Text("兑换到当前档案") } },
                dismissButton = { BadgeAction(onClick = { code = ""; redeemOpen = false }) { Text("取消") } })
        }
        unavailableGrant?.let { item -> BadgeDialog(onDismissRequest = { unavailableGrant = null }, title = { Text(badgeDisplayName(item)) },
            text = { Text("${badgeSerialText(item.serial)}\n唯一实例 ${item.grantId}\n此类型专属模型尚未提供，不使用其他徽章替代。") },
            confirmButton = { BadgeAction(onClick = { unavailableGrant = null }) { Text("关闭") } }) }
        registrationAction?.let { action ->
            BadgeDialog(onDismissRequest = { registrationAction = null }, title = { Text("登记本机徽章档案") },
                text = { Text("登记后可保存和恢复徽章。会上传随机档案及安装标识、设备型号和用于防止重复领取的设备标识摘要；已有定位授权且取得位置时附带最近位置，没有定位也可使用。\n\n已有档案请使用恢复码。登记本身不会发放徽章。") },
                confirmButton = { BadgeAction(onClick = { registrationAction = null; vm.registerDevice(action == "claim") }) { Text("同意并登记") } },
                dismissButton = { BadgeAction(onClick = { registrationAction = null }) { Text("取消") } })
        }
        if (profileDetails) state.profile?.let { profile ->
            BadgeDialog(onDismissRequest = { profileDetails = false }, title = { Text("本机徽章档案") },
                text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("设备：${profile.device.name}")
                    Text("档案 ID：${profile.subjectId}")
                    Text("随机安装标识：${profile.device.installationId}")
                    Text("这些公开标识仅用于核对，不能领取或恢复权益。换机或赠予他人，请在收藏中为每一枚徽章分别保存恢复码。", style = MaterialTheme.typography.bodySmall)
                    BadgeAction(onClick = { scope.launch { recoveryGrant = null; recovery = vm.recoveryCode(); profileDetails = false } }) { Text("备份整个旧版档案") }
                } }, confirmButton = { BadgeAction(onClick = { profileDetails = false }) { Text("关闭") } })
        }
        // Sensitive dialogs own SecureOn; never clear security flags inherited by the outer window.
        if (recover) {
            var input by remember { mutableStateOf("") }
            BadgeDialog(onDismissRequest = { recover = false; input = "" }, title = { Text("旧版档案恢复") },
                properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
                text = { Column {
                    Text("仅用于以前保存的 FL1 档案码。此操作会切换整个档案，不会合并本机收藏；请先备份当前档案。转移单枚徽章请使用“转入一枚徽章”。")
                    Text("恢复会登记本机型号、名称和应用内随机安装标识并更新设备关联记录，不采集 IP 或地理位置。", style = MaterialTheme.typography.bodySmall)
                    BadgeCodeField(input, { if (it.length <= 160) input = it }, label = "私密恢复码", modifier = Modifier.fillMaxWidth())
                } }, confirmButton = { BadgeAction(onClick = { val value = input; input = ""; recover = false; vm.recover(value) }, enabled = input.isNotBlank()) { Text("同意并恢复") } },
                dismissButton = { BadgeAction(onClick = { recover = false; input = "" }) { Text("取消") } })
        }
        if (transferOpen) {
            var input by remember { mutableStateOf("") }
            BadgeDialog(onDismissRequest = { transferOpen = false; input = "" }, title = { Text("让这一枚，来到你身边") },
                properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
                text = { BadgeTransferForm(input, { if (it.length <= 160) input = it }, state.profile != null, onRegister = {
                    transferOpen = false; input = ""; resumeTransferRegistration = true; registrationAction = "register"
                }) },
                confirmButton = { BadgeAction(enabled = !state.busy && state.profile != null && input.isNotBlank(), onClick = {
                    val value = input; input = ""; transferOpen = false; vm.recoverInstance(value)
                }) { Text("转入我的收藏") } },
                dismissButton = { BadgeAction(onClick = { transferOpen = false; input = "" }) { Text("暂时不用") } })
        }
        if (backupDialog) BadgeDialog(onDismissRequest = { backupDialog = false }, title = { Text("一键保存恢复码") },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("把当前收藏中每枚徽章的名称、编号、自定义 ID 和恢复码，整理成一份私密文本文件。")
                Text("需要联网核对归属。持有码的人可以转走对应徽章，请存入私密文件夹或密码管理器，不要发到群里。", style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { BadgeAction(onClick = {
                backupDialog = false
                vm.prepareRecoveryBackup { backup ->
                    pendingBackup = backup
                    val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault()).format(Instant.now())
                    try { saveBackup.launch("枳生徽章恢复码-$stamp.txt") }
                    catch (_: Exception) { pendingBackup = null; vm.backupSaveFailed() }
                }
            }, enabled = !state.busy) { Text("保存到文件") } }, dismissButton = { BadgeAction(onClick = { backupDialog = false }) { Text("取消") } })
        if (selectRecovery) BadgeDialog(onDismissRequest = { selectRecovery = false }, title = { Text("为哪一枚保存恢复码") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("每一枚都有自己的编号和恢复码。")
                state.collection.forEach { item -> BadgeAction(onClick = {
                    selectRecovery = false; vm.instanceRecoveryCode(item.grantId) { recoveryGrant = item; recovery = it }
                }, modifier = Modifier.fillMaxWidth()) { Text("${badgeDisplayName(item)}   ${badgeSerialText(item.serial)}") } }
            } }, confirmButton = { BadgeAction(onClick = { selectRecovery = false }) { Text("返回") } })
        recovery?.let { code ->
            BadgeDialog(onDismissRequest = { recovery = null }, title = { Text(if (recoveryGrant == null) "备份整个旧版档案" else "保存这一枚的恢复码") },
                properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
                text = { val item = recoveryGrant
                    if (item != null) BadgeInstanceCodeNotice(badgeDisplayName(item), badgeSerialText(item.serial))
                    else Text("此 FL1 码包含整个旧版档案的访问权，不能用来单独赠送一枚徽章。请保存在自己的密码管理器中。") },
                confirmButton = { BadgeAction(onClick = {
                    val clip = ClipData.newPlainText(if (recoveryGrant == null) "徽章档案恢复码" else "单枚徽章恢复码", code)
                    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                    recovery = null
                }) { Text("复制并自行保存") } }, dismissButton = { BadgeAction(onClick = { recovery = null }) { Text("关闭") } })
        }
}
@Composable
internal fun BadgeStaticPreview(showControls: Boolean = true) {
    val context = LocalContext.current
    var back by rememberSaveable { mutableStateOf(false) }
    val bitmap = remember(back) { runCatching {
        context.assets.open(if (back) "badges/preview-back.webp" else "badges/preview-front.webp").use {
            BitmapFactory.decodeStream(it)?.asImageBitmap()
        }
    }.getOrNull() }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        bitmap?.let { Image(it, if (back) "初光徽章背面静态外观预览" else "初光徽章正面静态外观预览",
            Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Fit) }
        if (showControls) BadgeAction(onClick = { back = !back }) { Text(if (back) "查看正面" else "查看背面") }
    }
}
internal fun badgeCampaignStatus(state: BadgeUiState): String = when {
    state.grant != null && state.campaign.claimEnabled -> "已领取，升级后长期保留"
    state.grant != null -> "领取窗口关闭不影响您的已有徽章，可离线查看"
    state.campaign.state == "ended" -> "本次领取已结束"
    state.campaign.state == "not_started" -> "领取尚未开始"
    state.campaign.state == "not_configured" -> "徽章服务尚未开放"
    state.campaign.state == "closed" -> "本次领取窗口已关闭"
    !state.campaign.eligible(BuildConfig.VERSION_CODE) -> "当前构建不在本次领取范围内"
    else -> "活动开放期间进入首页会自动获得，已有徽章可长期查看与恢复"
}
