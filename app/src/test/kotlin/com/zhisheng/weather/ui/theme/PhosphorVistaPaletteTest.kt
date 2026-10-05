package com.zhisheng.weather.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class PhosphorVistaPaletteTest {
    @Test
    fun terminalBlackBaseAndRestrainedSurfaceChromaStayWithinReadingPalette() {
        val palette = PhosphorVistaDarkPalette
        listOf(palette.bg, palette.surface, palette.card).forEach { color ->
            val channels = listOf(color.red, color.green, color.blue)
            assertTrue("Surfaces must not become saturated colour panels", channels.max() - channels.min() < 0.10f)
        }
        assertTrue("The page should read as terminal black", luminance(palette.bg) < 0.005)
        assertTrue(luminance(palette.surface) > luminance(palette.bg))
        assertTrue(luminance(palette.card) > luminance(palette.surface))
    }

    @Test
    fun darkPanelsRemainVisiblySeparatedFromTheBlackPage() {
        val palette = PhosphorVistaDarkPalette
        assertTrue("A panel must not disappear into black", contrast(palette.surface, palette.bg) >= 1.2)
        assertTrue("Raised content needs its own luminance step", contrast(palette.card, palette.surface) >= 1.14)
    }

    @Test
    fun readingColorsMeetAaContrastOnPrimarySurfaces() {
        listOf(PhosphorVistaDarkPalette, PhosphorVistaLightPalette).forEach { palette ->
            assertTrue(contrast(palette.text, palette.bg) >= 4.5)
            assertTrue(contrast(palette.textSecondary, palette.surface) >= 4.5)
            assertTrue(contrast(palette.textTertiary, palette.card) >= 4.5)
            assertTrue(contrast(palette.mint, palette.bg) >= 4.5)
            assertTrue(contrast(palette.cyan, palette.bg) >= 4.5)
            assertTrue(contrast(palette.orange, palette.bg) >= 4.5)
        }
    }

    private fun contrast(first: Color, second: Color): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val component = value.toDouble()
            return if (component <= 0.04045) component / 12.92 else ((component + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
