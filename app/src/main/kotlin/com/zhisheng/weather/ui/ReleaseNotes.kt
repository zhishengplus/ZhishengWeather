package com.zhisheng.weather.ui

internal enum class ReleaseNotesCategory(val label: String) {
    ALL("全部"), WIDGETS("小组件"), WEATHER("天气与城市"), EXPERIENCE("日常体验"),
}
internal data class ReleaseNote(val title: String, val detail: String)
internal data class ReleaseNotesSection(
    val id: String, val category: ReleaseNotesCategory, val title: String, val notes: List<ReleaseNote>,
)

/** Implementation changes since public beta10.5. Keep the current release summary short. */
internal val WhatsNewSections = listOf(
    ReleaseNotesSection("fixes", ReleaseNotesCategory.EXPERIENCE, "本次更新", listOf(
        ReleaseNote("城市切换", "支持左右滑动切换城市，优先显示缓存，减少连续切换时的空白和重复刷新。"),
        ReleaseNote("定位与添加地点", "重新整理首次使用引导，支持多种定位方式，新增省市筛选和经纬度添加，改进定位兼容。"),
        ReleaseNote("天气源补全", "新增补全优先级设置，按顺序补充主天气源缺少的数据。"),
        ReleaseNote("桌面小组件", "增加清华大学预览，修正部分刷新与时钟跳转问题。"),
        ReleaseNote("天气显示", "完善缺失数据处理，修正天气回看边缘显示、深浅主题同步等问题。"),
        ReleaseNote("使用体验", "修正更新说明的展示时机、重复定位提示和诊断复制反馈，修改了一些已知的问题。"),
    )),
)

internal fun releaseNotesFor(category: ReleaseNotesCategory): List<ReleaseNotesSection> =
    WhatsNewSections.filter { category == ReleaseNotesCategory.ALL || it.category == category }
