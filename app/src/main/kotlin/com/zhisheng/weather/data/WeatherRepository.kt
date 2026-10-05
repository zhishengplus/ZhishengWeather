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
import com.zhisheng.weather.model.WeatherLocationMatch
import com.zhisheng.weather.model.YesterdayInfo
import com.zhisheng.weather.model.Nowcast
import com.zhisheng.weather.model.WeatherConsistency
import com.zhisheng.weather.model.alertLevelOf
import com.zhisheng.weather.model.cityZone
import com.zhisheng.weather.model.wmoProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.coroutineContext

// 天气仓储：默认小米为主源，Open-Meteo 常规兜底（0.1.5-beta6 起百度/高德天气下线，
// 两把 AK 只服务定位地理——街道/区县/搜索）；手动选择时保持源纯净。
object WeatherRepository {

    internal val CHINA_POLLUTANT_UNITS = mapOf(
        "pm2p5" to "μg/m³",
        "pm10" to "μg/m³",
        "o3" to "μg/m³",
        "no2" to "μg/m³",
        "so2" to "μg/m³",
        "co" to "mg/m³",
    )

    // AUTO 与枳生天气源（融合+完整性）允许公共源补缺；其余手动锁定源保持完全纯源，
    // 让设置名称与实际数据严格一致。
    suspend fun fetchWeather(city: City, pref: SourcePref = SourcePref.AUTO): WeatherData = withContext(Dispatchers.IO) {
        val history = WeatherFetchBatch(listOf<suspend () -> List<MinutePrecip>?>(
            { PrecipitationHistorySource.fetch(city) },
        ), coroutineContext.job)
        try {
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
                SourcePref.NMC -> NmcSource.fetch(city)
                SourcePref.OPEN_METEO -> OpenMeteoSource.fetch(city)
                SourcePref.ZHISHENG -> fetchZhishengFusion(city)
                SourcePref.AUTO -> autoChain(city)
            }
            // 先丢掉已经过去的逐时，再决定要不要用公共源补齐；最后对齐「现在」。
            val trimmed = WeatherConsistency.dropPastHourly(data)
            val completed = if (shouldSupplementWithOpenMeteo(pref)) {
                supplementWithinBudget(trimmed, city)
            } else {
                backfillHourlyPrecipOnly(trimmed, city, pref)
            }
            val result = markSuccessfulFetch(
                WeatherConsistency.align(completed),
                System.currentTimeMillis(),
            )
            // 历史是可选模型回溯；仅收下已完成的结果，不能让它阻塞有效的当前天气。
            val historyPoints = history.collect(timeoutMs = 0).firstOrNull().orEmpty()
            if (result.current == null || result.error != null) result else result.copy(
                rainHistory = historyPoints,
                rainHistorySource = if (historyPoints.isNotEmpty()) "OPEN-METEO" else null,
            )
            } finally { history.close() }
    }

    private suspend fun supplementWithinBudget(data: WeatherData, city: City): WeatherData {
        if (data.current == null || data.error != null) return data
        val extras = WeatherFetchBatch(listOf<suspend () -> WeatherData?>(
            { backfillDaily(data, city) },
            { backfillHourly(data, city) },
            { backfillCurrent(data, city) },
        ), coroutineContext.job).collect(timeoutMs = 1_200)
        return mergeCompletedSupplements(data, extras[0], extras[1], extras[2])
    }

    internal fun mergeCompletedSupplements(
        data: WeatherData,
        daily: WeatherData?,
        hourly: WeatherData?,
        current: WeatherData?,
    ): WeatherData = data.copy(
        daily = daily?.daily ?: data.daily,
        hourly = hourly?.hourly ?: data.hourly,
        current = current?.current ?: data.current,
        blockSources = data.blockSources + listOfNotNull(daily, hourly, current)
            .flatMap { extra -> extra.blockSources.filter { (key, value) -> data.blockSources[key] != value }.toList() },
    )

    /**
     * 把“下载完成时间”和气象源自己的观测时间分开。
     * 失败结果不更新时间，避免用户误以为错误页拿到了新天气。
     */
    internal fun markSuccessfulFetch(data: WeatherData, fetchedAt: Long): WeatherData =
        if (data.current != null && data.error == null) data.copy(fetchedAt = fetchedAt) else data

    internal fun shouldSupplementWithOpenMeteo(pref: SourcePref): Boolean =
        pref == SourcePref.AUTO || pref == SourcePref.ZHISHENG

    internal fun shouldFillMissingHourlyPrecip(pref: SourcePref): Boolean =
        pref == SourcePref.AUTO || pref == SourcePref.ZHISHENG

    // AUTO 链：小米主导；小米失败先降中央气象台（国内直连、区县级），再降 Open-Meteo。
    // 只有整源失败时，才尝试用户已配置的百度/高德，避免行政区级数据降低正常链路精度。
    private suspend fun autoChain(city: City): WeatherData {
        // 0.0.9-debug 修复：原实现熔断打开时仍会再打一次小米（白等一轮超时），
        // 小米慢失败时连续两次 fetchXiaomi（内部各带 2 次重试）最坏 30s+，
        // 会吃满 ViewModel 的 25s 全局超时，Open-Meteo 兜底永远轮不到--
        // 与 SourceHealth「熔断期内直接跳过该源」的注释意图相反。现在熔断期内
        // 直接走公共源，冷却结束后自动恢复小米。
        if (SourceHealth.isDown(SourceHealth.XIAOMI)) {
            return autoPublicFallback(city)
        }
        val d = fetchXiaomi(city)
        val checked = WeatherConsistency.sanitize(d)
        if (checked.error == null && checked.current != null) {
            SourceHealth.recordSuccess(SourceHealth.XIAOMI)
            return checked
        }
        SourceHealth.recordFailure(SourceHealth.XIAOMI)
        return autoPublicFallback(city)
    }

    // AUTO 链：小米主导，随后中央气象台（官方、国内直连、免配置），最后 Open-Meteo
    //（0.1.5-beta6 起百度/高德天气已下线）。中央气象台熔断期内直接跳过，避免叠加超时。
    private suspend fun autoPublicFallback(city: City): WeatherData {
        if (!SourceHealth.isDown(SourceHealth.NMC)) {
            val nmc = NmcSource.fetch(city)
            val nmcChecked = WeatherConsistency.sanitize(nmc)
            if (nmcChecked.error == null && nmcChecked.current != null) {
                SourceHealth.recordSuccess(SourceHealth.NMC)
                return nmcChecked
            }
            SourceHealth.recordFailure(SourceHealth.NMC)
        }
        return OpenMeteoSource.fetch(city)
    }

    // —— 枳生天气源：并发抓取全部可用源 → FusionEngine 加权融合 + 实况纠偏 + 完整性补齐 ——
    // 免 key 常驻：小米 / 中央气象台 / 腾讯 / 中国天气网 / Open-Meteo（9 模式 + 补充包 + 空气质量）；
    // 配置了凭据时自动加入和风 / 彩云。任一腿失败自动剔除，≥2 源融合、仅 1 源直用、全失败走完整 OM 兜底。
    // 腿不读熔断（手动源哲学），但写入熔断供真实失败观测。

    private val zhishengBaseWeights = mapOf(
        "XIAOMI" to 1.0, "NMC" to 0.9, "WEATHERCN" to 0.8, "TENCENT" to 0.7,
        "QWEATHER" to 1.0, "CAIYUN" to 1.0,
        "OPEN-METEO:ecmwf_ifs025" to 1.0, "OPEN-METEO:gfs_global" to 0.9,
        "OPEN-METEO:icon_seamless" to 0.9, "OPEN-METEO:cma_grapes_global" to 0.8,
        "OPEN-METEO:jma_seamless" to 0.7, "OPEN-METEO:ukmo_seamless" to 0.9,
        "OPEN-METEO:meteofrance_seamless" to 0.8, "OPEN-METEO:gem_global" to 0.8,
        "OPEN-METEO:ecmwf_aifs025_single" to 0.85, "OPEN-METEO" to 0.85,
    )

    private fun zhishengWeight(id: String): Double = zhishengBaseWeights[id] ?: 0.7

    private data class FusionLegResult(
        val sources: List<FusionEngine.FusionSource> = emptyList(),
        val multi: OmMultiModel? = null,
        val supplements: OmSupplements? = null,
        val air: OmAirQuality? = null,
    )

    private suspend fun fetchZhishengFusion(city: City): WeatherData {
        val startedAt = System.currentTimeMillis()
        // 凭据判定照既有先例：先同步运行态，再查 ready
        SecretStore.currentQw()
        SecretStore.currentCaiyun()
        val qwEnabled = QWeatherApi.enabled
        val cyEnabled = SecretStore.caiyunReady
        fun source(id: String, data: WeatherData?): List<FusionEngine.FusionSource> =
            data?.takeIf { it.error == null }?.let {
                listOf(FusionEngine.FusionSource(id, id.substringBefore(':'), zhishengWeight(id), it))
            }.orEmpty()
        val requests = mutableListOf<suspend () -> FusionLegResult?>(
            { FusionLegResult(source("XIAOMI", leg(SourceHealth.XIAOMI, 8_000) { fetchXiaomi(city).takeIf { it.error == null && it.current != null } })) },
            { FusionLegResult(source("NMC", leg(SourceHealth.NMC, 8_000) { NmcSource.fetch(city).takeIf { it.error == null && it.current != null } })) },
            {
                val multi = leg(SourceHealth.OPEN_METEO, 7_000) {
                    OpenMeteoApi.fetchMultiModel(city.latitude, city.longitude, city.isPreciseLocation)
                }
                FusionLegResult(multi = multi, sources = multi?.models.orEmpty().flatMap {
                    source("OPEN-METEO:${it.model}", omModelToWeatherData(it, startedAt))
                })
            },
            {
                val supplement = leg(null, 7_000) { OpenMeteoApi.fetchSupplements(city.latitude, city.longitude, city.isPreciseLocation) }
                FusionLegResult(supplements = supplement, sources = source("OPEN-METEO", supplement?.let { omSupplementsToWeatherData(it, startedAt) }))
            },
            { FusionLegResult(air = leg(null, 6_000) { OpenMeteoApi.fetchAirQuality(city.latitude, city.longitude) }) },
            {
                val response = leg(null, 6_000) { tencentArea(city)?.let { (p, c) -> TencentWeatherApi.fetch(p, c) } }
                FusionLegResult(source("TENCENT", response?.let { tencentToWeatherData(it, startedAt) }))
            },
            { FusionLegResult(source("WEATHERCN", leg(null, 7_000) { WeatherComCnApi.fetch(city) }?.let { wcnToWeatherData(it, startedAt) })) },
        )
        if (qwEnabled) requests += { FusionLegResult(source("QWEATHER", leg(SourceHealth.QWEATHER, 8_000) { fetchQWeather(city)?.takeIf { it.error == null && it.current != null } })) }
        if (cyEnabled) requests += { FusionLegResult(source("CAIYUN", leg(SourceHealth.CAIYUN, 6_000) { CaiyunSource.fetch(city).takeIf { it.error == null && it.current != null } })) }
        // 保留首个有效天气的完整 8s 等待机会；只有实况可用后才开启 1.5s 收集窗。
        // 空气/日预报等辅助数据不能提前截断主源。窗内所有到达源继续使用原融合权重。
        val legs = WeatherFetchBatch(requests, coroutineContext.job)
            .collect(8_000, settleAfterUsableMs = 1_500) { result ->
            result.sources.any { WeatherConsistency.sanitize(it.data).current != null }
        }.filterNotNull()
        val sources = legs.flatMap { it.sources }
        val multi = legs.firstNotNullOfOrNull { it.multi }
        val supplements = legs.firstNotNullOfOrNull { it.supplements }
        val omAirData = legs.firstNotNullOfOrNull { it.air }

        if (sources.isEmpty()) {
            return backfillHourlyCompleteness(
                fusionFallback(city), city, omAirData, supplements)
        }
        if (sources.size == 1 && WeatherConsistency.sanitize(sources.single().data).current != null) {
            return backfillHourlyCompleteness(sources.single().data.copy(
                dataSource = "ZHISHENG",
                fusionSources = listOf(sources.single().label),
            ), city, omAirData, supplements)
        }
        // 误差积分板：用本次实况给上一轮快照打分 → 自适应系数（样本不足时为空表 = 纯先验）
        val factors = FusionScoreboard.settle(city.locationKey, sources, startedAt)
        val weighted = if (factors.isEmpty()) sources else sources.map {
            it.copy(weight = it.weight * (factors[it.id] ?: 1.0))
        }
        val fused = FusionEngine.fuse(
            FusionEngine.Input(city, weighted, startedAt, omAirData, supplements),
        )
        if (fused.current == null) {
            return backfillHourlyCompleteness(
                fusionFallback(city), city, omAirData, supplements)
        }
        val snappedLat = multi?.latitude
        val snappedLon = multi?.longitude
        val matched = if (snappedLat != null && snappedLon != null) {
            fused.copy(
                locationMatch = WeatherLocationMatch(
                    requestedLatitude = city.latitude,
                    requestedLongitude = city.longitude,
                    providerLatitude = snappedLat,
                    providerLongitude = snappedLon,
                    preciseGps = city.isPreciseLocation,
                    matchedLatitude = snappedLat,
                    matchedLongitude = snappedLon,
                ),
            )
        } else fused
        return backfillHourlyCompleteness(matched, city, omAirData, supplements)
    }

    /**
     * 逐时完整性兜底：融合、单源直通与降级自动出口都可能缺整列逐时字段。
     * 紫外线：OM best_match 逐时序列回填；仍缺的时段按当日 UV 峰值 × 日出日落太阳弧线推导（夜间为 0）。
     * 空气：OM 小时浓度走国标 HJ633 公式逐桶回填（部分缺失也补，不只整列空）。
     * 体感：融合同款本地公式（风冷/酷热指数）；阵风：缺阵风降级为风速（与融合引擎一致）。
     * 月相/月升月落：MoonCalc 本地天文补齐——非融合路径缺它，日月宽卡会只剩太阳一张。
     */
    private fun backfillHourlyCompleteness(
        data: WeatherData,
        city: City,
        omAir: OmAirQuality?,
        supplements: OmSupplements?,
    ): WeatherData {
        var result = data
        if (result.hourly.isEmpty()) return result
        if (supplements != null && result.hourly.none { it.uvIndex != null }) {
            val filled = FusionEngine.fillHourlyCompleteness(result.hourly, supplements)
            if (filled.any { it.uvIndex != null }) result = result.copy(hourly = filled)
        }
        if (result.hourly.any { it.uvIndex == null } && result.daily.any {
                it.uvIndex != null && it.sunrise != null && it.sunset != null
            }) {
            val dayByBucket = HashMap<Long, DailyWeather>()
            result.daily.forEach { d ->
                if (d.uvIndex != null && d.sunrise != null && d.sunset != null) {
                    (0 until 24).forEach { k -> dayByBucket[d.dateMillis + k * 3_600_000L] = d }
                }
            }
            if (dayByBucket.isNotEmpty()) {
                result = result.copy(hourly = result.hourly.map { h ->
                    if (h.uvIndex != null) return@map h
                    val day = dayByBucket[FusionEngine.hourBucket(h.timeMillis, result.utcOffsetSeconds)] ?: return@map h
                    val rise = day.sunrise?.sunMinute() ?: return@map h
                    val set = day.sunset?.sunMinute() ?: return@map h
                    if (set <= rise) return@map h
                    val frac = ((h.timeMillis - day.dateMillis) / 60_000.0 - rise) / (set - rise)
                    val peak = day.uvIndex ?: return@map h
                    h.copy(uvIndex = if (frac <= 0.0 || frac >= 1.0) 0
                    else Math.round(peak * kotlin.math.sin(kotlin.math.PI * frac)).toInt().coerceIn(0, peak))
                })
            }
        }
        if (omAir != null) {
            result = result.copy(hourly = result.hourly.map { h ->
                if (h.aqi != null) h
                else h.copy(aqi = FusionEngine.gbAqiAt(omAir, h.timeMillis)?.first)
            })
        }
        result = result.copy(hourly = result.hourly.map { h ->
            h.copy(
                feelsLike = h.feelsLike
                    ?: FusionEngine.localFeelsLike(h.temperature, h.humidity, h.windSpeed),
                windGust = h.windGust ?: h.windSpeed,
            )
        })
        result = result.copy(daily = result.daily.map {
            MoonCalc.enrich(it, city.latitude, city.longitude)
        })
        return result
    }

    /** "06:52" → 412 分钟。 */
    private fun String.sunMinute(): Int? {
        val parts = split(':')
        val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    /** 单腿取数：超时与异常全部收在 async 内部，失败即 null（腿间互不波及）。 */
    private suspend fun <T : Any> leg(sourceId: String?, timeoutMs: Long, block: suspend () -> T?): T? =
        try {
            val result = kotlinx.coroutines.withTimeout(timeoutMs) { block() }
            if (sourceId != null) {
                if (result != null) SourceHealth.recordSuccess(sourceId) else SourceHealth.recordFailure(sourceId)
            }
            result
        } catch (te: kotlinx.coroutines.TimeoutCancellationException) {
            sourceId?.let { SourceHealth.recordFailure(it) }
            null
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            sourceId?.let { SourceHealth.recordFailure(it) }
            null
        }

    /** 本轮小米/NMC 已尝试过；只给尚未请求的完整 OM 接口一次有界机会，不重跑失败链。 */
    private suspend fun fusionFallback(city: City): WeatherData {
        val fb = WeatherFetchBatch(listOf<suspend () -> WeatherData?>(
            { OpenMeteoSource.fetch(city) },
        ), coroutineContext.job).collect(4_000).firstOrNull()
            ?: return WeatherData(error = "天气源暂不可用，请稍后刷新")
        return if (fb.current != null) {
            fb.copy(dataSource = "ZHISHENG", fusionSources = listOfNotNull(fb.dataSource))
        } else fb
    }

    /** 腾讯天气按省市两级查询；affiliation 缺失时不可用（返回 null 即缺席）。 */
    private fun tencentArea(city: City): Pair<String, String>? {
        val aff = city.affiliation?.trim().orEmpty()
        if (aff.isEmpty()) return null
        val province = Regex("^(.+?(?:省|自治区|特别行政区|市))").find(aff)?.groupValues?.get(1)
            ?: return null
        val rest = aff.removePrefix(province).trim().trimStart('·', ',', '，', ' ')
        val cityPart = Regex("^(.+?(?:市|州|盟|地区))").find(rest)?.groupValues?.get(1)
            ?: rest.takeIf { it.isNotBlank() }
            ?: province // 直辖市：city 参数即省名
        return province to cityPart
    }

    // 小米实况经常不返回露点、云量和阵风。0.1.0 重构时保留了补充接口，
    // 却漏掉了这段调用，导致遥测区从八九项缩水成四项。
    private suspend fun backfillCurrent(data: WeatherData, city: City): WeatherData {
        val current = data.current ?: return data
        if (data.error != null) return data
        val needsSupplement = current.visibility == null || current.dewPoint == null ||
            current.cloudCover == null || current.windGust == null
        if (!needsSupplement) return data
        val supplement = OpenMeteoApi.fetch(
            city.latitude,
            city.longitude,
            preciseGps = city.isPreciseLocation,
        ) ?: return data
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
            // QWEATHER 手动锁源保持纯净，不混入小米数据（昨日/台风/逐日补齐由 1B/重做路径负责）。
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
                                id = al.id,
                                expiresAt = parseTimeMillis(al.expireTime).takeIf { it > 0 },
                            )
                        )
                    }
                },
                updateTime = null, // Current v1 response has no observation timestamp in our model.
                rainNowcast = m?.summary?.takeIf { m.code == "200" },
                rainMinutes = minuteList,
                rainMeta = minuteList.takeIf { it.isNotEmpty() }?.let {
                    RainMeta("QWEATHER", 5, parseTimeMillis(m?.updateTime).takeIf { t -> t != 0L })
                },
                carWashOk = idxLevel("2")?.let { it <= 2 },
                sportsOk = idxLevel("1")?.let { it <= 2 },
                extraIndices = qweatherLifeIndices(ix),
                typhoons = emptyList(),
                dataSource = "QWEATHER",
                blockSources = buildMap {
                    put("current", "QWEATHER")
                    if (!h?.hours.isNullOrEmpty()) put("hourly", "QWEATHER")
                    if (dailyList.isNotEmpty()) put("daily", "QWEATHER")
                    if (minuteList.isNotEmpty()) put("minutely", "QWEATHER")
                },
                locationMatch = WeatherLocationMatch(
                    requestedLatitude = city.latitude,
                    requestedLongitude = city.longitude,
                    providerLatitude = lat.toDouble(),
                    providerLongitude = lon.toDouble(),
                    preciseGps = city.isPreciseLocation,
                ),
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
        val result = qwRetry(times = 2) {
            XiaomiApi.instance.getWeather(
                latitude = city.latitude,
                longitude = city.longitude,
                isLocated = city.isPreciseLocation,
                locationKey = resolvedKey,
                days = 15,
            )
        } ?: throw java.io.IOException("小米源请求失败")
        // 请求和映射必须使用同一个 key；accu:18 是雨，weathercn:18 是雾。
        val data = mapXiaomiToWeatherData(result, resolvedKey).copy(
            locationMatch = WeatherLocationMatch(
                requestedLatitude = city.latitude,
                requestedLongitude = city.longitude,
                providerLatitude = city.latitude,
                providerLongitude = city.longitude,
                preciseGps = city.isPreciseLocation,
            ),
        )
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
        val sourceKey = city.weatherLocationKey ?: city.locationKey
        if (sourceKey.startsWith("weathercn:", true) || sourceKey.startsWith("accu:", true)) {
            return sourceKey
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

    // —— Open-Meteo 逐日补齐（全球 16 天免 key）——
    // 和风逐日上限 10 天、小米海外城市仅约 5 天 → 东京等城市凑不满 15 天，
    // 用 Open-Meteo 把尾部补齐
    private suspend fun backfillDaily(data: WeatherData, city: City): WeatherData {
        if (data.error != null || data.daily.size >= 15) return data
        val response = OpenMeteoApi.fetchDaily(
            city.latitude,
            city.longitude,
            preciseGps = city.isPreciseLocation,
        ) ?: return data
        val om = response.daily ?: return data
        // v0.0.9-debug 修复：小米逐日日期基于 pubTime 时刻（实测 22:00/07:00 等，非当天 0 点），
        // Open-Meteo 为城市本地 0 点。原实现按精确毫秒过滤 + distinctBy 去重，
        // 两源的「同一天」毫秒值对不上：海外城市补齐后同一天会出现两行（一行 22:00 一行 00:00），
        // 且顺序按「主源在前、补齐在后」拼接而非时间序。改为按手机本地日历日去重（与
        // 逐日行的星期标签同一口径），合并后按时间排序。
        // 去重分桶跟随主源时区；补充源只负责生成自己的日期，不能反过来改写主源日界线。
        val bucketOffsetSeconds = data.utcOffsetSeconds ?: response.utc_offset_seconds
        val offsetMs = bucketOffsetSeconds * 1000L
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
    // 小米 hourly 本身没有降水概率，AUTO 链还要把这一项补进已有逐时，避免澄空画出一排「—」。
    // OM 时间为城市本地墙上时间，用 utc_offset_seconds 折回真实 epoch，保证跨时区显示正确。
    // At least eight distinct upcoming hourly slots and an eight-hour horizon are needed.
    // A few near-expiry entries must not suppress the public fallback.
    internal fun hasUsefulHourlyForecast(hourly: List<HourlyWeather>, nowMillis: Long): Boolean {
        val hourMs = 3_600_000L
        val upcoming = hourly.filter { it.timeMillis in (nowMillis - hourMs)..(nowMillis + 24 * hourMs) }
        return upcoming.map { it.timeMillis / hourMs }.distinct().size >= 8 &&
            (upcoming.maxOfOrNull { it.timeMillis } ?: 0L) >= nowMillis + 8 * hourMs
    }

    private suspend fun backfillHourly(data: WeatherData, city: City): WeatherData {
        if (data.error != null) return data
        val hoursOk = hasUsefulHourlyForecast(data.hourly, System.currentTimeMillis())
        val needsPrecip = data.hourly.any { it.precipProb == null }
        if (hoursOk && !needsPrecip) return data
        val om = OpenMeteoApi.fetchHourly(
            city.latitude,
            city.longitude,
            preciseGps = city.isPreciseLocation,
        ) ?: return data
        val list = omToHourly(om)
        if (hoursOk) return applyHourlyPrecipSupplement(data, list)
        return if (list.size >= 2) data.copy(
            hourly = list.take(24),
            blockSources = data.blockSources + ("hourly" to "OPEN-METEO"),
        ) else data
    }

    private suspend fun backfillHourlyPrecipOnly(data: WeatherData, city: City, pref: SourcePref): WeatherData {
        if (!shouldFillMissingHourlyPrecip(pref)) return data
        if (data.error != null || data.hourly.size < 2) return data
        if (data.hourly.none { it.precipProb == null }) return data
        val om = OpenMeteoApi.fetchHourly(
            city.latitude,
            city.longitude,
            preciseGps = city.isPreciseLocation,
        ) ?: return data
        return applyHourlyPrecipSupplement(data, omToHourly(om))
    }

    private fun applyHourlyPrecipSupplement(data: WeatherData, supplement: List<HourlyWeather>): WeatherData {
        val filled = mergeHourlyPrecipSupplement(data.hourly, supplement)
        return if (filled === data.hourly) data else data.copy(
            hourly = filled,
            blockSources = data.blockSources + ("hourly-precip-supplement" to "OPEN-METEO"),
        )
    }

    internal fun omToHourly(om: OpenMeteoHourlyResponse): List<HourlyWeather> {
        val h = om.hourly ?: return emptyList()
        val offsetMs = om.utc_offset_seconds * 1000L
        val cityLocalNow = System.currentTimeMillis() + offsetMs
        return h.time?.mapIndexedNotNull { i, t ->
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
        }.orEmpty()
    }

    internal fun mergeHourlyPrecipSupplement(
        hourly: List<HourlyWeather>,
        supplement: List<HourlyWeather>,
    ): List<HourlyWeather> {
        if (hourly.isEmpty() || hourly.none { it.precipProb == null }) return hourly
        val byBucket = supplement.mapNotNull { hour ->
            hour.precipProb?.let { (hour.timeMillis / 3_600_000L) to it }
        }.toMap()
        if (byBucket.isEmpty()) return hourly
        var changed = false
        val filled = hourly.map { hour ->
            if (hour.precipProb != null) hour
            else {
                val prob = byBucket[hour.timeMillis / 3_600_000L] ?: return@map hour
                changed = true
                hour.copy(precipProb = prob)
            }
        }
        return if (changed) filled else hourly
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
        val dailyAqi = r.forecastDaily?.aqi?.value
        val dailyWindDirection = r.forecastDaily?.wind?.direction?.value
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
                        windDirectionDeg = meanDirectionDeg(
                            dailyWindDirection?.getOrNull(i)?.from,
                            dailyWindDirection?.getOrNull(i)?.to,
                        ),
                        precipProbability = normalizeProviderProbability(dailyPrecip?.getOrNull(i)),
                        sunrise = formatClock(sun?.from),
                        sunset = formatClock(sun?.to),
                        moonPhase = dailyMoon?.getOrNull(i),
                        aqi = dailyAqi?.getOrNull(i),
                    )
                )
            }
        }
    }

    internal fun mapXiaomiToWeatherData(
        r: XiaomiForecastResult,
        locationKey: String? = null,
    ): WeatherData {
        val indexValues = r.indices?.indices.orEmpty()
            .mapNotNull { item ->
                val key = item.type?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                val value = item.value?.trim()?.toDoubleOrNull()?.takeIf(Double::isFinite)
                if (key == null || value == null) null else key to value
            }
            .toMap()
        val current = r.current?.let { cur ->
            val profile = WeatherCondition.xiaomiProfile(cur.weather, locationKey)
            CurrentWeather(
                temperature = cur.temperature?.value?.toDoubleOrNull(),
                // 指数值仅作同一份小米响应内的缺值补充，绝不覆盖实况对象已有字段。
                feelsLike = cur.feelsLike?.value?.toDoubleOrNull() ?: indexValues["feelslike"],
                condition = profile.condition,
                weatherText = WeatherCondition.xiaomiLabel(cur.weather, locationKey),
                humidity = pct(cur.humidity?.value?.toDoubleOrNull() ?: indexValues["humidity"]),
                // 小米风速带 unit 字段：km/h 透传，m/s 换算（v0.0.1）
                windSpeed = xiaomiWindKmh(cur.wind?.speed),
                windDirectionDeg = cur.wind?.direction?.value?.toDoubleOrNull(),
                pressure = xiaomiPressureHpa(cur.pressure)
                    ?: indexValues["pressure"]?.takeIf { it in 250.0..1_100.0 },
                uvIndex = cur.uvIndex?.toIntOrNull()
                    ?: indexValues["uvindex"]?.let { Math.round(it).toInt() },
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
                val wind = hourlyWind?.getOrNull(i)
                val explicitTime = parseTimeMillis(wind?.datetime).takeIf { it != 0L }
                add(
                    HourlyWeather(
                        // 风数组自带逐点时间时以源时间为准；缺失才按发布时间顺推，避免跨小时错配。
                        timeMillis = explicitTime ?: xiaomiHourlyMillis(start, i, hourOffset),
                        temperature = temps?.getOrNull(i)?.toDouble(),
                        condition = profile.condition,
                        windSpeed = wind?.speed?.toDoubleOrNull(),
                        windDirectionDeg = wind?.direction?.toDoubleOrNull(),
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
                type = a.type,
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
            carWashOk = xiaomiSuitability(r.indices, "carWash"),
            sportsOk = xiaomiSuitability(r.indices, "sports"),
            extraIndices = xiaomiLifeIndices(r.indices),
            yesterday = r.yesterday?.let {
                val startCondition = WeatherCondition.fromXiaomi(it.weatherStart, locationKey)
                val endCondition = WeatherCondition.fromXiaomi(it.weatherEnd, locationKey)
                YesterdayInfo(
                    high = it.tempMax?.toDoubleOrNull(),
                    low = it.tempMin?.toDoubleOrNull(),
                    aqi = it.aqi?.toIntOrNull(),
                    condition = WeatherCondition.moreSignificant(startCondition, endCondition),
                    dateMillis = parseTimeMillis(it.date).takeIf { value -> value != 0L },
                    weatherStart = startCondition,
                    weatherEnd = endCondition,
                    sunrise = formatClock(it.sunRise),
                    sunset = formatClock(it.sunSet),
                    windDirectionStartDeg = it.windDircStart?.toDoubleOrNull(),
                    windDirectionEndDeg = it.windDircEnd?.toDoubleOrNull(),
                    windSpeedStart = it.windSpeedStart?.toDoubleOrNull(),
                    windSpeedEnd = it.windSpeedEnd?.toDoubleOrNull(),
                )
            },
            typhoons = r.typhoon?.mapNotNull { t ->
                if (t.typhoonCname.isNullOrEmpty()) null
                else TyphoonInfo(
                    name = t.typhoonCname,
                    ename = t.typhoonEname,
                    type = t.typhoonType,
                    windSpeed = t.centWindSpeed,
                    id = t.typhoonCode,
                    source = "XIAOMI",
                    latitude = t.lat,
                    longitude = t.lon,
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
            precip?.shortDescription,
            precip?.description,
            prob?.probabilityDescV2,
            prob?.probabilityDesc,
            normalizedProbabilityText(prob?.maxProbability),
            normalizedProbabilityText(precip?.probability),
            precip?.headDescription,
        ).firstOrNull { !it.isNullOrBlank() }
            ?.let { fillXiaomiDistancePlaceholder(it, precip?.kmNum) }
    }

    /**
     * 小米短临文案偶尔返回 printf 模板（例如“降水在 %d 公里外”），距离另放在 kmNum。
     * 不能把未替换模板直接交给主界面；距离缺失时去掉坏占位，保留可读的天气结论。
     */
    internal fun fillXiaomiDistancePlaceholder(text: String, kmNum: String?): String {
        if (!text.contains(Regex("%(?:\\d+\\$)?[dfs]"))) return text
        val distance = kmNum?.toDoubleOrNull()?.let { value ->
            if (value % 1.0 == 0.0) value.toInt().toString()
            else "%.1f".format(java.util.Locale.ROOT, value)
        }
        return if (distance != null) {
            text.replace(Regex("%(?:\\d+\\$)?[dfs]"), distance)
        } else {
            text.replace(Regex("\\s*%(?:\\d+\\$)?[dfs]\\s*公里外"), "附近")
                .replace(Regex("%(?:\\d+\\$)?[dfs]"), "")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
        }
    }

    private fun xiaomiMinuteSeries(precip: XiaomiMinutelyPrecip?): List<MinutePrecip> {
        val values = precip?.value?.map { it.toFloat() }.orEmpty()
        if (values.isEmpty()) return emptyList()
        val start = parseTimeMillis(precip?.pubTime).takeIf { it != 0L }
            ?: System.currentTimeMillis()
        val phase = WeatherCondition.xiaomiProfile(precip?.weather?.toString(), null).phase
            .takeUnless { it == PrecipitationPhase.NONE }
            ?: PrecipitationPhase.RAIN
        return Nowcast.minuteSeries(values, start, phase = phase)
    }

    private fun normalizedProbabilityText(raw: String?): String? {
        val probabilities = raw?.split(Regex("[\\s,，/]+"))
            ?.mapNotNull(::normalizeProviderProbability)
            .orEmpty()
        val maximum = probabilities.maxOrNull() ?: return null
        return "未来2小时降水概率 $maximum%"
    }

    private fun normalizedProbabilityText(raw: List<Double>?): String? {
        val maximum = raw.orEmpty()
            .mapNotNull { normalizeProviderProbability(it.toString()) }
            .maxOrNull() ?: return null
        return "未来2小时降水概率 $maximum%"
    }

    internal fun meanDirectionDeg(from: String?, to: String?): Double? {
        val values = listOfNotNull(
            from?.toDoubleOrNull()?.takeIf(Double::isFinite),
            to?.toDoubleOrNull()?.takeIf(Double::isFinite),
        )
        if (values.isEmpty()) return null
        if (values.size == 1) return ((values.first() % 360.0) + 360.0) % 360.0
        val radians = values.map { Math.toRadians(((it % 360.0) + 360.0) % 360.0) }
        val x = radians.sumOf(Math::cos)
        val y = radians.sumOf(Math::sin)
        if (kotlin.math.abs(x) < 1e-9 && kotlin.math.abs(y) < 1e-9) return values.first()
        return ((Math.toDegrees(kotlin.math.atan2(y, x)) % 360.0) + 360.0) % 360.0
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
        val text = raw?.trim() ?: return null
        val explicitPercent = text.endsWith("%")
        val value = text.removeSuffix("%").trim().toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 0.0) return null
        val percent = if (!explicitPercent && value <= 1.0) value * 100.0 else value
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

    internal fun xiaomiUpdateMillis(r: XiaomiForecastResult, fetchedAt: Long): Long? =
        sequenceOf(
            r.current?.pubTime,
            r.updateTime,
            r.forecastHourly?.temperature?.pubTime,
            r.forecastHourly?.pubTime,
            r.aqi?.pubTime,
        ).map(::parseTimeMillis).firstOrNull { it > 0L && it <= fetchedAt + 300_000L }

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
