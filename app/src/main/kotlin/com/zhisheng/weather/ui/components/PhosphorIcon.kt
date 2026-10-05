package com.zhisheng.weather.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

/** Regular-weight Phosphor asset renderer. Source and MIT notice: THIRD_PARTY_NOTICES.md. */
@Composable
fun PhosphorIcon(
    @DrawableRes resourceId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        painter = painterResource(resourceId),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
