package com.zhisheng.weather.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TyphoonMapStyleTest {
    private val testToken = "0".repeat(32)

    @Test
    fun standardAndSatelliteUseMatchingOfficialLabels() {
        for ((satellite, pair) in listOf(false to listOf("vec", "cva"), true to listOf("img", "cia"))) {
            val style = Json.parseToJsonElement(typhoonMapStyleJson(testToken, true, satellite)).jsonObject
            val sources = style.getValue("sources").jsonObject
            assertEquals(pair.map { "tdt-$it" }.toSet(), sources.keys)
            for (layer in pair) {
                val url = sources.getValue("tdt-$layer").jsonObject.getValue("tiles").jsonArray.single().jsonPrimitive.content
                assertTrue(url.startsWith("https://t0.tianditu.gov.cn/${layer}_w/wmts?"))
                assertTrue(url.contains("TILEMATRIXSET=w"))
                assertTrue(url.contains("TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"))
            }
            assertEquals(TYPHOON_TDT_LABEL_LAYER, style.getValue("layers").jsonArray.last().jsonObject.getValue("id").jsonPrimitive.content)
        }
    }

    @Test
    fun missingOrMalformedTokenNeverLoadsAnotherMapProvider() {
        for (token in listOf("", " ", "bad\"token", "https://other.test/key")) {
            val style = Json.parseToJsonElement(typhoonMapStyleJson(token, true)).jsonObject
            assertTrue(style.getValue("sources").jsonObject.isEmpty())
            assertEquals(1, style.getValue("layers").jsonArray.size)
            assertFalse(validTyphoonMapToken(token))
        }
    }

    @Test
    fun themesKeepGeographyAndAdministrativeNamesUnmodified() {
        val light = Json.parseToJsonElement(typhoonMapStyleJson(testToken, true)).jsonObject
        val dark = Json.parseToJsonElement(typhoonMapStyleJson(testToken, false)).jsonObject
        assertEquals(light.getValue("sources"), dark.getValue("sources"))
        assertEquals(light.getValue("layers").jsonArray.drop(1), dark.getValue("layers").jsonArray.drop(1))
        assertTrue(validTyphoonMapToken(testToken))
    }

    @Test
    fun serviceProbeRejectsAuthErrorBodyEvenWhenHttpReportsSuccess() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())
        assertTrue(typhoonTileHeaderValid("image/png", png))
        assertTrue(typhoonTileHeaderValid("image/jpg; charset=binary", jpeg))
        assertFalse(typhoonTileHeaderValid("application/xml", "<error>invalid token</error>".toByteArray()))
        assertFalse(typhoonTileHeaderValid("image/png", "<error>invalid token</error>".toByteArray()))
        assertFalse(typhoonTileHeaderValid("image/png", byteArrayOf()))
    }
}
