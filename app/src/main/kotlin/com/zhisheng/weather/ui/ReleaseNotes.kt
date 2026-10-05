package com.zhisheng.weather.ui

internal enum class ReleaseNotesCategory(val label: String) {
    ALL("全部"), WIDGETS("小组件"), WEATHER("天气与城市"), EXPERIENCE("日常体验"),
}

internal data class ReleaseNote(val title: String, val detail: String)
internal data class ReleaseNotesSection(
    val id: String, val category: ReleaseNotesCategory, val title: String, val notes: List<ReleaseNote>,
)

/** Final changes: 10.1 maintenance and subsequent 10.2/10.3 fixes.
 * Reverted openings and unconfirmed manufacturer reports are not features.
 */
internal val WhatsNewSections = listOf(
    ReleaseNotesSection("widgets", ReleaseNotesCategory.WIDGETS, "桌面小组件", listOf(
        ReleaseNote("大小更协调，边缘更清楚", "调整不同桌面尺寸下的比例：小窗居中呈现方形，横向组件留出边距，全景组件重新安排字号和间距；改善边缘发虚与文字裁切。"),
        ReleaseNote("预览更贴近桌面", "编辑已有组件时，按桌面提供的尺寸显示预览；新建时标明参考尺寸，减少预览与实际效果的落差。"),
        ReleaseNote("数字大小，更好调了", "温度、时间和天气读数都能跟着调整，小读数保留清晰易读的大小；窄日历组件按空间安排预报列数。"),
        ReleaseNote("长摘要可以换行了", "全景组件的天气摘要最多显示两行，不再提前删掉后半句，也不会为了放进一行而把字缩得很小。"),
        ReleaseNote("点击操作更顺手", "时间、日期和天气区域支持设置跳转，也能选择编辑当前组件；日期优先打开手机里的系统日历。"),
        ReleaseNote("添加有没有成功，会告诉你", "区分添加成功、桌面未接收和等待确认；没有加上时提供手动添加方法，以及常见系统的操作指引。"),
        ReleaseNote("编辑时，下面不再挡一大块", "底部按钮改为轻透的玻璃样式，背景随滚动自然渐隐，给选项留出更多空间；数字滑块和最后一项都能完整滚到上方。"),
        ReleaseNote("刷新后的信息更完整", "修复天气数据恢复后小时预报仍隐藏，以及多余小字显示的问题。"),
    )),
    ReleaseNotesSection("cities", ReleaseNotesCategory.WEATHER, "定位与收藏", listOf(
        ReleaseNote("重新打开，定位衔接更稳", "修复重新进入应用后没有正确回到定位地区的问题。"),
        ReleaseNote("收藏的地方，留在原地", "收藏城市保持固定位置，不再跟着当前位置改变；已有收藏也会保留。"),
        ReleaseNote("同一个地方，不再反复出现", "改进城市去重，减少跨地区移动后同一城市出现多个记录的情况。"),
        ReleaseNote("找地点时少一些等待", "改善部分旧版 Android 上地址搜索长时间没有响应的问题。"),
    )),
    ReleaseNotesSection("weather", ReleaseNotesCategory.WEATHER, "天气读数与预报", listOf(
        ReleaseNote("紫外线上下看得明白", "统一首页与生活指数的当前紫外线展示；只有全天预报时会说明时段，减少实况和全天指数混在一起的困惑。"),
        ReleaseNote("下拉刷新，时间也跟着更新", "成功获取天气后，“更新于”显示这次刷新的完成时间；数据源的发布时间另外保留。"),
        ReleaseNote("下雨的时间，判断更准确", "修正部分短时降水区间和降水概率的解析，减少正在下雨却提示稍后才下雨、百分数被放大的情况。"),
        ReleaseNote("晴雨变化与日期更准确", "修正部分天气编码和昼夜现象的匹配；跨午夜的小时标注、今天与明天的预报摘要也一起调整。"),
        ReleaseNote("体感与空气质量读数修正", "修正部分体感温度、风向和空气质量的计算与显示；当前读数不再混入上一小时的预报。"),
        ReleaseNote("异地时区显示更稳", "保留城市当地时区，改善海外城市的小时预报、日月时间及紫外线等补充数据的时间对应。"),
        ReleaseNote("预警和缺测，不含糊", "改进同名地区及自治区简称的预警匹配；预报缺少数据时明确提示，不把未知写成晴天或无雨。"),
    )),
    ReleaseNotesSection("atmosphere", ReleaseNotesCategory.EXPERIENCE, "天气氛围", listOf(
        ReleaseNote("天空可以跟着日照变化", "晨曦、暖金、霞光与蓝调融入澄空主页原有的背景、云层和天气图标，雨雾天的光色更柔和；五款澄空组件也按各自城市变化，保留透明度与字色。外观设置可随时开关，也能预览一天的天空。"),
        ReleaseNote("不同天气，更有自己的样子", "细雨与普通雨的表现拉开差别，冻雨增加表面冰霜感；酷热与严寒天气的氛围也能正确呈现。"),
        ReleaseNote("夜间多云预览不再重复", "整理天气预览入口，去掉重复的夜间多云效果。"),
        ReleaseNote("关掉氛围，就安静下来", "修复城市卡片关闭氛围后仍留下云、雨、雪等绘制的问题。"),
    )),
    ReleaseNotesSection("daily", ReleaseNotesCategory.EXPERIENCE, "横屏与日常使用", listOf(
        ReleaseNote("横屏久放，多一层保护", "待机画面会轻微移动，降低长期固定显示带来的烧屏风险，尽量减少使用中的干扰。"),
        ReleaseNote("横着放稳，就能进入横屏", "修复手机横向放稳后可能一直停在竖屏的问题。"),
        ReleaseNote("打开应用，直接看天气", "移除应用内开屏动画与经典主页开场序列；点击更新说明直接打开这个窗口。"),
        ReleaseNote("更新说明，更容易看了", "重新设计更新说明窗口，按小组件、天气与城市、日常体验分类；长内容可以滚动，大字体与横屏也能查看。"),
        ReleaseNote("排好的顺序，记得更牢", "修复保存设置后，首页模块顺序偶尔被重置的问题。"),
        ReleaseNote("其他语言也保留原始信息", "改善日语展示，保留预警中的地点、时长和数值，减少通用翻译改写造成的信息丢失。"),
    )),
    ReleaseNotesSection("updates", ReleaseNotesCategory.EXPERIENCE, "更新与下载", listOf(
        ReleaseNote("检查和下载，遇到问题会重试", "优先连接 Gitee；下载停滞或文件校验失败时尝试备用地址。"),
        ReleaseNote("取消后重试，不再互相干扰", "改进下载取消与重试处理，避免上一轮下载影响新一次下载；安装包继续进行完整性校验。"),
    )),
)

internal fun releaseNotesFor(category: ReleaseNotesCategory): List<ReleaseNotesSection> =
    WhatsNewSections.filter { category == ReleaseNotesCategory.ALL || it.category == category }
