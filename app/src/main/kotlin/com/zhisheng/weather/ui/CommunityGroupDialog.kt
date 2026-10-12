package com.zhisheng.weather.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhisheng.weather.i18n.uiText
import com.zhisheng.weather.ui.components.WeatherDialogWindow
import com.zhisheng.weather.ui.theme.*

@Composable
fun CommunityGroupDialog(onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var selectedIndex by rememberSaveable { mutableIntStateOf(CommunityGroups.first().index) }
    val group = CommunityGroups.first { it.index == selectedIndex }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(
        usePlatformDefaultWidth = true, decorFitsSystemWindows = false,
    )) {
        WeatherDialogWindow(overlay = true)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .38f)).clickable(onClick = onClose))
            val panelHeight = (maxHeight - 64.dp).coerceAtLeast(120.dp).coerceAtMost(640.dp)
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center) {
                Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().heightIn(max = panelHeight)
                    .zhishengDialogPanel()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 14.dp)) {
                            Text("QQ 交流群", style = MaterialTheme.typography.labelSmall,
                                color = ZhishengCyan, letterSpacing = 1.4.sp)
                            Text("枳生天气用户交流群", style = MaterialTheme.typography.titleLarge,
                                color = ZhishengText, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.size(52.dp).semantics { contentDescription = uiText("关闭") }
                            .clickable(role = Role.Button, onClick = onClose), contentAlignment = Alignment.Center) {
                            Text("×", style = MaterialTheme.typography.headlineSmall, color = ZhishengTextSecondary)
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    Row(Modifier.fillMaxWidth().selectableGroup().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CommunityGroups.forEach { choice ->
                            val selected = choice.index == selectedIndex
                            Box(Modifier.weight(1f)
                                .background(if (selected) ZhishengCyan.copy(alpha = .12f) else Color.Transparent)
                                .selectable(selected, role = Role.Tab) { selectedIndex = choice.index }
                                .padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Text(if (choice.index == 2) "2 群 · 推荐" else "1 群",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) ZhishengCyan else ZhishengTextSecondary,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(group.number, style = MaterialTheme.typography.titleLarge, color = ZhishengMint,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Spacer(Modifier.height(10.dp))
                        Image(painterResource(group.qrResource),
                            contentDescription = uiText("枳生天气 QQ 群二维码，群号 ${group.number}"),
                            modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth().aspectRatio(1f),
                            contentScale = ContentScale.Fit)
                        Spacer(Modifier.height(10.dp))
                        Text("前瞻预览、使用交流与反馈。优先加入 2 群。",
                            style = MaterialTheme.typography.bodyMedium, color = ZhishengTextSecondary,
                            textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                    }
                    HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.weight(1f).background(ZhishengMint.copy(alpha = .12f))
                            .clickable(role = Role.Button) {
                                val opened = openCommunityGroupLink(group) { url, packageName ->
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            packageName?.let { setPackage(it) }
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    }.isSuccess
                                }
                                if (!opened) Toast.makeText(context,
                                    uiText("暂时无法打开加群入口，可复制群号在 QQ 中搜索。"), Toast.LENGTH_LONG).show()
                            }.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Text("一键加群", style = MaterialTheme.typography.labelLarge,
                                color = ZhishengMint, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.weight(1f).clickable(role = Role.Button) {
                            clipboard.setText(AnnotatedString(group.number))
                            Toast.makeText(context, uiText("群号已复制"), Toast.LENGTH_SHORT).show()
                        }.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Text("复制群号", style = MaterialTheme.typography.labelLarge, color = ZhishengCyan)
                        }
                    }
                }
            }
        }
    }
}
