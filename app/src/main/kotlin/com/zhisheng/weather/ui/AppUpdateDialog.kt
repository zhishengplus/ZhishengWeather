package com.zhisheng.weather.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhisheng.weather.data.AppUpdate
import com.zhisheng.weather.data.AppUpdateCheck
import com.zhisheng.weather.data.AppUpdateInfo
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import kotlinx.coroutines.launch

private sealed class UpdateUi {
    data object Checking : UpdateUi()
    data object UpToDate : UpdateUi()
    data class Available(val info: AppUpdateInfo) : UpdateUi()
    data class NeedPermission(val info: AppUpdateInfo) : UpdateUi()
    data class Downloading(val info: AppUpdateInfo) : UpdateUi()
    data class Ready(val info: AppUpdateInfo) : UpdateUi()
    data class Failed(val message: String, val info: AppUpdateInfo? = null) : UpdateUi()
}

@Composable
fun AppUpdateDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<UpdateUi>(UpdateUi.Checking) }
    var progress by remember { mutableFloatStateOf(0f) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val info = (ui as? UpdateUi.NeedPermission)?.info ?: return@rememberLauncherForActivityResult
        if (AppUpdate.canInstall(context)) {
            scope.launch { downloadAndInstall(context, info, { progress = it }, { ui = it }) }
        }
    }

    LaunchedEffect(Unit) {
        ui = when (val result = AppUpdate.check()) {
            is AppUpdateCheck.Available -> UpdateUi.Available(result.info)
            AppUpdateCheck.UpToDate -> UpdateUi.UpToDate
            is AppUpdateCheck.Failed -> UpdateUi.Failed(result.message)
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(ZhishengBg.copy(alpha = 0.82f))
                .safeDrawingPadding()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            val panelHeight = minOf(maxHeight - 24.dp, 520.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .heightIn(max = panelHeight)
                    .background(ZhishengSurface, RectangleShape)
                    .border(1.dp, ZhishengCyan.copy(alpha = 0.54f), RectangleShape),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 14.dp)) {
                        Text(
                            "ZHISHENG WEATHER / UPDATE",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengCyan,
                            letterSpacing = 1.3.sp,
                        )
                        Text(
                            "检查更新",
                            style = MaterialTheme.typography.titleLarge,
                            color = ZhishengText,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Box(
                        modifier = Modifier.size(52.dp).clickable(role = Role.Button, onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", style = MaterialTheme.typography.headlineSmall, color = ZhishengTextSecondary)
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Text(
                        headline(ui),
                        style = MaterialTheme.typography.titleSmall,
                        color = headlineColor(ui),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        body(ui, progress),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZhishengTextSecondary,
                    )
                }
                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val secondary = secondaryAction(ui)
                    if (secondary != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(role = Role.Button) {
                                    openUrl(context, secondary.second)
                                }
                                .padding(horizontal = 16.dp, vertical = 15.dp),
                        ) {
                            Text(
                                secondary.first,
                                style = MaterialTheme.typography.labelLarge,
                                color = ZhishengTextSecondary,
                            )
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    val primary = primaryAction(ui)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                enabled = ui !is UpdateUi.Checking && ui !is UpdateUi.Downloading,
                                role = Role.Button,
                            ) {
                                when (val current = ui) {
                                    UpdateUi.Checking, is UpdateUi.Downloading -> Unit
                                    UpdateUi.UpToDate, is UpdateUi.Ready -> onClose()
                                    is UpdateUi.Available -> {
                                        if (!AppUpdate.canSelfUpdate()) {
                                            openUrl(context, current.info.pageUrl)
                                        } else if (!AppUpdate.canInstall(context)) {
                                            ui = UpdateUi.NeedPermission(current.info)
                                        } else {
                                            scope.launch {
                                                downloadAndInstall(
                                                    context,
                                                    current.info,
                                                    { progress = it },
                                                    { ui = it },
                                                )
                                            }
                                        }
                                    }
                                    is UpdateUi.NeedPermission -> {
                                        permissionLauncher.launch(AppUpdate.installPermissionIntent(context))
                                    }
                                    is UpdateUi.Failed -> {
                                        ui = UpdateUi.Checking
                                        scope.launch {
                                            ui = when (val result = AppUpdate.check()) {
                                                is AppUpdateCheck.Available -> UpdateUi.Available(result.info)
                                                AppUpdateCheck.UpToDate -> UpdateUi.UpToDate
                                                is AppUpdateCheck.Failed -> UpdateUi.Failed(result.message)
                                            }
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            primary,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (ui is UpdateUi.Checking || ui is UpdateUi.Downloading) {
                                ZhishengTextTertiary
                            } else {
                                ZhishengMint
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

private suspend fun downloadAndInstall(
    context: android.content.Context,
    info: AppUpdateInfo,
    onProgress: (Float) -> Unit,
    setUi: (UpdateUi) -> Unit,
) {
    setUi(UpdateUi.Downloading(info))
    onProgress(0f)
    try {
        val file = AppUpdate.download(context, info) { value ->
            if (value != null) onProgress(value)
        }
        AppUpdate.install(context, file)
        setUi(UpdateUi.Ready(info))
    } catch (t: Throwable) {
        setUi(UpdateUi.Failed(t.message?.takeIf { it.isNotBlank() } ?: "下载或安装失败", info))
    }
}

private fun headline(ui: UpdateUi): String = when (ui) {
    UpdateUi.Checking -> "正在检查"
    UpdateUi.UpToDate -> "已是最新版本"
    is UpdateUi.Available -> "发现新版本 v${ui.info.versionName}"
    is UpdateUi.NeedPermission -> "需要允许安装"
    is UpdateUi.Downloading -> "正在下载 v${ui.info.versionName}"
    is UpdateUi.Ready -> "已打开系统安装页"
    is UpdateUi.Failed -> "暂时无法完成"
}

@Composable
private fun headlineColor(ui: UpdateUi) = when (ui) {
    is UpdateUi.Available, is UpdateUi.Ready -> ZhishengMint
    is UpdateUi.Failed, is UpdateUi.NeedPermission -> ZhishengOrange
    else -> ZhishengCyan
}

private fun body(ui: UpdateUi, progress: Float): String = when (ui) {
    UpdateUi.Checking -> "正在查询公共版版本信息，不会自动下载。"
    UpdateUi.UpToDate -> "当前安装的就是最新公共版。有新版本时，再点检查更新即可下载。"
    is UpdateUi.Available -> buildString {
        if (AppUpdate.canSelfUpdate()) {
            append("下载后会打开系统安装页，需要再确认一次。不会在后台自动安装。")
        } else {
            append("当前版本需从 GitHub 获取更新，不能由公共版直接覆盖。")
        }
        if (ui.info.notes.isNotBlank()) {
            append("\n\n")
            append(ui.info.notes)
        }
    }
    is UpdateUi.NeedPermission -> "系统要求先允许枳生天气安装未知应用，然后才能打开安装页。授权后会自动继续下载。"
    is UpdateUi.Downloading ->
        if (progress > 0f) "下载中 ${"%d".format((progress * 100).toInt())}%" else "正在下载安装包…"
    is UpdateUi.Ready -> "请在系统安装页确认覆盖安装。如果取消了，可以再点检查更新。"
    is UpdateUi.Failed -> ui.message
}

private fun primaryAction(ui: UpdateUi): String = when (ui) {
    UpdateUi.Checking -> "[ 检查中 ]"
    UpdateUi.UpToDate, is UpdateUi.Ready -> "[ 关闭 ]"
    is UpdateUi.Available -> if (AppUpdate.canSelfUpdate()) "[ 下载并安装 ]" else "[ 打开 GitHub ]"
    is UpdateUi.NeedPermission -> "[ 去授权 ]"
    is UpdateUi.Downloading -> "[ 下载中 ]"
    is UpdateUi.Failed -> "[ 重试 ]"
}

private fun secondaryAction(ui: UpdateUi): Pair<String, String>? = when (ui) {
    is UpdateUi.Available -> if (AppUpdate.canSelfUpdate()) "[ 打开 GitHub ]" to ui.info.pageUrl else null
    is UpdateUi.NeedPermission -> "[ 打开 GitHub ]" to ui.info.pageUrl
    is UpdateUi.Downloading -> "[ 打开 GitHub ]" to ui.info.pageUrl
    is UpdateUi.Ready -> "[ 打开 GitHub ]" to ui.info.pageUrl
    is UpdateUi.Failed -> "[ 打开 GitHub ]" to (ui.info?.pageUrl ?: AppUpdate.RELEASES_PAGE)
    else -> null
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
