package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * boundaries.bin 字节层回读测试：按 scripts/generate_boundaries.mjs 的格式规范
 * 手工编码最小数据，验证 [BoundaryCodec] 逐字段解析正确。
 */
class BoundaryCodecTest {

    // ── 测试用编码器（独立实现格式规范，与解码端互为印证） ──

    private fun indexEntry(
        adcode: Int, parent: Int, name: String,
        lon: Double, lat: Double, blockOffset: Int, blockLength: Int,
    ): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(8 + 2 + nameBytes.size + 16).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(adcode).putInt(parent).putShort(nameBytes.size.toShort()).put(nameBytes)
        buf.putInt((lon * 1e6).toInt()).putInt((lat * 1e6).toInt())
        buf.putInt(blockOffset).putInt(blockLength)
        return buf.array()
    }

    private fun header(cityCount: Int, districtCount: Int, version: Int = 2): ByteArray {
        val buf = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(BoundaryCodec.MAGIC).putShort(version.toShort()).putInt(cityCount).putInt(districtCount)
        return buf.array()
    }

    private fun varint(v: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var u = v
        while (u > 0x7f) { out.write((u and 0x7f) or 0x80); u = u ushr 7 }
        out.write(u)
        return out.toByteArray()
    }

    private fun zigzag(v: Int) = (v shl 1) xor (v shr 31)

    /** 编码一个几何块：varint 外环数 + 各外环 + varint 内线数 + 各内线。 */
    private fun block(rings: List<List<Pair<Double, Double>>>, lines: List<List<Pair<Double, Double>>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(varint(rings.size))
        for (path in rings) writePath(out, path)
        out.write(varint(lines.size))
        for (path in lines) writePath(out, path)
        return out.toByteArray()
    }

    private fun writePath(out: ByteArrayOutputStream, path: List<Pair<Double, Double>>) {
        out.write(varint(path.size))
        var px = 0
        var py = 0
        for ((lon, lat) in path) {
            val x = Math.round(lon * 1e5).toInt()
            val y = Math.round(lat * 1e5).toInt()
            out.write(varint(zigzag(x - px)))
            out.write(varint(zigzag(y - py)))
            px = x; py = y
        }
    }

    private fun hangzhouBlock() = block(
        rings = listOf(
            listOf(120.0 to 30.0, 120.1 to 30.0, 120.1 to 30.1, 120.0 to 30.1, 120.0 to 30.0),
        ),
        lines = listOf(listOf(120.05 to 30.02, 120.06 to 30.04)),
    )

    // ── 解析 ──

    @Test
    fun parseReadsHeaderEntriesAndFallbackStubs() {
        val districtBlock = block(rings = listOf(listOf(107.0 to 29.0, 107.2 to 29.0, 107.1 to 29.2, 107.0 to 29.0)), lines = emptyList())
        val cityEntry = indexEntry(330100, 330000, "杭州市", 120.155076, 30.274085, blockOffset = 100, blockLength = districtBlock.size)
        val data = header(cityCount = 1, districtCount = 2) +
            cityEntry +
            indexEntry(500119, 500000, "南川区", 107.099153, 29.156646, blockOffset = 100, blockLength = districtBlock.size) +
            indexEntry(110101, 110000, "东城区", 116.418757, 39.917544, blockOffset = 0, blockLength = 0) +
            districtBlock

        val index = BoundaryCodec.parse(data)

        assertEquals(1, index.cities.size)
        val city = index.cities[0]
        assertEquals(330100, city.adcode)
        assertEquals("杭州市", city.name)
        assertEquals(120.155076, city.centerLon, 1e-9)
        assertEquals(30.274085, city.centerLat, 1e-9)
        assertEquals(100, city.blockOffset)

        assertEquals(1, index.districts.size)
        assertEquals("南川区", index.districts[0].name)

        // 无几何块的区县进入回退索引
        assertEquals(1, index.parentStubsByName.size)
        val stubs = index.parentStubsByName["东城区"]!!
        assertEquals(110000, stubs[0].parentAdcode)
    }

    @Test
    fun pointInRingsHandlesContainmentAndHoles() {
        val square = floatArrayOf(0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f)
        val lines = listOf(square)
        assertEquals(true, BoundaryCodec.pointInRings(5.0, 5.0, lines))
        assertEquals(false, BoundaryCodec.pointInRings(15.0, 5.0, lines))
        assertEquals(false, BoundaryCodec.pointInRings(-1.0, -1.0, lines))

        // 奇偶规则：孔洞内（内环中空）不算包含
        val donut = listOf(
            square,
            floatArrayOf(4f, 4f, 6f, 4f, 6f, 6f, 4f, 6f),
        )
        assertEquals(false, BoundaryCodec.pointInRings(5.0, 5.0, donut))
        assertEquals(true, BoundaryCodec.pointInRings(2.0, 5.0, donut))
    }

    @Test
    fun parseRejectsBadMagic() {
        val buf = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0x12345678).putShort(2).putInt(0).putInt(0)
        assertThrows(IllegalStateException::class.java) { BoundaryCodec.parse(buf.array()) }
    }

    @Test
    fun parseRejectsUnknownVersion() {
        val buf = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(BoundaryCodec.MAGIC).putShort(99).putInt(0).putInt(0)
        assertThrows(IllegalStateException::class.java) { BoundaryCodec.parse(buf.array()) }
    }

    // ── 几何解码 ──

    @Test
    fun decodeGeometryRoundTripsCoordinates() {
        val blockBytes = hangzhouBlock()
        val data = ByteArray(100 + blockBytes.size)
        blockBytes.copyInto(data, 100)
        val entry = BoundaryRepository.Entry(330100, "杭州市", 120.155076, 30.274085, blockOffset = 100, blockLength = blockBytes.size)

        val geometry = BoundaryCodec.decodeGeometry(data, entry)!!

        assertEquals(1, geometry.outerRings.size)
        assertEquals(5, geometry.outerRings[0].size / 2)
        assertEquals(120.0f, geometry.outerRings[0][0], 1e-4f)
        assertEquals(30.0f, geometry.outerRings[0][1], 1e-4f)
        assertEquals(1, geometry.innerLines.size)
        assertEquals(120.06f, geometry.innerLines[0][2], 1e-4f)
        assertEquals(30.04f, geometry.innerLines[0][3], 1e-4f)
    }

    @Test
    fun decodeGeometryReturnsNullForMissingOrCorruptBlock() {
        val entry = BoundaryRepository.Entry(330100, "杭州市", 120.15, 30.27, blockOffset = 0, blockLength = 0)
        assertNull(BoundaryCodec.decodeGeometry(ByteArray(64), entry))

        // 指向文件末尾之外的块
        val outOfRange = BoundaryRepository.Entry(330100, "杭州市", 120.15, 30.27, blockOffset = 60, blockLength = 100)
        assertNull(BoundaryCodec.decodeGeometry(ByteArray(64), outOfRange))

        // 截断的块（声明 10 个点只给 2 个点）：负 delta 越界读 → 结构异常 → null
        val truncated = block(rings = listOf(List(10) { (120.0 + it * 0.01) to 30.0 }), lines = emptyList())
            .copyOfRange(0, 6)
        val data = ByteArray(100 + truncated.size)
        truncated.copyInto(data, 100)
        val corrupt = BoundaryRepository.Entry(330100, "杭州市", 120.15, 30.27, blockOffset = 100, blockLength = truncated.size)
        assertNull(BoundaryCodec.decodeGeometry(data, corrupt))
    }
}
