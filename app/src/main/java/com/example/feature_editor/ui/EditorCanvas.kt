package com.example.feature_editor.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.feature_editor.domain.AnnotationHitTester
import com.example.feature_editor.domain.HandleType
import com.example.feature_editor.domain.PageCoordinatesConverter
import com.example.feature_editor.model.Annotation
import com.example.feature_editor.model.EditorFontFamily
import com.example.feature_editor.model.EditorTool
import com.example.feature_editor.model.InkAnnotation
import com.example.feature_editor.model.RedactionAnnotation
import com.example.feature_editor.model.ShapeAnnotation
import com.example.feature_editor.model.ShapeType
import com.example.feature_editor.model.SignatureAnnotation
import com.example.feature_editor.model.StampAnnotation
import com.example.feature_editor.model.StampType
import com.example.feature_editor.model.TEXT_PADDING_X
import com.example.feature_editor.model.TEXT_PADDING_Y
import com.example.feature_editor.model.TextAlignment
import com.example.feature_editor.model.TextAnnotation
import com.example.ui.theme.BrandIndigo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Page canvas.
 *
 * COORDINATE SPACE: every annotation lives in *page space* = pixels of the rendered page bitmap.
 * The canvas is just a view onto that space scaled by `baseScale = canvasWidth / bitmapWidth`.
 * That keeps annotations glued to the page when the layout changes (keyboard, toolbars, rotation)
 * and makes on-screen rendering identical to the exported PDF.
 */
@Composable
fun EditorCanvas(
    pageIndex: Int,
    pageBitmap: Bitmap?,
    activeTool: EditorTool,
    selectedColor: Color,
    selectedStrokeWidth: Float,
    selectedShapeType: ShapeType,
    selectedStampType: StampType,
    eraserRadiusDp: Float = 24f,
    eraserPosition: Offset? = null,
    annotations: List<Annotation>,
    draftAnnotation: Annotation? = null,
    selectedAnnotationId: String?,
    editingAnnotationId: String?,
    onPageTap: (Offset) -> Unit,
    onPageDoubleTap: (Offset) -> Unit,
    onAnnotationTap: (String) -> Unit,
    onSelectAnnotation: (String) -> Unit,
    onDeselect: () -> Unit,
    onStartEdit: (String) -> Unit,
    onOpenShapeOptionsSheet: () -> Unit,
    onTextChanged: (String, String) -> Unit,
    onCommitEdit: () -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onDuplicateAnnotation: (String) -> Unit,
    onMoveAnnotation: (String, Offset) -> Unit,
    onResizeAnnotation: (String, HandleType, Offset) -> Unit,
    onRotateAnnotation: (String, Float) -> Unit,
    onBringToFront: (String) -> Unit,
    onSendToBack: (String) -> Unit,
    onUpdateDraft: (Annotation?) -> Unit,
    onCommitDraft: () -> Unit,
    onCommitDraftAndSelect: () -> Unit,
    onStartEraseGesture: () -> Unit,
    /** (pointInPage, eraserRadiusInPageUnits) */
    onEraseAt: (Offset, Float) -> Unit,
    onUpdateEraserPosition: (Offset?) -> Unit,
    onFinishEraseGesture: () -> Unit,
    onAnnotationAdded: (Annotation) -> Unit,
    onRemoveAnnotation: (Annotation) -> Unit,
    modifier: Modifier = Modifier,
    zoomLevel: Float = 1f,
    /** (annotationId, widthInPageUnits, heightInPageUnits) of a laid-out text box. */
    onTextSizeChanged: (String, Float, Float) -> Unit = { _, _, _ -> }
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // The gesture block below is created once per page and must never read stale values.
    val currentTool by rememberUpdatedState(activeTool)
    val currentAnnotations by rememberUpdatedState(annotations)
    val currentSelectedId by rememberUpdatedState(selectedAnnotationId)
    val currentEditingId by rememberUpdatedState(editingAnnotationId)
    val currentZoom by rememberUpdatedState(zoomLevel)
    val currentColor by rememberUpdatedState(selectedColor)
    val currentStrokeWidth by rememberUpdatedState(selectedStrokeWidth)
    val currentShapeType by rememberUpdatedState(selectedShapeType)
    val currentStampType by rememberUpdatedState(selectedStampType)
    val currentEraserRadiusDp by rememberUpdatedState(eraserRadiusDp)
    val currentOnEraseAt by rememberUpdatedState(onEraseAt)
    val currentOnPageTap by rememberUpdatedState(onPageTap)

    // Bounds (canvas px) of the floating action bar. The page gesture block ignores touches that start
    // on it; otherwise it consumes the pointer-up and the buttons' own click is cancelled.
    var actionBarBounds by remember { mutableStateOf(Rect.Zero) }
    val currentActionBarBounds by rememberUpdatedState(actionBarBounds)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val canvasWidthPx = constraints.maxWidth.toFloat()
        val canvasHeightPx = constraints.maxHeight.toFloat()
        val pageWidth = pageBitmap?.width?.takeIf { it > 0 }?.toFloat() ?: canvasWidthPx.coerceAtLeast(1f)
        val baseScale = if (pageWidth > 0f) canvasWidthPx / pageWidth else 1f
        val currentBaseScale by rememberUpdatedState(baseScale)

        val eraserRadiusPage = PageCoordinatesConverter.dpToPageUnits(density, eraserRadiusDp, zoomLevel, baseScale)
        val eraserBorderPage = PageCoordinatesConverter.dpToPageUnits(density, 1.5f, zoomLevel, baseScale)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pageIndex) {
                    detectTapGestures(
                        onDoubleTap = { tapPoint ->
                            val bs = currentBaseScale
                            val pagePt = PageCoordinatesConverter.layoutToPage(tapPoint, bs)
                            val slop = PageCoordinatesConverter.dpToPageUnits(density, 48f, currentZoom, bs)
                            val hit = AnnotationHitTester.hitTestAnnotations(pagePt, currentAnnotations, pageIndex, slop)
                            if (hit is TextAnnotation) {
                                onSelectAnnotation(hit.id)
                                onStartEdit(hit.id)
                            } else {
                                onPageDoubleTap(pagePt)
                            }
                        }
                    )
                }
                .pointerInput(pageIndex) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downPointerId = down.id

                        // Touch on the floating action bar (edit / order / duplicate / delete): leave it
                        // completely alone so the buttons receive a clean tap.
                        val barRect = currentActionBarBounds
                        if (barRect != Rect.Zero && barRect.inflate(4.dp.toPx()).contains(down.position)) {
                            return@awaitEachGesture
                        }

                        val tool = currentTool
                        val currentAnns = currentAnnotations
                        val selectedId = currentSelectedId
                        val editingId = currentEditingId
                        val zoom = currentZoom
                        val bs = currentBaseScale
                        val slopInPage = PageCoordinatesConverter.dpToPageUnits(density, 48f, zoom, bs)
                        val handleRadiusPage = PageCoordinatesConverter.dpToPageUnits(density, 24f, zoom, bs)
                        val rotateOffsetPage = PageCoordinatesConverter.dpToPageUnits(density, 20f, zoom, bs)
                        val downPagePoint = PageCoordinatesConverter.layoutToPage(down.position, bs)
                        val touchSlopPx = viewConfiguration.touchSlop

                        fun toPage(p: Offset): Offset = PageCoordinatesConverter.layoutToPage(p, bs)

                        // While a text box is being edited, touches inside it belong to the text field
                        // (caret placement, selection handles). A touch anywhere else just finishes editing.
                        if (editingId != null) {
                            val editing = currentAnns.find { it.id == editingId && it.pageIndex == pageIndex }
                            val inside = editing?.hitTest(downPagePoint, slopInPage * 0.15f) == true
                            if (!inside) {
                                down.consume()
                                currentOnPageTap(downPagePoint)
                            }
                            return@awaitEachGesture
                        }

                        try {
                            val selectedAnn = currentAnns.find { it.id == selectedId && it.pageIndex == pageIndex }
                            val hitHandle = selectedAnn?.let { ann ->
                                AnnotationHitTester.hitTestHandles(
                                    point = downPagePoint,
                                    annotation = ann,
                                    handleRadius = handleRadiusPage,
                                    rotateHandleOffset = rotateOffsetPage
                                )
                            }

                            if (selectedAnn != null && hitHandle != null && hitHandle != HandleType.BODY) {
                                // ---- Resize / rotate via handles ----
                                down.consume()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                var prevPagePoint = downPagePoint

                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.size > 1) break
                                    val change = event.changes.firstOrNull { it.id == downPointerId } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }
                                    if (change.positionChange() != Offset.Zero) {
                                        change.consume()
                                        val currentPagePoint = toPage(change.position)
                                        val deltaInPage = currentPagePoint - prevPagePoint
                                        prevPagePoint = currentPagePoint

                                        if (hitHandle == HandleType.ROTATE) {
                                            val center = selectedAnn.boundsRect().center
                                            val angle = Math.toDegrees(
                                                atan2(
                                                    (currentPagePoint.y - center.y).toDouble(),
                                                    (currentPagePoint.x - center.x).toDouble()
                                                )
                                            ).toFloat() + 90f // handle sits above the box => 0deg when upright
                                            onRotateAnnotation(selectedAnn.id, angle)
                                        } else {
                                            onResizeAnnotation(selectedAnn.id, hitHandle, deltaInPage)
                                        }
                                    }
                                }
                            } else if (selectedAnn != null && (hitHandle == HandleType.BODY || selectedAnn.hitTest(downPagePoint, slopInPage))) {
                                // ---- Tap (edit) or drag (move) the selected annotation ----
                                down.consume()
                                var isDrag = false
                                var prevPagePoint = downPagePoint

                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.size > 1) break
                                    val change = event.changes.firstOrNull { it.id == downPointerId } ?: break
                                    if (!change.pressed) {
                                        change.consume()
                                        if (!isDrag) {
                                            onAnnotationTap(selectedAnn.id)
                                        }
                                        break
                                    }
                                    if (!isDrag) {
                                        // Use TOTAL distance from the down point (slow drags never exceed a per-event threshold).
                                        if ((change.position - down.position).getDistance() > touchSlopPx) {
                                            isDrag = true
                                            prevPagePoint = toPage(change.position)
                                            change.consume()
                                        }
                                    } else {
                                        change.consume()
                                        val currentPagePoint = toPage(change.position)
                                        val deltaInPage = currentPagePoint - prevPagePoint
                                        prevPagePoint = currentPagePoint
                                        onMoveAnnotation(selectedAnn.id, deltaInPage)
                                    }
                                }
                            } else {
                                when (tool) {
                                    EditorTool.SELECT -> {
                                        val hit = currentAnns
                                            .filter { it.pageIndex == pageIndex }
                                            .sortedByDescending { it.zIndex }
                                            .firstOrNull { it.hitTest(downPagePoint, slopInPage) }

                                        if (hit != null) {
                                            down.consume()
                                            onSelectAnnotation(hit.id)
                                        } else if (selectedId != null) {
                                            down.consume()
                                            onDeselect()
                                        }
                                    }
                                    EditorTool.PEN, EditorTool.HIGHLIGHTER -> {
                                        down.consume()
                                        var points = listOf(downPagePoint)
                                        val isHighlighter = tool == EditorTool.HIGHLIGHTER
                                        // Read once per stroke from the always-fresh state (this was the "color never changes" bug).
                                        val strokeColor = currentColor
                                        val strokeWidth = currentStrokeWidth

                                        fun draft() = InkAnnotation(
                                            page = pageIndex,
                                            points = points,
                                            color = strokeColor,
                                            strokeWidth = strokeWidth,
                                            isHighlighter = isHighlighter
                                        )

                                        onUpdateDraft(draft())

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.size > 1) {
                                                onUpdateDraft(null)
                                                break
                                            }
                                            val change = event.changes.firstOrNull { it.id == downPointerId } ?: break
                                            if (!change.pressed) {
                                                change.consume()
                                                onCommitDraft()
                                                break
                                            }
                                            change.consume()
                                            points = points + toPage(change.position)
                                            onUpdateDraft(draft())
                                        }
                                    }
                                    EditorTool.SHAPE -> {
                                        down.consume()
                                        val startPt = downPagePoint
                                        val shapeType = currentShapeType
                                        val shapeColor = currentColor
                                        val shapeStroke = currentStrokeWidth
                                        val minLen = PageCoordinatesConverter.dpToPageUnits(density, 12f, zoom, bs)

                                        val hitShape = currentAnns
                                            .filter { it.pageIndex == pageIndex }
                                            .sortedByDescending { it.zIndex }
                                            .firstOrNull { it.hitTest(downPagePoint, slopInPage) }

                                        if (hitShape != null) {
                                            onSelectAnnotation(hitShape.id)
                                        } else {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.changes.size > 1) {
                                                    onUpdateDraft(null)
                                                    break
                                                }
                                                val change = event.changes.firstOrNull { it.id == downPointerId } ?: break
                                                if (!change.pressed) {
                                                    change.consume()
                                                    val endPt = toPage(change.position)
                                                    if ((endPt - startPt).getDistance() >= minLen) {
                                                        onCommitDraftAndSelect()
                                                    } else {
                                                        onUpdateDraft(null)
                                                    }
                                                    break
                                                }
                                                change.consume()
                                                val endPt = toPage(change.position)
                                                onUpdateDraft(
                                                    ShapeAnnotation(
                                                        page = pageIndex,
                                                        shapeType = shapeType,
                                                        start = startPt,
                                                        end = endPt,
                                                        color = shapeColor,
                                                        strokeWidth = shapeStroke
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    EditorTool.TEXT -> {
                                        val hitText = currentAnns
                                            .filter { it.pageIndex == pageIndex }
                                            .sortedByDescending { it.zIndex }
                                            .firstOrNull { it.hitTest(downPagePoint, slopInPage * 0.5f) }

                                        down.consume()
                                        if (hitText != null) {
                                            onSelectAnnotation(hitText.id)
                                        } else {
                                            // Create the text box on a clean TAP (finger lifted without moving),
                                            // so starting a pan / pinch never spawns an accidental box.
                                            var moved = false
                                            var released = false
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.changes.size > 1) break
                                                val change = event.changes.firstOrNull { it.id == downPointerId } ?: break
                                                if (!change.pressed) {
                                                    change.consume()
                                                    released = true
                                                    break
                                                }
                                                if ((change.position - down.position).getDistance() > touchSlopPx) {
                                                    moved = true
                                                }
                                            }
                                            if (released && !moved) {
                                                onPageTap(downPagePoint)
                                            }
                                        }
                                    }
                                    EditorTool.STAMP -> {
                                        down.consume()
                                        // Tapping an existing stamp selects it (resize / rotate / move) instead of stacking a new one.
                                        val hitStamp = currentAnns
                                            .filter { it.pageIndex == pageIndex && it is StampAnnotation }
                                            .sortedByDescending { it.zIndex }
                                            .firstOrNull { it.hitTest(downPagePoint, slopInPage * 0.5f) }
                                        if (hitStamp != null) {
                                            onSelectAnnotation(hitStamp.id)
                                            return@awaitEachGesture
                                        }
                                        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                        onAnnotationAdded(
                                            StampAnnotation(
                                                page = pageIndex,
                                                stampType = currentStampType,
                                                position = downPagePoint,
                                                dateText = dateFormat.format(Date())
                                            )
                                        )
                                    }
                                    EditorTool.ERASER -> {
                                        down.consume()
                                        val radiusPage = PageCoordinatesConverter.dpToPageUnits(
                                            density, currentEraserRadiusDp, zoom, bs
                                        )
                                        onStartEraseGesture()
                                        onUpdateEraserPosition(downPagePoint)
                                        currentOnEraseAt(downPagePoint, radiusPage)

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.size > 1) break
                                            val change = event.changes.firstOrNull { it.id == downPointerId } ?: break
                                            if (!change.pressed) {
                                                change.consume()
                                                break
                                            }
                                            change.consume()
                                            val erasePt = toPage(change.position)
                                            onUpdateEraserPosition(erasePt)
                                            currentOnEraseAt(erasePt, radiusPage)
                                        }
                                    }
                                    EditorTool.REDACT -> {
                                        down.consume()
                                        val startPt = downPagePoint
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.size > 1) {
                                                onUpdateDraft(null)
                                                break
                                            }
                                            val change = event.changes.firstOrNull { it.id == downPointerId } ?: break
                                            if (!change.pressed) {
                                                change.consume()
                                                onCommitDraft()
                                                break
                                            }
                                            change.consume()
                                            val endPt = toPage(change.position)
                                            val left = minOf(startPt.x, endPt.x)
                                            val top = minOf(startPt.y, endPt.y)
                                            val right = maxOf(startPt.x, endPt.x)
                                            val bottom = maxOf(startPt.y, endPt.y)
                                            onUpdateDraft(
                                                RedactionAnnotation(
                                                    page = pageIndex,
                                                    bounds = Rect(left, top, right, bottom)
                                                )
                                            )
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        } finally {
                            if (tool == EditorTool.ERASER) {
                                onFinishEraseGesture()
                                onUpdateEraserPosition(null)
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                pageBitmap?.let { bmp ->
                    drawImage(
                        image = bmp.asImageBitmap(),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                    )
                }

                // Everything below is in page space.
                withTransform({ scale(baseScale, baseScale, Offset.Zero) }) {
                    annotations
                        .filter { it.pageIndex == pageIndex }
                        .sortedBy { it.zIndex }
                        .forEach { annotation ->
                            annotation.draw(this)
                        }

                    draftAnnotation?.let { draft ->
                        if (draft.pageIndex == pageIndex) {
                            draft.draw(this)
                        }
                    }

                    // Circular eraser cursor following the finger
                    if (activeTool == EditorTool.ERASER && eraserPosition != null) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.25f),
                            radius = eraserRadiusPage,
                            center = eraserPosition
                        )
                        drawCircle(
                            color = Color.White,
                            radius = eraserRadiusPage,
                            center = eraserPosition,
                            style = Stroke(width = eraserBorderPage * 2f)
                        )
                        drawCircle(
                            color = BrandIndigo,
                            radius = eraserRadiusPage,
                            center = eraserPosition,
                            style = Stroke(width = eraserBorderPage)
                        )
                    }
                }
            }

            // Text boxes are real Compose text (crisp + native editing), positioned from page space.
            annotations.filter { it.pageIndex == pageIndex && it is TextAnnotation }.forEach { ann ->
                val textAnn = ann as TextAnnotation
                key(textAnn.id) {
                    TextAnnotationView(
                        annotation = textAnn,
                        baseScale = baseScale,
                        pageWidth = pageWidth,
                        isEditing = editingAnnotationId == textAnn.id,
                        onTextChanged = { onTextChanged(textAnn.id, it) },
                        onMeasured = { w, h -> onTextSizeChanged(textAnn.id, w, h) }
                    )
                }
            }

            // Universal selection overlay
            val selectedAnn = annotations.find { it.id == selectedAnnotationId && it.pageIndex == pageIndex }
            if (selectedAnn != null && editingAnnotationId == null) {
                val bounds = selectedAnn.boundsRect()
                val frameLeftPx = bounds.left * baseScale
                val frameTopPx = bounds.top * baseScale
                val frameWidthPx = bounds.width * baseScale
                val frameHeightPx = bounds.height * baseScale
                val visualRotation =
                    if (selectedAnn is TextAnnotation || selectedAnn is SignatureAnnotation || selectedAnn is ShapeAnnotation || selectedAnn is StampAnnotation) selectedAnn.rotation else 0f

                // Frame + handles: visual only (all touch handling is done by the page gesture block above).
                Box(
                    modifier = Modifier
                        .offset { IntOffset(frameLeftPx.roundToInt(), frameTopPx.roundToInt()) }
                        .size(
                            with(density) { frameWidthPx.toDp() },
                            with(density) { frameHeightPx.toDp() }
                        )
                        .graphicsLayer { rotationZ = visualRotation }
                        .border(2.dp, BrandIndigo, RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-32).dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(BrandIndigo)
                            .semantics { contentDescription = "Rotate handle" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    HandleCircle(Modifier.align(Alignment.TopStart), "Top left resize handle")
                    HandleCircle(Modifier.align(Alignment.TopEnd), "Top right resize handle")
                    HandleCircle(Modifier.align(Alignment.BottomStart), "Bottom left resize handle")
                    HandleCircle(Modifier.align(Alignment.BottomEnd), "Bottom right resize handle")

                    HandlePill(Modifier.align(Alignment.CenterStart), isVertical = true, "Left resize handle")
                    HandlePill(Modifier.align(Alignment.CenterEnd), isVertical = true, "Right resize handle")
                    HandlePill(Modifier.align(Alignment.TopCenter), isVertical = false, "Top resize handle")
                    HandlePill(Modifier.align(Alignment.BottomCenter), isVertical = false, "Bottom resize handle")
                }

                // Floating action bar. It is a direct child of the page Box (NOT nested in the frame above):
                // Compose ignores touches on children that sit outside their parent's bounds, which is
                // exactly where this bar lives.
                var barSize by remember { mutableStateOf(IntSize.Zero) }
                val marginPx = with(density) { 6.dp.toPx() }
                val clearancePx = with(density) { 48.dp.toPx() } // rotate handle + breathing room
                val belowGapPx = with(density) { 12.dp.toPx() }
                var barY = frameTopPx - barSize.height - clearancePx
                if (barY < marginPx) barY = frameTopPx + frameHeightPx + belowGapPx
                barY = barY.coerceIn(marginPx, max(marginPx, canvasHeightPx - barSize.height - marginPx))
                val barX = (frameLeftPx + frameWidthPx / 2f - barSize.width / 2f)
                    .coerceIn(marginPx, max(marginPx, canvasWidthPx - barSize.width - marginPx))

                SideEffect {
                    actionBarBounds = if (barSize == IntSize.Zero) Rect.Zero
                    else Rect(barX, barY, barX + barSize.width, barY + barSize.height)
                }
                DisposableEffect(Unit) { onDispose { actionBarBounds = Rect.Zero } }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .offset { IntOffset(barX.roundToInt(), barY.roundToInt()) }
                        .onSizeChanged { barSize = it }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (selectedAnn is TextAnnotation) {
                            IconButton(
                                onClick = { onStartEdit(selectedAnn.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit annotation",
                                    tint = BrandIndigo,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else if (selectedAnn is ShapeAnnotation) {
                            IconButton(
                                onClick = onOpenShapeOptionsSheet,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ColorLens,
                                    contentDescription = "Shape options",
                                    tint = BrandIndigo,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { onBringToFront(selectedAnn.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlipToFront,
                                contentDescription = "Bring to front",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onSendToBack(selectedAnn.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlipToBack,
                                contentDescription = "Send to back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDuplicateAnnotation(selectedAnn.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Duplicate annotation",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDeleteAnnotation(selectedAnn.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete annotation",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One text annotation. The box hugs its content (grows while typing, wraps at the page edge),
 * its real size is reported back so selection frame / hit-testing / export all match.
 */
@Composable
private fun TextAnnotationView(
    annotation: TextAnnotation,
    baseScale: Float,
    pageWidth: Float,
    isEditing: Boolean,
    onTextChanged: (String) -> Unit,
    onMeasured: (Float, Float) -> Unit
) {
    val density = LocalDensity.current

    val fontSize = with(density) { (annotation.fontSize * baseScale).toSp() }
    val padX = with(density) { (TEXT_PADDING_X * baseScale).toDp() }
    val padY = with(density) { (TEXT_PADDING_Y * baseScale).toDp() }
    // Wrap before running off the right edge of the page.
    val maxWidth = with(density) { (max(60f, pageWidth - annotation.position.x - 8f) * baseScale).toDp() }
    // Comfortable tap / caret target while typing an empty or very short text.
    val minWidth = if (isEditing) with(density) { (annotation.fontSize * 2.5f * baseScale).toDp() } else 0.dp

    val composeFontFamily = when (annotation.fontFamily) {
        EditorFontFamily.SANS_SERIF -> FontFamily.SansSerif
        EditorFontFamily.SERIF -> FontFamily.Serif
        EditorFontFamily.MONOSPACE -> FontFamily.Monospace
        EditorFontFamily.CURSIVE -> FontFamily.Cursive
    }
    val composeTextAlign = when (annotation.alignment) {
        TextAlignment.LEFT -> TextAlign.Start
        TextAlignment.CENTER -> TextAlign.Center
        TextAlignment.RIGHT -> TextAlign.End
    }
    val textStyle = TextStyle(
        color = annotation.color,
        fontSize = fontSize,
        fontWeight = if (annotation.isBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (annotation.isItalic) FontStyle.Italic else FontStyle.Normal,
        textAlign = composeTextAlign,
        fontFamily = composeFontFamily
    )

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (annotation.position.x * baseScale).roundToInt(),
                    (annotation.position.y * baseScale).roundToInt()
                )
            }
            .graphicsLayer { rotationZ = annotation.rotation }
            .widthIn(min = minWidth, max = maxWidth)
            .width(IntrinsicSize.Max)
            .onSizeChanged { size ->
                if (baseScale > 0f) onMeasured(size.width / baseScale, size.height / baseScale)
            }
            .then(
                if (isEditing) {
                    Modifier
                        .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(4.dp))
                        .border(2.dp, BrandIndigo, RoundedCornerShape(4.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = padX, vertical = padY)
    ) {
        if (isEditing) {
            val focusRequester = remember { FocusRequester() }
            // Caret at the end when (re)entering edit mode.
            var fieldValue by remember {
                mutableStateOf(TextFieldValue(annotation.text, TextRange(annotation.text.length)))
            }

            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { focusRequester.requestFocus() } // focus => soft keyboard opens
            }

            BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    fieldValue = newValue
                    if (newValue.text != annotation.text) onTextChanged(newValue.text)
                },
                textStyle = textStyle,
                cursorBrush = SolidColor(BrandIndigo),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = "Editing text: ${annotation.text}" },
                decorationBox = { innerTextField ->
                    Box {
                        if (annotation.text.isEmpty()) {
                            Text(
                                text = "Type here",
                                style = textStyle.copy(color = Color.Gray.copy(alpha = 0.7f)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        innerTextField()
                    }
                }
            )
        } else {
            Text(
                text = annotation.text.ifEmpty { " " },
                style = textStyle,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HandleCircle(modifier: Modifier, contentDesc: String) {
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(Color.White)
            .border(2.5.dp, BrandIndigo, CircleShape)
            .semantics { contentDescription = contentDesc }
    )
}

@Composable
private fun HandlePill(modifier: Modifier, isVertical: Boolean, contentDesc: String) {
    Box(
        modifier = modifier
            .size(if (isVertical) 6.dp else 14.dp, if (isVertical) 14.dp else 6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White)
            .border(2.dp, BrandIndigo, RoundedCornerShape(3.dp))
            .semantics { contentDescription = contentDesc }
    )
}
