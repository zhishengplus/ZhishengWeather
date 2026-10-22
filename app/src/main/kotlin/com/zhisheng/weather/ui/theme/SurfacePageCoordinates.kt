package com.zhisheng.weather.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow

/** Keep the page's light attached to its cards while the pager translates the whole page.
 * Vertical list motion still changes the sample. Coordinates are only used in layout callbacks,
 * never observed by composition or updated as per-frame Compose state on the page itself.
 */
internal class SurfacePageCoordinates(private val viewport: SurfacePageCoordinates? = null) {
    var coordinates: LayoutCoordinates? = null

    fun positionOf(child: LayoutCoordinates, inWindow: Boolean = false): Offset {
        val page = coordinates?.takeIf { it.isAttached }
        val global = if (inWindow) child.positionInWindow() else child.positionInRoot()
        // A dialog can inherit composition locals while living in another Android window.
        return if (page == null || page.findRootCoordinates() !== child.findRootCoordinates()) global
        else {
            val anchor = viewport?.coordinates?.takeIf { it.isAttached }
            val anchorX = anchor?.let { if (inWindow) it.positionInWindow().x else it.positionInRoot().x } ?: 0f
            Offset(anchorX + page.localPositionOf(child, Offset.Zero).x, global.y)
        }
    }
}

internal val LocalSurfacePageCoordinates = staticCompositionLocalOf<SurfacePageCoordinates?> { null }
