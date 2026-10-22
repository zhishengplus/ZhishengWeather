package com.zhisheng.weather.widget

import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class LauncherPreviewMetadataTest {
    @Test fun everyProviderHasRealArtworkForBothOldAndNewLauncherPreviewProtocols() {
        val resources = File("src/main/res")
        for (style in WidgetStyle.entries) for (kind in WidgetKind.entries) {
            val provider = File(resources, "xml/widget_${style.key}_${kind.name.lowercase()}.xml").readText()
            fun reference(attribute: String) = Regex("android:$attribute=\"@([^\"]+)\"").find(provider)?.groupValues?.get(1)
            val image = reference("previewImage") ?: error("Missing image preview: ${style.key}/${kind.name}")
            val layout = reference("previewLayout") ?: error("Missing layout preview: ${style.key}/${kind.name}")
            assertTrue(image.startsWith("drawable/widget_preview_"))
            val png = File(resources, "drawable-nodpi/${image.substringAfter('/')}.png")
            assertTrue("Preview cannot be empty", png.length() > 2_000)
            val bytes = png.readBytes()
            assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a), bytes.copyOfRange(0, 8))
            val header = ByteBuffer.wrap(bytes)
            assertEquals("IHDR", String(bytes, 12, 4, Charsets.US_ASCII))
            assertTrue(header.getInt(16) > 200 && header.getInt(20) > 100)
            assertTrue(String(bytes, Charsets.ISO_8859_1).contains("IDAT"))
            val xml = File(resources, "$layout.xml").readText()
            assertTrue(xml.contains("android:src=\"@$image\""))
            assertTrue(xml.contains("清华大学示例天气"))
        }
    }
}
