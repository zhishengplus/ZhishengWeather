package com.zhisheng.weather.ui

import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class VistaIconAssetsTest {
    @Test fun everyVistaConditionHasIndependentReadableAssetsWithoutReplacingClassic() {
        val names = listOf("sun", "moon", "cloud_sun", "cloud_moon", "cloud", "clouds", "rain", "drizzle",
            "bolt", "sleet", "snow", "fog", "haze", "sand", "wind")
        names.forEach { name ->
            assertTrue(File("src/main/res/drawable-nodpi/weather_$name.png").isFile)
            listOf("day", "night").forEach { mode ->
                val path = File("src/main/res/drawable-nodpi/vista_${mode}_$name.png")
                assertTrue("Missing new-theme artwork: $path", path.isFile)
                val png = path.readBytes()
                assertEquals("PNG", String(png, 1, 3, Charsets.US_ASCII))
                assertEquals(256, ByteBuffer.wrap(png, 16, 4).int)
                assertEquals(256, ByteBuffer.wrap(png, 20, 4).int)
                assertEquals("Artwork must retain an alpha channel", 6, png[25].toInt())
            }
        }
    }

    @Test fun everyConditionHasALicensedClassicMeteocon() {
        val names = listOf("sun", "moon", "cloud_sun", "cloud_moon", "cloud", "clouds", "rain", "drizzle",
            "bolt", "sleet", "snow", "fog", "haze", "sand", "wind")
        assertTrue(File("src/main/assets/licenses/meteocons.txt").readText().contains("MIT License"))
        names.forEach { name ->
            val path = File("src/main/res/drawable-nodpi/classic_$name.png")
            assertTrue("Missing classic-theme artwork: $path", path.isFile)
            val png = path.readBytes()
            assertEquals("PNG", String(png, 1, 3, Charsets.US_ASCII))
            assertEquals(384, ByteBuffer.wrap(png, 16, 4).int)
            assertEquals(384, ByteBuffer.wrap(png, 20, 4).int)
            assertEquals("Artwork must retain an alpha channel", 6, png[25].toInt())
            assertTrue(File("../artwork/meteocons-fill").isDirectory || File("artwork/meteocons-fill").isDirectory)
        }
    }

    /** 解码 PNG 里出现次数最多的不透明像素颜色（简化实现：只处理标准非隔行 PNG）。 */
    private fun dominantColor(png: ByteArray): Int {
        var offset = 8
        var width = 0; var height = 0; var bitDepth = 0; var colorType = 0
        val idat = java.io.ByteArrayOutputStream()
        while (offset + 8 <= png.size) {
            val length = ByteBuffer.wrap(png, offset, 4).int
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + 8
            when (type) {
                "IHDR" -> {
                    width = ByteBuffer.wrap(png, dataStart, 4).int
                    height = ByteBuffer.wrap(png, dataStart + 4, 4).int
                    bitDepth = png[dataStart + 8].toInt()
                    colorType = png[dataStart + 9].toInt()
                }
                "IDAT" -> idat.write(png, dataStart, length)
                "IEND" -> break
            }
            offset = dataStart + length + 4
        }
        require(bitDepth == 8 && colorType == 6) { "只支持 8 位 RGBA PNG" }
        val raw = java.util.zip.InflaterInputStream(java.io.ByteArrayInputStream(idat.toByteArray())).readBytes()
        val bpp = 4
        val stride = width * bpp
        val previous = IntArray(stride)
        val current = IntArray(stride)
        val counts = HashMap<Int, Int>()
        var pos = 0
        for (y in 0 until height) {
            val filter = raw[pos++].toInt()
            for (i in 0 until stride) current[i] = raw[pos + i].toInt() and 0xFF
            pos += stride
            for (i in 0 until stride) {
                val left = if (i >= bpp) current[i - bpp] else 0
                val up = previous[i]
                val upLeft = if (i >= bpp) previous[i - bpp] else 0
                current[i] = (current[i] + when (filter) {
                    0 -> 0
                    1 -> left
                    2 -> up
                    3 -> (left + up) / 2
                    4 -> paeth(left, up, upLeft)
                    else -> 0
                }) and 0xFF
            }
            for (x in 0 until width) {
                val i = x * bpp
                val alpha = current[i + 3]
                if (alpha == 255) {
                    val color = (current[i] shl 16) or (current[i + 1] shl 8) or current[i + 2]
                    counts[color] = (counts[color] ?: 0) + 1
                }
            }
            System.arraycopy(current, 0, previous, 0, stride)
        }
        return counts.maxByOrNull { it.value }?.key ?: 0
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }
}
