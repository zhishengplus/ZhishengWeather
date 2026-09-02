package com.zhisheng.weather.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * boundaries.bin 编解码器：与 scripts/generate_boundaries.mjs 对应的纯字节层。
 * 无状态、无副作用；结构异常以返回值表达，不在内部做降级决策。
 *
 * 文件布局（小端）：
 * ```
 * 头部 14B  magic i32("BDY1") | version u16 = 2 | cityCount i32 | districtCount i32
 * 索引区    城市条目 × cityCount + 区县条目 × districtCount，两者同布局：
 *           adcode i32 | parentAdcode i32 | nameLen u16 | name UTF-8 |
 *           centerLon i32(×1e6) | centerLat i32(×1e6) | blockOffset i32 | blockLength i32
 * 块区      每块 = varint 外环数 + 各外环 + varint 内线数 + 各内线
 *           每条线 = varint 点数 + zigzag varint delta（坐标为 1e-5° 量化整数）
 * ```
 *
 * magic 或 version 不符时 [parse] 抛出异常，由调用方决定整体降级策略；
 * blockOffset 无效的区县条目不视为损坏——生成端对无几何的区县本就会留空，
 * 调用方将其降级为「名称 → 所属城市」的回退索引。
 */
internal object BoundaryCodec {

    /** 魔数 "BDY1"。 */
    const val MAGIC = 0x31594442

    /** 当前格式版本；不一致时 [parse] 抛出。 */
    const val FORMAT_VERSION = 2

    /** 索引条目。blockOffset/blockLength 指向块区中的几何块。 */
    class RawEntry(
        val adcode: Int,
        val parentAdcode: Int,
        val name: String,
        val centerLon: Double,
        val centerLat: Double,
        val blockOffset: Int,
        val blockLength: Int,
    )

    class ParentStub(
        val parentAdcode: Int,
        val centerLon: Double,
        val centerLat: Double,
    )

    class ParsedIndex(
        val cities: List<RawEntry>,
        val districts: List<RawEntry>,
        val parentStubsByName: Map<String, List<ParentStub>>,
    )

    /** 解析索引区。数据截断、magic/version 不符时抛出异常。 */
    fun parse(data: ByteArray): ParsedIndex {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buf.int
        check(magic == MAGIC) { "boundaries.bin: bad magic" }
        val version = buf.short.toInt() and 0xffff
        check(version == FORMAT_VERSION) { "boundaries.bin: unsupported version $version" }
        val cityCount = buf.int
        val districtCount = buf.int

        val cities = ArrayList<RawEntry>(cityCount)
        val districts = ArrayList<RawEntry>(districtCount)
        val parentStubs = HashMap<String, MutableList<ParentStub>>()
        repeat(cityCount) {
            readEntry(buf)?.let { if (it.hasBlock(data.size)) cities.add(it) }
        }
        repeat(districtCount) {
            val e = readEntry(buf) ?: return@repeat
            if (e.hasBlock(data.size)) {
                districts.add(e)
            } else {
                parentStubs.getOrPut(e.name) { mutableListOf() }
                    .add(ParentStub(e.parentAdcode, e.centerLon, e.centerLat))
            }
        }
        return ParsedIndex(cities, districts, parentStubs)
    }

    /**
     * 解码条目指向的几何块。数据损坏等任何结构异常均返回 null，不抛出——
     * 单个城市几何缺失不应影响 App 运行。
     */
    fun decodeGeometry(data: ByteArray, entry: BoundaryRepository.Entry): BoundaryRepository.CityGeometry? {
        if (entry.blockOffset <= 0 || entry.blockOffset + entry.blockLength > data.size) return null
        return try {
            val cur = Cursor(data, entry.blockOffset)
            val ringCount = cur.varint()
            val rings = ArrayList<FloatArray>(ringCount)
            repeat(ringCount) { decodePath(cur)?.let { rings.add(it) } }
            val lineCount = cur.varint()
            val lines = ArrayList<FloatArray>(lineCount)
            repeat(lineCount) { decodePath(cur)?.let { lines.add(it) } }
            if (rings.isEmpty()) {
                null
            } else {
                BoundaryRepository.CityGeometry(entry.adcode, entry.centerLon, entry.centerLat, rings, lines)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun RawEntry.hasBlock(fileSize: Int): Boolean =
        blockOffset > 0 && blockOffset + blockLength <= fileSize

    private fun readEntry(buf: ByteBuffer): RawEntry? {
        val adcode = buf.int
        val parentAdcode = buf.int
        val name = readName(buf) ?: return null
        val centerLon = buf.int / 1e6
        val centerLat = buf.int / 1e6
        val blockOffset = buf.int
        val blockLength = buf.int
        return RawEntry(adcode, parentAdcode, name, centerLon, centerLat, blockOffset, blockLength)
    }

    private fun readName(buf: ByteBuffer): String? {
        val len = buf.short.toInt() and 0xffff
        if (len == 0 || len > MAX_NAME_BYTES) return null
        val arr = ByteArray(len)
        buf.get(arr)
        return String(arr, Charsets.UTF_8)
    }

    private fun decodePath(cur: Cursor): FloatArray? {
        val n = cur.varint()
        if (n < 2 || n > MAX_POINTS_PER_PATH) return null
        val out = FloatArray(n * 2)
        var x = 0f
        var y = 0f
        for (i in 0 until n) {
            x += cur.zigzag()
            y += cur.zigzag()
            out[i * 2] = x / COORD_SCALE
            out[i * 2 + 1] = y / COORD_SCALE
        }
        return out
    }

    private class Cursor(val b: ByteArray, var p: Int) {
        fun varint(): Int {
            var shift = 0
            var r = 0L
            while (true) {
                val byte = b[p++].toInt() and 0xff
                r = r or ((byte and 0x7f).toLong() shl shift)
                if (byte and 0x80 == 0) break
                shift += 7
            }
            return r.toInt()
        }

        fun zigzag(): Int {
            val v = varint()
            return (v ushr 1) xor -(v and 1)
        }
    }

    /**
     * 射线法奇偶填充判定（even-odd）：点是否落在任一外环内。
     * 环坐标为 lon/lat 交错（与 [BoundaryRepository.CityGeometry] 一致），
     * 台湾尺度下平面近似误差可忽略。孔洞由奇偶规则自然处理。
     */
    fun pointInRings(lon: Double, lat: Double, rings: List<FloatArray>): Boolean {
        var inside = false
        for (ring in rings) {
            var i = 0
            var j = ring.size - 2
            while (i < ring.size) {
                val xi = ring[i].toDouble(); val yi = ring[i + 1].toDouble()
                val xj = ring[j].toDouble(); val yj = ring[j + 1].toDouble()
                if ((yi > lat) != (yj > lat) &&
                    lon < (xj - xi) * (lat - yi) / (yj - yi) + xi
                ) {
                    inside = !inside
                }
                j = i
                i += 2
            }
        }
        return inside
    }

    private const val MAX_NAME_BYTES = 128
    private const val MAX_POINTS_PER_PATH = 1_000_000
    private const val COORD_SCALE = 1e5f
}
