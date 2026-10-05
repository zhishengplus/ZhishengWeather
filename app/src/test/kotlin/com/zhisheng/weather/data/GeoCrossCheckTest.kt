package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoCrossCheckTest {

    @Test
    fun normalizeStripsAdminSuffixesRepeatedly() {
        assertEquals("平舆", normalizePlaceName("平舆县"))
        assertEquals("滨湖", normalizePlaceName("滨湖新区"))
        assertEquals("北京市海淀", normalizePlaceName(" 北京市 海淀区 "))
        assertEquals("平舆", normalizePlaceName("平舆　县"))
        assertEquals("", normalizePlaceName(null))
        assertEquals("", normalizePlaceName("　 "))
    }

    @Test
    fun crossCheckUsesAdcodeAsPrimaryEvidence() {
        assertEquals(
            GeoMatchLevel.MATCH_HIGH,
            crossCheckDistricts("410423", "410423", "平舆县", "平舆县"),
        )
        // 同城不同码：区划库版本差/开发区口径差 → 降置信但不拒绝
        assertEquals(
            GeoMatchLevel.MATCH_CITY_GRAIN,
            crossCheckDistricts("370112", "370102", "高新区", "历下区"),
        )
        assertEquals(
            GeoMatchLevel.MISMATCH,
            crossCheckDistricts("410423", "310101", "平舆县", "徐汇区"),
        )
    }

    @Test
    fun crossCheckFallsBackToNameComparisonWhenAdcodeMissing() {
        assertEquals(GeoMatchLevel.MATCH_HIGH, crossCheckDistricts(null, null, "平舆县", "平舆县"))
        assertEquals(GeoMatchLevel.MATCH_HIGH, crossCheckDistricts(null, null, "滨湖新区", "滨湖区"))
        assertEquals(GeoMatchLevel.MATCH_CITY_GRAIN, crossCheckDistricts(null, null, "徐汇区", "徐家汇区"))
        assertEquals(GeoMatchLevel.MISMATCH, crossCheckDistricts(null, null, "平舆县", "徐汇区"))
    }

    @Test
    fun crossCheckTwoCharNamesRequireExactOrContainment() {
        // 2 字名禁用一字容错："朝阳/朝阴"一字之差必须拒绝
        assertEquals(GeoMatchLevel.MISMATCH, crossCheckDistricts(null, null, "朝阴县", "朝阳区"))
        assertEquals(GeoMatchLevel.MATCH_HIGH, crossCheckDistricts(null, null, "朝阳区", "朝阳区"))
        // 无从比对（缺名）只能标为降置信，不能冒充高置信
        assertEquals(GeoMatchLevel.MATCH_CITY_GRAIN, crossCheckDistricts(null, null, null, null))
    }

    @Test
    fun oneEditApartSupportsTheThreeCharGate() {
        assertTrue(isOneEditApart("余杭区", "徐杭区"))
        assertFalse(isOneEditApart("余杭区", "上城区"))
        assertTrue(isOneEditApart("滨湖", "滨湖"))
    }

    @Test
    fun mergeStreetSegmentsDedupesAndKeepsAtMostTwo() {
        assertEquals(
            "建设街道·滨湖路",
            mergeStreetSegments(
                listOf("建设街道·滨湖路", "滨湖路·市民广场"),
                exclude = listOf("滨湖区"),
            ),
        )
        assertNull(
            mergeStreetSegments(
                listOf("滨湖区", "滨湖"),
                exclude = listOf("滨湖区"),
            ),
        )
    }

    @Test
    fun malformedAdcodesDoNotCreateFalsePrefixMatches() {
        assertEquals(
            GeoMatchLevel.MISMATCH,
            crossCheckDistricts("41", "41", "徐汇区", "平舆县"),
        )
    }

    @Test
    fun cityGrainConflictSelectsOneStreetInsteadOfInventingAMergedAddress() {
        assertEquals("建设街道·滨湖路", moreCompleteStreet("建设街道·滨湖路", "中心路"))
        assertEquals("中心路", moreCompleteStreet(null, "中心路"))
        assertNull(moreCompleteStreet(null, " "))
    }
}
