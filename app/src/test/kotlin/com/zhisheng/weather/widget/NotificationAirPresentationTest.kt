package com.zhisheng.weather.widget

import com.zhisheng.weather.model.AqiInfo
import org.junit.Assert.*
import org.junit.Test

class NotificationAirPresentationTest {
    @Test fun realProviderAndModelEstimateKeepTheirOwnLabels() {
        assertEquals("良 82", notificationAirValue(AqiInfo(value = 82, level = "良")))
        assertEquals("良 82（估算）", notificationAirValue(AqiInfo(value = 82, level = "良", estimated = true)))
    }
    @Test fun missingIndexCannotBorrowACleanAirLevel() {
        assertEquals("—", notificationAirValue(AqiInfo(level = "优", estimated = true)))
        assertEquals("—", notificationAirValue(null))
    }
}
