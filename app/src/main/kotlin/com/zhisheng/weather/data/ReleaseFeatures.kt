package com.zhisheng.weather.data

/** Release gates, not preference deletion. Flip individually when each feature is ready. */
object ReleaseFeatures {
    const val weatherAtmosphere = true
    const val radar = false
    const val typhoon = false
    const val coastalWeather = false
    const val starPhotography = false
    const val classicTheme = true
    const val themeChoiceOnStartup = false

    fun interfaceStyle(saved: InterfaceStyle): InterfaceStyle =
        if (classicTheme) saved else InterfaceStyle.PHOSPHOR_VISTA

    // Upgrade once to light. A subsequent explicit dark/system choice remains respected.
    fun themeMode(saved: String?, releaseAppearanceApplied: Boolean): ThemeMode =
        if (releaseAppearanceApplied) ThemeMode.from(saved) else ThemeMode.LIGHT
}
