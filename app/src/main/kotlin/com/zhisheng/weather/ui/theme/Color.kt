package com.zhisheng.weather.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 枳生天气 · 双色板（v0.0.5）
// 深色 = 磷光终端：近黑底 + 荧光信号色（原色板）
// 浅色 = 纸面终端：暖纸白底 + 油墨信号色（荧光色直接搬到白底会发糊，
//        所以浅色不是简单反转，而是信号色整体加深为油墨版，像白纸上印的仪表图）

@Immutable
data class ZhishengPalette(
    val isLight: Boolean,
    val bg: Color,
    val surface: Color,
    val card: Color,
    val cardBorder: Color,
    val mint: Color,
    val orange: Color,
    val cyan: Color,
    val red: Color,
    val warning: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
)

val ZhishengDarkPalette = ZhishengPalette(
    isLight = false,
    bg = Color(0xFF050507),            // 页面底（近黑带微蓝）
    surface = Color(0xFF0E0E12),       // 面板
    card = Color(0xFF14141A),          // 内嵌块（比面板亮一档）
    cardBorder = Color(0xFF23232B),    // 面板边框
    mint = Color(0xFF50FF50),          // 数据绿 · 主强调（v0.0.2 起扩展为选中/成功态）
    orange = Color(0xFFFF9830),        // 信号橙 · 标题/标签
    cyan = Color(0xFF20F0FF),          // 线框青 · 图标/温度°
    red = Color(0xFFFF3030),           // 警报红
    warning = Color(0xFFFFD24A),       // 预警黄（国标四档专用，深色荧光黄）
    text = Color(0xFFE8F0E8),          // 数据白（微冷）
    textSecondary = Color(0xFFC8D8C8), // 钢灰 · 次级（微冷）
    textTertiary = Color(0xFF95A395),  // 注释灰（约 5.4:1，≥ AA 4.5:1）
)

// 深色柔和强调：只收绿色与蓝色的发光亮度，色相和语义不变。
// 它不是把整页蒙灰，正文、橙色标题和警报层级仍保持原来的清晰度。
val ZhishengDarkSoftAccentPalette = ZhishengDarkPalette.copy(
    mint = Color(0xFF32C267),
    cyan = Color(0xFF2FAFC3),
)

// v0.0.5 重做：清冷翡翠（冷灰极简）。
// 冷调纸面：底最深 → 面板 → 卡片纯白，三级明度拉开；发丝边代替粗边框，留白感。
// 信号色主次与深色版对齐：翡翠绿(主数据) > 钢青(线框/图标) > 琥珀(标签，小面积)，
// 全部压到纸白底 ≥ 4.5:1，暖褐系全部清退（此前"暖纸油墨"显旧）。
val ZhishengLightPalette = ZhishengPalette(
    isLight = true,
    bg = Color(0xFFF2F4F5),            // 冷灰纸（最深档）
    surface = Color(0xFFF7F9FA),       // 面板（中间档）
    card = Color(0xFFFFFFFF),          // 卡片纯白（最亮档）
    cardBorder = Color(0xFFE3E6EA),    // 发丝边（冷灰）
    mint = Color(0xFF0F7F68),          // 翡冷翠 · 数据主色（清凉偏青玉绿，≈4.9:1）
    orange = Color(0xFFB45309),        // 琥珀 · 标题/标签小面积（≈5.0:1）
    cyan = Color(0xFF16697A),          // 钢青 · 线框/图标（≈6.3:1）
    red = Color(0xFFD63838),           // 警报红（≈4.8:1）
    warning = Color(0xFF8F6F0A),       // 国标黄（纸白底 AA，≈4.5:1）
    text = Color(0xFF2E3540),          // 石墨蓝灰（≈11:1；纯黑在纸面上太硬）
    textSecondary = Color(0xFF59636F), // 冷灰（约 6:1）
    textTertiary = Color(0xFF6F7985),  // 注释灰（约 4.5:1）
)


val ZhishengLightSoftAccentPalette = ZhishengLightPalette.copy(
    mint = Color(0xFF0B6857),
    cyan = Color(0xFF145B6B),
)

// beta6-2 · 磷光视界：保留磷光信号色，但让大面积底色更安静、层级更清楚。
// 绿色用于实时数据与确认，青色用于导航与图表，琥珀只用于时间、标题和告警提示。
val PhosphorVistaDarkPalette = ZhishengPalette(
    isLight = false,
    bg = Color(0xFF0A0E15),
    surface = Color(0xFF1D242E),
    card = Color(0xFF2D3541),
    cardBorder = Color(0xFF50616C),
    mint = Color(0xFF8EC9C2),
    orange = Color(0xFFF2B56B),
    cyan = Color(0xFF95C8EF),
    red = Color(0xFFFF9CA0),
    warning = Color(0xFFF1C96A),
    text = Color(0xFFEDF3F4),
    textSecondary = Color(0xFFB3C0C7),
    textTertiary = Color(0xFFA7B7C2),
)

val PhosphorVistaDarkSoftAccentPalette = PhosphorVistaDarkPalette.copy(
    mint = Color(0xFF85BFB9),
    cyan = Color(0xFF8ABCDD),
)

val PhosphorVistaLightPalette = ZhishengPalette(
    isLight = true,
    bg = Color(0xFFF6F8FA),
    surface = Color(0xFFFEFEFF),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFDDE3E6),
    mint = Color(0xFF1E555A),
    orange = Color(0xFF9B4B0D),
    cyan = Color(0xFF245780),
    red = Color(0xFFB52D3A),
    warning = Color(0xFF765700),
    text = Color(0xFF19232E),
    textSecondary = Color(0xFF40515F),
    textTertiary = Color(0xFF435361),
)

// Meteocons cloud pixels are #E5EFFC. Compress RGB equally on light surfaces:
// this preserves the hue relationships of sun/rain/cloud, and preserves alpha.
internal const val VistaLightWeatherIconRgbScale = 0.58f

val PhosphorVistaLightSoftAccentPalette = PhosphorVistaLightPalette.copy(
    mint = Color(0xFF305359),
    cyan = Color(0xFF345373),
)

val LocalZhishengPalette = staticCompositionLocalOf { ZhishengDarkPalette }

// 兼容既有调用点的主题访问器：UI 里原有 `ZhishengText` 等全局 val 的调用处不用改，
// 非 composable 上下文（如 DrawScope 扩展、纯函数）不能使用，需改为传参（v0.0.5）
val ZhishengBg: Color @Composable get() = LocalZhishengPalette.current.bg
val ZhishengSurface: Color @Composable get() = LocalZhishengPalette.current.surface
val ZhishengCard: Color @Composable get() = LocalZhishengPalette.current.card
val ZhishengCardBorder: Color @Composable get() = LocalZhishengPalette.current.cardBorder
val ZhishengMint: Color @Composable get() = LocalZhishengPalette.current.mint
val ZhishengOrange: Color @Composable get() = LocalZhishengPalette.current.orange
val ZhishengCyan: Color @Composable get() = LocalZhishengPalette.current.cyan
val ZhishengRed: Color @Composable get() = LocalZhishengPalette.current.red
val ZhishengWarning: Color @Composable get() = LocalZhishengPalette.current.warning
val ZhishengText: Color @Composable get() = LocalZhishengPalette.current.text
val ZhishengTextSecondary: Color @Composable get() = LocalZhishengPalette.current.textSecondary
val ZhishengTextTertiary: Color @Composable get() = LocalZhishengPalette.current.textTertiary

// Temperature graphics use a softer amber than small text, with no muddy hue interpolation.
internal fun vistaTemperatureInk(high: Double?, light: Boolean): Color =
    if ((high ?: 0.0) >= 25.0) { if (light) Color(0xFFD19A62) else Color(0xFFE4B678) }
    else { if (light) Color(0xFF7099AF) else Color(0xFF8EBBD5) }
