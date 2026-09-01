package com.zhisheng.weather.data

import com.zhisheng.weather.model.AqiInfo
import com.zhisheng.weather.model.AlertInfo
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.CurrentWeather
import com.zhisheng.weather.model.DailyWeather
import com.zhisheng.weather.model.HourlyWeather
import com.zhisheng.weather.model.LifeIndexExtra
import com.zhisheng.weather.model.MinutePrecip
import com.zhisheng.weather.model.PrecipitationPhase
import com.zhisheng.weather.model.RainMeta
import com.zhisheng.weather.model.TyphoonInfo
import com.zhisheng.weather.model.WeatherCondition
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.model.YesterdayInfo
import com.zhisheng.weather.model.Nowcast
import com.zhisheng.weather.model.WeatherConsistency
import com.zhisheng.weather.model.alertLevelOf
import com.zhisheng.weather.model.cityZone
import com.zhisheng.weather.model.wmoProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// 天气仓储：默认小米为主源，Open-Meteo 兜底；手动选择和风/彩云时保持源纯净。
object WeatherRepository {

    internal val CHINA_POLLUTANT_UNITS = mapOf(
        "pm2p5" to "μg/m³",
        "pm10" to "μg/m³",
        "o3" to "μg/m³",
        "no2" to "μg/m³",
        "so2" to "μg/m³",
        "co" to "mg/m³",
    )

    // 只有 AUTO 允许公共源补缺；小米始终主导天气现象与降水判断。
    // 手动锁定任一来源时保持完全纯源，让设置名称与实际数据严格一致。
    suspend fun fetchWeather(city: City, pref: SourcePref = SourcePref.AUTO): WeatherData {
        val data = when (pref) {
            SourcePref.QWEATHER -> {
                SecretStore.currentQw()
                if (QWeatherApi.enabled) {
                    fetchQWeather(city) ?: WeatherData(error = "和风天气请求失败（检查凭据与网络）")
                } else {
                    WeatherData(error = "还没有配置和风天气：请在设置中打开开发者模式后接入")
                }
            }
            SourcePref.CAIYUN -> {
                SecretStore.currentCaiyun()
                CaiyunSource.fetch(city)
            }
            SourcePref.XIAOMI -> fetchXiaomi(city)
            SourcePref.OPEN_METEO -> OpenMeteoSource.fetch(city)
            SourcePref.AUTO -> autoChain(city)
        }
        // 先丢掉已经过去的逐时，再决定要不要用公共源补齐；最后对齐「现在」。
        val trimmed = WeatherConsistency.dropPastHourly(data)
        val completed = if (shouldSupplementWithOpenMeteo(pref)) {
            backfillCurrent(backfillHourly(backfillDaily(trimmed, city), city), city)
        } else {
            trimmed
        }
        return WeatherConsistency.align(completed)
    }

    internal fun shouldSupplementWithOpenMeteo(pref: SourcePref): Boolean =
        pref == SourcePref.AUTO

    // AUTO 链：小米主导；Open-Meteo 只补小米缺项，小米整源失败时才兜底。
    private suspend fun autoChain(city: City): WeatherData {
        // 0.0.9-debug 修复：原实现熔断打开时仍会再打一次小米（白等一轮超时），
        // 小米慢失败时连续两次 fetchXiaomi（内部各带 2 次重试）最坏 30s+，
        // 会吃满 ViewModel 的 25s 全局超时，Open-Meteo 兜底永远轮不到--
        // 与 SourceHealth「熔断期内直接跳过该源」的注释意图相反。现在熔断期内
        // 直接走公共源，冷却结束后自动恢复小米。
        if (SourceHealth.isDown(SourceHealth.XIAOMI)) {
            return OpenMeteoSource.fetch(city)
        }
        val d = fetchXiaomi(city)
        val checked = WeatherConsistency.sanitize(d)
        if (checked.error == null && checked.current != null) {
            SourceHealth.recordSuccess(SourceHealth.XIAOMI)
            return checked
        }
        SourceHealth.recordFailure(SourceHealth.XIAOMI)
        return OpenMeteoSource.fetch(city)
    }

    // 小米实况经常不返回露点、云量和阵风。0.1.0 重构时保留了补充接口，
    // 却漏掉了这段调用，导致遥测区从八九项缩水成四项。
    private suspend fun backfillCurrent(data: WeatherData, city: City): WeatherData {
        val current = data.current ?: return data
        if (data.error != null) return data
        val needsSupplement = current.visibility == null || current.dewPoint == null ||
            current.cloudCover == null || current.windGust == null
        if (!needsSupplement) return data
        val supplement = OpenMeteoApi.fetch(city.latitude, city.longitude) ?: return data
        return mergeCurrentSupplement(data, supplement)
    }

    internal fun mergeCurrentSupplement(data: WeatherData, supplement: OpenMeteoResult): WeatherData {
        val current = data.current ?: return data
        val extra = supplement.current ?: return data
        val merged = current.copy(
            visibility = current.visibility ?: extra.visibility?.let { it / 1000.0 },
            dewPoint = current.dewPoint ?: extra.dew_point_2m,
            cloudCover = current.cloudCover ?: extra.cloud_cover,
            windGust = current.windGust ?: extra.wind_gusts_10m,
        )
        if (merged == current) return data
        return data.copy(
            current = merged,
            blockSources = data.blockSources + ("current-supplement" to "OPEN-METEO"),
        )
    }

    // —— 和风天气主路径 ——
    // 每路请求带 1 次重试：手机网络下偶发超时/连接抖动若无重试，
    // 对应区块会静默消失（v0.0.1 修复：平舆丢月相即 daily 单发失败所致）
    // v0.0.1：透传 CancellationException（城市切换取消）；4xx 不重试（海外 minutely 400 等确定性失败）
    private suspend fun <T> qwRetry(times: Int = 2, block: suspend () -> T?): T? {
        repeat(times) { i ->
            try {
                val r = block()
                if (r != null) return r
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (he: retrofit2.HttpException) {
                if (he.code() < 500) return null
            } catch (_: Exception) {
            }
            if (i < times - 1) kotlinx.coroutines.delay(350L)
        }
        return null
    }

    private suspend fun fetchQWeather(city: City): WeatherData? = try {
        coroutineScope {
            val lat = QWeatherApi.lat(city.latitude)
            val lon = QWeatherApi.lat(city.longitude)
            val svc = QWeatherApi.service

            val now = async { qwRetry { svc.current(lat, lon) } }
            val daily = async {
                qwRetry { svc.daily(lat, lon, 10) }
                    ?: qwRetry { svc.daily(lat, lon, 7) }
                    ?: qwRetry { svc.daily(lat, lon, 3) }
            }
            val hourly = async {
                // 新版接口最多 240 小时。高档套餐先取完整时效；若凭据权限只覆盖
                // 较短时效，按档回退，不能让免费用户因为一次 4xx 丢掉整个逐时区。
                qwRetry { svc.hourly(lat, lon, 240) }
                    ?: qwRetry { svc.hourly(lat, lon, 168) }
                    ?: qwRetry { svc.hourly(lat, lon, 72) }
                    ?: qwRetry { svc.hourly(lat, lon, 24) }
            }
            val alerts = async { qwRetry { svc.alerts(lat, lon) } }
            val air = async { qwRetry { svc.air(lat, lon) } }
            val minutely = async { qwRetry { svc.minutely(QWeatherApi.lonLat(city)) } }
            val indices = async {
                // type=0 表示套餐允许的全部生活指数；不支持时退回基础四项。
                qwRetry { svc.indices(QWeatherApi.lonLat(city), "0") }
                    ?: qwRetry { svc.indices(QWeatherApi.lonLat(city), "1,2,3,9") }
            }
            // 小米源补：昨日复盘 + 台风 + 逐日扩展（按城市名反查小米 key，取距离最近命中，
            // 防同名异地串台——v0.0.1 修复：金川区(金昌)显示四川金川县预警）
            val cur = now.await() ?: return@coroutineScope null
            val d = daily.await()
            val h = hourly.await()
            val w = alerts.await()
            val a = air.await()
            val m = minutely.await()
            val ix = indices.await()
            // QWEATHER 手动锁源保持纯净，不再混入小米昨日/台风/逐日数据。
            val s: XiaomiForecastResult? = null
            val utcOffsetSeconds = offsetSeconds(
                h?.hours?.firstOrNull()?.forecastTime ?: d?.days?.firstOrNull()?.forecastStartTime,
            )

            // v0.0.1：单路失败留痕，logcat 可查（此前区块静默消失无从定位）
            val missing = buildList {
                if (d == null) add("daily")
                if (h == null) add("hourly")
                if (w == null) add("alerts")
                if (a == null) add("air")
                if (m == null) add("minutely")
                if (ix == null) add("indices")
            }
            if (missing.isNotEmpty()) {
                android.util.Log.w(
                    "ZhishengWeather",
                    "QWeather 城市=${city.name} 缺失路: $missing（对应区块由备源/本地兜底）",
                )
            }

            val idxLevel = { type: String ->
                ix?.daily?.firstOrNull { it.type == type }?.level?.toIntOrNull()
            }

            // 逐日 = 和风(≤10天) + 小米续接；月相缺失的行用本地 Meeus 计算补上
            // （小米源 moonPhase 恒空，和风 daily 单路失败时月相不再整行消失）
            val dailyList = buildList {
                d?.days?.mapNotNull { dd ->
                    val t = parseTimeMillis(dd.forecastStartTime)
                    if (t == 0L) null else {
                        val dayCode = dd.daytime?.condition?.code
                        val nightCode = dd.nighttime?.condition?.code
                        val dayCondition = WeatherCondition.fromQwCode(dayCode)
                        val nightCondition = WeatherCondition.fromQwCode(nightCode)
                        val condition = qweatherDailyCondition(dd)
                        val profileCode = if (condition == nightCondition && nightCondition != dayCondition) {
                            nightCode
                        } else {
                            dayCode ?: nightCode
                        }
                        DailyWeather(
                            dateMillis = t,
                            high = dd.temperatureMax?.value,
                            low = dd.temperatureMin?.value,
                            average = dd.temperatureAvg?.value,
                            condition = condition,
                            profile = WeatherCondition.qwProfile(profileCode, null),
                            weatherText = qweatherDailyText(dd),
                            windSpeed = listOfNotNull(
                                speedKmh(dd.daytime?.wind?.speed),
                                speedKmh(dd.nighttime?.wind?.speed),
                            ).maxOrNull(),
                            windDirectionDeg = dd.daytime?.wind?.direction?.degree
                                ?: dd.nighttime?.wind?.direction?.degree,
                            windGust = listOfNotNull(
                                speedKmh(dd.daytime?.windGustMax),
                                speedKmh(dd.nighttime?.windGustMax),
                            ).maxOrNull(),
                            precipProbability = qweatherDailyProbability(dd),
                            precipMm = qweatherDailyPrecipMm(dd),
                            humidity = listOfNotNull(
                                pct(dd.daytime?.humidity),
                                pct(dd.nighttime?.humidity),
                            ).takeIf { it.isNotEmpty() }?.average(),
                            cloudCover = listOfNotNull(
                                pct(dd.daytime?.cloudCover),
                                pct(dd.nighttime?.cloudCover),
                            ).takeIf { it.isNotEmpty() }?.average(),
                            uvIndex = dd.uvIndexMax,
                            sunrise = formatClock(dd.astro?.sunrise),
                            sunset = formatClock(dd.astro?.sunset),
                            moonrise = formatClock(dd.astro?.moonrise),
                            moonset = formatClock(dd.astro?.moonset),
                            moonPhase = dd.astro?.moonPhase,
                        )
                    }
                }?.let { addAll(it) }
            }.map { dd -> MoonCalc.enrich(dd, city.latitude, city.longitude) }

            val minuteList = m?.minutely?.takeIf { m.code == "200" }?.mapNotNull { mi ->
                val t = parseTimeMillis(mi.fxTime)
                if (t == 0L) null else MinutePrecip(
                    t,
                    // 和风 minutely.precip 是 5 分钟累计毫米，统一换算成 mm/h。
                    Nowcast.accumulatedMmToRate(mi.precip?.toFloatOrNull() ?: 0f, 5),
                    if (mi.type.equals("snow", true)) PrecipitationPhase.SNOW else PrecipitationPhase.RAIN,
                )
            } ?: emptyList()

            WeatherData(
                current = CurrentWeather(
                    temperature = cur.temperature?.value,
                    feelsLike = cur.feelsLike?.value,
                    condition = WeatherCondition.fromQw(cur.condition?.icon, cur.condition?.code),
                    profile = WeatherCondition.qwProfile(cur.condition?.icon, cur.condition?.code),
                    weatherText = cur.condition?.text,
                    humidity = pct(cur.humidity),
                    windSpeed = speedKmh(cur.wind?.speed),
                    windDirectionDeg = cur.wind?.direction?.degree,
                    pressure = pressureHpa(cur.pressure),
                    uvIndex = cur.uvIndex,
                    visibility = distKm(cur.visibility),
                    dewPoint = cur.dewPoint?.value,
                    cloudCover = pct(cur.cloudCover),
                    windGust = speedKmh(cur.windGust),
                    // 遥测与氛围层使用雨强；amount 是过去一小时累计量，不等同于此刻雨势。
                    precipMm = qweatherCurrentPrecipRate(cur.precipitation),
                ),
                hourly = h?.hours?.mapNotNull { hh ->
                    val t = parseTimeMillis(hh.forecastTime)
                    if (t == 0L) null else HourlyWeather(
                        timeMillis = t,
                        temperature = hh.temperature?.value,
                        feelsLike = hh.feelsLike?.value,
                        condition = WeatherCondition.fromQw(hh.condition?.icon, hh.condition?.code),
                        profile = WeatherCondition.qwProfile(hh.condition?.icon, hh.condition?.code),
                        windSpeed = speedKmh(hh.wind?.speed),
                        windDirectionDeg = hh.wind?.direction?.degree,
                        windGust = speedKmh(hh.windGust),
                        precipProb = normalizeQwProbability(hh.precipitation?.probability),
                        precipMm = precipToMm(hh.precipitation?.amount),
                        humidity = pct(hh.humidity),
                        pressure = pressureHpa(hh.pressure),
                        visibility = distKm(hh.visibility),
                        dewPoint = hh.dewPoint?.value,
                        cloudCover = pct(hh.cloudCover),
                        uvIndex = hh.uvIndex,
                    )
                } ?: emptyList(),
                daily = dailyList,
                aqi = a?.let { air ->
                    preferredAirIndex(air.indexes)?.let { idx ->
                        AqiInfo(
                            value = idx.aqi?.let { Math.round(it).toInt() },
                            level = idx.category ?: idx.level,
                            standard = qweatherAqiStandard(idx.code),
                            primary = idx.primaryPollutant?.name,
                            pm25 = pollutant(air, "pm2p5"),
                            pm10 = pollutant(air, "pm10"),
                            o3 = pollutant(air, "o3"),
                            no2 = pollutant(air, "no2"),
                            so2 = pollutant(air, "so2"),
                            co = pollutant(air, "co"),
                            pollutantUnits = qweatherPollutantUnits(air),
                        )
                    }
                } ?: s?.aqi?.let { sa ->
                    AqiInfo(
                        value = sa.aqi?.toIntOrNull(),
                        level = aqiLevel(sa.aqi?.toIntOrNull()),
                        standard = "中国",
                        pm25 = sa.pm25,
                        pm10 = sa.pm10,
                        o3 = sa.o3,
                        no2 = sa.no2,
                        so2 = sa.so2,
                        co = sa.co,
                        pollutantUnits = CHINA_POLLUTANT_UNITS,
                    )
                },
                alerts = buildList {
                    w?.alerts?.forEach { al ->
                        add(
                            AlertInfo(
                                title = al.headline ?: al.eventType?.name ?: "天气预警",
                                detail = al.description,
                                level = al.severity,
                                pubTime = al.issuedTime,
                                // 优先官方预警色 color.code（blue/yellow/orange/red），无颜色习惯时按 severity 英文枚举兜底
                                severity = alertLevelOf(al.color?.code ?: al.severity),
                            )
                        )
                    }
                    s?.alerts?.forEach { xa ->
                        val title = xa.title ?: ""
                        if (title.isNotBlank() && none { it.title == title }) {
                            add(
                                AlertInfo(
                                    title = title,
                                    detail = xa.detail,
                                    level = xa.level,
                                    pubTime = xa.pubTime,
                                    severity = alertLevelOf(xa.level),
                                )
                            )
                        }
                    }
                },
                updateTime = System.currentTimeMillis(),
                rainNowcast = m?.summary?.takeIf { m.code == "200" },
                rainMinutes = minuteList,
                rainMeta = minuteList.takeIf { it.isNotEmpty() }?.let {
                    RainMeta("QWEATHER", 5, parseTimeMillis(m?.updateTime).takeIf { t -> t != 0L })
                },
                carWashOk = idxLevel("2")?.let { it <= 2 },
                sportsOk = idxLevel("1")?.let { it <= 2 },
                extraIndices = qweatherLifeIndices(ix),
                yesterday = s?.yesterday?.let {
                    YesterdayInfo(
                        high = it.tempMax?.toDoubleOrNull(),
                        low = it.tempMin?.toDoubleOrNull(),
                        aqi = it.aqi?.toIntOrNull(),
                        condition = WeatherCondition.fromXiaomi(it.weatherEnd, city.locationKey),
                    )
                },
                typhoons = s?.typhoon?.mapNotNull { t ->
                    if (t.typhoonCname.isNullOrEmpty()) null
                    else TyphoonInfo(
                        name = t.typhoonCname,
                        ename = t.typhoonEname,
                        type = t.typhoonType,
                        windSpeed = t.centWindSpeed,
                    )
                } ?: emptyList(),
                dataSource = "QWEATHER",
                blockSources = buildMap {
                    put("current", "QWEATHER")
                    if (!h?.hours.isNullOrEmpty()) put("hourly", "QWEATHER")
                    if (dailyList.isNotEmpty()) put("daily", "QWEATHER")
                    if (minuteList.isNotEmpty()) put("minutely", "QWEATHER")
                },
                utcOffsetSeconds = utcOffsetSeconds,
            )
        }
    } catch (ce: kotlinx.coroutines.CancellationException) {
        throw ce
    } catch (e: Exception) {
        android.util.Log.w("ZhishengWeather", "QWeather 主路径整体失败", e)
        null
    }

    private fun pollutant(air: QwAir, code: String): String? =
        air.pollutants.firstOrNull { it.code == code }
            ?.concentration?.value
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { if (it == it.toInt().toDouble()) it.toInt().toString() else it.toString() }

    internal fun qweatherPollutantUnits(air: QwAir): Map<String, String> =
        air.pollutants.mapNotNull { pollutant ->
            val code = pollutant.code?.lowercase()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val unit = displayPollutantUnit(pollutant.concentration?.unit) ?: return@mapNotNull null
            code to unit
        }.toMap()

    internal fun displayPollutantUnit(raw: String?): String? = when (
        raw?.trim()?.lowercase()?.replace(" ", "")
    ) {
        "μg/m3", "µg/m3", "ug/m3", "μg/m³", "µg/m³", "ug/m³" -> "μg/m³"
        "mg/m3", "mg/m³" -> "mg/m³"
        "ppb" -> "ppb"
        "ppm" -> "ppm"
        else -> raw?.trim()?.takeIf { it.isNotEmpty() }
    }

    internal fun qweatherAqiStandard(code: String?): String? = when (code?.lowercase()) {
        "cn-mee", "cn-mee-1h" -> "中国"
        "us-epa" -> "美国"
        "eu-eea" -> "欧洲"
        "jp-moe" -> "日本"
        "qaqi" -> "QWeather"
        null, "" -> null
        else -> code.uppercase()
    }

    internal fun qweatherLifeIndices(indices: QwIndices?): List<LifeIndexExtra> =
        indices?.daily.orEmpty().mapNotNull { item ->
            val type = item.type ?: return@mapNotNull null
            // 运动和洗车在上方已有专门的适宜/不适宜卡片，避免重复展示。
            if (type == "1" || type == "2") return@mapNotNull null
            val named = qweatherIndexName(type, item.name)
            val category = item.category?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            // 接口原名可能长达“空气污染扩散条件指数”，双列卡会被撑成竖排。
            // 首页统一使用稳定短名，详情值仍忠实保留接口返回。
            LifeIndexExtra(named.first, named.second, category)
        }.distinctBy { it.name }

    internal fun qweatherIndexName(type: String, apiName: String? = null): Pair<String, String> = when (type) {
        "3" -> "穿衣" to "DRESS"
        "4" -> "钓鱼" to "FISHING"
        "5" -> "紫外线" to "UV"
        "6" -> "旅游" to "TRAVEL"
        "7" -> "过敏" to "ALLERGY"
        "8" -> "舒适度" to "COMFORT"
        "9" -> "感冒" to "COLD"
        "10" -> "空气扩散" to "AIR"
        "11" -> "空调" to "A/C"
        "12" -> "太阳镜" to "GLASSES"
        "13" -> "化妆" to "MAKEUP"
        "14" -> "晾晒" to "DRYING"
        "15" -> "交通" to "TRAFFIC"
        "16" -> "防晒" to "SPF"
        else -> {
            val short = apiName?.trim()
                ?.removeSuffix("指数")
                ?.takeIf { it.isNotEmpty() }
            (short ?: "生活指数") to "INDEX $type"
        }
    }

    // 和风新版单位换算：优先用 API 返回的 unit 字段判定（v0.0.1：启发式会把大雾
    // 能见度 500m 误显示成 500km），unit 缺失时才退回启发式
    internal fun speedKmh(v: QwVal?): Double? = v?.value?.takeIf(Double::isFinite)?.let {
        when (v.unit?.trim()?.lowercase()?.replace(" ", "")) {
            "km/h", "kmh", "kph" -> it
            "mph", "mi/h" -> it * 1.609344
            "kn", "kt", "knot", "knots" -> it * 1.852
            "m/s", "mps", "meter/s", "metre/s", null, "" -> it * 3.6
            else -> null
        }
    }

    /** 内部气压一律使用 hPa，避免 Pa/kPa/inHg 被直接当成 hPa 显示。 */
    internal fun pressureHpa(v: QwVal?): Double? = v?.value?.takeIf(Double::isFinite)?.let {
        when (v.unit?.trim()?.lowercase()?.replace(" ", "")) {
            "pa", "pascal", "pascals" -> it / 100.0
            "kpa" -> it * 10.0
            "inhg" -> it * 33.8638866667
            "hpa", "mb", "mbar", null, "" -> it
            else -> null
        }
    }

    private fun distKm(v: QwVal?): Double? = v?.value?.let {
        when (v.unit?.lowercase()) {
            "km" -> it
            "m" -> it / 1000.0
            else -> if (it > 1000.0) it / 1000.0 else it
        }
    }

    private fun pct(v: Double?): Double? = v?.let { if (it <= 1.0) it * 100.0 else it }

    internal fun normalizeQwProbability(v: Double?): Int? {
        val raw = v?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        val percent = if (raw <= 1.0) raw * 100.0 else raw
        if (percent > 100.0) return null
        return Math.round(percent).toInt()
    }

    // —— 小米源兜底路径（原有逻辑） ——
    private suspend fun fetchXiaomi(city: City): WeatherData = try {
        // 兜底链路同样带重试：此前单次请求一抖就整屏红字，比主链路还脆弱（v0.0.1）
        val resolvedKey = resolveXiaomiKey(city)
            ?: return WeatherData(error = "小米源未找到与 ${city.name} 坐标匹配的城市")
        val result = qwRetry(times = 1) {
            XiaomiApi.instance.getWeather(
                latitude = city.latitude,
                longitude = city.longitude,
                locationKey = resolvedKey,
                days = 15,
            )
        } ?: throw java.io.IOException("小米源请求失败")
        // 请求和映射必须使用同一个 key；accu:18 是雨，weathercn:18 是雾。
        val data = mapXiaomiToWeatherData(result, resolvedKey)
        // 小米源 moonPhase 恒空：本地计算补上，日月卡月相行不再整行消失
        val withMoon = data.copy(
            daily = data.daily.map { dd -> MoonCalc.enrich(dd, city.latitude, city.longitude) }
        )
        withMoon
    } catch (ce: kotlinx.coroutines.CancellationException) {
        throw ce
    } catch (e: Exception) {
        WeatherData(error = userFacingFetchError("小米天气"))
    }

    // 小米 key 形如 "weathercn:xxx"/"accu:xxx"；和风搜索存下的是和风 id（纯数字），
    // 直接拿去调小米接口会返回全空（v0.0.1 修复：和风整体失败时和风搜索的城市整屏空白）
    private suspend fun resolveXiaomiKey(city: City): String? {
        if (city.locationKey.startsWith("weathercn:", true) || city.locationKey.startsWith("accu:", true)) {
            return city.locationKey
        }
        return nearestXiaomiKey(city.name, city.latitude, city.longitude)
    }

    // 按城市名反查小米 key：同名异地（金川区/金川县、朝阳…）必须取距离最近的命中，
    // 且超过 150km 视为无匹配，宁可缺数据也不串城市（v0.0.1）。
    // v0.0.4：结果做会话级缓存——此前每次和风刷新都附带一次 searchCity 往返（15s 超时风险）
    private const val XIAOMI_MATCH_MAX_KM = 150.0

    private val xiaomiKeyCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private suspend fun nearestXiaomiKey(name: String, lat: Double, lon: Double): String? {
        val cacheKey = "$name|$lat|$lon"
        xiaomiKeyCache[cacheKey]?.let { return it }
        val hit = try {
            val hits = XiaomiApi.instance.searchCity(name)
                .filter { it.status == 0 && !it.locationKey.isNullOrBlank() }
            val nearest = hits.minByOrNull { h ->
                val hl = h.latitude?.toDoubleOrNull()
                val ho = h.longitude?.toDoubleOrNull()
                if (hl == null || ho == null) Double.MAX_VALUE / 2 else distanceKm(lat, lon, hl, ho)
            }
            if (nearest == null) {
                null
            } else {
                val hl = nearest.latitude?.toDoubleOrNull()
                val ho = nearest.longitude?.toDoubleOrNull()
                if (hl == null || ho == null) nearest.locationKey // 命中无坐标，退化为直接用
                else if (distanceKm(lat, lon, hl, ho) <= XIAOMI_MATCH_MAX_KM) nearest.locationKey else null
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            null
        }
        if (hit != null) xiaomiKeyCache[cacheKey] = hit
        return hit
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dp / 2) * Math.sin(dp / 2) +
            Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2)
        return 2 * r * Math.asin(Math.sqrt(a))
    }

    // —— Open-Meteo 逐日补齐（全球 16 天免 key）——
    // 和风逐日上限 10 天、小米海外城市仅约 5 天 → 东京等城市凑不满 15 天，
    // 用 Open-Meteo 把尾部补齐
    private suspend fun backfillDaily(data: WeatherData, city: City): WeatherData {
        if (data.error != null || data.daily.size >= 15) return data
        val response = OpenMeteoApi.fetchDaily(city.latitude, city.longitude) ?: return data
        val om = response.daily ?: return data
        // v0.0.9-debug 修复：小米逐日日期基于 pubTime 时刻（实测 22:00/07:00 等，非当天 0 点），
        // Open-Meteo 为城市本地 0 点。原实现按精确毫秒过滤 + distinctBy 去重，
        // 两源的「同一天」毫秒值对不上：海外城市补齐后同一天会出现两行（一行 22:00 一行 00:00），
        // 且顺序按「主源在前、补齐在后」拼接而非时间序。改为按手机本地日历日去重（与
        // 逐日行的星期标签同一口径），合并后按时间排序。
        val offsetMs = response.utc_offset_seconds * 1000L
        fun epochDay(ms: Long): Long =
            java.time.Instant.ofEpochMilli(ms + offsetMs)
                .atZone(java.time.ZoneOffset.UTC).toLocalDate().toEpochDay()
        val existing = data.daily.map { epochDay(it.dateMillis) }.toSet()
        val extra = omToDaily(om, city, response.utc_offset_seconds)
            .filter { epochDay(it.dateMillis) !in existing }
            .take(15 - data.daily.size)
        if (extra.isEmpty()) return data
        val merged = (data.daily + extra)
            .distinctBy { epochDay(it.dateMillis) }
            .sortedBy { it.dateMillis }
            .map { dd -> MoonCalc.enrich(dd, city.latitude, city.longitude) }
        return data.copy(
            daily = merged,
            blockSources = data.blockSources + ("daily-supplement" to "OPEN-METEO"),
        )
    }

    private fun omToDaily(om: OpenMeteoDaily, city: City, utcOffsetSeconds: Int): List<DailyWeather> {
        val times = om.time ?: return emptyList()
        return times.mapIndexedNotNull { i, day ->
            val t = try {
                java.time.LocalDate.parse(day)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant().toEpochMilli() - utcOffsetSeconds * 1000L
            } catch (_: Exception) {
                0L
            }
            if (t == 0L) null else {
                val profile = wmoProfile(om.weather_code?.getOrNull(i), true)
                MoonCalc.enrich(
                DailyWeather(
                    dateMillis = t,
                    high = om.temperature_2m_max?.getOrNull(i),
                    low = om.temperature_2m_min?.getOrNull(i),
                    condition = profile.condition,
                    weatherText = profile.condition.label,
                    windSpeed = om.wind_speed_10m_max?.getOrNull(i),
                    windDirectionDeg = om.wind_direction_10m_dominant?.getOrNull(i),
                    windGust = om.wind_gusts_10m_max?.getOrNull(i),
                    precipProbability = om.precipitation_probability_max?.getOrNull(i)?.let { Math.round(it).toInt() },
                    precipMm = om.precipitation_sum?.getOrNull(i),
                    humidity = om.relative_humidity_2m_mean?.getOrNull(i),
                    cloudCover = om.cloud_cover_mean?.getOrNull(i),
                    sunrise = formatLocalClock(om.sunrise?.getOrNull(i)),
                    sunset = formatLocalClock(om.sunset?.getOrNull(i)),
                    profile = profile,
                ),
                city.latitude,
                city.longitude,
            )
            }
        }
    }

    // 逐时补齐：和风/小米逐时缺失（海外 4xx 落空等）时用 Open-Meteo 取 24 小时；
    // OM 时间为城市本地墙上时间，用 utc_offset_seconds 折回真实 epoch，保证跨时区显示正确。
    // v0.0.4：补齐门槛从「size>=2」改为「≥2 条且最后一条距今 <3h」——主源只回了
    // 2-3 条近过期数据（日末边缘）时原逻辑会跳过补齐，逐时区近乎空白
    private suspend fun backfillHourly(data: WeatherData, city: City): WeatherData {
        if (data.error != null) return data
        val last = data.hourly.maxOfOrNull { it.timeMillis } ?: 0L
        val freshEnough = last >= System.currentTimeMillis() - 3 * 3_600_000L
        if (data.hourly.size >= 2 && freshEnough) return data
        val om = OpenMeteoApi.fetchHourly(city.latitude, city.longitude) ?: return data
        val h = om.hourly ?: return data
        val offsetMs = om.utc_offset_seconds * 1000L
        val cityLocalNow = System.currentTimeMillis() + offsetMs
        val list = h.time?.mapIndexedNotNull { i, t ->
            val local = try {
                java.time.LocalDateTime.parse(t).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            } catch (_: Exception) {
                null
            }
            if (local == null || local < cityLocalNow - 3_600_000L) null
            else {
                val profile = wmoProfile(
                    h.weather_code?.getOrNull(i),
                    (local / 3_600_000L % 24L).toInt() in 6..18,
                )
                HourlyWeather(
                timeMillis = local - offsetMs,
                temperature = h.temperature_2m?.getOrNull(i),
                feelsLike = h.apparent_temperature?.getOrNull(i),
                // 逐时补齐同样按城市本地小时判昼夜，夜间不再整排太阳（v0.0.4）
                condition = profile.condition,
                windSpeed = h.wind_speed_10m?.getOrNull(i),
                windDirectionDeg = h.wind_direction_10m?.getOrNull(i),
                windGust = h.wind_gusts_10m?.getOrNull(i),
                precipProb = h.precipitation_probability?.getOrNull(i)?.let { Math.round(it).toInt() },
                precipMm = h.precipitation?.getOrNull(i),
                humidity = h.relative_humidity_2m?.getOrNull(i),
                pressure = h.surface_pressure?.getOrNull(i),
                visibility = h.visibility?.getOrNull(i)?.div(1_000.0),
                dewPoint = h.dew_point_2m?.getOrNull(i),
                cloudCover = h.cloud_cover?.getOrNull(i),
                uvIndex = h.uv_index?.getOrNull(i)?.let { u -> Math.round(u).toInt() },
                profile = profile,
            )
            }
        }?.take(24) ?: emptyList()
        return if (list.size >= 2) data.copy(
            hourly = list,
            blockSources = data.blockSources + ("hourly" to "OPEN-METEO"),
        ) else data
    }

    // WMO 映射已收敛到 model.WmoMaps.wmoToCondition（v0.0.4，原 fromWmoCode 与 OpenMeteoSource.wmo 双份重复）

    // Open-Meteo 时间为无时区本地格式（2026-08-07T04:53）
    private fun formatLocalClock(s: String?): String? {
        if (s.isNullOrEmpty()) return null
        return try {
            java.time.LocalDateTime.parse(s).format(clockFmt)
        } catch (_: Exception) {
            null
        }
    }

    // 小米源逐日映射（15 天）
    private fun mapXiaomiDaily(
        r: XiaomiForecastResult,
        locationKey: String? = null,
    ): List<DailyWeather> {
        val dailyWindSpeed = r.forecastDaily?.wind?.speed?.value
        val dailyPrecip = r.forecastDaily?.precipitationProbability?.value
        val dailySun = r.forecastDaily?.sunRiseSet?.value
        val dailyMoon = r.forecastDaily?.moonPhase?.value
        return buildList {
            val highs = r.forecastDaily?.temperature?.value
            val codes = r.forecastDaily?.weather?.value
            // pubTime 解析失败退回当日 0 点，避免逐日日期全部掉回 1970（v0.0.1）
            val start = parseTimeMillis(r.forecastDaily?.pubTime).takeIf { it != 0L }
                ?: todayStartMillis()
            val offset = offsetSeconds(r.forecastDaily?.pubTime)
            val n = minOf(highs?.size ?: 0, codes?.size ?: 0, 15)
            for (i in 0 until n) {
                val t = highs?.getOrNull(i)
                val w = codes?.getOrNull(i)
                val sun = dailySun?.getOrNull(i)
                // from/to 哪个是高温不固定（小米各城市返回顺序不一致），按数值大小定
                // 而不是按字段名，否则逐日行会出现「低 31° / 高 22°」的倒挂（v0.0.2）
                val a = t?.from?.toDoubleOrNull()
                val b = t?.to?.toDoubleOrNull()
                val hiT = if (a != null && b != null) maxOf(a, b) else a ?: b
                val loT = if (a != null && b != null) minOf(a, b) else b ?: a
                add(
                    DailyWeather(
                        dateMillis = xiaomiDailyDateMillis(start, i, offset),
                        high = hiT,
                        low = loT,
                        condition = WeatherCondition.moreSignificant(
                            WeatherCondition.fromXiaomi(w?.from, locationKey),
                            WeatherCondition.fromXiaomi(w?.to, locationKey),
                        ),
                        profile = listOf(
                            WeatherCondition.xiaomiProfile(w?.from, locationKey),
                            WeatherCondition.xiaomiProfile(w?.to, locationKey),
                        ).maxByOrNull { profile -> profile.condition.significanceRank },
                        weatherText = WeatherCondition.turnPhrase(w?.from, w?.to, locationKey),
                        windSpeed = listOfNotNull(
                            dailyWindSpeed?.getOrNull(i)?.from?.toDoubleOrNull(),
                            dailyWindSpeed?.getOrNull(i)?.to?.toDoubleOrNull(),
                        ).maxOrNull(),
                        precipProbability = normalizeProviderProbability(dailyPrecip?.getOrNull(i)),
                        sunrise = formatClock(sun?.from),
                        sunset = formatClock(sun?.to),
                        moonPhase = dailyMoon?.getOrNull(i),
                    )
                )
            }
        }
    }

    private fun mapXiaomiToWeatherData(
        r: XiaomiForecastResult,
        locationKey: String? = null,
    ): WeatherData {
        val current = r.current?.let { cur ->
            val profile = WeatherCondition.xiaomiProfile(cur.weather, locationKey)
            CurrentWeather(
                temperature = cur.temperature?.value?.toDoubleOrNull(),
                feelsLike = cur.feelsLike?.value?.toDoubleOrNull(),
                condition = profile.condition,
                weatherText = WeatherCondition.xiaomiLabel(cur.weather, locationKey),
                humidity = pct(cur.humidity?.value?.toDoubleOrNull()),
                // 小米风速带 unit 字段：km/h 透传，m/s 换算（v0.0.1）
                windSpeed = xiaomiWindKmh(cur.wind?.speed),
                windDirectionDeg = cur.wind?.direction?.value?.toDoubleOrNull(),
                pressure = xiaomiPressureHpa(cur.pressure),
                uvIndex = cur.uvIndex?.toIntOrNull(),
                visibility = xiaomiDistanceKm(cur.visibility),
                profile = profile,
            )
        }

        val hourlyWind = r.forecastHourly?.wind?.value
        val hourlyAqi = r.forecastHourly?.aqi?.value
        val hourly = buildList {
            val temps = r.forecastHourly?.temperature?.value
            val codes = r.forecastHourly?.weather?.value
            val pubRaw = r.forecastHourly?.temperature?.pubTime ?: r.forecastHourly?.pubTime
            val start = parseTimeMillis(pubRaw).takeIf { it != 0L }
                ?: (System.currentTimeMillis() / 3_600_000L * 3_600_000L)
            val hourOffset = offsetSeconds(pubRaw)
            val n = minOf(temps?.size ?: 0, codes?.size ?: 0, 24)
            for (i in 0 until n) {
                val profile = WeatherCondition.xiaomiProfile(codes?.getOrNull(i)?.toString(), locationKey)
                add(
                    HourlyWeather(
                        timeMillis = xiaomiHourlyMillis(start, i, hourOffset),
                        temperature = temps?.getOrNull(i)?.toDouble(),
                        condition = profile.condition,
                        windSpeed = hourlyWind?.getOrNull(i)?.speed?.toDoubleOrNull(),
                        aqi = hourlyAqi?.getOrNull(i),
                        profile = profile,
                    )
                )
            }
        }

        val daily = mapXiaomiDaily(r, locationKey)

        val aqi = r.aqi?.let { a ->
            AqiInfo(
                value = a.aqi?.toIntOrNull(),
                level = aqiLevel(a.aqi?.toIntOrNull()),
                standard = "中国",
                primary = a.primary,
                pm25 = a.pm25,
                pm10 = a.pm10,
                o3 = a.o3,
                no2 = a.no2,
                so2 = a.so2,
                co = a.co,
                pollutantUnits = CHINA_POLLUTANT_UNITS,
                suggest = a.suggest, // v0.0.4：小米健康建议接入 AQI 卡
            )
        }

        val alerts = r.alerts?.map { a ->
            AlertInfo(
                title = a.title ?: "",
                detail = a.detail,
                level = a.level,
                pubTime = a.pubTime,
                severity = alertLevelOf(a.level),
            )
        } ?: emptyList()

        return WeatherData(
            current = current,
            hourly = hourly,
            daily = daily,
            aqi = aqi,
            alerts = alerts,
            // 首页“更新”反映源数据时刻，不把刚下载到的旧响应伪装成刚刚观测。
            updateTime = xiaomiUpdateMillis(r, System.currentTimeMillis()),
            rainNowcast = xiaomiNowcast(r.minutely),
            // v0.0.6：小米 precipitation.value 为约 120 个逐分钟点；此前只接了文案和雨区距离
            rainMinutes = xiaomiMinuteSeries(r.minutely?.precipitation),
            rainMeta = r.minutely?.precipitation?.takeIf { !it.value.isNullOrEmpty() }?.let { precip ->
                RainMeta(
                    source = "XIAOMI",
                    intervalMinutes = 1,
                    updateTime = parseTimeMillis(precip.pubTime).takeIf { t -> t != 0L },
                )
            },
            // v0.0.4：小米 kmNum（雨区距离）接入，分钟降水卡下方展示
            rainDistanceKm = r.minutely?.precipitation?.kmNum?.toDoubleOrNull(),
            carWashOk = r.indices?.indices?.firstOrNull { it.type == "carWash" }?.value?.let { it == "0" },
            sportsOk = r.indices?.indices?.firstOrNull { it.type == "sports" }?.value?.let { it == "0" },
            yesterday = r.yesterday?.let {
                YesterdayInfo(
                    high = it.tempMax?.toDoubleOrNull(),
                    low = it.tempMin?.toDoubleOrNull(),
                    aqi = it.aqi?.toIntOrNull(),
                    condition = WeatherCondition.fromXiaomi(it.weatherEnd, locationKey),
                )
            },
            typhoons = r.typhoon?.mapNotNull { t ->
                if (t.typhoonCname.isNullOrEmpty()) null
                else TyphoonInfo(
                    name = t.typhoonCname,
                    ename = t.typhoonEname,
                    type = t.typhoonType,
                    windSpeed = t.centWindSpeed,
                )
            } ?: emptyList(),
            dataSource = "XIAOMI",
            blockSources = mapOf("current" to "XIAOMI", "hourly" to "XIAOMI", "daily" to "XIAOMI", "minutely" to "XIAOMI"),
            utcOffsetSeconds = offsetSeconds(r.current?.pubTime ?: r.forecastDaily?.pubTime ?: r.forecastHourly?.pubTime),
        )
    }

    // 风向方位（度数 → 中文）
    fun windDirection(deg: Double?): String? {
        if (deg == null || !deg.isFinite()) return null
        val dirs = arrayOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")
        // 容忍数据源偶发返回负角度或超过 360°，避免数组下标越界拖垮天气详情页。
        val normalized = ((deg % 360.0) + 360.0) % 360.0
        val idx = (((normalized + 22.5) / 45.0).toInt()) % 8
        return dirs[idx]
    }

    private fun xiaomiNowcast(minutely: XiaomiMinutely?): String? {
        val precip = minutely?.precipitation
        val prob = minutely?.probability
        return listOf(
            precip?.description,
            prob?.probabilityDescV2,
            prob?.maxProbability,
            prob?.probabilityDesc,
        ).firstOrNull { !it.isNullOrBlank() }
    }

    private fun xiaomiMinuteSeries(precip: XiaomiMinutelyPrecip?): List<MinutePrecip> {
        val values = precip?.value?.map { it.toFloat() }.orEmpty()
        if (values.isEmpty()) return emptyList()
        val start = parseTimeMillis(precip?.pubTime).takeIf { it != 0L }
            ?: System.currentTimeMillis()
        return Nowcast.minuteSeries(values, start)
    }

    // 时刻（HH:mm）
    private val clockFmt = DateTimeFormatter.ofPattern("HH:mm")
    private fun formatClock(s: String?): String? {
        if (s.isNullOrEmpty()) return null
        return try {
            OffsetDateTime.parse(s).format(clockFmt)
        } catch (_: Exception) {
            null
        }
    }

    fun aqiLevel(value: Int?): String? = when {
        value == null -> null
        value <= 50 -> "优"
        value <= 100 -> "良"
        value <= 150 -> "轻度污染"
        value <= 200 -> "中度污染"
        value <= 300 -> "重度污染"
        else -> "严重污染"
    }

    // Open-Meteo 给的是 US AQI，不能套国标 HJ633 的「轻度污染」分段。
    fun usAqiLevel(value: Int?): String? = when {
        value == null -> null
        value <= 50 -> "优"
        value <= 100 -> "良"
        value <= 150 -> "对敏感人群不健康"
        value <= 200 -> "不健康"
        value <= 300 -> "非常不健康"
        else -> "有害"
    }

    internal fun qweatherCurrentPrecipRate(precip: QwPrecip?): Double? =
        // amount 是当前统计时段的累计量，不能冒充 mm/h。只有接口明确返回
        // intensity 时才展示实时雨强；缺失时交给天气现象和分钟序列表达降水。
        precipToMm(precip?.intensity)

    internal fun qweatherDailyCondition(day: QwDay): WeatherCondition {
        val daytime = WeatherCondition.fromQwCode(day.daytime?.condition?.code)
        val nighttime = WeatherCondition.fromQwCode(day.nighttime?.condition?.code)
        return WeatherCondition.moreSignificant(daytime, nighttime)
    }

    internal fun qweatherDailyText(day: QwDay): String? {
        val daytime = day.daytime?.condition?.text?.trim()?.takeIf { it.isNotEmpty() }
        val nighttime = day.nighttime?.condition?.text?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            daytime == null -> nighttime
            nighttime == null || nighttime == daytime -> daytime
            else -> "${daytime}转${nighttime}"
        }
    }

    internal fun qweatherDailyProbability(day: QwDay): Int? = listOfNotNull(
        normalizeQwProbability(day.daytime?.precipitation?.probability),
        normalizeQwProbability(day.nighttime?.precipitation?.probability),
    ).maxOrNull()

    internal fun qweatherDailyPrecipMm(day: QwDay): Double? {
        val periods = listOfNotNull(
            precipToMm(day.daytime?.precipitation?.amount),
            precipToMm(day.nighttime?.precipitation?.amount),
        )
        return periods.takeIf { it.isNotEmpty() }?.sum()
    }

    internal fun preferredAirIndex(indexes: List<QwAirIndex>): QwAirIndex? {
        fun rank(code: String?): Int {
            val c = code?.lowercase().orEmpty()
            return when {
                c == "cn-mee" -> 0
                c == "cn-mee-1h" -> 1
                c == "qaqi" -> 3
                c.isBlank() -> 4
                else -> 2 // 其他国家和地区的本地 AQI（us-epa、eu-eea、jp-moe 等）
            }
        }
        return indexes.minByOrNull { rank(it.code) }
    }

    // 小米逐日 pubTime 经常是 22:00/07:00 这类发布时间，不是当天 0 点。
    // 先折到城市本地日历日再按天累加，避免「今天」跳到第二天、月相时刻偏几小时。
    internal fun xiaomiDailyDateMillis(
        pubTimeMillis: Long,
        dayIndex: Int,
        utcOffsetSeconds: Int?,
    ): Long {
        val zone = cityZone(utcOffsetSeconds)
        return java.time.Instant.ofEpochMilli(pubTimeMillis).atZone(zone).toLocalDate()
            .plusDays(dayIndex.toLong())
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }

    internal fun xiaomiHourlyMillis(
        pubTimeMillis: Long,
        hourIndex: Int,
        utcOffsetSeconds: Int?,
    ): Long {
        val zone = cityZone(utcOffsetSeconds)
        return java.time.Instant.ofEpochMilli(pubTimeMillis).atZone(zone)
            .withMinute(0).withSecond(0).withNano(0)
            .plusHours(hourIndex.toLong())
            .toInstant()
            .toEpochMilli()
    }

    internal fun userFacingFetchError(sourceLabel: String): String =
        "${sourceLabel}暂时无法获取，请检查网络后重试"

    internal fun precipToMm(v: QwVal?): Double? {
        val n = v?.value ?: return null
        if (!n.isFinite() || n < 0.0) return null
        return when (v.unit?.trim()?.lowercase()?.replace(" ", "")) {
            "cm", "cm/h" -> n * 10.0
            "in", "inch", "in/h", "inch/h" -> n * 25.4
            "mm", "mm/h", null, "" -> n
            else -> null
        }
    }

    internal fun normalizeProviderProbability(raw: String?): Int? {
        val value = raw?.trim()?.removeSuffix("%")?.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 0.0) return null
        val percent = if (value <= 1.0) value * 100.0 else value
        return Math.round(percent).toInt().takeIf { it in 0..100 }
    }

    internal fun xiaomiWindKmh(v: XiaomiUnitValue?): Double? {
        val n = v?.value?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        return when (v.unit?.trim()?.lowercase()?.replace(" ", "")) {
            "m/s", "mps" -> n * 3.6
            "mph", "mi/h" -> n * 1.609344
            "kn", "kt", "knot", "knots" -> n * 1.852
            "km/h", "kmh", "kph", null, "" -> n
            else -> null
        }
    }

    internal fun xiaomiPressureHpa(v: XiaomiUnitValue?): Double? {
        val n = v?.value?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        return when (v.unit?.trim()?.lowercase()?.replace(" ", "")) {
            "pa" -> n / 100.0
            "kpa" -> n * 10.0
            "inhg" -> n * 33.8638866667
            "hpa", "mb", "mbar", null, "" -> n
            else -> null
        }
    }

    internal fun xiaomiDistanceKm(v: XiaomiUnitValue?): Double? {
        val n = v?.value?.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return null
        return when (v.unit?.trim()?.lowercase()?.replace(" ", "")) {
            "m", "meter", "metre" -> n / 1_000.0
            "mi", "mile", "miles" -> n * 1.609344
            "km", null, "" -> n
            else -> null
        }
    }

    internal fun xiaomiUpdateMillis(r: XiaomiForecastResult, fetchedAt: Long): Long =
        sequenceOf(
            r.current?.pubTime,
            r.updateTime,
            r.forecastHourly?.temperature?.pubTime,
            r.forecastHourly?.pubTime,
            r.aqi?.pubTime,
        ).map(::parseTimeMillis).firstOrNull { it > 0L } ?: fetchedAt

    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    // 当日 0 点（系统时区）：小米源 pubTime 解析失败时的日期兜底基准
    private fun todayStartMillis(): Long =
        java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

    private fun parseTimeMillis(s: String?): Long {
        if (s.isNullOrEmpty()) return 0L
        return try {
            OffsetDateTime.parse(s, formatter).toInstant().toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    private fun offsetSeconds(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(s, formatter).offset.totalSeconds
        } catch (_: Exception) {
            null
        }
    }
}
