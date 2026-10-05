/* Hallmark · pre-emit critique: P5 H4 E4 S5 R5 V4 */
/* Hallmark · atmospheric utility · restrained phosphor · anchor: signal green · macrostructure: forecast console */
package com.zhisheng.weather.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.data.InterfaceStyle

/** Shared geometry for the two complete interface styles. */
@Immutable
data class ZhishengChrome(
    val pagePadding: Dp,
    val sectionGap: Dp,
    val panelPadding: Dp,
    val panelShape: CornerBasedShape,
    val compactShape: CornerBasedShape,
    val dialogShape: CornerBasedShape,
    val borderWidth: Dp,
    val contentMaxWidth: Dp,
)

val ClassicTerminalChrome = ZhishengChrome(
    pagePadding = 16.dp,
    sectionGap = 12.dp,
    panelPadding = 14.dp,
    panelShape = RoundedCornerShape(0.dp),
    compactShape = RoundedCornerShape(0.dp),
    dialogShape = RoundedCornerShape(0.dp),
    borderWidth = 1.dp,
    contentMaxWidth = 720.dp,
)

val PhosphorVistaChrome = ZhishengChrome(
    pagePadding = 24.dp,
    sectionGap = 20.dp,
    panelPadding = 16.dp,
    panelShape = RoundedCornerShape(26.dp),
    compactShape = RoundedCornerShape(16.dp),
    dialogShape = RoundedCornerShape(28.dp),
    borderWidth = 1.dp,
    contentMaxWidth = 760.dp,
)

val LocalInterfaceStyle = staticCompositionLocalOf { InterfaceStyle.CLASSIC_TERMINAL }
val LocalZhishengChrome = staticCompositionLocalOf { ClassicTerminalChrome }

val isPhosphorVista: Boolean
    @Composable get() = LocalInterfaceStyle.current == InterfaceStyle.PHOSPHOR_VISTA
