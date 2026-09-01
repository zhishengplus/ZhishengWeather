package com.zhisheng.weather.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhisheng.weather.BuildConfig
import com.zhisheng.weather.R
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengCardBorder
import com.zhisheng.weather.ui.theme.ZhishengCyan
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.i18n.uiText

internal val CommunityQqGroup: String
    get() = BuildConfig.COMMUNITY_QQ_GROUP

@Composable
fun CommunityGroupDialog(onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ZhishengBg.copy(alpha = 0.86f))
                .safeDrawingPadding()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .background(ZhishengSurface, RectangleShape)
                    .border(1.dp, ZhishengCardBorder, RectangleShape),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 14.dp)) {
                        Text(
                            "COMM LINK / QQ",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengCyan,
                            letterSpacing = 1.4.sp,
                        )
                        Text(
                            "枳生天气用户交流群",
                            style = MaterialTheme.typography.titleLarge,
                            color = ZhishengText,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clickable(role = Role.Button, onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", style = MaterialTheme.typography.headlineSmall, color = ZhishengTextSecondary)
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)

                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("CHANNEL ID", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                        Text(
                            CommunityQqGroup,
                            style = MaterialTheme.typography.titleMedium,
                            color = ZhishengMint,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        )
                    }

                    Spacer(Modifier.size(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 300.dp)
                            .aspectRatio(1016f / 1099f)
                            .background(ZhishengBg)
                            .border(1.dp, ZhishengCyan.copy(alpha = 0.72f), RectangleShape)
                            .padding(7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.qq_group_qr),
                            contentDescription = uiText("枳生天气 QQ 群二维码，群号 $CommunityQqGroup"),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    Spacer(Modifier.size(12.dp))
                    Text(
                        "扫码申请加入，或复制群号后在 QQ 中搜索。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZhishengTextSecondary,
                    )
                }

                HorizontalDivider(thickness = 1.dp, color = ZhishengCardBorder)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(role = Role.Button) {
                                clipboard.setText(AnnotatedString(CommunityQqGroup))
                                Toast.makeText(context, uiText("群号已复制"), Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("[ 复制群号 ]", style = MaterialTheme.typography.labelLarge, color = ZhishengMint)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(ZhishengCardBorder.copy(alpha = 0.16f))
                            .clickable(role = Role.Button, onClick = onClose)
                            .padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("[ 关闭 ]", style = MaterialTheme.typography.labelLarge, color = ZhishengCyan)
                    }
                }
            }
        }
    }
}
