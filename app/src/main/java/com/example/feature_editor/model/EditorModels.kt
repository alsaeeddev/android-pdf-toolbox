package com.example.feature_editor.model

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import com.example.feature_editor.domain.HandleType
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Text box inner padding, in page units (same space as every other annotation coordinate). */
const val TEXT_PADDING_X = 6f
const val TEXT_PADDING_Y = 4f
const val MIN_TEXT_SIZE = 12f
const val MAX_TEXT_SIZE = 160f
const val DEFAULT_TEXT_SIZE = 32f

/** Signature stroke thickness in un-scaled stroke space. */
const val SIGNATURE_STROKE_WIDTH = 3f

enum class EditorTool {
    SELECT,
    VIEW,
    PEN,
    HIGHLIGHTER,
    ERASER,
    TEXT,
    SHAPE,
    STAMP,
    SIGNATURE,
    REDACT
}

enum class EraserMode {
    STROKE,
    OBJECT
}

enum class ShapeType {
    RECTANGLE,
    ELLIPSE,
    LINE,
    ARROW
}

enum class StampType(val label: String, val colorHex: String) {
    APPROVED("APPROVED", "#10B981"),
    DRAFT("DRAFT", "#F59E0B"),
    CONFIDENTIAL("CONFIDENTIAL", "#EF4444"),
    FINAL("FINAL", "#4F46E5"),
    REVIEWED("REVIEWED", "#14B8A6")
}

enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT
}

enum class EditorFontFamily(val displayName: String, val fontName: String) {
    SANS_SERIF("Sans", "sans-serif"),
    SERIF("Serif", "serif"),
    MONOSPACE("Mono", "monospace"),
    CURSIVE("Cursive", "cursive")
}

// Distance from point P to line segment AB
private const val STAMP_BASE_HEIGHT = 65f
private val stampWidthCache = java.util.concurrent.ConcurrentHashMap<StampType, Float>()

/** Unscaled stamp width: at least 140, wider when the label (24px bold) needs it. */
private fun stampBaseWidth(type: StampType): Float = stampWidthCache.getOrPut(type) {
    val p = Paint().apply { isAntiAlias = true; textSize = 24f; isFakeBoldText = true }
    maxOf(140f, p.measureText(type.label) + 44f)
}

fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val l2 = (b - a).getDistanceSquared()
    if (l2 == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2).coerceIn(0f, 1f)
    val projection = Offset(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
    return (p - projection).getDistance()
}

/**
 * Unified Annotation Model interface.
 * All coordinates and bounds are stored in PDF page coordinates.
 */
sealed interface Annotation {
    val id: String
    val pageIndex: Int
    val rotation: Float
    val zIndex: Int
    val bounds: Rect

    fun boundsRect(): Rect = bounds
    fun hitTest(pointInPageSpace: Offset, slop: Float): Boolean
    fun moved(dx: Float, dy: Float): Annotation
    fun resized(handle: HandleType, newBounds: Rect): Annotation
    fun rotated(angle: Float): Annotation
    fun draw(drawScope: DrawScope)
}

/**
 * Unified sealed interface extending Annotation, maintaining backward compatibility with PageAnnotation.
 */
sealed interface PageAnnotation : Annotation {

    data class Text(
        val annotationId: String = UUID.randomUUID().toString(),
        val page: Int,
        val text: String,
        val position: Offset,
        val color: Color,
        val fontSize: Float = DEFAULT_TEXT_SIZE,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val alignment: TextAlignment = TextAlignment.LEFT,
        val fontFamily: EditorFontFamily = EditorFontFamily.SANS_SERIF,
        override val rotation: Float = 0f,
        /** Measured size of the rendered text box (incl. padding), in page units. Kept in sync by the canvas. */
        val width: Float = 120f,
        val height: Float = 48f,
        override val zIndex: Int = 0
    ) : PageAnnotation {
        override val id: String get() = annotationId
        override val pageIndex: Int get() = page

        override val bounds: Rect
            get() = Rect(
                position.x,
                position.y,
                position.x + width.coerceAtLeast(24f),
                position.y + height.coerceAtLeast(24f)
            )

        override fun hitTest(pointInPageSpace: Offset, slop: Float): Boolean {
            val rect = boundsRect()
            val cx = rect.center.x
            val cy = rect.center.y
            val rad = Math.toRadians(rotation.toDouble())
            val dx = (pointInPageSpace.x - cx).toDouble()
            val dy = (pointInPageSpace.y - cy).toDouble()
            val localX = (dx * cos(-rad) - dy * sin(-rad)).toFloat()
            val localY = (dx * sin(-rad) + dy * cos(-rad)).toFloat()
            val hw = rect.width / 2f + slop
            val hh = rect.height / 2f + slop
            return abs(localX) <= hw && abs(localY) <= hh
        }

        override fun moved(dx: Float, dy: Float): Text =
            copy(position = position + Offset(dx, dy))

        /**
         * Resizing a text box scales the font (like Adobe Fill & Sign), keeping the opposite
         * side/corner anchored so the box follows the finger.
         */
        override fun resized(handle: HandleType, newBounds: Rect): Text {
            val cur = bounds
            val factor = when (handle) {
                HandleType.TOP_CENTER, HandleType.BOTTOM_CENTER -> newBounds.height / cur.height
                else -> newBounds.width / cur.width
            }
            val newFont = (fontSize * factor).coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
            val eff = newFont / fontSize
            val newW = cur.width * eff
            val newH = cur.height * eff
            val left = when (handle) {
                HandleType.TOP_LEFT, HandleType.MIDDLE_LEFT, HandleType.BOTTOM_LEFT -> cur.right - newW
                else -> cur.left
            }
            val top = when (handle) {
                HandleType.TOP_LEFT, HandleType.TOP_CENTER, HandleType.TOP_RIGHT -> cur.bottom - newH
                else -> cur.top
            }
            return copy(position = Offset(left, top), fontSize = newFont, width = newW, height = newH)
        }

        override fun rotated(angle: Float): Text = copy(rotation = angle)

        override fun draw(drawScope: DrawScope) {
            // Drawn via Compose Layer / native canvas
        }
    }

    data class Shape(
        val annotationId: String = UUID.randomUUID().toString(),
        val page: Int,
        val shapeType: ShapeType,
        val start: Offset,
        val end: Offset,
        val color: Color,
        val strokeWidth: Float = 4f,
        val fillColor: Color? = null,
        val opacity: Float = 1f,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0
    ) : PageAnnotation {
        override val id: String get() = annotationId
        override val pageIndex: Int get() = page

        override val bounds: Rect
            get() {
                val left = minOf(start.x, end.x)
                val top = minOf(start.y, end.y)
                val right = maxOf(start.x, end.x)
                val bottom = maxOf(start.y, end.y)
                val pad = strokeWidth / 2f
                return Rect(left - pad, top - pad, right + pad, bottom + pad)
            }

        override fun hitTest(pointInPageSpace: Offset, slop: Float): Boolean {
            val effectiveSlop = slop + strokeWidth / 2f
            return when (shapeType) {
                ShapeType.LINE, ShapeType.ARROW -> {
                    // The line is drawn rotated around its bounds centre, so un-rotate the touch point first.
                    val c = boundsRect().center
                    val rad = Math.toRadians(rotation.toDouble())
                    val dx = (pointInPageSpace.x - c.x).toDouble()
                    val dy = (pointInPageSpace.y - c.y).toDouble()
                    val lx = (dx * cos(-rad) - dy * sin(-rad)).toFloat()
                    val ly = (dx * sin(-rad) + dy * cos(-rad)).toFloat()
                    distanceToSegment(Offset(c.x + lx, c.y + ly), start, end) <= effectiveSlop
                }
                ShapeType.RECTANGLE, ShapeType.ELLIPSE -> {
                    val rect = boundsRect()
                    val center = rect.center
                    val rad = Math.toRadians(rotation.toDouble())
                    val dx = (pointInPageSpace.x - center.x).toDouble()
                    val dy = (pointInPageSpace.y - center.y).toDouble()
                    val localX = (dx * cos(-rad) - dy * sin(-rad)).toFloat()
                    val localY = (dx * sin(-rad) + dy * cos(-rad)).toFloat()
                    val hw = rect.width / 2f + slop
                    val hh = rect.height / 2f + slop
                    abs(localX) <= hw && abs(localY) <= hh
                }
            }
        }

        override fun moved(dx: Float, dy: Float): Shape =
            copy(start = start + Offset(dx, dy), end = end + Offset(dx, dy))

        override fun resized(handle: HandleType, newBounds: Rect): Shape {
            // `bounds` is the geometry grown by strokeWidth/2 on every side. Convert the requested
            // (padded) bounds back to geometry, otherwise every drag tick adds the padding again and
            // the shape can only grow, never shrink.
            val pad = strokeWidth / 2f
            var l = newBounds.left + pad
            var t = newBounds.top + pad
            var r = newBounds.right - pad
            var b = newBounds.bottom - pad

            // Keep a small minimum size; the edge opposite to the dragged handle stays anchored.
            val minSize = 8f
            if (r - l < minSize) {
                when (handle) {
                    HandleType.TOP_LEFT, HandleType.MIDDLE_LEFT, HandleType.BOTTOM_LEFT -> l = r - minSize
                    HandleType.TOP_RIGHT, HandleType.MIDDLE_RIGHT, HandleType.BOTTOM_RIGHT -> r = l + minSize
                    else -> { val cx = (l + r) / 2f; l = cx - minSize / 2f; r = cx + minSize / 2f }
                }
            }
            if (b - t < minSize) {
                when (handle) {
                    HandleType.TOP_LEFT, HandleType.TOP_CENTER, HandleType.TOP_RIGHT -> t = b - minSize
                    HandleType.BOTTOM_LEFT, HandleType.BOTTOM_CENTER, HandleType.BOTTOM_RIGHT -> b = t + minSize
                    else -> { val cy = (t + b) / 2f; t = cy - minSize / 2f; b = cy + minSize / 2f }
                }
            }
            return copy(start = Offset(l, t), end = Offset(r, b))
        }

        override fun rotated(angle: Float): Shape = copy(rotation = angle)

        override fun draw(drawScope: DrawScope) {
            // `rotation` was stored but never painted => shapes looked un-rotated on screen.
            if (rotation == 0f) {
                drawContent(drawScope)
            } else {
                drawScope.rotate(degrees = rotation, pivot = bounds.center) { drawContent(this) }
            }
        }

        private fun drawContent(drawScope: DrawScope) {
            val strokeColor = color.copy(alpha = color.alpha * opacity)
            val left = minOf(start.x, end.x)
            val top = minOf(start.y, end.y)
            val right = maxOf(start.x, end.x)
            val bottom = maxOf(start.y, end.y)

            when (shapeType) {
                ShapeType.RECTANGLE -> {
                    fillColor?.let { fill ->
                        drawScope.drawRect(
                            color = fill.copy(alpha = fill.alpha * opacity),
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top)
                        )
                    }
                    drawScope.drawRect(
                        color = strokeColor,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style = Stroke(width = strokeWidth)
                    )
                }
                ShapeType.ELLIPSE -> {
                    fillColor?.let { fill ->
                        drawScope.drawOval(
                            color = fill.copy(alpha = fill.alpha * opacity),
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top)
                        )
                    }
                    drawScope.drawOval(
                        color = strokeColor,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style = Stroke(width = strokeWidth)
                    )
                }
                ShapeType.LINE -> {
                    drawScope.drawLine(
                        color = strokeColor,
                        start = start,
                        end = end,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
                ShapeType.ARROW -> {
                    drawScope.drawLine(
                        color = strokeColor,
                        start = start,
                        end = end,
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    val angle = atan2(end.y - start.y, end.x - start.x)
                    val headLength = (20f + strokeWidth * 2f).coerceAtMost(40f)
                    val arrowAngle = Math.PI / 6
                    val p1 = Offset(
                        (end.x - headLength * cos(angle - arrowAngle)).toFloat(),
                        (end.y - headLength * sin(angle - arrowAngle)).toFloat()
                    )
                    val p2 = Offset(
                        (end.x - headLength * cos(angle + arrowAngle)).toFloat(),
                        (end.y - headLength * sin(angle + arrowAngle)).toFloat()
                    )
                    drawScope.drawLine(strokeColor, end, p1, strokeWidth = strokeWidth, cap = StrokeCap.Round)
                    drawScope.drawLine(strokeColor, end, p2, strokeWidth = strokeWidth, cap = StrokeCap.Round)
                }
            }
        }
    }

    data class Freehand(
        val annotationId: String = UUID.randomUUID().toString(),
        val page: Int,
        val points: List<Offset>,
        val color: Color,
        val strokeWidth: Float,
        val isHighlighter: Boolean = false,
        val opacity: Float = 1f,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0
    ) : PageAnnotation {
        override val id: String get() = annotationId
        override val pageIndex: Int get() = page

        override val bounds: Rect
            get() {
                if (points.isEmpty()) return Rect.Zero
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                for (p in points) {
                    if (p.x < minX) minX = p.x
                    if (p.y < minY) minY = p.y
                    if (p.x > maxX) maxX = p.x
                    if (p.y > maxY) maxY = p.y
                }
                val pad = strokeWidth / 2f
                return Rect(minX - pad, minY - pad, maxX + pad, maxY + pad)
            }

        override fun hitTest(pointInPageSpace: Offset, slop: Float): Boolean {
            if (points.isEmpty()) return false
            val effectiveSlop = slop + strokeWidth / 2f
            if (points.size == 1) return (pointInPageSpace - points[0]).getDistance() <= effectiveSlop
            for (i in 0 until points.size - 1) {
                if (distanceToSegment(pointInPageSpace, points[i], points[i + 1]) <= effectiveSlop) {
                    return true
                }
            }
            return false
        }

        override fun moved(dx: Float, dy: Float): Freehand =
            copy(points = points.map { it + Offset(dx, dy) })

        override fun resized(handle: HandleType, newBounds: Rect): Freehand {
            if (points.isEmpty()) return copy()
            // Work on the real point geometry (bounds minus the stroke padding).
            val pad = strokeWidth / 2f
            val oldL = bounds.left + pad
            val oldT = bounds.top + pad
            val oldW = bounds.width - 2f * pad
            val oldH = bounds.height - 2f * pad
            val newL = newBounds.left + pad
            val newT = newBounds.top + pad
            val newW = (newBounds.width - 2f * pad).coerceAtLeast(1f)
            val newH = (newBounds.height - 2f * pad).coerceAtLeast(1f)
            // A perfectly horizontal / vertical stroke has no extent on one axis: just translate on it.
            val scaleX = if (oldW > 0.01f) newW / oldW else 1f
            val scaleY = if (oldH > 0.01f) newH / oldH else 1f
            val newPoints = points.map { pt ->
                Offset(
                    newL + (pt.x - oldL) * scaleX,
                    newT + (pt.y - oldT) * scaleY
                )
            }
            return copy(points = newPoints)
        }

        override fun rotated(angle: Float): Freehand = copy(rotation = angle)

        override fun draw(drawScope: DrawScope) {
            if (points.size < 2) return
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    val p0 = points[i - 1]
                    val p1 = points[i]
                    quadraticTo(p0.x, p0.y, (p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
                }
                lineTo(points.last().x, points.last().y)
            }
            val drawAlpha = if (isHighlighter) 0.35f * opacity else opacity
            drawScope.drawPath(
                path = path,
                color = color.copy(alpha = color.alpha * drawAlpha),
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }

    data class Highlight(
        val annotationId: String = UUID.randomUUID().toString(),
        val page: Int,
        override val bounds: Rect,
        val color: Color = Color(0xFFFDE047),
        val opacity: Float = 0.35f,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0
    ) : PageAnnotation {
        override val id: String get() = annotationId
        override val pageIndex: Int get() = page

        override fun hitTest(pointInPageSpace: Offset, slop: Float): Boolean {
            return bounds.inflate(slop).contains(pointInPageSpace)
        }

        override fun moved(dx: Float, dy: Float): Highlight =
            copy(bounds = bounds.translate(Offset(dx, dy)))

        override fun resized(handle: HandleType, newBounds: Rect): Highlight =
            copy(bounds = newBounds)

        override fun rotated(angle: Float): Highlight = copy(rotation = angle)

        override fun draw(drawScope: DrawScope) {
            drawScope.drawRect(
                color = color.copy(alpha = color.alpha * opacity),
                topLeft = bounds.topLeft,
                size = bounds.size
            )
        }
    }

    data class Underline(
        val annotationId: String = UUID.randomUUID().toString(),
        val page: Int,
        val start: Offset,
        val end: Offset,
        val color: Color = Color.Red,
        val strokeWidth: Float = 2f,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0
    ) : PageAnnotation {
        override val id: String get() = annotationId
        override val pageIndex: Int get() = page

        override val bounds: Rect
            get() {
                val left = minOf(start.x, end.x)
                val top = minOf(start.y, end.y)
                val right = maxOf(start.x, end.x)
                val bottom = maxOf(start.y, end.y)
                val pad = strokeWidth / 2f
                return Rect(left - pad, top - pad, right + pad, bottom + pad)
            }

        override fun hitTest(pointInPageSpace: Offset, slop: Float): Boolean {
            return distanceToSegment(pointInPageSpace, start, end) <= slop + strokeWidth / 2f
        }

        override fun moved(dx: Float, dy: Float): Underline =
            copy(start = start + Offset(dx, dy), end = end + Offset(dx, dy))

        override fun resized(handle: HandleType, newBounds: Rect): Underline =
            copy(start = newBounds.bottomLeft, end = newBounds.bottomRight)

        override fun rotated(angle: Float): Underline = copy(rotation = angle)

        override fun draw(drawScope: DrawScope) {
            drawScope.drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }

    data class Strike(
        val annotationId: String = UUID.randomUUID().toString(),
        val page: Int,
        val start: Offset,
        val end: Offset,
        val color: Color = Color.Red,
        val strokeWidth: Float = 2f,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0
    ) : PageAnnotation {
        override val id: String get() = annotationId
        override val pageIndex: Int get() = page

        override val bounds: Rect
            get() {
                val left = minOf(start.x, end.x)
                val top = minOf(start.y, end.y)
                val right = maxOf(start.x, end.x)
                val bottom = maxOf(start.y, end.y)
                val pad = strokeWidth / 2f
                return Rect(left - pad, top - pad, right + pad, bottom + pad)
            }

        override fun hitTest(pointInPageSpace: Offset, slop: Float): Boolean {
            return distanceToSegment(pointInPageSpace, start, end) <= slop + strokeWidth / 2f
        }

        override fun moved(dx: Float, dy: Float): Strike =
            copy(start = start + Offset(dx, dy), end = end + Offset(dx, dy))

        override fun resized(handle: HandleType, newBounds: Rect): Strike =
            copy(start = Offset(newBounds.left, newBounds.center.y), end = Offset(newBounds.right, newBounds.center.y))

        override fun rotated(angle: Float): Strike = copy(rotation = angle)

        override fun draw(drawScope: DrawScope) {
            drawScope.drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }

    data class Stamp(
        val annotationId: String = UUID.randomUUID().toString(),
        val page: Int,
        val stampType: StampType,
        /** Centre of the stamp in page space (also the rotation pivot). */
        val position: Offset,
        val dateText: String,
        /** Uniform scale (keeps the proportions, so the text always stays inside the box). */
        val scale: Float = 1f,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0
    ) : PageAnnotation {
        override val id: String get() = annotationId
        override val pageIndex: Int get() = page

        // The box is as wide as its label needs (CONFIDENTIAL used to overflow a fixed 140 box).
        private val boxWidth: Float get() = stampBaseWidth(stampType) * scale
        private val boxHeight: Float get() = STAMP_BASE_HEIGHT * scale

        override val bounds: Rect
            get() = Rect(
                position.x - boxWidth / 2f, position.y - boxHeight / 2f,
                position.x + boxWidth / 2f, position.y + boxHeight / 2f
            )

        override fun hitTest(pointInPageSpace: Offset, slop: Float): Boolean {
            val rect = boundsRect()
            val center = rect.center
            val rad = Math.toRadians(rotation.toDouble())
            val dx = (pointInPageSpace.x - center.x).toDouble()
            val dy = (pointInPageSpace.y - center.y).toDouble()
            val localX = (dx * cos(-rad) - dy * sin(-rad)).toFloat()
            val localY = (dx * sin(-rad) + dy * cos(-rad)).toFloat()
            val hw = rect.width / 2f + slop
            val hh = rect.height / 2f + slop
            return abs(localX) <= hw && abs(localY) <= hh
        }

        override fun moved(dx: Float, dy: Float): Stamp =
            copy(position = position + Offset(dx, dy))

        /** Uniform scale only; the edge/corner opposite to the dragged handle stays anchored. */
        override fun resized(handle: HandleType, newBounds: Rect): Stamp {
            val cur = bounds
            val factor = when (handle) {
                HandleType.TOP_CENTER, HandleType.BOTTOM_CENTER -> newBounds.height / cur.height
                else -> newBounds.width / cur.width
            }
            val newScale = (scale * factor).coerceIn(0.3f, 6f)
            val eff = newScale / scale
            val newW = cur.width * eff
            val newH = cur.height * eff
            val left = when (handle) {
                HandleType.TOP_LEFT, HandleType.MIDDLE_LEFT, HandleType.BOTTOM_LEFT -> cur.right - newW
                HandleType.TOP_CENTER, HandleType.BOTTOM_CENTER -> cur.center.x - newW / 2f
                else -> cur.left
            }
            val top = when (handle) {
                HandleType.TOP_LEFT, HandleType.TOP_CENTER, HandleType.TOP_RIGHT -> cur.bottom - newH
                HandleType.MIDDLE_LEFT, HandleType.MIDDLE_RIGHT -> cur.center.y - newH / 2f
                else -> cur.top
            }
            return copy(position = Offset(left + newW / 2f, top + newH / 2f), scale = newScale)
        }

        override fun rotated(angle: Float): Stamp = copy(rotation = angle)

        override fun draw(drawScope: DrawScope) {
            val stampColor = Color(android.graphics.Color.parseColor(stampType.colorHex))
            val width = boxWidth
            val height = boxHeight
            val s = scale

            drawScope.rotate(degrees = rotation, pivot = position) {
                drawRoundRect(
                    color = stampColor.copy(alpha = 0.12f),
                    topLeft = Offset(position.x - width / 2, position.y - height / 2),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(8f * s, 8f * s)
                )
                drawRoundRect(
                    color = stampColor,
                    topLeft = Offset(position.x - width / 2, position.y - height / 2),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(8f * s, 8f * s),
                    style = Stroke(width = 3f * s)
                )
                drawIntoCanvas { canvas ->
                    val textPaint = Paint().apply {
                        isAntiAlias = true
                        textSize = 24f * s
                        isFakeBoldText = true
                        color = android.graphics.Color.parseColor(stampType.colorHex)
                        textAlign = Paint.Align.CENTER
                    }
                    val datePaint = Paint().apply {
                        isAntiAlias = true
                        textSize = 14f * s
                        color = android.graphics.Color.parseColor(stampType.colorHex)
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.nativeCanvas.drawText(stampType.label, position.x, position.y - 2f * s, textPaint)
                    canvas.nativeCanvas.drawText(dateText, position.x, position.y + 20f * s, datePaint)
                }
            }
        }
    }

    data class Signature(
        val annotationId: String = UUID.randomUUID().toString(),
        val page: Int,
        /** Page position of the stroke-space origin (0,0). Visible bounds start at position + contentMin * scale. */
        val position: Offset,
        val strokes: List<List<Offset>>,
        val strokeColor: Color = Color.Black,
        val scale: Float = 1f,
        /** true => every entry in [strokes] is a closed glyph contour that is filled (typed signature). */
        val filled: Boolean = false,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0
    ) : PageAnnotation {
        override val id: String get() = annotationId
        override val pageIndex: Int get() = page

        /** Bounding box of all stroke points in un-scaled stroke space. */
        private val contentRect: Rect
            get() {
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = -Float.MAX_VALUE
                var maxY = -Float.MAX_VALUE
                for (stroke in strokes) {
                    for (p in stroke) {
                        if (p.x < minX) minX = p.x
                        if (p.y < minY) minY = p.y
                        if (p.x > maxX) maxX = p.x
                        if (p.y > maxY) maxY = p.y
                    }
                }
                if (minX == Float.MAX_VALUE) return Rect(0f, 0f, 120f, 60f)
                return Rect(minX, minY, maxX, maxY)
            }

        // Bounds always match exactly what draw() paints, so selection frame, handles and hit-testing line up.
        override val bounds: Rect
            get() {
                val c = contentRect
                val left = position.x + c.left * scale
                val top = position.y + c.top * scale
                return Rect(
                    left,
                    top,
                    left + (c.width * scale).coerceAtLeast(1f),
                    top + (c.height * scale).coerceAtLeast(1f)
                )
            }

        override fun hitTest(pointInPageSpace: Offset, slop: Float): Boolean {
            val rect = boundsRect()
            val center = rect.center
            val rad = Math.toRadians(rotation.toDouble())
            val dx = (pointInPageSpace.x - center.x).toDouble()
            val dy = (pointInPageSpace.y - center.y).toDouble()
            val localX = (dx * cos(-rad) - dy * sin(-rad)).toFloat()
            val localY = (dx * sin(-rad) + dy * cos(-rad)).toFloat()
            val hw = rect.width / 2f + slop
            val hh = rect.height / 2f + slop
            return abs(localX) <= hw && abs(localY) <= hh
        }

        override fun moved(dx: Float, dy: Float): Signature =
            copy(position = position + Offset(dx, dy))

        /** Uniform scale only (keeps signature proportions); the opposite side/corner stays anchored. */
        override fun resized(handle: HandleType, newBounds: Rect): Signature {
            val cur = bounds
            val factor = when (handle) {
                HandleType.TOP_CENTER, HandleType.BOTTOM_CENTER -> newBounds.height / cur.height
                else -> newBounds.width / cur.width
            }
            val newScale = (scale * factor).coerceIn(0.1f, 8f)
            val eff = newScale / scale
            val newW = cur.width * eff
            val newH = cur.height * eff
            val left = when (handle) {
                HandleType.TOP_LEFT, HandleType.MIDDLE_LEFT, HandleType.BOTTOM_LEFT -> cur.right - newW
                HandleType.TOP_CENTER, HandleType.BOTTOM_CENTER -> cur.center.x - newW / 2f
                else -> cur.left
            }
            val top = when (handle) {
                HandleType.TOP_LEFT, HandleType.TOP_CENTER, HandleType.TOP_RIGHT -> cur.bottom - newH
                HandleType.MIDDLE_LEFT, HandleType.MIDDLE_RIGHT -> cur.center.y - newH / 2f
                else -> cur.top
            }
            val c = contentRect
            return copy(
                position = Offset(left - c.left * newScale, top - c.top * newScale),
                scale = newScale
            )
        }

        override fun rotated(angle: Float): Signature = copy(rotation = angle)

        override fun draw(drawScope: DrawScope) {
            drawScope.rotate(degrees = rotation, pivot = bounds.center) {
                if (filled) {
                    // One path => nested contours (holes in a, e, o ...) are cut out by the winding rule.
                    val glyphs = Path().apply {
                        strokes.forEach { pts ->
                            if (pts.size > 2) {
                                moveTo(position.x + pts[0].x * scale, position.y + pts[0].y * scale)
                                for (i in 1 until pts.size) {
                                    lineTo(position.x + pts[i].x * scale, position.y + pts[i].y * scale)
                                }
                                close()
                            }
                        }
                    }
                    drawPath(path = glyphs, color = strokeColor)
                    return@rotate
                }
                strokes.forEach { strokePoints ->
                    if (strokePoints.size > 1) {
                        val path = Path().apply {
                            moveTo(position.x + strokePoints[0].x * scale, position.y + strokePoints[0].y * scale)
                            for (i in 1 until strokePoints.size) {
                                lineTo(position.x + strokePoints[i].x * scale, position.y + strokePoints[i].y * scale)
                            }
                        }
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(
                                width = SIGNATURE_STROKE_WIDTH * scale,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }

    data class Image(
        val annotationId: String = UUID.randomUUID().toString(),
        val page: Int,
        override val bounds: Rect,
        val bitmap: Bitmap? = null,
        val opacity: Float = 1f,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0
    ) : PageAnnotation {
        override val id: String get() = annotationId
        override val pageIndex: Int get() = page

        override fun hitTest(pointInPageSpace: Offset, slop: Float): Boolean {
            val rect = boundsRect()
            val center = rect.center
            val rad = Math.toRadians(rotation.toDouble())
            val dx = (pointInPageSpace.x - center.x).toDouble()
            val dy = (pointInPageSpace.y - center.y).toDouble()
            val localX = (dx * cos(-rad) - dy * sin(-rad)).toFloat()
            val localY = (dx * sin(-rad) + dy * cos(-rad)).toFloat()
            val hw = rect.width / 2f + slop
            val hh = rect.height / 2f + slop
            return abs(localX) <= hw && abs(localY) <= hh
        }

        override fun moved(dx: Float, dy: Float): Image =
            copy(bounds = bounds.translate(Offset(dx, dy)))

        override fun resized(handle: HandleType, newBounds: Rect): Image =
            copy(bounds = newBounds)

        override fun rotated(angle: Float): Image = copy(rotation = angle)

        override fun draw(drawScope: DrawScope) {
            bitmap?.let { bmp ->
                drawScope.drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        isAntiAlias = true
                        alpha = (opacity * 255).toInt()
                    }
                    canvas.nativeCanvas.drawBitmap(
                        bmp,
                        null,
                        RectF(bounds.left, bounds.top, bounds.right, bounds.bottom),
                        paint
                    )
                }
            }
        }
    }

    data class Redaction(
        val annotationId: String = UUID.randomUUID().toString(),
        val page: Int,
        override val bounds: Rect,
        override val rotation: Float = 0f,
        override val zIndex: Int = 0
    ) : PageAnnotation {
        override val id: String get() = annotationId
        override val pageIndex: Int get() = page

        override fun hitTest(pointInPageSpace: Offset, slop: Float): Boolean {
            return bounds.inflate(slop).contains(pointInPageSpace)
        }

        override fun moved(dx: Float, dy: Float): Redaction =
            copy(bounds = bounds.translate(Offset(dx, dy)))

        override fun resized(handle: HandleType, newBounds: Rect): Redaction =
            copy(bounds = newBounds)

        override fun rotated(angle: Float): Redaction = copy(rotation = angle)

        override fun draw(drawScope: DrawScope) {
            drawScope.drawRect(
                color = Color.Black,
                topLeft = bounds.topLeft,
                size = bounds.size
            )
        }
    }
}

// Subtype Typealiases for unified naming
typealias TextAnnotation = PageAnnotation.Text
typealias ShapeAnnotation = PageAnnotation.Shape
typealias InkAnnotation = PageAnnotation.Freehand
typealias StampAnnotation = PageAnnotation.Stamp
typealias SignatureAnnotation = PageAnnotation.Signature
typealias RedactionAnnotation = PageAnnotation.Redaction
typealias HighlightAnnotation = PageAnnotation.Highlight
typealias UnderlineAnnotation = PageAnnotation.Underline
typealias StrikeAnnotation = PageAnnotation.Strike
typealias ImageAnnotation = PageAnnotation.Image

val defaultEditorColors = listOf(
    Color(0xFF000000), // Black
    Color(0xFF4F46E5), // Indigo
    Color(0xFF14B8A6), // Teal
    Color(0xFFEF4444), // Red
    Color(0xFF10B981), // Green
    Color(0xFFF59E0B), // Amber
    Color(0xFF8B5CF6), // Purple
    Color(0xFFEC4899), // Pink
    Color(0xFFFFFFFF)  // White
)

val highlighterColors = listOf(
    Color(0xFFFDE047), // Yellow
    Color(0xFF86EFAC), // Green
    Color(0xFF93C5FD), // Blue
    Color(0xFFFCA5A5), // Red
    Color(0xFFF0ABFC)  // Purple
)
