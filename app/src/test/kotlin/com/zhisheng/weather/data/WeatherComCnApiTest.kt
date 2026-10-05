package com.zhisheng.weather.data

import com.zhisheng.weather.model.City
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 中国天气网（d1.weather.com.cn + city.js 码表）解析测试。fixture 为 2026-09-14 实测结构。 */
class WeatherComCnApiTest {

    private val cityJs = """var city_data =
    {
      "北京": { "北京": {
        "北京": { "AREAID": "101010100", "NAMECN": "北京" },
        "海淀": { "AREAID": "101010200", "NAMECN": "海淀" } } },
      "甘肃": { "金昌": {
        "金昌": { "AREAID": "101160601", "NAMECN": "金昌" },
        "永昌": { "AREAID": "101160602", "NAMECN": "永昌" },
        "金川": { "AREAID": "101160603", "NAMECN": "金川" } } }
    }"""

    @Test
    fun cityJsParsesDistricts() {
        val zones = WeatherComCnApi.parseCityJs(cityJs)!!
        assertEquals(5, zones.size)
        assertEquals(
            "101010100",
            zones.first { it.province == "北京" && it.district == "北京" }.areaId,
        )
    }

    @Test
    fun zoneMatchingHandlesDistrictAndParentFallback() {
        val zones = WeatherComCnApi.parseCityJs(cityJs)!!
        // 区县剥后缀精确命中（金川区 → "金川"，实测 AREAID 101160603）
        val jc = WeatherComCnApi.matchZone(
            City("金川区", "甘肃省金昌市", 38.52, 102.19, "k"), zones,
        )!!
        assertEquals("101160603", jc.areaId)
        val yc = WeatherComCnApi.matchZone(
            City("永昌县", "甘肃省金昌市", 38.2, 101.9, "k"), zones,
        )!!
        assertEquals("101160602", yc.areaId)
        // 直辖市直辖区县
        val bj = WeatherComCnApi.matchZone(
            City("海淀区", "北京市", 39.9, 116.3, "k"), zones,
        )!!
        assertEquals("101010200", bj.areaId)
        // 省外不串码
        assertNull(WeatherComCnApi.matchZone(City("普陀区", "浙江省舟山市", 30.0, 122.3, "k"), zones))
    }

    @Test
    fun skParsesVisibilityAndAqi() {
        val text = """var dataSK={"nameen":"beijing","cityname":"北京","city":"101010100","temp":"25.1",
            "WD":"南风","wde":"S","WS":"2级","wse":"8km/h","SD":"29%","qy":"1014","njd":"30km",
            "time":"17:25","rain":"0","rain24h":"0","aqi":"29","weather":"晴","weathercode":"d00",
            "date":"09月14日(星期一)"}"""
        val sk = WeatherComCnApi.parseSk(text)!!
        assertEquals(25.1, sk.temperature!!, 0.001)
        assertEquals(29.0, sk.humidity!!, 0.001)
        assertEquals(1014.0, sk.pressure!!, 0.001)
        assertEquals(30.0, sk.visibilityKm!!, 0.001) // 其他免费源没有的字段
        assertEquals(29, sk.aqi)
        assertEquals("0", sk.weatherCode) // "d00" → 国标 0
        assertEquals(8.0, sk.windSpeedKmh!!, 0.001)
    }

    @Test
    fun indexTextParsesTodayAlarmAndIndices() {
        val text = """var cityDZ ={"weatherinfo":{"city":"北京","temp":"999","tempn":"18","weather":"晴",
            "wd":"西南风转北风","ws":"3-4级转<3级","weathercode":"d0","weathercoden":"n0","fctime":"202609140800"}};
            var alarmDZ ={"w":[{"w1":"海南省","w2":"三亚市","w5":"暴雨","w7":"橙色","w8":"2026-09-14 13:40",
            "w9":"详情文本","w13":"海南省三亚市发布暴雨橙色预警信号"}]};
            var dataZS ={"zs":{"date":"2026091411","cl_name":"穿衣指数","cl_hint":"较热","cl_des_s":"x",
            "xc_name":"洗车指数","xc_hint":"适宜","xc_des_s":"y"},"cn":"北京"};"""
        val (today, alarms, indices) = WeatherComCnApi.parseIndexText(text)
        assertNull(today!!.high) // "999" 哨兵 → 缺失
        assertEquals(18.0, today.low!!, 0.001)
        assertEquals("0", today.dayCode)
        assertEquals(1, alarms.size)
        assertEquals("海南省三亚市发布暴雨橙色预警信号", alarms[0].title)
        assertEquals("暴雨", alarms[0].type)
        assertEquals("橙色", alarms[0].level)
        assertEquals(2, indices.size) // date 不是指数
        assertEquals("洗车指数", indices.first { it.en == "xc" }.name)
        assertEquals("适宜", indices.first { it.en == "xc" }.level)
    }

    @Test
    fun extractVarHandlesNestedBraces() {
        val text = """var dataZS ={"zs":{"a_name":"A","a_hint":"h"},"cn":"北京"};var fc ={"x":1};"""
        val zs = WeatherComCnApi.extractVar(text, "dataZS")!!
        assertTrue(zs.containsKey("zs"))
        val fc = WeatherComCnApi.extractVar(text, "fc")!!
        assertEquals("1", fc["x"].toString())
        assertNull(WeatherComCnApi.extractVar(text, "missing"))
    }
}
