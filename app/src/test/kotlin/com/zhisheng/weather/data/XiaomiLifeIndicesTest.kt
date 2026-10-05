package com.zhisheng.weather.data

import org.junit.Assert.*
import org.junit.Test

class XiaomiLifeIndicesTest {
    @Test fun publicResponseFieldsAreNotInventedAsExtraAdvice() {
        val indices = XiaomiIndices(listOf(XiaomiIndexItem("uvIndex", "6"), XiaomiIndexItem("humidity", "78"),
            XiaomiIndexItem("feelsLike", "26"), XiaomiIndexItem("pressure", "1007"),
            XiaomiIndexItem("carWash", "1"), XiaomiIndexItem("sports", "0")))
        assertEquals(false, xiaomiSuitability(indices, "carWash"))
        assertEquals(true, xiaomiSuitability(indices, "sports"))
        assertEquals(listOf("强"), xiaomiLifeIndices(indices).map { it.category })
        assertEquals(listOf("UV"), xiaomiLifeIndices(indices).map { it.en })
    }
    @Test fun unknownFlagsAreNotBadWeatherAndZeroUvIsValid() {
        assertNull(xiaomiSuitability(XiaomiIndices(listOf(XiaomiIndexItem("sports", "99"))), "sports"))
        assertEquals("弱", xiaomiLifeIndices(XiaomiIndices(listOf(XiaomiIndexItem("uvIndex", "0")))).single().category)
        listOf("NaN", "Infinity", "-1", "", "unknown").forEach {
            assertTrue(xiaomiLifeIndices(XiaomiIndices(listOf(XiaomiIndexItem("uvIndex", it)))).isEmpty())
        }
    }
}
