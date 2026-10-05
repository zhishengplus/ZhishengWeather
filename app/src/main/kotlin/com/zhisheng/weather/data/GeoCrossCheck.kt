package com.zhisheng.weather.data

// 双 key 定位交叉验证（1A）：高德与百度对新地点的首次反查结果按
// "规则归一化 → 层级对齐 → 模糊匹配"三段式做分级判定（业界地址匹配标准思路）。
// 只比同一层级（区县对区县），不跨级比；高德只回 adcode 没有区县名，
// 名称级比对（L1-L3）的对手是既有城市名（小米/和风反查所得）——实为三源互验。

enum class GeoMatchLevel {
    /** 两源一致（adcode 相等或名称强匹配）：街道可展示。 */
    MATCH_HIGH,

    /** 同城市但区县口径不一致（前 4 位行政码相同）：降置信保留更完整方。 */
    MATCH_CITY_GRAIN,

    /** 跨城市：明确不是同一个地方，降级为城市级展示（不给街道）。 */
    MISMATCH,
}

// 归一化：全半角空格统一后，循环剥离行政区划后缀（"平舆县"→"平舆"、"滨湖新区"→"滨湖"）
internal fun normalizePlaceName(value: String?): String {
    if (value.isNullOrBlank()) return ""
    var v = value.trim().replace(" ", "").replace("　", "")
    val suffixes = listOf(
        "特别行政区", "自治区", "自治州", "自治县", "林区", "开发区", "高新区",
        "园区", "新区", "特区", "合作区", "省", "市", "区", "县", "旗", "盟", "街道", "镇", "乡",
    )
    var changed = true
    while (changed && v.isNotEmpty()) {
        changed = false
        for (suffix in suffixes) {
            if (v.length > suffix.length && v.endsWith(suffix)) {
                v = v.removeSuffix(suffix)
                changed = true
            }
        }
    }
    return v
}

// 编辑距离 ≤1（含相等）。两字名的比对此函数不单独放行——由调用方按长度门控。
internal fun isOneEditApart(a: String, b: String): Boolean {
    if (a == b) return true
    if (kotlin.math.abs(a.length - b.length) > 1) return false
    val (short, long) = if (a.length <= b.length) a to b else b to a
    var si = 0
    var li = 0
    var mismatchSeen = false
    while (si < short.length && li < long.length) {
        if (short[si] == long[li]) {
            si++
            li++
            continue
        }
        if (mismatchSeen) return false
        mismatchSeen = true
        if (short.length == long.length) {
            si++
            li++
        } else {
            li++
        }
    }
    return true
}

// 分级判定：
// L0 两家 adcode 相等 → 高置信；同城（前 4 位行政码一致）→ 降置信（区划库版本差/
//     开发区与新区口径差/粒度差）；跨城 → 拒绝。
// L1-L3 任一侧缺码时的名称级降级判据：百度区县名 vs 既有城市名。
internal fun crossCheckDistricts(
    amapAdcode: String?,
    baiduAdcode: String?,
    baiduDistrict: String?,
    cityName: String?,
): GeoMatchLevel {
    val amapCode = amapAdcode?.trim()?.takeIf { it.matches(Regex("\\d{6}")) }
    val baiduCode = baiduAdcode?.trim()?.takeIf { it.matches(Regex("\\d{6}")) }
    return when {
    amapCode == null || baiduCode == null -> {
        val a = normalizePlaceName(baiduDistrict)
        val b = normalizePlaceName(cityName)
        when {
            // 无从比对不等于高置信：允许降级使用单源，但禁止跨源拼接。
            a.isEmpty() || b.isEmpty() -> GeoMatchLevel.MATCH_CITY_GRAIN
            a == b || a.contains(b) || b.contains(a) -> GeoMatchLevel.MATCH_HIGH
            b.length >= 3 && isOneEditApart(a, b) -> GeoMatchLevel.MATCH_CITY_GRAIN
            else -> GeoMatchLevel.MISMATCH
        }
    }
    amapCode == baiduCode -> GeoMatchLevel.MATCH_HIGH
    amapCode.take(4) == baiduCode.take(4) -> GeoMatchLevel.MATCH_CITY_GRAIN
    else -> GeoMatchLevel.MISMATCH
}
}

/** 区县只能对齐到同城时，不把两个可能互相冲突的街道拼成新地址。 */
internal fun moreCompleteStreet(amapStreet: String?, baiduStreet: String?): String? =
    listOfNotNull(amapStreet?.trim(), baiduStreet?.trim())
        .filter(String::isNotBlank)
        .maxWithOrNull(compareBy<String>({ it.split('·', '、', '/', ',').size }, { it.length }))

// 街道合并去重：街道不做跨源硬匹配（两家粒度差异大）——拆段、归一化去重、
// 过滤与城市名/区名重复的段，最多取 2 段（与 streetLabel 的拼装口径一致）。
internal fun mergeStreetSegments(streets: List<String?>, exclude: List<String?>): String? {
    val excluded = exclude.mapNotNull { value ->
        normalizePlaceName(value).takeIf(String::isNotEmpty)
    }.toSet()
    val seen = mutableSetOf<String>()
    val ordered = mutableListOf<String>()
    streets.filterNotNull()
        .flatMap { it.split('·', '、', '/', ',') }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { segment ->
            val normalized = normalizePlaceName(segment)
            if (normalized.isEmpty() || normalized in seen || normalized in excluded) return@forEach
            seen += normalized
            ordered += segment
        }
    return ordered.take(2).joinToString("·").ifBlank { null }
}
