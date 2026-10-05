package com.zhisheng.weather.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/** Tiny physical feedback on deliberate actions; scrolling cancels the standard click interaction. */
@Composable
internal fun Modifier.vistaClick(label: String, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) 0.985f else 1f,
        spring(dampingRatio = 0.82f, stiffness = 550f), label = "vista-press")
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .clickable(interactionSource = source, indication = null,
            role = Role.Button, onClickLabel = label, onClick = onClick)
}
