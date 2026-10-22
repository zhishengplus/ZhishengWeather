package com.zhisheng.weather.data

import com.zhisheng.weather.model.*
import kotlinx.serialization.json.*
import okhttp3.FormBody
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.Locale

class DomesticWeatherProtocolTest {
    private val city = City("", "", 39.91488908, 116.40387397, "geo:device", coordinateKind = CoordinateKind.DEVICE,
        precisePermissionGranted = true, locationAccuracyMeters = 8f)
    private val at = Instant.parse("2026-10-11T02:00:00Z").toEpochMilli()
    private fun obj(raw: String) = Json.parseToJsonElement(raw).jsonObject
    private val now = obj("""{"results":[{"location":{"id":"BJ","name":"北京","timezone_offset":"+08:00"},"now":{"text":"晴","temperature":"20"},"last_update":"2026-10-11T09:45:00+08:00"}]}""")
    private val day = obj("""{"results":[{"location":{"id":"BJ","name":"北京","timezone_offset":"+08:00"},"daily":[{"date":"2026-10-11","high":"26","low":"13","text_day":"晴","text_night":"多云"}],"last_update":"2026-10-11T08:00:00+08:00"}]}""")
    @Test fun seniverseSendsOriginalCoordinatesEvenWithoutAnAddressInAnyLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val request = seniverseRequest(city, "test-only-key", "now")!!
            assertEquals("39.91488908:116.40387397", request.url.queryParameter("location"))
            assertEquals("https", request.url.scheme)
            assertNull(seniverseRequest(city, "", "now"))
            assertNull(seniverseRequest(city.copy(latitude = Double.NaN), "k", "now"))
        } finally { Locale.setDefault(previous) }
    }
    @Test fun mojiRejectsUnsafeAndWrongProductEndpointsBeforeCredentialsLeaveTheDevice() {
        assertTrue(validMojiEndpoint("https://aliv18.data.moji.com/whapi/json/aliweather/condition", false))
        assertTrue(validMojiEndpoint("https://aliv18.data.moji.com/whapi/json/aliweather/forecast15days", true))
        assertTrue(validMojiEndpoint("https://subscribed-product.market.alicloudapi.com/whapi/json/aliweather/condition", false))
        assertFalse(validMojiEndpoint("https://subscribed-product.market.alicloudapi.com.evil.test/whapi/json/aliweather/condition", false))
        listOf("http://aliv18.data.moji.com/whapi/json/aliweather/condition", "https://evil.test/whapi/json/aliweather/condition",
            "https://aliv18.data.moji.com.evil.test/whapi/json/aliweather/condition", "https://aliv18.data.moji.com:444/whapi/json/aliweather/condition",
            "https://aliv18.data.moji.com/whapi/json/alicityweather/condition", "https://u@aliv18.data.moji.com/whapi/json/aliweather/condition",
            "https://aliv18.data.moji.com/whapi/json/aliweather/condition?appcode=k").forEach { assertFalse(it, validMojiEndpoint(it, false)) }
    }
    @Test fun mojiUsesGcjCoordinatesAndKeepsAppCodeOutOfTheUrl() {
        val creds = MojiRuntimeCreds("test-only-appcode", "https://aliv18.data.moji.com/whapi/json/aliweather/condition", "test-only-token")
        val request = mojiRequest(city, creds)!!
        val form = request.body as FormBody
        assertEquals("APPCODE test-only-appcode", request.header("Authorization"))
        assertFalse(request.url.toString().contains("test-only"))
        val lat = form.value((0 until form.size).first { form.name(it) == "lat" }).toDouble()
        val lon = form.value((0 until form.size).first { form.name(it) == "lon" }).toDouble()
        val wgs = gcj02ToWgs84(lat, lon)
        assertEquals(city.latitude, wgs.latitude, 0.00003)
        assertEquals(city.longitude, wgs.longitude, 0.00003)
        assertNull(mojiRequest(city, creds.copy(currentToken = "")))
        assertNull(mojiRequest(city, creds, true))
    }
    @Test fun confirmedLegacyMojiAliasUsesVerifiedHttpsGatewayForRequestsAndStorage() {
        val gateway = "7b975b4aa6684075a85d760b6c1c99cf-cn-beijing.alicloudapi.com"
        for (scheme in listOf("https", "http")) {
            val creds = MojiRuntimeCreds(" test-appcode ", "$scheme://aliv8.data.moji.com/whapi/json/aliweather/condition/", " test-token ",
                "$scheme://aliv8.data.moji.com/whapi/json/aliweather/forecast15days", " day-token ")
            val current = mojiRequest(city, creds)!!
            val daily = mojiRequest(city, creds, true)!!
            assertEquals("https", current.url.scheme)
            assertEquals(443, current.url.port)
            assertEquals(gateway, current.url.host)
            assertEquals("/whapi/json/aliweather/condition", current.url.encodedPath)
            assertEquals(gateway, daily.url.host)
            assertEquals("APPCODE test-appcode", current.header("Authorization"))
            val saved = normalizedMojiCredentials(creds)
            assertEquals(current.url.toString(), saved.currentEndpoint)
            assertEquals(daily.url.toString(), saved.dailyEndpoint)
            assertTrue(saved.ready)
        }
    }
    @Test fun legacyAliasDoesNotAuthorizeArbitraryGatewaysOrMalformedUrls() {
        val path = "/whapi/json/aliweather/condition"
        listOf("https://aliv8.data.moji.com.evil.test$path", "https://aliv8.data.moji.com:444$path",
            "http://aliv8.data.moji.com:8080$path", "https://user@aliv8.data.moji.com$path",
            "https://aliv8.data.moji.com$path?token=private", "https://aliv8.data.moji.com$path#secret",
            "https://unrelated.alicloudapi.com$path", "https://aliv8.data.moji.com/whapi/json/alicityweather/condition")
            .forEach { assertNull(it, normalizedMojiEndpoint(it, false)) }
        val cleared = normalizedMojiCredentials(MojiRuntimeCreds())
        assertEquals(MojiRuntimeCreds(), cleared)
    }
    @Test fun seniverseFreeFieldsRemainMissingAndPublishTimesAreNotRefreshTimes() {
        val data = parseSeniverseWeather(now, day, null, city, at)
        assertEquals("SENIVERSE", data.dataSource)
        assertEquals(20.0, data.current!!.temperature!!, 0.0)
        assertNull(data.current!!.humidity)
        assertNull(data.current!!.windSpeed)
        assertEquals(1, data.daily.size)
        assertTrue(data.hourly.isEmpty() && data.rainMinutes.isEmpty())
        assertEquals(Instant.parse("2026-10-11T01:45:00Z").toEpochMilli(), data.updateTime)
        assertEquals(Instant.parse("2026-10-11T00:00:00Z").toEpochMilli(), data.moduleEvidence["forecast"]!!.observationTime)
        assertEquals(city.latitude, data.locationMatch!!.requestedLatitude, 0.0)
        assertFalse(data.blockSources.containsKey("alerts"))
    }
    @Test fun mismatchedCityBlocksDoNotEnterTheSameWeatherBundle() {
        val data = parseSeniverseWeather(now, obj(day.toString().replace("BJ", "SH")), null, city, at)
        assertTrue(data.daily.isEmpty())
        assertEquals(20.0, data.current!!.temperature!!, 0.0)
    }
    @Test fun authFailureAndEmptyPayloadCannotBecomeSuccessfulWeather() {
        assertNotNull(parseSeniverseWeather(obj("""{"status_code":"AP010003"}"""), null, null, city, at).error)
        assertNotNull(parseSeniverseWeather(obj("""{"results":[{"now":{}}]}"""), null, null, city, at).error)
        assertNotNull(parseMojiWeather(obj("""{"code":1,"data":{"condition":{"temp":20}}}"""), null, city, at).error)
        assertNotNull(parseMojiWeather(obj("""{"code":0,"data":{}}"""), null, city, at).error)
    }
    @Test fun mojiParsesPersonalSubscriptionWithoutInventingHourlyOrWindSpeed() {
        val m = obj("""{"code":0,"data":{"city":{"cityId":1,"name":"海淀区","ianatimezone":"Asia/Shanghai"},"condition":{"temp":"18","humidity":"45","condition":"多云","windLevel":"3","updatetime":"2026-10-11 09:30:00"}}}""")
        val d = obj("""{"code":0,"data":{"city":{"cityId":1},"forecast":[{"predictDate":"2026-10-11","tempDay":"25","tempNight":"12","conditionDay":"晴","conditionNight":"多云","updatetime":"2026-10-11 08:00:00"}]}}""")
        val data = parseMojiWeather(m, d, city, at)
        assertEquals("MOJI", data.dataSource)
        assertEquals(18.0, data.current!!.temperature!!, 0.0)
        assertNull(data.current!!.windSpeed)
        assertTrue(data.hourly.isEmpty())
        assertEquals(25.0, data.daily.single().high!!, 0.0)
        assertEquals(city.latitude, data.locationMatch!!.requestedLatitude, 0.0)
        assertNotEquals(city.longitude, data.locationMatch!!.providerLongitude)
    }
    @Test fun mojiOldDomesticProductCanOmitTimezoneWithoutLosingPublishedDates() {
        val m = obj("""{"code":0,"data":{"city":{"cityId":1,"name":"北京","counname":"中国"},"condition":{"temp":"0","updatetime":"2026-10-11 09:30:00"}}}""")
        val d = obj("""{"code":0,"data":{"city":{"cityId":1},"forecast":[{"predictDate":"2026-10-11","tempDay":"10","tempNight":"0"}]}}""")
        val data = parseMojiWeather(m, d, city, at)
        assertEquals(Instant.parse("2026-10-11T01:30:00Z").toEpochMilli(), data.updateTime)
        assertEquals(1, data.daily.size)
        assertEquals(0.0, data.current!!.temperature!!, 0.0)
        assertTrue(parseMojiWeather(m, d, city.copy(latitude = 40.0, longitude = -74.0), at).daily.isEmpty())
        assertEquals(GeoPoint(40.0, -74.0), wgs84ToGcj02(40.0, -74.0))
    }
    @Test fun tencentRequiresAnIdentifiedCityAndNeverUsesAnUnnamedCoordinateAsBeijing() {
        assertNull(tencentPublicArea(city))
        assertEquals("甘肃省" to "金昌市", tencentPublicArea(city.copy(name = "金川区", affiliation = "中国 · 甘肃省 · 金昌市")))
        assertEquals("北京市" to "北京市", tencentPublicArea(city.copy(name = "海淀区", affiliation = "北京市")))
        assertEquals("甘肃省" to "金昌市", tencentPublicArea(city.copy(name = "金川区", affiliation = "甘肃省金昌市")))
    }
    @Test fun supplierCloudAndWindPhenomenaDoNotLoseTheirIcons() {
        for ((text, condition) in listOf("晴间多云" to WeatherCondition.PARTLY_CLOUDY, "大部多云" to WeatherCondition.PARTLY_CLOUDY,
            "大风" to WeatherCondition.WIND, "局部阵雨" to WeatherCondition.RAIN)) {
            assertEquals(condition, parseSeniverseWeather(obj(now.toString().replace("晴", text)), null, null, city, at).current!!.condition)
        }
        assertNull(parseSeniverseWeather(obj(now.toString().replace("晴", "未知天气现象")), null, null, city, at).current!!.condition)
    }
}
