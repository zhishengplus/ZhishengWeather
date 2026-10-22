package com.zhisheng.weather.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.zhisheng.weather.data.ServiceDiagnostics
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ServiceDiagnosticsDialog(enabled: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }
    var privacy by remember { mutableStateOf(false) }
    if (privacy) ServicePrivacyDialog { privacy = false }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, containerColor = zhishengOverlayColor(),
        title = { Text("帮助改进服务", color = ZhishengText) },
        text = { Column {
            Text("上传机型、系统、应用版本、屏幕规格及定位状态，用于改善兼容性与使用体验；不包含坐标、个人 Key、搜索记录或页面操作。", color = ZhishengTextSecondary)
            TextButton(onClick = { privacy = true }) { Text("查看隐私说明", color = ZhishengMint) }
            message?.let { Text(it, color = ZhishengTextSecondary) }
            TextButton(enabled = !busy, onClick = { busy = true; scope.launch {
                try { ServiceDiagnostics.erase(context); message = "已关闭并删除本次安装的设备诊断。" }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "已关闭，记录暂未删除，请联网后重试。" }
                finally { busy = false }
            } }) { Text("关闭并删除设备诊断", color = ZhishengOrange) }
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            if (enabled) onDismiss() else scope.launch {
                busy = true
                try {
                    SettingsRepository.setServiceDiagnostics(true)
                    if (ServiceDiagnostics.report(context.applicationContext)) onDismiss()
                    else message = "已开启，将在下次打开时重试同步。"
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { message = "设置暂未能保存，请稍后重试。" }
                finally { busy = false }
            }
        }) { Text(if (enabled) "知道了" else "同意并开启", color = ZhishengMint) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("返回", color = ZhishengTextSecondary) } })
}
