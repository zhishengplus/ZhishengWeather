package com.zhisheng.weather.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos

/**
 * 城市边界数据仓库。
 *
 * 数据由 scripts/generate_boundaries.mjs 预生成（assets/geo/boundaries.bin），
 * 字节层解析见 [BoundaryCodec]。城市块含城市外轮廓环（由区县拓扑 merge 而来，
 * 与区县边界严格共边）与区县内部边界线；区县条目含自身轮廓块，供区县级
 * 名称直接渲染所在区县。坐标为 1e-5° 量化整数的 zigzag varint delta。
 *
 * 内存策略：启动仅解析索引（[BoundaryMatcher] 承担名称匹配），几何块按
 * 城市惰性解码并 LRU 缓存；数据缺失/损坏时保持空索引，UI 侧优雅降级。
 */
object BoundaryRepository {

    /** 索引条目。blockOffset/blockLength 指向二进制中的几何块。 */
    class Entry internal constructor(
        val adcode: Int,
        val name: String,
        val centerLon: Double,
        val centerLat: Double,
        internal val blockOffset: Int,
        internal val blockLength: Int,
    )

    /** 一个城市/区县的可渲染几何（坐标单位：度，lon/lat 交错）。 */
    data class CityGeometry(
        val adcode: Int,
        val centerLon: Double,
        val centerLat: Double,
        val outerRings: List<FloatArray>,
        val innerLines: List<FloatArray>,
    )

    @Volatile private var ready = false
    private var bytes = ByteArray(0)
    @Volatile private var matcher = BoundaryMatcher.EMPTY

    private val blockCache = object : LinkedHashMap<Int, CityGeometry>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, CityGeometry>): Boolean = size > 8
    }
    private val lock = Any()

    suspend fun ensureLoaded(context: Context) {
        if (ready) return
        withContext(Dispatchers.IO) {
            if (ready) return@withContext
            synchronized(lock) {
                if (ready) return@withContext
                try {
                    val data = context.assets.open("geo/boundaries.bin").use { it.readBytes() }
                    applyIndex(BoundaryCodec.parse(data), data)
                } catch (_: Exception) { /* 数据缺失/损坏时保持空索引，UI 侧优雅降级 */ }
                ready = true
            }
        }
    }

    fun resolve(name: String, affiliation: String?, lat: Double?, lon: Double?): Entry? =
        matcher.resolve(name, affiliation, lat, lon) ?: regionFallback(lat, lon)

    /**
     * 坐标兜底：DataV 无台湾城市级数据，名称/上级行政区均未命中时，
     * 坐标落在台湾省轮廓内则显示省级轮廓。几何仅解码一次并复用块缓存。
     */
    private fun regionFallback(lat: Double?, lon: Double?): Entry? {
        if (lat == null || lon == null) return null
        val taiwan = matcher.entryNamed(TAIWAN_PROVINCE) ?: return null
        val geometry = synchronized(lock) { blockCache[taiwan.adcode] }
            ?: BoundaryCodec.decodeGeometry(bytes, taiwan)?.also {
                synchronized(lock) { blockCache[it.adcode] = it }
            }
            ?: return null
        return if (BoundaryCodec.pointInRings(lon, lat, geometry.outerRings)) taiwan else null
    }

    private const val TAIWAN_PROVINCE = "台湾省"

    /** 惰性解码城市几何块（LRU 缓存）。 */
    suspend fun geometry(entry: Entry): CityGeometry? {
        synchronized(lock) { blockCache[entry.adcode] }?.let { return it }
        return withContext(Dispatchers.IO) {
            synchronized(lock) {
                blockCache[entry.adcode]?.let { return@withContext it }
                BoundaryCodec.decodeGeometry(bytes, entry)?.also { blockCache[it.adcode] = it }
            }
        }
    }

    private fun applyIndex(index: BoundaryCodec.ParsedIndex, data: ByteArray) {
        bytes = data
        val citiesByName = HashMap<String, MutableList<Entry>>()
        val citiesByAdcode = HashMap<Int, Entry>()
        val cities = ArrayList<Entry>(index.cities.size)
        for (raw in index.cities) {
            val e = Entry(raw.adcode, raw.name, raw.centerLon, raw.centerLat, raw.blockOffset, raw.blockLength)
            cities.add(e)
            citiesByAdcode[e.adcode] = e
            citiesByName.getOrPut(e.name) { mutableListOf() }.add(e)
        }
        val districtsByName = HashMap<String, MutableList<Entry>>()
        for (raw in index.districts) {
            val e = Entry(raw.adcode, raw.name, raw.centerLon, raw.centerLat, raw.blockOffset, raw.blockLength)
            districtsByName.getOrPut(e.name) { mutableListOf() }.add(e)
        }
        val parentStubsByName = HashMap<String, List<ParentStub>>()
        for ((name, stubs) in index.parentStubsByName) {
            parentStubsByName[name] = stubs.map { ParentStub(it.parentAdcode, it.centerLon, it.centerLat) }
        }
        matcher = BoundaryMatcher(citiesByName, citiesByAdcode, districtsByName, parentStubsByName)
    }
}

/** 无自身几何块的区县：仅存名称 → 所属城市，用于名称回退。 */
internal class ParentStub internal constructor(
    val parentAdcode: Int,
    val centerLon: Double,
    val centerLat: Double,
)

/**
 * 名称匹配器：城市名/区县名 → 渲染条目。
 *
 * 匹配顺序：精确命中 → 去后缀（自治区/省/市/地区/自治州/盟/林区）→
 * 补「市」→ 前缀回退（短名匹配全称，如「香港」→「香港特别行政区」）→
 * 无自身几何的区县回退所属城市 → 上级行政区回退（affiliation，
 * 如 DataV 无台湾城市级数据时「台北市」→「台湾省」）。
 * 同名候选（如北京/长春的朝阳区）以与查询坐标最近者消歧。
 * 纯逻辑无状态，可独立单元测试。
 */
internal class BoundaryMatcher(
    private val citiesByName: Map<String, List<BoundaryRepository.Entry>>,
    private val citiesByAdcode: Map<Int, BoundaryRepository.Entry>,
    private val districtsByName: Map<String, List<BoundaryRepository.Entry>>,
    private val parentStubsByName: Map<String, List<ParentStub>>,
) {
    fun resolve(name: String, affiliation: String?, lat: Double?, lon: Double?): BoundaryRepository.Entry? {
        matchByName(name).takeIf { it.isNotEmpty() }?.let { return pick(it, lat, lon) }

        // 前缀回退：短名匹配全称条目（如「香港」→「香港特别行政区」、「台湾」→「台湾省」）
        if (name.length in 2..5) {
            val prefixed = LinkedHashMap<Int, BoundaryRepository.Entry>()
            for (index in listOf(citiesByName, districtsByName)) {
                for (candidates in index.values) {
                    for (e in candidates) {
                        if (e.name.startsWith(name)) prefixed.putIfAbsent(e.adcode, e)
                    }
                }
            }
            if (prefixed.isNotEmpty()) return pick(prefixed.values, lat, lon)
        }

        // 无自身轮廓的区县名 → 回退所属城市
        val stubParents = LinkedHashMap<Int, ParentStub>()
        fun addStubs(list: List<ParentStub>?) = list?.forEach { stubParents.putIfAbsent(it.parentAdcode, it) }
        addStubs(parentStubsByName[name])
        val stripped = stripSuffixes(name)
        if (stripped.length < name.length) addStubs(parentStubsByName[stripped])
        val viaParent = LinkedHashMap<Int, BoundaryRepository.Entry>()
        for (s in stubParents.values) {
            citiesByAdcode[s.parentAdcode]?.let { viaParent.putIfAbsent(it.adcode, it) }
        }
        if (viaParent.isNotEmpty()) return pick(viaParent.values, lat, lon)

        // 上级行政区回退：城市名不在数据中（如 DataV 无台湾城市级数据，
        // 「台北市」→ 依 affiliation「台湾省」显示省级轮廓）
        if (!affiliation.isNullOrBlank()) {
            for (token in affiliation.split(' ', '，', ',', '·')) {
                val token = token.trim()
                if (token.isEmpty()) continue
                matchByName(token).takeIf { it.isNotEmpty() }?.let { return pick(it, lat, lon) }
            }
        }
        return null
    }

    /** 按全名精确取条目（坐标兜底用）。 */
    fun entryNamed(name: String): BoundaryRepository.Entry? = citiesByName[name]?.firstOrNull()

    /** 名称匹配链：精确命中 → 去后缀 → 补「市」。 */
    private fun matchByName(name: String): List<BoundaryRepository.Entry> {
        val found = LinkedHashMap<Int, BoundaryRepository.Entry>()
        fun add(list: List<BoundaryRepository.Entry>?) = list?.forEach { found.putIfAbsent(it.adcode, it) }
        add(citiesByName[name])
        add(districtsByName[name])
        val stripped = stripSuffixes(name)
        if (stripped.length < name.length) {
            add(citiesByName[stripped])
            add(districtsByName[stripped])
        }
        if (!name.endsWith("市") && stripped.length in 2..4) add(citiesByName["${stripped}市"])
        // 「X州」简称：自治州/盟的口语简称（甘孜州、阿坝州、锡林郭勒州），
        // 匹配全称含「自治州」或「盟」的条目；真实「X州」城市（杭州、苏州等）
        // 已在精确命中阶段先行加入，不受影响
        if (name.endsWith("州") && name.length in 3..6) {
            val base = name.removeSuffix("州")
            for (candidates in citiesByName.values) {
                for (e in candidates) {
                    if (e.name.startsWith(base) && (e.name.endsWith("自治州") || e.name.endsWith("盟"))) {
                        found.putIfAbsent(e.adcode, e)
                    }
                }
            }
        }
        return found.values.toList()
    }

    private fun stripSuffixes(name: String): String = name
        .removeSuffix("特别行政区").removeSuffix("维吾尔自治区").removeSuffix("回族自治区")
        .removeSuffix("壮族自治区").removeSuffix("自治区").removeSuffix("省")
        .removeSuffix("市").removeSuffix("地区").removeSuffix("自治州").removeSuffix("盟").removeSuffix("林区")

    private fun pick(
        entries: Collection<BoundaryRepository.Entry>,
        lat: Double?, lon: Double?,
    ): BoundaryRepository.Entry? {
        if (entries.isEmpty()) return null
        if (entries.size == 1 || lat == null || lon == null) return entries.first()
        val k = cos(Math.toRadians(lat))
        return entries.minBy { e ->
            val dLat = e.centerLat - lat
            val dLon = (e.centerLon - lon) * k
            dLat * dLat + dLon * dLon
        }
    }

    companion object {
        val EMPTY = BoundaryMatcher(emptyMap(), emptyMap(), emptyMap(), emptyMap())
    }
}
