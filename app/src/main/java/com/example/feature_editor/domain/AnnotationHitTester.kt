package com.example.feature_editor.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.feature_editor.model.Annotation
import com.example.feature_editor.model.PageAnnotation
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class HandleType {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    MIDDLE_LEFT,
    MIDDLE_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
    ROTATE,
    BODY
}

object AnnotationHitTester {

    /**
     * Calculates rectangular bounds for a text annotation.
     */
    fun getTextBounds(annotation: PageAnnotation.Text): Rect {
        return annotation.boundsRect()
    }

    /**
     * Hit-tests whether a point lies within an annotation, accounting for inverse rotation and touch slop.
     */
    fun hitTest(point: Offset, annotation: Annotation, touchSlop: Float = 24f): Boolean {
        return annotation.hitTest(point, touchSlop)
    }

    /**
     * Hit-tests all annotations on a page from topmost to bottommost (highest z-order first).
     */
    fun hitTestAnnotations(
        point: Offset,
        annotations: List<Annotation>,
        pageIndex: Int,
        touchSlop: Float = 24f
    ): Annotation? {
        val pageAnnotations = annotations
            .filter { it.pageIndex == pageIndex }
            .sortedByDescending { it.zIndex }

        for (ann in pageAnnotations) {
            if (ann.hitTest(point, touchSlop)) {
                return ann
            }
        }
        return null
    }

    /**
     * Hit-tests handles (or body) of any selected annotation in page coordinates.
     */
    fun hitTestHandles(
        point: Offset,
        annotation: Annotation,
        handleRadius: Float = 28f,
        rotateHandleOffset: Float = 36f
    ): HandleType? {
        val bounds = annotation.boundsRect()
        val cx = bounds.center.x
        val cy = bounds.center.y
        val rad = Math.toRadians(annotation.rotation.toDouble())

        val dx = (point.x - cx).toDouble()
        val dy = (point.y - cy).toDouble()
        val localX = (dx * cos(-rad) - dy * sin(-rad)).toFloat()
        val localY = (dx * sin(-rad) + dy * cos(-rad)).toFloat()

        val hw = bounds.width / 2f
        val hh = bounds.height / 2f

        // Rotate handle sits [rotateHandleOffset] page units above top-center
        val rotateHandlePos = Offset(0f, -hh - rotateHandleOffset)
        if ((Offset(localX, localY) - rotateHandlePos).getDistance() <= handleRadius) {
            return HandleType.ROTATE
        }

        // 8 Perimeter handles
        val handles = listOf(
            HandleType.TOP_LEFT to Offset(-hw, -hh),
            HandleType.TOP_CENTER to Offset(0f, -hh),
            HandleType.TOP_RIGHT to Offset(hw, -hh),
            HandleType.MIDDLE_LEFT to Offset(-hw, 0f),
            HandleType.MIDDLE_RIGHT to Offset(hw, 0f),
            HandleType.BOTTOM_LEFT to Offset(-hw, hh),
            HandleType.BOTTOM_CENTER to Offset(0f, hh),
            HandleType.BOTTOM_RIGHT to Offset(hw, hh)
        )

        for ((type, pos) in handles) {
            if ((Offset(localX, localY) - pos).getDistance() <= handleRadius) {
                return type
            }
        }

        if (abs(localX) <= hw && abs(localY) <= hh) {
            return HandleType.BODY
        }

        return null
    }
}
