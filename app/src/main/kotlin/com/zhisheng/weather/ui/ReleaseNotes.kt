package com.zhisheng.weather.ui

internal enum class ReleaseNotesCategory(val label: String) {
    ALL("全部"), WIDGETS("小组件"), WEATHER("天气与城市"), EXPERIENCE("日常体验"),
}
internal data class ReleaseNote(val title: String, val detail: String)
internal data class ReleaseNotesSection(
    val id: String, val category: ReleaseNotesCategory, val title: String, val notes: List<ReleaseNote>,
)

/** Public beta10.8 -> 0.1.6: user-visible changes, not the internal build history. */
internal val WhatsNewSections = listOf(
    ReleaseNotesSection("weather-outfit", ReleaseNotesCategory.WEATHER, "出门前，搭一套", listOf(
        ReleaseNote("天气穿搭来到首页", "用人台看清上身、外搭、下装和鞋履。点选装备查看理由，也能换同类款式和配色，把天气建议变成一套看得见的搭配。"),
        ReleaseNote("按天气，也按你的感受", "现在出门或接下来6小时，都可结合体感、风雨与紫外线安排衣物。支持日常、通勤、走动多，以及怕冷、怕热偏好；缺少数据时会说明。"),
        ReleaseNote("推荐有依据，位置由你定", "衣物与随身用品附有推荐理由和气象、防晒资料链接。复用已有天气，不需要额外下载模型；可在首页设置中调整位置或隐藏。"),
    )),
    ReleaseNotesSection("first-light", ReleaseNotesCategory.EXPERIENCE, "把初光放进口袋", listOf(
        ReleaseNote("可以把玩的三维收藏", "收藏列表里直接拖动，轻触后连续放大，也可双指缩放；查看专属编号、署名和设计手记。支持收藏多枚同类徽章，戒指默认正面，同屏收藏一起轻晃。"),
        ReleaseNote("领取与加载分开等待", "活动开放且符合资格时可领取初光，澄空之戒属于额外活动掉落。模型没准备好也能先收下；短暂断网会重试，单枚失败可原位重载，不拖住其他收藏。更新或重试不会重复抽取戒指。"),
        ReleaseNote("换机前，给收藏留份备份", "在徽章右上角菜单点“一键保存恢复码”，把文件放在应用之外；新增或转入收藏后再保存一份。恢复码能转走对应徽章，请私下保管。正常覆盖更新保留收藏，卸载或清数据前请先备份。"),
        ReleaseNote("名字、编号和转赠", "初光和澄空之戒可在菜单修改自定义 ID；晴空冠冕和与君同行的纪念 ID 由管理员填写。赠送码可兑换，单枚恢复码可换机或转赠，编号和 ID 不能代替恢复码。丢码且旧应用无法打开时，可联系管理员核对原归属与记录，协助处理。"),
    )),
    ReleaseNotesSection("typhoon-tracking", ReleaseNotesCategory.WEATHER, "跟着台风看路径", listOf(
        ReleaseNote("从首页，打开台风追踪", "新增独立台风页，可按年份和名称查找，逐点回放路径，查看中心强度和7、10、12级风圈。实况、预报与历史路径分开展示，缓存和缺测状态有提示。"),
        ReleaseNote("两种界面，同一片海", "澄空与经典都保留首页台风卡片，入口换上蓝色旋涡。天地图可切换标准地图与卫星影像，保留官方中文地名；预报使用中央气象台资料。"),
    )),
    ReleaseNotesSection("history-and-layout", ReleaseNotesCategory.WEATHER, "天气回看，更自由", listOf(
        ReleaseNote("一天、一段时间，或一整月", "在往年同日比较之外，新增精确日期、日期段和整月查询，支持闰日。没有记录的日期会单独提示。"),
        ReleaseNote("预报横着看，也能竖着看", "多天天气可切换横向、纵向布局并记住偏好；首页详情按气象、日月信息分组，长预报更方便阅读。"),
    )),
    ReleaseNotesSection("weather-sources", ReleaseNotesCategory.WEATHER, "读懂天气，少些缺漏", listOf(
        ReleaseNote("预报信息更完整", "补齐腾讯逐小时预报、中国天气网七天与逐三小时预报；切换日预报来源时保留已有逐小时信息。预警不再只取前三条，合并重复通知并保留不同类型、发布机构和完整标题。"),
        ReleaseNote("过期与缺失数据处理", "过滤过期实况和失效短时数据，修正预报日期、现象与湿度等字段的对应关系；缺失值不再冒充有效数值。港澳搜索补充简繁和常用别名，并改善天气现象显示。"),
        ReleaseNote("选源与回退调整", "自动选源采用限时请求；枳生源保留主源当前值，再按来源补缺。页面注明实际来源与预报范围，便于判断数据从哪里来。"),
    )),
    ReleaseNotesSection("cities-and-location", ReleaseNotesCategory.WEATHER, "找到城市，留在这座城", listOf(
        ReleaseNote("选中的城市，不再被定位抢回", "改善搜索选城和左右切城的衔接。手动选城后，回到前台或查看穿搭继续保留这座城；同城天气刷新与定位微调尽量保留浏览位置。"),
        ReleaseNote("定位多留一条路", "主动唤醒可用的系统定位来源，为 GPS 冷启动留出时间。网络定位不可用时，可授权 GPS 重试；不要求 Google 服务，也不会自动开启街道地址显示。"),
    )),
    ReleaseNotesSection("everyday-polish", ReleaseNotesCategory.EXPERIENCE, "熟悉的画面，更顺手", listOf(
        ReleaseNote("画质不减，减少重复工作", "优化首页、逐时、穿搭和徽章的重复绘制与加载，完善独立弹窗的高刷新率请求。保留原有清晰度、光影和动效，改善滑动与切页体验。"),
        ReleaseNote("日月与经典细节", "日月卡支持波形和同心弧切换；深浅主题的月相亮暗含义保持一致。修正经典逐时跳动及滚动内容遮挡页头，调整部分数字和提示排版；更新默认图标，保留天气娘选项。"),
    )),
    ReleaseNotesSection("widget-stability", ReleaseNotesCategory.WIDGETS, "桌面照常陪你", listOf(
        ReleaseNote("长按图标，入口能打开", "修正部分桌面点击刷新、切换城市或设置时误报“未安装应用”的问题，独立体验包也指向自己的安装包。"),
        ReleaseNote("小组件与提醒更稳妥", "改善连续改设置时的保存与刷新调度，先登记后台刷新再绘制，增加周期更新兜底；调整时光气象单行最小尺寸。系统后台限制仍会影响更新，强行停止后需重新打开应用。"),
    )),
)

internal fun releaseNotesFor(category: ReleaseNotesCategory): List<ReleaseNotesSection> =
    WhatsNewSections.filter { category == ReleaseNotesCategory.ALL || it.category == category }
