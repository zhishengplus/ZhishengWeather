package com.zhisheng.weather.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.ui.theme.*

internal const val SERVICE_PRIVACY_NOTICE = """枳生天气隐私说明
更新日期：2026 年 10 月 20 日

枳生天气只为完成天气查询和您主动开启的附加功能处理必要信息。可选功能都能在设置中关闭；未保存过选择时，基础诊断与设备版本统计默认开启。

一、天气查询
选择城市时，应用会把城市名称、编号或坐标发送给您选择的数据来源。随包地名可离线搜索；使用系统地址或个人地点服务时，搜索词与坐标可能联网处理。

二、可选位置服务
开启后，应用会把坐标、精度、时间和随机安装标识上传到开发者服务器，并由高德 Web 服务解析地址，用于补全天气地点和排查失败。关闭后停止上传，也可在说明页删除本机已上传的位置历史。

三、帮助改进服务与设备版本统计
两个开关相互独立。默认开启仅适用于未保存选择的情况，已关闭的选择会保留。开启后会上传机型、系统与应用版本、屏幕与语言等基础信息，以及公告的获取、展示和已读状态，用于兼容性改进、版本分布和公告统计；不包含坐标、个人 Key、搜索记录或页面操作。公告仅在前台获取，不附带位置或机型。

四、第三方与保存
天气来源、系统地址服务、高德和更新服务只接收完成请求所需的信息，请同时查看它们的隐私规则。自有数据通过加密连接传到开发者服务器（位于中国大陆），仅用于上述目的，不向其他用户公开，按提供服务所需期限保存并自动清理。

五、您的选择
关闭对应开关后停止新增上传；“帮助改进服务”和“枳生天气位置服务”的说明页可删除本机已上传的数据。卸载前如需删除，建议先完成删除操作。关闭系统定位权限后，仍可手动选城查询天气；未成年人请在监护人指导下决定是否开启可选功能。

六、说明变更
信息种类、用途或接收方发生重要变化时，会更新说明并在相关功能中重新征得同意。可通过设置中的用户交流入口联系开发者。"""

@Composable
internal fun ServicePrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = zhishengOverlayColor(),
        title = { Text("隐私说明", color = ZhishengText) },
        text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
            Text(SERVICE_PRIVACY_NOTICE, color = ZhishengTextSecondary)
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了", color = ZhishengMint) } })
}
