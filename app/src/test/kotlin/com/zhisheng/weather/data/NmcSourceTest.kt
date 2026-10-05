package com.zhisheng.weather.data

import com.zhisheng.weather.model.AlertLevel
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 中央气象台（nmc.cn）解析测试。fixture 为 2026-09-14 从真实页面截取的结构样本
 * （数值/日期以当时实况为准，只断言结构映射，不断言具体天气值）。
 */
class NmcSourceTest {

    @Test fun realFallsBackToRecognizedTextWhenIconCodeIsMissingOrUnknown() {
        listOf(null, "9999", "future-code").forEach { code ->
            val current = NmcSource.parseNmcReal(NmcApi.NmcReal(
                weather = NmcApi.NmcWeather(img = code, info = " 雷阵雨 ", temperature = 18.0)))!!
            assertEquals(WeatherCondition.THUNDERSTORM, current.condition)
            assertEquals("雷阵雨", current.weatherText)
            assertEquals(18.0, current.temperature!!, 0.001)
        }
    }

    @Test fun realMissingConditionDoesNotFabricateClearWeatherOrLoseTemperature() {
        listOf(null, "9999", "-", "未知").forEach { info ->
            val current = NmcSource.parseNmcReal(NmcApi.NmcReal(
                weather = NmcApi.NmcWeather(img = "9999", info = info, temperature = 18.0)))!!
            assertEquals(WeatherCondition.UNKNOWN, current.condition)
            assertEquals("天气现象暂缺", current.weatherText)
            assertEquals(18.0, current.temperature!!, 0.001)
        }
    }

    @Test fun sanyaRealResponseHasValidTemperatureButNoPublishedPhenomenon() {
        // /rest/weather?stationid=PtnWb, 2026-09-28 08:20 Beijing time.
        // The separate /f/rest/real/PtnWb endpoint returned the same fields.
        val raw = """{"station":{"code":"PtnWb","province":"海南省","city":"三亚"},
            "publish_time":"2026-09-28 08:20","weather":{"temperature":30.3,
            "humidity":82.0,"rain":0.0,"info":"-","img":"9999","feelst":36.5}}"""
        val real = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<NmcApi.NmcReal>(raw)
        val current = NmcSource.parseNmcReal(real)!!
        assertEquals(30.3, current.temperature!!, 0.001)
        assertEquals(36.5, current.feelsLike!!, 0.001)
        assertEquals(WeatherCondition.UNKNOWN, current.condition)
        assertEquals("天气现象暂缺", current.weatherText)
    }


    @Test fun realKeepsKnownIconAndUnrecognizedProviderText() {
        val known = NmcSource.parseNmcReal(NmcApi.NmcReal(
            weather = NmcApi.NmcWeather(img = "07", info = null)))!!
        assertEquals(WeatherCondition.DRIZZLE, known.condition)
        val unknown = NmcSource.parseNmcReal(NmcApi.NmcReal(
            weather = NmcApi.NmcWeather(img = "9999", info = "新的天气现象")))!!
        assertEquals(WeatherCondition.UNKNOWN, unknown.condition)
        assertEquals("新的天气现象", unknown.weatherText)
    }

    // —— 实况 JSON ——
    private val realJson = """
    {"station":{"code":"Wqsps","province":"北京市","city":"北京","url":"/publish/forecast/ABJ/beijing.html"},
     "publish_time":"2026-09-14 10:00",
     "weather":{"temperature":23.0,"temperatureDiff":-3.4,"airpressure":1021.0,"humidity":34.0,
                "rain":0.0,"rcomfort":62,"icomfort":0,"info":"晴","img":"0","feelst":22.2},
     "wind":{"direct":"东北风","degree":49.0,"power":"微风","speed":2.5},
     "warn":{"alert":"9999","pic":"9999"},
     "sunriseSunset":{"sunrise":"2026-09-14 05:53","sunset":"2026-09-14 18:25"}}
    """.trimIndent()

    @Test fun realJsonMapsToCurrentWeather() {
        val real = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString<NmcApi.NmcReal>(realJson)
        val current = NmcSource.parseNmcReal(real)
        assertNotNull(current)
        assertEquals(23.0, current!!.temperature!!, 0.001)
        assertEquals(22.2, current.feelsLike!!, 0.001)
        assertEquals(WeatherCondition.CLEAR, current.condition)
        assertEquals("晴", current.weatherText)
        assertEquals(34.0, current.humidity!!, 0.001)
        assertEquals(1021.0, current.pressure!!, 0.001)
        assertEquals(49.0, current.windDirectionDeg!!, 0.001)
        assertEquals(9.0, current.windSpeed!!, 0.001) // nmc 是 m/s（2.5），App 内部 km/h
    }

    // —— 逐 3 小时（hourValues 区块；hourimg 的 div 内是 <img>，不产生值） ——
    private val hourlyHtml = """
    <div id=hourValues><div style="width:4704px;position: relative;">
    <div id=day0 class="clearfix pull-left">
    <div class="hour3 hbg"><div> 11:00 </div><div class=hourimg style="padding-top: 10px;"><img src="https://image.nmc.cn/assets/img/w/40x40/3/0.png"></div><div> - </div><div class=tmp_lte_30> 25.6° </div><div> 2.6m/s </div><div> 西北风 </div><div class=hide> 1020.3hPa </div><div> 32.4% </div><div class=hide> 0% </div></div>
    <div class="hour3 hbg"><div> 14:00 </div><div class=hourimg style="padding-top: 10px;"><img src="https://image.nmc.cn/assets/img/w/40x40/3/0.png"></div><div> - </div><div class=tmp_lte_30> 26.2° </div><div> 3m/s </div><div> 西南风 </div><div class=hide> 1017.4hPa </div><div> 32.8% </div><div class=hide> 0% </div></div>
    <div class="hour3 hbg"><div> 23:00 </div><div class=hourimg style="padding-top: 10px;"><img src="https://image.nmc.cn/assets/img/w/40x40/3/0.png"></div><div> - </div><div class=tmp_lte_20> 21.2° </div><div> 2.4m/s </div><div> 东北风 </div><div class=hide> 1014.6hPa </div><div> 54.3% </div><div class=hide> 1.5% </div></div>
    </div>
    <div id=day1 class="clearfix pull-left">
    <div class="hour3 hbg"><div> 02:00 </div><div class=hourimg style="padding-top: 10px;"><img src="https://image.nmc.cn/assets/img/w/40x40/3/0.png"></div><div> - </div><div class=tmp_lte_20> 20.1° </div><div> 2.1m/s </div><div> 东北风 </div><div class=hide> 1015.0hPa </div><div> 55.0% </div><div class=hide> 2% </div></div>
    </div>
    </div></div>
    """.trimIndent()

    // —— 7 天预报（/rest/weather 的 predict.detail 结构化 JSON，2026-09-14 金昌站真实样本） ——
    private val predictJson = """
    {"station":{"code":"chtaw","province":"甘肃省","city":"金昌","url":"/publish/forecast/AGS/jinchang.html"},
     "publish_time":"2026-09-14 12:00",
     "detail":[
      {"date":"2026-09-14","pt":"2026-09-14 12:00","day":{"weather":{"info":"晴","img":"0","temperature":"26"},"wind":{"direct":"无持续风向","power":"微风"}},"night":{"weather":{"info":"多云","img":"1","temperature":"10"},"wind":{"direct":"无持续风向","power":"微风"}},"precipitation":0.0},
      {"date":"2026-09-19","pt":"2026-09-19 08:00","day":{"weather":{"info":"小雨","img":"7","temperature":"23"},"wind":{"direct":"无持续风向","power":"微风"}},"night":{"weather":{"info":"小雨","img":"7","temperature":"14"},"wind":{"direct":"无持续风向","power":"微风"}},"precipitation":20.0}
     ]}
    """.trimIndent()

    @Test fun predictJsonParsesDayNightAndPrecip() {
        val predict = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString<NmcApi.NmcPredict>(predictJson)
        val sun = NmcApi.NmcSun(sunrise = "2026-09-14 06:53", sunset = "2026-09-14 19:25")
        val daily = NmcSource.parseNmcPredict(predict, sun)
        assertEquals(2, daily.size)
        val today = daily[0]
        assertEquals(26.0, today.high!!, 0.001)
        assertEquals(10.0, today.low!!, 0.001)
        // JSON 的 img 是真国标码：0=晴、1=多云
        assertEquals(WeatherCondition.PARTLY_CLOUDY, today.condition)
        assertEquals("晴转多云", today.weatherText)
        assertEquals("06:53", today.sunrise)
        assertEquals("19:25", today.sunset)
        val rainy = daily[1]
        assertEquals(20.0, rainy.precipMm!!, 0.001)
        assertEquals(WeatherCondition.DRIZZLE, rainy.condition)
        // 首日之外不带日出日落
        assertNull(daily[1].sunrise)
    }

    @Test fun dailyConditionRetainsMoreSignificantNightWeather() {
        fun day(dayCode: String?, nightCode: String?) = NmcApi.NmcPredictDay(
            date = "2026-09-18",
            day = NmcApi.NmcHalf(weather = NmcApi.NmcHalfWeather(img = dayCode)),
            night = NmcApi.NmcHalf(weather = NmcApi.NmcHalfWeather(img = nightCode)),
        )
        fun condition(dayCode: String?, nightCode: String?) = NmcSource.parseNmcPredict(
            NmcApi.NmcPredict(detail = listOf(day(dayCode, nightCode))), null,
        ).single().condition

        assertEquals(WeatherCondition.THUNDERSTORM, condition("0", "4"))
        assertEquals(WeatherCondition.THUNDERSTORM, condition("4", "0"))
        assertEquals(WeatherCondition.SNOW, condition("0", "15"))
        assertEquals(WeatherCondition.CLEAR, condition("9999", "0"))
        assertEquals(WeatherCondition.CLEAR, condition("0", null))
        assertEquals(WeatherCondition.UNKNOWN, condition("9999", null))
    }

    @Test fun airParsesValueAndRejectsEmpty() {
        val beijing = NmcSource.parseNmcAir(NmcApi.NmcAir(forecasttime = "2026-09-14 14:00", aqi = "24", text = "优"))
        assertEquals(24, beijing?.value)
        assertEquals("优", beijing?.level)
        assertEquals("中国", beijing?.standard)
        assertNull(NmcSource.parseNmcAir(NmcApi.NmcAir(aqi = "", text = "")))
        assertNull(NmcSource.parseNmcAir(NmcApi.NmcAir(aqi = "9999")))
    }

    @Test fun passedChartKeepsOnlyRecentTwoHours() {
        val now = java.time.LocalDateTime.of(2026, 9, 14, 12, 0)
            .toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli()
        fun row(hour: Int, temp: Double?) = NmcApi.NmcPassed(
            time = String.format("2026-09-14 %02d:00", hour), temperature = temp,
            humidity = 40.0, pressure = 880.0, windDirection = 300.0, windSpeed = 3.0, rain1h = 0.0,
        )
        val kept = NmcSource.parseNmcPassed(listOf(row(8, 20.0), row(10, 22.0), row(11, 23.0), row(12, 24.0)), now)
        // 12:00 当前 + 11:00 上一小时；10:00 及更早被宽限窗裁掉
        assertEquals(2, kept.size)
        assertEquals(23.0, kept[0].temperature!!, 0.001)
        assertEquals(24.0, kept[1].temperature!!, 0.001)
        // 9999 哨兵值（超合理域）应被过滤
        val sentinel = NmcSource.parseNmcPassed(listOf(NmcApi.NmcPassed(
            time = "2026-09-14 11:00", temperature = 23.0, humidity = 9999.0,
            pressure = 9999.0, windDirection = 9999.0, windSpeed = 9999.0, rain1h = 0.0,
        )), now)
        assertEquals(1, sentinel.size)
        assertNull(sentinel[0].humidity)
        assertNull(sentinel[0].pressure)
        assertNull(sentinel[0].windDirectionDeg)
        assertNull(sentinel[0].windSpeed)
    }

    @Test fun hourlyHtmlParsesValuesAndCrossDay() {
        // 2026-09-14 10:00 北京时间起算
        val now = java.time.LocalDateTime.of(2026, 9, 14, 10, 0)
            .toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli()
        val hourly = NmcSource.parseNmcHourly(hourlyHtml, now)
        assertEquals(4, hourly.size)
        val first = hourly[0]
        assertEquals(25.6, first.temperature!!, 0.001)
        assertEquals(9.36, first.windSpeed!!, 0.001) // 2.6 m/s → km/h
        assertEquals(315.0, first.windDirectionDeg!!, 0.001) // 西北风
        assertEquals(1020.3, first.pressure!!, 0.001)
        assertEquals(32.4, first.humidity!!, 0.001)
        assertEquals(0, first.precipProb)
        // 图标尾段是国标码：3/0 → 晴
        assertEquals(WeatherCondition.CLEAR, first.condition)
        // 23:00 之后的 02:00 归入次日
        assertTrue("跨日递推：02:00 应晚于 23:00", hourly[3].timeMillis > hourly[2].timeMillis)
        val zone = java.time.ZoneOffset.ofHours(8)
        assertEquals(2, java.time.Instant.ofEpochMilli(hourly[3].timeMillis).atZone(zone).hour)
        val crossDay = java.time.Instant.ofEpochMilli(hourly[3].timeMillis).atZone(zone).toLocalDate()
        assertEquals(java.time.LocalDate.of(2026, 9, 15), crossDay) // 23:00 之后的 02:00 = 次日 9/15
    }

    // —— 预警标题解析与城市过滤 ——
    @Test fun alarmTitlesParseAndFilter() {
        val ref = NmcSource.NmcCityRef("AHI", "TtrxS", "/publish/forecast/AHI/wanning.html", "海南省", "万宁")
        val alarms = listOf(
            NmcApi.NmcAlarm("海南省万宁市气象台发布暴雨红色预警信号", "2026/09/14 10:17", "/publish/alarm/1.html"),
            NmcApi.NmcAlarm("海南省海口市气象台发布大风蓝色预警信号", "2026/09/14 10:16", "/publish/alarm/2.html"),
            NmcApi.NmcAlarm("海南省气象台发布台风黄色预警信号", "2026/09/14 09:00", "/publish/alarm/3.html"),
            NmcApi.NmcAlarm("甘肃省金昌市气象台发布寒潮蓝色预警信号", "2026/09/14 08:00", "/publish/alarm/4.html"),
        )
        val matched = NmcSource.parseNmcAlarms(alarms, ref)
        assertEquals(2, matched.size) // 万宁市级 + 海南省级
        assertEquals(AlertLevel.RED, matched[0].severity)
        assertEquals("暴雨", matched[0].type)
        assertEquals(AlertLevel.YELLOW, matched[1].severity)
    }

    @Test fun alarmTitleIgnoresMalformed() {
        assertNull(NmcSource.parseAlarmTitle("今日天气提示"))
        assertNotNull(NmcSource.parseAlarmTitle("河北省张家口市气象台发布大风蓝色预警信号"))
    }

    @Test fun provincialAlertsNeverLeakAcrossProvinces() {
        // 真机事故：上海宝山收到海南省级预警——省级标题地名被剥成空串后
        // “城市名.contains(空串)”恒真。回归：跨省省级预警一律不命中。
        val baoshan = NmcSource.NmcCityRef("ABJ", "sNifH", "/publish/forecast/ABJ/baoshan.html", "上海市", "宝山")
        val alarms = listOf(
            NmcApi.NmcAlarm("海南省气象台发布台风黄色预警信号", "2026/09/14 09:00", "/a/1.html"),
            NmcApi.NmcAlarm("海南省万宁市气象台发布暴雨红色预警信号", "2026/09/14 10:17", "/a/2.html"),
            NmcApi.NmcAlarm("甘肃省金昌市气象台发布寒潮蓝色预警信号", "2026/09/14 08:00", "/a/3.html"),
            NmcApi.NmcAlarm("上海市气象台发布大风蓝色预警信号", "2026/09/14 11:00", "/a/4.html"),
            NmcApi.NmcAlarm("上海市宝山区气象台发布雷雨大风黄色预警信号", "2026/09/14 11:10", "/a/5.html"),
        )
        val matched = NmcSource.parseNmcAlarms(alarms, baoshan)
        assertEquals(2, matched.size)
        assertTrue(matched.all { it.title.startsWith("上海市") })
        assertEquals(AlertLevel.YELLOW, matched[1].severity)
        // 本省省级预警仍应命中本市城市：万宁收 省级台风 + 本市暴雨 = 2 条
        val wanning = NmcSource.NmcCityRef("AHI", "TtrxS", "/publish/forecast/AHI/wanning.html", "海南省", "万宁")
        val forWanning = NmcSource.parseNmcAlarms(alarms, wanning)
        assertEquals(2, forWanning.size)
        assertTrue(forWanning.any { it.title.contains("海南省气象台") })
        assertTrue(forWanning.any { it.title.contains("万宁市") })
    }

    @Test fun sameNameCityAlertsNeverLeakAcrossProvinces() {
        val chaoyang = NmcSource.NmcCityRef("ALN", "ln-chaoyang", "/chaoyang.html", "辽宁省", "朝阳")
        val alarms = listOf(
            NmcApi.NmcAlarm("北京市朝阳区气象台发布暴雨黄色预警信号", "2026/09/30 10:00", "/a/beijing.html"),
            NmcApi.NmcAlarm("辽宁省朝阳市气象台发布大风蓝色预警信号", "2026/09/30 10:01", "/a/local.html"),
            NmcApi.NmcAlarm("辽宁省气象台发布寒潮黄色预警信号", "2026/09/30 10:02", "/a/province.html"),
            NmcApi.NmcAlarm("朝阳市气象台发布雷电橙色预警信号", "2026/09/30 10:03", "/a/local-short.html"),
        )

        assertEquals(
            listOf(
                "辽宁省朝阳市气象台发布大风蓝色预警信号",
                "辽宁省气象台发布寒潮黄色预警信号",
                "朝阳市气象台发布雷电橙色预警信号",
            ),
            NmcSource.parseNmcAlarms(alarms, chaoyang).map { it.title },
        )
    }

    @Test fun autonomousRegionProvincialAlertsMatchShortName() {
        val nanning = NmcSource.NmcCityRef("AGX", "gx-nanning", "/nanning.html", "广西壮族自治区", "南宁")
        val alarm = NmcApi.NmcAlarm("广西气象台发布暴雨黄色预警信号", "2026/09/30 10:00", "/a/guangxi.html")

        assertEquals(listOf(alarm.title), NmcSource.parseNmcAlarms(listOf(alarm), nanning).map { it.title })
    }

    // —— 省份匹配：全称、简称、直辖市 ——
    @Test fun predictToleratesEmptyStringFields() {
        // 真机全国抽查事故：万宁 predict.detail[5].precipitation = ""（缺数据用空串表示），
        // 严格 Double 解码让整个 /rest/weather 响应报废。宽松解码必须整体成功且该天降水为空。
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        val raw = """
        {"msg":"success","code":0,"data":{
          "real":{"weather":{"temperature":27.2,"humidity":"94","airpressure":9999.0,"info":"小雨","img":"7"},
                  "wind":{"direct":"9999","degree":"","speed":""},
                  "publish_time":"2026-09-14 15:40",
                  "sunriseSunset":{"sunrise":"2026-09-14 06:26","sunset":"2026-09-14 18:33"}},
          "predict":{"detail":[
            {"date":"2026-09-14","day":{"weather":{"info":"小雨","img":"7","temperature":"26"},"wind":{"direct":"西南风","power":"3-4级"}},"night":{"weather":{"info":"多云","img":"1","temperature":"24"},"wind":{"direct":"南风","power":"微风"}},"precipitation":35.1},
            {"date":"2026-09-19","day":{"weather":{"info":"小雨","img":"7","temperature":"26"}},"night":{"weather":{"info":"小雨","img":"7","temperature":"24"},"wind":{}},"precipitation":""}
          ]},
          "air":"",
          "passedchart":[{"time":"2026-09-14 14:00","temperature":27.0,"humidity":"90","pressure":1008.0,"windDirection":"180","windSpeed":"4.0","rain1h":0.3}]
        }}
        """.trimIndent()
        val env = json.decodeFromString<NmcApi.NmcFullEnvelope>(raw)
        val full = env.data!!
        val current = NmcSource.parseNmcReal(full.real!!)!!
        assertEquals(27.2, current.temperature!!, 0.001)
        assertEquals(WeatherCondition.DRIZZLE, current.condition) // 国标 7 = 小雨
        assertNull(current.windSpeed) // speed "" → null 而非整包失败
        val daily = NmcSource.parseNmcPredict(full.predict, full.real.sunriseSunset)
        assertEquals(2, daily.size)
        assertEquals(35.1, daily[0].precipMm!!, 0.001)
        assertNull(daily[1].precipMm) // "" → null
        assertEquals("06:26", daily[0].sunrise)
        assertNull(NmcSource.parseNmcAir(NmcApi.decodeAir(full.air))) // air "" → 无 AQI
        val passed = NmcSource.parseNmcPassed(full.passedchart, nowMillis = 1789358400000L) // 2026-09-14 12:00 北京
        // 14:00（北京）晚于 12:00 → 未来条目被过滤
        assertEquals(0, passed.size)
    }

    @Test fun predictParsesSevenDaysWithPrecipitationAndSun() {
        val real = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString<NmcApi.NmcReal>(
                """{"weather":{"temperature":23.0,"info":"晴","img":"0","humidity":34.0},
                    "publish_time":"2026-09-14 10:00",
                    "sunriseSunset":{"sunrise":"2026-09-14 05:53","sunset":"2026-09-14 18:25"}}"""
            )
        val predict = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString<NmcApi.NmcPredict>(
                """{"detail":[
                    {"date":"2026-09-14","day":{"weather":{"info":"晴","img":"0","temperature":"26"},"wind":{"direct":"东北风","power":"微风"}},"night":{"weather":{"info":"多云","img":"1","temperature":"16"},"wind":{"direct":"东风","power":"微风"}},"precipitation":0.0},
                    {"date":"2026-09-19","day":{"weather":{"info":"小雨","img":"7","temperature":"23"}},"night":{"weather":{"info":"小雨","img":"7","temperature":"14"}},"precipitation":20.0}
                ]}"""
            )
        val daily = NmcSource.parseNmcPredict(predict, real.sunriseSunset)
        assertEquals(2, daily.size)
        val today = daily[0]
        assertEquals(26.0, today.high!!, 0.001)
        assertEquals(16.0, today.low!!, 0.001)
        assertEquals(WeatherCondition.PARTLY_CLOUDY, today.condition) // 全天现象覆盖夜间多云
        assertEquals("晴转多云", today.weatherText)
        assertEquals(45.0, today.windDirectionDeg!!, 0.001)
        assertEquals(0.0, today.precipMm!!, 0.001)
        assertEquals("05:53", today.sunrise)
        assertEquals("18:25", today.sunset)
        val rainy = daily[1]
        assertEquals(WeatherCondition.DRIZZLE, rainy.condition)
        assertEquals("小雨转小雨".takeIf { false } ?: "小雨", rainy.weatherText)
        assertEquals(20.0, rainy.precipMm!!, 0.001)
        assertNull(rainy.sunrise) // 只有首日带日出日落
    }

    @Test fun provinceMatchingHandlesAliasesAndMunicipalities() {
        val provinces = listOf(
            NmcApi.NmcProvince("ABJ", "北京市", ""),
            NmcApi.NmcProvince("AHE", "河北省", ""),
            NmcApi.NmcProvince("ANM", "内蒙古自治区", ""),
            NmcApi.NmcProvince("AXZ", "西藏自治区", ""),
            NmcApi.NmcProvince("AGD", "广东省", ""),
        )
        assertEquals("ABJ", NmcSource.matchProvince("北京市", "北京市", provinces)?.code)
        assertEquals("ANM", NmcSource.matchProvince("内蒙古自治区呼和浩特市", "呼和浩特", provinces)?.code)
        assertEquals("ANM", NmcSource.matchProvince("内蒙古 呼和浩特", "呼和浩特", provinces)?.code)
        assertEquals("AXZ", NmcSource.matchProvince("西藏 拉萨市", "城关区", provinces)?.code)
        assertEquals("AGD", NmcSource.matchProvince("广东 广州", "天河区", provinces)?.code)
        assertNull(NmcSource.matchProvince("海外", "伦敦", provinces))
    }

    @Test fun provinceMatchingHandlesEthnicAutonomousRegionShortNames() {
        val cases = listOf(
            Triple("广西", "广西壮族自治区", "南宁"),
            Triple("宁夏", "宁夏回族自治区", "银川"),
            Triple("新疆", "新疆维吾尔自治区", "乌鲁木齐"),
        )
        val provinces = cases.mapIndexed { index, (_, full, _) -> NmcApi.NmcProvince("P$index", full, "") }

        cases.forEachIndexed { index, (short, _, city) ->
            assertEquals("$short affiliation must select its province", "P$index",
                NmcSource.matchProvince("$short·$city", city, provinces)?.code)
        }
    }

    @Test fun ethnicAutonomousRegionShortNamesAllowParentCityFallback() {
        val cases = listOf(
            Triple("广西", "广西壮族自治区", "南宁"),
            Triple("宁夏", "宁夏回族自治区", "银川"),
            Triple("新疆", "新疆维吾尔自治区", "乌鲁木齐"),
        )

        cases.forEachIndexed { index, (short, full, parentCity) ->
            val province = NmcApi.NmcProvince("P$index", full, "")
            val ref = kotlinx.coroutines.runBlocking {
                NmcSource.resolveCitySync(City("新区", "$short·${parentCity}市", 30.0, 110.0, "district-$index"),
                    listOf(province)) { pcode ->
                    if (pcode == "P$index") listOf(NmcApi.NmcCityEntry("S$index", full, parentCity, "/city-$index.html"))
                    else emptyList()
                }
            }

            assertEquals("$short district must use the parent station", "S$index", ref?.scode)
            assertEquals(parentCity, ref?.cityName)
        }
    }

    @Test fun cityMatchingStripsSuffixesAndLocksProvince() {
        val provinces = listOf(NmcApi.NmcProvince("ABJ", "北京市", ""), NmcApi.NmcProvince("AJL", "吉林省", ""))
        val beijing = listOf(
            NmcApi.NmcCityEntry("Wqsps", "北京市", "北京", "/publish/forecast/ABJ/beijing.html"),
            NmcApi.NmcCityEntry("MjXfi", "北京市", "朝阳", "/publish/forecast/ABJ/chaoyang.html"),
            NmcApi.NmcCityEntry("fElIR", "北京市", "海淀", "/publish/forecast/ABJ/haidian.html"),
        )
        val jilin = listOf(
            NmcApi.NmcCityEntry("ABC1", "吉林省", "长春", "/publish/forecast/AJL/changchun.html"),
            NmcApi.NmcCityEntry("ABC2", "吉林省", "朝阳", "/publish/forecast/AJL/chaoyang.html"),
        )
        // 北京“朝阳区”锁定北京并命中北京“朝阳”（真实存在的区站），不误配吉林“朝阳”
        val ref = kotlinx.coroutines.runBlocking {
            NmcSource.resolveCitySync(City("朝阳区", "北京市", 39.9, 116.4, "k"), provinces) { p ->
                when (p) { "ABJ" -> beijing; else -> jilin }
            }
        }
        assertEquals("ABJ", ref?.pcode)
        assertEquals("MjXfi", ref?.scode)
        // 纯“万宁市”剥后缀命中
        val wanning = kotlinx.coroutines.runBlocking {
            NmcSource.resolveCitySync(City("万宁市", "海南省省直辖县级行政区划", 18.8, 110.4, "k"),
                listOf(NmcApi.NmcProvince("AHI", "海南省", ""))) { _ ->
                listOf(NmcApi.NmcCityEntry("TtrxS", "海南省", "万宁", "/publish/forecast/AHI/wanning.html"))
            }
        }
        assertEquals("TtrxS", wanning?.scode)
    }

    @Test fun districtWithoutStationFallsBackToParentCity() {
        // 真机反馈：甘肃金昌市金川区（精确定位）——nmc 普通省份只有地级市站点，无区级。
        val provinces = listOf(NmcApi.NmcProvince("AGS", "甘肃省", ""))
        val gansu = listOf(
            NmcApi.NmcCityEntry("ARkZA", "甘肃省", "兰州", "/publish/forecast/AGS/lanzhou.html"),
            NmcApi.NmcCityEntry("chtaw", "甘肃省", "金昌", "/publish/forecast/AGS/jinchang.html"),
        )
        val ref = kotlinx.coroutines.runBlocking {
            NmcSource.resolveCitySync(City("金川区", "甘肃省金昌市", 38.52, 102.19, "k"), provinces) { p ->
                when (p) { "AGS" -> gansu; else -> emptyList() }
            }
        }
        assertEquals("AGS", ref?.pcode)
        assertEquals("chtaw", ref?.scode)
        assertEquals("金昌", ref?.cityName)
        // 不相关区名（省里没有任何站点是其前缀）仍应失败并给出明确错误
        val miss = kotlinx.coroutines.runBlocking {
            NmcSource.resolveCitySync(City("普陀区", "甘肃省舟曲县", 33.7, 104.5, "k"), provinces) { p ->
                when (p) { "AGS" -> gansu; else -> emptyList() }
            }
        }
        assertNull(miss)
    }

    @Test fun stripProvincePrefixHandlesAliases() {
        assertEquals("金昌市金川区", NmcSource.stripProvincePrefix("甘肃省金昌市金川区", "甘肃省"))
        assertEquals("呼和浩特市", NmcSource.stripProvincePrefix("内蒙古自治区呼和浩特市", "内蒙古自治区"))
        assertEquals("金昌市", NmcSource.stripProvincePrefix("甘肃 金昌市", "甘肃省"))
        assertEquals("南宁市", NmcSource.stripProvincePrefix("广西·南宁市", "广西壮族自治区"))
        assertEquals("银川市", NmcSource.stripProvincePrefix("宁夏·银川市", "宁夏回族自治区"))
        assertEquals("乌鲁木齐市", NmcSource.stripProvincePrefix("新疆·乌鲁木齐市", "新疆维吾尔自治区"))
    }

    @Test fun windDirMappingCoversEightPoints() {
        assertEquals(0.0, NmcSource.windDirToDegree("北风")!!, 0.001)
        assertEquals(45.0, NmcSource.windDirToDegree("东北风")!!, 0.001)
        assertEquals(90.0, NmcSource.windDirToDegree("东风")!!, 0.001)
        assertEquals(135.0, NmcSource.windDirToDegree("东南风")!!, 0.001)
        assertEquals(180.0, NmcSource.windDirToDegree("南风")!!, 0.001)
        assertEquals(225.0, NmcSource.windDirToDegree("西南风")!!, 0.001)
        assertEquals(270.0, NmcSource.windDirToDegree("西风")!!, 0.001)
        assertEquals(315.0, NmcSource.windDirToDegree("西北风")!!, 0.001)
        assertNull(NmcSource.windDirToDegree("-"))
        assertNull(NmcSource.windDirToDegree(null))
    }

    @Test fun predictSentinelDayHalfFallsBackToTempchart() {
        // 晚间 nmc 把"今天"的昼段整个置 9999（2026-09-14 16:20 金昌真实样本）。
        // 修复前症状：日历行显示 "9999转晴"、今天高温 "—°"、无图标。
        val predict = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString<NmcApi.NmcPredict>(
                """{"detail":[
                  {"date":"2026-09-14",
                   "day":{"weather":{"info":"9999","img":"9999","temperature":"9999"},"wind":{"direct":"9999","power":"9999"}},
                   "night":{"weather":{"info":"晴","img":"0","temperature":"11"},"wind":{"direct":"无持续风向","power":"微风"}},
                   "precipitation":0.0}
                ]}"""
            )
        val tempchart = listOf(NmcApi.NmcTemp(time = "2026/09/14", max_temp = 26.6, min_temp = 11.0))
        val today = NmcSource.parseNmcPredict(predict, null, tempchart).single()
        assertEquals("晴", today.weatherText)
        assertEquals(26.6, today.high!!, 0.001)
        assertEquals(11.0, today.low!!, 0.001)
        assertEquals(WeatherCondition.CLEAR, today.condition) // 昼段 9999 → 夜间晴兜底
        assertNull(today.windSpeed) // power "9999" → null
        assertNull(NmcSource.windDirToDegree("9999"))
    }

    @Test fun realPressureFallsBackToStationObservation() {
        // 小站 real.airpressure=9999（金昌实测），但同站最近整点 passedchart 有 852hPa——
        // 修复前"气压"格整格消失。风速同时验证 m/s → km/h。
        val real = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString<NmcApi.NmcReal>(
                """{"weather":{"temperature":26.1,"airpressure":9999.0,"humidity":18.0,"info":"晴","img":"0","feelst":24.7},
                    "wind":{"direct":"东风","degree":41.0,"speed":2.7},
                    "publish_time":"2026-09-14 16:20"}"""
            )
        assertNull(NmcSource.parseNmcReal(real)!!.pressure)
        val withFallback = NmcSource.parseNmcReal(real, pressureFallbackHpa = 852.9)!!
        assertEquals(852.9, withFallback.pressure!!, 0.001)
        assertEquals(9.72, withFallback.windSpeed!!, 0.001) // 2.7 m/s → km/h
    }

    @Test fun windPowerMapsToKmh() {
        assertEquals(8.0, NmcSource.windPowerToSpeed("微风")!!, 0.001)
        assertEquals(20.0, NmcSource.windPowerToSpeed("3-4级")!!, 0.001)
        assertNull(NmcSource.windPowerToSpeed(null))
    }

    @Test fun hourlyParsesDayAnchorsAndSkipsOverlapDuplicates() {
        // 真实页面结构（2026-09-14 金昌实测）：日容器间有重叠——15日11:00-20:00 在
        // day0 尾部与 day1 头部各出现一次；跨零点首段带 "15日" 前缀。修复前按
        // "小时回退即 +1 天" 递推，重复段会被错标成 16 日并把后续整条时间轴推错一天。
        fun b(label: String, temp: String) =
            """<div class="hour3 hbg"><div> $label </div><div class=hourimg style="padding-top: 10px;"><img src="https://image.nmc.cn/assets/img/w/40x40/3/0.png"></div><div> - </div><div class=tmp_lte_15> $temp° </div><div> 3.2m/s </div><div> 西南风 </div><div class=hide> 852.9hPa </div><div> 56.3% </div><div class=hide> 10% </div></div>"""
        val html = """
        <div id=hourValues><div style="width:4704px;position: relative;">
        <div id=day0 class="clearfix pull-left">
        ${b("23:00", "12.9")}
        ${b("15日02:00", "11.8")}
        ${b("05:00", "11.2")}
        ${b("08:00", "13.2")}
        ${b("11:00", "21.8")}
        ${b("14:00", "23.8")}
        ${b("17:00", "23.4")}
        ${b("20:00", "17.9")}
        </div>
        <div id=day1 class="clearfix pull-left">
        ${b("11:00", "21.8")}
        ${b("14:00", "23.8")}
        ${b("17:00", "23.4")}
        ${b("20:00", "17.9")}
        ${b("23:00", "15")}
        ${b("16日02:00", "11.6")}
        ${b("05:00", "10.1")}
        ${b("08:00", "12.0")}
        </div>
        </div></div>
        """.trimIndent()
        val now = java.time.LocalDateTime.of(2026, 9, 14, 16, 45)
            .toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli()
        val hourly = NmcSource.parseNmcHourly(html, now)
        val zone = java.time.ZoneOffset.ofHours(8)
        fun at(i: Int) = java.time.Instant.ofEpochMilli(hourly[i].timeMillis).atZone(zone)
        // 16 块去重后 = 12：14日23:00 + 15日02..20 + 15日23:00 + 16日02..08
        assertEquals(12, hourly.size)
        assertEquals(java.time.LocalDate.of(2026, 9, 14), at(0).toLocalDate())
        assertEquals(23, at(0).hour)
        assertEquals(java.time.LocalDate.of(2026, 9, 15), at(4).toLocalDate())
        assertEquals(11, at(4).hour) // 15日11:00（首次出现）
        assertEquals(java.time.LocalDate.of(2026, 9, 16), at(11).toLocalDate())
        assertEquals(8, at(11).hour)
        // 重复段只保留一次
        assertEquals(1, hourly.count { kotlin.math.abs(it.temperature!! - 21.8) < 0.001 })
        // 单调递增且无重复时间点
        assertEquals(hourly.map { it.timeMillis }.sorted(), hourly.map { it.timeMillis })
        assertEquals(hourly.size, hourly.map { it.timeMillis }.distinct().size)
    }

    @Test fun hourlyIconCodesMapToConditions() {
        // 图标尾段是国标现象码：7=小雨、8=中雨、2=阴（金昌 9/19-9/20 雨天时段实测）。
        fun b(label: String, code: String) =
            """<div class="hour3 hbg"><div> $label </div><div class=hourimg><img src="https://image.nmc.cn/assets/img/w/40x40/3/$code.png"></div><div> - </div><div class=tmp_lte_15> 20.9° </div><div> 1.7m/s </div><div> 东北风 </div><div class=hide> 855.2hPa </div><div> 41.9% </div><div class=hide> 30% </div></div>"""
        val html = """
        <div id=hourValues><div>
        ${b("11:00", "7")}
        ${b("14:00", "8")}
        ${b("17:00", "2")}
        </div></div>
        """.trimIndent()
        val now = java.time.LocalDateTime.of(2026, 9, 14, 10, 0)
            .toInstant(java.time.ZoneOffset.ofHours(8)).toEpochMilli()
        val hourly = NmcSource.parseNmcHourly(html, now)
        assertEquals(WeatherCondition.DRIZZLE, hourly[0].condition)
        assertEquals(WeatherCondition.RAIN, hourly[1].condition)
        assertEquals(WeatherCondition.OVERCAST, hourly[2].condition)
    }
}
