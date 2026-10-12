package com.zhisheng.weather.widget

/** Only rebuildable caches move; widget configuration and pin acknowledgements stay. */
internal fun widgetWeatherCacheEntries(entries: Map<String, *>): Map<String, String> =
    entries.mapNotNull { (key, value) ->
        if ((key.startsWith("snapshot_") || key == "recent_cities") && value is String) key to value else null
    }.toMap()
