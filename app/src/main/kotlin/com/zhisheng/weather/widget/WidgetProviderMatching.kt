package com.zhisheng.weather.widget

/** Some launchers return a shortened component class, but another app's ID is never ours. */
internal fun widgetProviderClassMatches(
    applicationId: String,
    localClassName: String,
    providerPackage: String,
    providerClassName: String,
): Boolean {
    if (providerPackage != applicationId || providerClassName.isBlank()) return false
    if (providerClassName == localClassName) return true
    val expanded = when {
        providerClassName.startsWith('.') -> applicationId + providerClassName
        '.' !in providerClassName -> "$applicationId.$providerClassName"
        else -> providerClassName
    }
    return expanded == localClassName ||
        ('.' !in providerClassName || providerClassName.startsWith('.')) &&
        expanded.substringAfterLast('.') == localClassName.substringAfterLast('.')
}
