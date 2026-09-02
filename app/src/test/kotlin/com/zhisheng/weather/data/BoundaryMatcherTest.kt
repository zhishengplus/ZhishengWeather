package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 名称匹配逻辑测试：消歧、后缀/前缀回退、区县回退所属城市。
 */
class BoundaryMatcherTest {

    private fun city(adcode: Int, name: String, lon: Double, lat: Double) =
        BoundaryRepository.Entry(adcode, name, lon, lat, blockOffset = 0, blockLength = 0)

    private val hangzhou = city(330100, "杭州市", 120.155, 30.274)
    private val beijing = city(110000, "北京市", 116.405, 39.905)
    private val changchun = city(220100, "长春市", 125.324, 43.886)
    private val hongkong = city(810000, "香港特别行政区", 114.173, 22.320)

    private val nanchuan = BoundaryRepository.Entry(500119, "南川区", 107.099, 29.157, blockOffset = 16, blockLength = 224)
    private val chaoyangBeijing = BoundaryRepository.Entry(110105, "朝阳区", 116.486, 39.921, blockOffset = 64, blockLength = 256)
    private val chaoyangChangchun = BoundaryRepository.Entry(220104, "朝阳区", 125.318, 43.865, blockOffset = 320, blockLength = 192)
    private val shenyang = city(210100, "沈阳市", 123.429, 41.796)
    private val siping = city(220300, "四平市", 124.351, 43.166)

    private val matcher = BoundaryMatcher(
        citiesByName = mapOf(
            "杭州市" to listOf(hangzhou),
            "北京市" to listOf(beijing),
            "长春市" to listOf(changchun),
            "香港特别行政区" to listOf(hongkong),
            "沈阳市" to listOf(shenyang),
            "四平市" to listOf(siping),
        ),
        citiesByAdcode = mapOf(
            330100 to hangzhou, 110000 to beijing, 220100 to changchun, 810000 to hongkong,
            210100 to shenyang, 220300 to siping,
        ),
        districtsByName = mapOf(
            "南川区" to listOf(nanchuan),
            "朝阳区" to listOf(chaoyangBeijing, chaoyangChangchun),
        ),
        parentStubsByName = mapOf(
            "铁西区" to listOf(
                ParentStub(210100, 123.351, 41.788),   // 沈阳市铁西区
                ParentStub(220300, 124.346, 43.174),   // 四平市铁西区
            ),
        ),
    )

    @Test
    fun exactCityNameResolves() {
        assertEquals(330100, matcher.resolve("杭州市", null, null, null)?.adcode)
    }

    @Test
    fun suffixStrippedQueryResolvesCity() {
        assertEquals(330100, matcher.resolve("杭州", null, null, null)?.adcode)
    }

    @Test
    fun districtWithOwnBlockResolvesDirectly() {
        assertEquals(500119, matcher.resolve("南川区", null, 29.14, 107.16)?.adcode)
    }

    @Test
    fun duplicateDistrictsDisambiguatedByNearestCenter() {
        assertEquals(110105, matcher.resolve("朝阳区", null, 40.00, 116.50)?.adcode)
        assertEquals(220104, matcher.resolve("朝阳区", null, 43.90, 125.30)?.adcode)
    }

    @Test
    fun prefixFallbackMatchesFullName() {
        assertEquals(810000, matcher.resolve("香港", null, null, null)?.adcode)
    }

    @Test
    fun stubOnlyDistrictFallsBackToParentCity() {
        // 铁西区无自身轮廓 → 回退到经纬度最近的所属城市（四平）
        assertEquals(220300, matcher.resolve("铁西区", null, 43.90, 125.30)?.adcode)
    }

    @Test
    fun prefectureShorthandResolvesAutonomousPrefecture() {
        // 「X州」口语简称 → 全称「X…自治州」（甘孜州 → 甘孜藏族自治州）
        val ganzi = city(513300, "甘孜藏族自治州", 101.96, 30.05)
        val aba = city(513200, "阿坝藏族羌族自治州", 102.22, 31.90)
        val hangzhou = city(330100, "杭州市", 120.155, 30.274)
        val m = BoundaryMatcher(
            citiesByName = mapOf(
                "甘孜藏族自治州" to listOf(ganzi),
                "阿坝藏族羌族自治州" to listOf(aba),
                "杭州市" to listOf(hangzhou),
            ),
            citiesByAdcode = mapOf(513300 to ganzi, 513200 to aba, 330100 to hangzhou),
            districtsByName = emptyMap(),
            parentStubsByName = emptyMap(),
        )
        assertEquals(513300, m.resolve("甘孜州", null, null, null)?.adcode)
        assertEquals(513200, m.resolve("阿坝州", null, null, null)?.adcode)
        // 盟级单位的口语「X州」变体（锡林郭勒州 → 锡林郭勒盟）
        val xilingol = city(152500, "锡林郭勒盟", 116.09, 43.94)
        val m2 = BoundaryMatcher(
            citiesByName = mapOf("锡林郭勒盟" to listOf(xilingol)),
            citiesByAdcode = mapOf(152500 to xilingol),
            districtsByName = emptyMap(),
            parentStubsByName = emptyMap(),
        )
        assertEquals(152500, m2.resolve("锡林郭勒州", null, null, null)?.adcode)
        // 真实「X州」城市不受简称回退影响
        assertEquals(330100, m.resolve("杭州市", null, null, null)?.adcode)
    }

    @Test
    fun unknownNameReturnsNull() {
        assertNull(matcher.resolve("亚特兰大", null, 33.75, -84.39))
    }

    @Test
    fun affiliationFallsBackToProvinceOutline() {
        // DataV 无台湾城市级数据：「台北市」按名字解析不到，回退 affiliation「台湾省」
        val taiwan = city(710000, "台湾省", 121.5, 23.7)
        val taiwanMatcher = BoundaryMatcher(
            citiesByName = mapOf("台湾省" to listOf(taiwan)),
            citiesByAdcode = mapOf(710000 to taiwan),
            districtsByName = emptyMap(),
            parentStubsByName = emptyMap(),
        )
        assertEquals(710000, taiwanMatcher.resolve("台北市", "台湾省 台北市", 25.03, 121.56)?.adcode)
        assertNull(taiwanMatcher.resolve("台北市", null, 25.03, 121.56))
    }
}
