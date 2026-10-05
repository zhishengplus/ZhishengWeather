package com.zhisheng.weather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import com.zhisheng.weather.data.AccentTone
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.data.HomeSurfaceStyle

internal val LocalVistaSoftGlow = staticCompositionLocalOf { true }

/** Vista's cards use physical press feedback; a dark ripple reads as a flickering shadow on glass. */
private object QuietIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = QuietIndicationNode()
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = javaClass.hashCode()
}

private class QuietIndicationNode : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() { drawContent() }
}

// 枳生天气 · 双主题（v0.0.5）：isLight 决定纸面/磷光两套色板与 M3 colorScheme
private fun darkScheme(p: ZhishengPalette) = darkColorScheme(
    primary = p.mint,
    onPrimary = Color(0xFF001500),
    primaryContainer = Color(0xFF003300),
    onPrimaryContainer = Color(0xFFB0FFB0),
    secondary = p.cyan,
    onSecondary = Color(0xFF003333),
    background = p.bg,
    onBackground = p.text,
    surface = p.surface,
    onSurface = p.text,
    surfaceVariant = p.card,
    onSurfaceVariant = p.textSecondary,
    outline = p.cardBorder,
    outlineVariant = p.cardBorder,
    error = p.red,
    onError = Color(0xFF3F0B0B),
    errorContainer = Color(0xFF461313),
    onErrorContainer = Color(0xFFFFDAD4),
)

private fun lightScheme(p: ZhishengPalette) = lightColorScheme(
    primary = p.mint,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEAE0),
    onPrimaryContainer = Color(0xFF06382E),
    secondary = p.cyan,
    onSecondary = Color.White,
    background = p.bg,
    onBackground = p.text,
    surface = p.surface,
    onSurface = p.text,
    surfaceVariant = Color(0xFFE8ECEF),
    onSurfaceVariant = p.textSecondary,
    outline = p.cardBorder,
    outlineVariant = p.cardBorder,
    error = p.red,
    onError = Color.White,
    errorContainer = Color(0xFFFBE7E7),
    onErrorContainer = Color(0xFF3D0B0B),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZhishengWeatherTheme(
    isLight: Boolean = true,
    accentTone: AccentTone = AccentTone.STANDARD,
    interfaceStyle: InterfaceStyle = InterfaceStyle.PHOSPHOR_VISTA,
    homeSurfaceStyle: HomeSurfaceStyle = HomeSurfaceStyle.CURRENT,
    softGlow: Boolean = true,
    softGlowLevel: com.zhisheng.weather.data.SoftGlowLevel = com.zhisheng.weather.data.SoftGlowLevel.SUBTLE,
    content: @Composable () -> Unit,
) {
    val targetPalette = when (interfaceStyle) {
        InterfaceStyle.CLASSIC_TERMINAL -> when {
            isLight && accentTone == AccentTone.SOFT -> ZhishengLightSoftAccentPalette
            isLight -> ZhishengLightPalette
            accentTone == AccentTone.SOFT -> ZhishengDarkSoftAccentPalette
            else -> ZhishengDarkPalette
        }
        InterfaceStyle.PHOSPHOR_VISTA -> when {
            isLight && accentTone == AccentTone.SOFT -> PhosphorVistaLightSoftAccentPalette
            isLight -> PhosphorVistaLightPalette
            accentTone == AccentTone.SOFT -> PhosphorVistaDarkSoftAccentPalette
            else -> PhosphorVistaDarkPalette
        }
    }
    val palette = rememberThemePalette(targetPalette)
    val glowPhase = rememberVistaGlowPhase(softGlow && interfaceStyle == InterfaceStyle.PHOSPHOR_VISTA, softGlowLevel.cycleSeconds)
    val chrome = if (interfaceStyle == InterfaceStyle.PHOSPHOR_VISTA) PhosphorVistaChrome else ClassicTerminalChrome
    val originalIndication = LocalIndication.current
    val originalRipple = LocalRippleConfiguration.current
    CompositionLocalProvider(
        LocalZhishengPalette provides palette,
        LocalHomeSurfaceStyle provides homeSurfaceStyle,
        LocalInterfaceStyle provides interfaceStyle,
        LocalZhishengChrome provides chrome,
        LocalVistaGlowPhase provides glowPhase,
        LocalVistaGlowLevel provides softGlowLevel,
        LocalVistaSoftGlow provides (softGlow && interfaceStyle == InterfaceStyle.PHOSPHOR_VISTA),
        LocalIndication provides (if (interfaceStyle == InterfaceStyle.PHOSPHOR_VISTA) QuietIndication else originalIndication),
        LocalRippleConfiguration provides (if (interfaceStyle == InterfaceStyle.PHOSPHOR_VISTA) null else originalRipple),
    ) {
        MaterialTheme(
            colorScheme = (if (isLight) lightScheme(palette) else darkScheme(palette)).let { scheme ->
                if (interfaceStyle != InterfaceStyle.PHOSPHOR_VISTA) scheme else scheme.copy(
                    primaryContainer = lerp(palette.surface, palette.mint, 0.13f),
                    onPrimaryContainer = palette.text,
                    secondaryContainer = lerp(palette.surface, palette.cyan, 0.10f),
                    onSecondaryContainer = palette.text,
                    surfaceContainerLowest = palette.bg,
                    surfaceContainerLow = palette.surface,
                    surfaceContainer = palette.card,
                    surfaceContainerHigh = lerp(palette.card, palette.text, 0.025f),
                    surfaceContainerHighest = lerp(palette.card, palette.text, 0.05f),
                    surfaceBright = palette.card,
                    surfaceDim = palette.bg,
                    surfaceTint = Color.Transparent,
                )
            },
            typography = if (interfaceStyle == InterfaceStyle.PHOSPHOR_VISTA) PhosphorVistaTypography else ZhishengTypography,
            shapes = if (interfaceStyle == InterfaceStyle.PHOSPHOR_VISTA) {
                Shapes(
                    extraSmall = chrome.compactShape,
                    small = chrome.compactShape,
                    medium = chrome.panelShape,
                    large = chrome.dialogShape,
                    extraLarge = chrome.dialogShape,
                )
            } else {
                Shapes()
            },
            content = content,
        )
    }
}
