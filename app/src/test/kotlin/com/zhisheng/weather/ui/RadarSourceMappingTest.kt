package com.zhisheng.weather.ui

import com.zhisheng.weather.data.CaiyunRadarReason
import com.zhisheng.weather.model.RadarFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

// 雷达双数据源：彩云边界四角映射与失败文案
class RadarSourceMappingTest {

    @Test
    fun caiyunBoundsMapToMercatorQuadCorners() {
        val frame = RadarFrame(
            timeMillis = 1L,
            imageUrl = "https://cdn.caiyunapp.com/x.png",
            southLat = 10.0,
            westLng = 20.0,
            northLat = 40.0,
            eastLng = 60.0,
        )
        val quad = frame.toLatLngQuad()

        assertNotNull(quad)
        // 接口顺序 [南纬, 西经, 北纬, 东经] → 左上(北,西) 右上(北,东) 右下(南,东) 左下(南,西)
        assertEquals(40.0, quad!!.topLeft.latitude, 1e-9)
        assertEquals(20.0, quad.topLeft.longitude, 1e-9)
        assertEquals(40.0, quad.topRight.latitude, 1e-9)
        assertEquals(60.0, quad.topRight.longitude, 1e-9)
        assertEquals(10.0, quad.bottomRight.latitude, 1e-9)
        assertEquals(60.0, quad.bottomRight.longitude, 1e-9)
        assertEquals(10.0, quad.bottomLeft.latitude, 1e-9)
        assertEquals(20.0, quad.bottomLeft.longitude, 1e-9)
    }

    @Test
    fun incompleteBoundsYieldNullQuad() {
        val frame = RadarFrame(timeMillis = 1L, imageUrl = "https://x/y.png", southLat = 10.0, westLng = 20.0)
        assertNull(frame.toLatLngQuad())
    }

    @Test
    fun caiyunFailureReasonsHaveUserFacingCopy() {
        assertEquals("未配置彩云 Token", caiyunRadarMessage(CaiyunRadarReason.NOT_CONFIGURED))
        assertEquals("彩云雷达权限未开通", caiyunRadarMessage(CaiyunRadarReason.NO_PERMISSION))
        assertEquals("彩云雷达服务不可用", caiyunRadarMessage(CaiyunRadarReason.SERVICE_UNAVAILABLE))
        assertEquals("彩云未返回可用帧", caiyunRadarMessage(CaiyunRadarReason.EMPTY_FRAMES))
    }
}
