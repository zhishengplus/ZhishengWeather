package com.zhisheng.weather.data

import org.junit.Assert.*
import org.junit.Test

class ModuleRoutingRegressionTest {
    @Test fun restoredWeatherKeysRetainTheirSourceIdentity() {
        assertEquals("AMAP", SourcePref.from("amap").en)
        assertEquals("BAIDU", SourcePref.from("baidu").en)
    }

    @Test fun restoredSourcesRequireDeveloperMode() {
        assertEquals(SourcePref.AUTO, SourcePref.from("amap").effective(false))
        assertEquals(SourcePref.AUTO, SourcePref.from("baidu").effective(false))
    }
}
