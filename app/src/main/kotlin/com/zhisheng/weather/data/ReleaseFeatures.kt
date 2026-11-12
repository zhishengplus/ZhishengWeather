package com.zhisheng.weather.data

/** Release gates, not preference deletion. Flip individually when each feature is ready. */
object ReleaseFeatures {
    const val weatherAtmosphere = true
    const val radar = false
    const val typhoon = true
    const val coastalWeather = false
    const val starPhotography = false
    const val classicTheme = true
    const val themeChoiceOnStartup = false
    // 首页日月卡：true = 卡片右上角带波形/同心弧切换件（默认波形）；false = 退回原版单卡片无切换
    const val luminaryArc = true

    fun interfaceStyle(saved: InterfaceStyle): InterfaceStyle =
        if (classicTheme) saved else InterfaceStyle.PHOSPHOR_VISTA

    // Upgrade once to light. A subsequent explicit dark/system choice remains respected.
    fun themeMode(saved: String?, releaseAppearanceApplied: Boolean): ThemeMode =
        if (releaseAppearanceApplied) ThemeMode.from(saved) else ThemeMode.LIGHT
}
