package com.example.feature_editor.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

object PageCoordinatesConverter {

    /**
     * Converts a touch offset on screen/canvas to PDF page space.
     */
    fun screenToPage(
        screenOffset: Offset,
        pageOffsetOnScreen: Offset = Offset.Zero,
        zoom: Float = 1f
    ): Offset {
        val relative = screenOffset - pageOffsetOnScreen
        return if (zoom > 0f) relative / zoom else relative
    }

    /**
     * Converts a PDF page coordinate to screen/canvas space.
     */
    fun pageToScreen(
        pageOffset: Offset,
        pageOffsetOnScreen: Offset = Offset.Zero,
        zoom: Float = 1f
    ): Offset {
        return pageOffset * zoom + pageOffsetOnScreen
    }

    /**
     * Converts density-independent touch slop (e.g. 48dp) to PDF page units given the current zoom level.
     */
    fun touchSlopInPageSpace(density: Density, zoom: Float = 1f, dpValue: Float = 48f): Float {
        val slopPx = with(density) { dpValue.dp.toPx() }
        return if (zoom > 0f) slopPx / zoom else slopPx
    }

    /**
     * Converts a pointer position inside the page canvas (layout pixels, already un-zoomed by Compose)
     * into page space (rendered-bitmap pixels).
     *
     * [baseScale] = canvasWidthPx / pageBitmapWidthPx. Page space is independent of the on-screen
     * canvas size, so annotations stay glued to the page when the keyboard opens / the layout changes.
     */
    fun layoutToPage(layoutOffset: Offset, baseScale: Float): Offset =
        if (baseScale > 0f) layoutOffset / baseScale else layoutOffset

    /**
     * Converts a size in dp (measured on the physical screen) to page units, accounting for
     * pinch-zoom ([zoom]) and the canvas-to-page scale ([baseScale]).
     */
    fun dpToPageUnits(density: Density, dpValue: Float, zoom: Float, baseScale: Float): Float {
        val px = with(density) { dpValue.dp.toPx() }
        val denom = zoom * baseScale
        return if (denom > 0f) px / denom else px
    }
}
