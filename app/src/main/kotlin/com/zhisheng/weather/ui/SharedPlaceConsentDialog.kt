package com.zhisheng.weather.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.SecretStore
import com.zhisheng.weather.data.SharedPlaceApi
import com.zhisheng.weather.data.SharedPlaceConfiguration
import com.zhisheng.weather.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal const val SHARED_PLACE_DISCLOSURE = SERVICE_PRIVACY_NOTICE

@Composable
internal fun SharedPlaceConsentDialog(config: SharedPlaceConfiguration, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPrivacy by remember { mutableStateOf(false) }
    if (showPrivacy) ServicePrivacyDialog { showPrivacy = false }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = zhishengOverlayColor(),
        title = { Text("枳生天气位置服务", color = ZhishengText) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("开启后位置会上传到开发者服务器，用于补全天气地点；可随时关闭并删除本机已上传的历史。", color = ZhishengTextSecondary)
                TextButton(onClick = { showPrivacy = true }) { Text("查看隐私说明", color = ZhishengMint) }
                message?.let { Text(it, color = ZhishengTextSecondary) }
                if (config.installationId.isNotBlank()) TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            SecretStore.setSharedPlaceConsent(false)
                            SharedPlaceApi.eraseHistory(context, config)
                            SecretStore.forgetDeletedSharedPlaceIdentity(config)
                            message = "本次安装的位置历史已删除，服务已关闭。独立地址汇总按说明到期清理。"
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { message = "服务已关闭；服务器记录尚未删除，请联网后重试。" }
                        finally { busy = false }
                    }
                }) { Text("关闭服务并删除本次安装的位置历史", color = ZhishengOrange) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                if (config.enabled) onDismiss() else {
                    busy = true
                    scope.launch {
                        var connected = false
                        try {
                            if (SharedPlaceApi.ready(context)) {
                                connected = true
                                SecretStore.setSharedPlaceConsent(true)
                                onDismiss()
                            } else message = "位置服务暂未开放，尚未启用。可继续使用系统定位。"
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) {
                            // Only exception types enter diagnostics; no coordinates, credentials or response body.
                            android.util.Log.w("PlaceConnection", "enable stage=${if (connected) "save" else "connect"}; type=${error.javaClass.simpleName}; cause=${error.cause?.javaClass?.simpleName.orEmpty()}")
                            message = if (connected) "本机未能保存服务设置，上传尚未开启，请重试。"
                                else "暂时无法连接位置服务，未开启上传，请稍后重试。"
                        }
                        finally { busy = false }
                    }
                }
            }) { Text(if (busy) "正在连接…" else if (config.enabled) "知道了" else "同意并使用", color = ZhishengMint) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("返回", color = ZhishengTextSecondary) } },
    )
}
