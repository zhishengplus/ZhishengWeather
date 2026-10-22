package com.zhisheng.weather.ui

import org.junit.Assert.*
import org.junit.Test

class AqiPresentationTest {
    @Test fun modelEstimatesRemainExplicitWithoutRelabellingProviderValues() {
        assertEquals("120（估算）", Fmt.aqiValueLabel(120, true))
        assertEquals("120", Fmt.aqiValueLabel(120))
        assertEquals("模型估算，非监测AQI", Fmt.aqiBasisLabel("美国", true))
        assertEquals("AQI · 美标", Fmt.aqiBasisLabel("美国"))
    }

    @Test fun missingIndexNeverDisplaysZeroOrAnExcellentLevel() {
        assertEquals("—", Fmt.aqiValueLabel(null, true))
        assertEquals("AQI暂无数据", Fmt.aqiLevelLabel(null, "优"))
        assertEquals("优", Fmt.aqiLevelLabel(0, "优"))
        assertEquals("空气质量", Fmt.aqiLevelLabel(40, null))
    }
}
