package com.example.feature_editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.data.local.dao.SignatureDao
import com.example.core.data.repository.PdfDocumentRepository
import com.example.core.pdf.PdfPasswordStore
import com.example.core.pdf.PdfRenderSession
import com.example.core.pdf.isPdfPasswordError
import com.example.feature_editor.domain.AnnotationHitTester
import com.example.feature_editor.domain.AnnotationJsonSerializer
import com.example.feature_editor.domain.EditorSnapshot
import com.example.feature_editor.domain.HandleType
import com.example.feature_editor.domain.UndoRedoManager
import com.example.feature_editor.model.Annotation
import com.example.feature_editor.model.DEFAULT_TEXT_SIZE
import com.example.feature_editor.model.EditorFontFamily
import com.example.feature_editor.model.EditorTool
import com.example.feature_editor.model.EraserMode
import com.example.feature_editor.model.InkAnnotation
import com.example.feature_editor.model.PageAnnotation
import com.example.feature_editor.model.RedactionAnnotation
import com.example.feature_editor.model.ShapeAnnotation
import com.example.feature_editor.model.ShapeType
import com.example.feature_editor.model.SignatureAnnotation
import com.example.feature_editor.model.StampAnnotation
import com.example.feature_editor.model.StampType
import com.example.feature_editor.model.TextAlignment
import com.example.feature_editor.model.SIGNATURE_STROKE_WIDTH
import com.example.feature_editor.model.TEXT_PADDING_X
import com.example.feature_editor.model.TEXT_PADDING_Y
import com.example.feature_editor.model.TextAnnotation
import com.example.ui.theme.BrandIndigo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

object Timber {
    fun d(message: String, vararg args: Any?) {
        val formatted = try {
            if (args.isNotEmpty()) String.format(message, *args) else message
        } catch (_: Exception) {
            message
        }
        Log.d("myPrev_Editor", formatted)
    }
}

data class EditorUiState(
    val uriString: String = "",
    val fileName: String = "Document.pdf",
    val pageCount: Int = 0,
    val currentPage: Int = 1,
    val zoomLevel: Float = 1f,
    val isLoading: Boolean = true,
    val isPageRendering: Boolean = false,
    val errorMessage: String? = null,
    val isPermissionError: Boolean = false,
    val currentPageBitmap: Bitmap? = null,
    val activeTool: EditorTool = EditorTool.SELECT,
    val selectedColor: Color = BrandIndigo,
    val selectedStrokeWidth: Float = 6f,
    val selectedShapeType: ShapeType = ShapeType.RECTANGLE,
    val selectedStampType: StampType = StampType.APPROVED,
    val eraserMode: EraserMode = EraserMode.STROKE,
    val eraserRadiusDp: Float = 24f,
    val eraserPosition: Offset? = null,
    val annotations: List<Annotation> = emptyList(),
    val draftAnnotation: Annotation? = null,
    val selectedAnnotationId: String? = null,
    val editingAnnotationId: String? = null,
    val showShapeOptionsSheet: Boolean = false,
    val showTextHint: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isSaving: Boolean = false,
    val saveMessage: String? = null
)

class EditorViewModel(
    private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    private val pdfRepository: PdfDocumentRepository,
    val signatureDao: SignatureDao
) : ViewModel() {

    var uriString: String = savedStateHandle.get<String>("uri") ?: ""
        private set
    val initialPage: Int = savedStateHandle.get<Int>("page") ?: 1
    val initialZoom: Float = savedStateHandle.get<Float>("zoom") ?: 1f

    private val _uiState = MutableStateFlow(
        EditorUiState(
            uriString = uriString,
            currentPage = initialPage.coerceAtLeast(1),
            zoomLevel = initialZoom.coerceAtLeast(1f)
        )
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val undoRedoManager = UndoRedoManager()
    private var renderSession: PdfRenderSession? = null
    private var hasShownTextHint = false
    private var autosaveJob: Job? = null

    /** Undo-stack size right before the text box that is currently being created (null if not a fresh box). */
    private var newTextUndoMark: Int? = null
    private var newTextId: String? = null

    /** Last used text style, so the next text box starts with the same look. */
    private data class TextStyleDefaults(
        val fontSize: Float = DEFAULT_TEXT_SIZE,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val alignment: TextAlignment = TextAlignment.LEFT,
        val fontFamily: EditorFontFamily = EditorFontFamily.SANS_SERIF
    )
    private var textDefaults = TextStyleDefaults()

    init {
        Timber.d("URI received in editor: %s (page=%d, zoom=%.2f)", uriString, initialPage, initialZoom)
        loadDocument()
    }

    fun retry() {
        loadDocument()
    }

    private fun loadDocument() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, isPermissionError = false)
            if (uriString.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "No document URI provided."
                )
                return@launch
            }

            try {
                val uri = Uri.parse(uriString)

                val openResult = pdfRepository.openPdf(uri)
                if (openResult.isFailure) {
                    val ex = openResult.exceptionOrNull()
                    val isPerm = ex is SecurityException || (ex?.localizedMessage?.lowercase()?.contains("permission") == true)
                    val errorMsg = if (isPerm) {
                        "Permission to access this document was denied or has expired. Tap below to re-open the document."
                    } else {
                        ex?.localizedMessage ?: "Failed to open document."
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isPermissionError = isPerm,
                        errorMessage = errorMsg
                    )
                    return@launch
                }

                val docEntity = openResult.getOrNull()
                val session = PdfRenderSession(context, uri)
                val result = session.open(PdfPasswordStore.get(uriString))

                if (result.isSuccess) {
                    renderSession = session
                    val total = result.getOrThrow()
                    val targetPage = initialPage.coerceIn(1, total)

                    // Restore draft annotations if autosave file exists
                    var restoredAnnotations = emptyList<Annotation>()
                    try {
                        val draftDir = File(context.filesDir, "annotation_drafts")
                        val draftFile = File(draftDir, "draft_${uriString.hashCode()}.json")
                        if (draftFile.exists()) {
                            val jsonStr = withContext(Dispatchers.IO) { draftFile.readText() }
                            restoredAnnotations = AnnotationJsonSerializer.fromJson(jsonStr)
                        }
                    } catch (_: Exception) {}

                    _uiState.value = _uiState.value.copy(
                        fileName = docEntity?.name ?: uri.lastPathSegment ?: "Document.pdf",
                        pageCount = total,
                        currentPage = targetPage,
                        annotations = restoredAnnotations,
                        isLoading = false,
                        errorMessage = null,
                        isPermissionError = false
                    )

                    loadCurrentPageBitmap(targetPage, isFirstRender = true)
                } else {
                    val ex = result.exceptionOrNull()
                    val needsPassword = ex?.isPdfPasswordError() == true
                    val isPerm = !needsPassword && (ex is SecurityException || (ex?.localizedMessage?.lowercase()?.contains("permission") == true))
                    val errorMsg = if (needsPassword) {
                        "This PDF is password protected. Open it in the viewer and enter the password first, then try editing again."
                    } else if (isPerm) {
                        "Permission to access this document was denied or has expired. Tap below to re-open the document."
                    } else {
                        ex?.localizedMessage ?: "Failed to open document."
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isPermissionError = isPerm,
                        errorMessage = errorMsg
                    )
                }
            } catch (se: SecurityException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isPermissionError = true,
                    errorMessage = "Permission to access this document was denied or has expired. Tap below to re-open the document."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Unexpected error reading PDF document."
                )
            }
        }
    }

    fun reopenDocumentWithUri(newUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, isPermissionError = false)
            val result = pdfRepository.openPdf(newUri, forceRefresh = true)
            if (result.isSuccess) {
                val doc = result.getOrThrow()
                renderSession?.close()
                renderSession = null
                uriString = doc.uri
                _uiState.value = _uiState.value.copy(
                    uriString = doc.uri,
                    fileName = doc.name
                )
                loadDocument()
            } else {
                val ex = result.exceptionOrNull()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isPermissionError = ex is SecurityException,
                    errorMessage = "Failed to re-open document: ${ex?.localizedMessage}"
                )
            }
        }
    }

    private fun loadCurrentPageBitmap(pageNumber: Int, isFirstRender: Boolean = false) {
        val pageIndex = pageNumber - 1
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPageRendering = true)
            val session = renderSession
            if (session != null && pageIndex in 0 until _uiState.value.pageCount) {
                val bitmap = session.renderPage(pageIndex, 1080)
                _uiState.value = _uiState.value.copy(
                    currentPageBitmap = bitmap,
                    isPageRendering = false
                )
            } else {
                _uiState.value = _uiState.value.copy(isPageRendering = false)
            }
        }
    }

    suspend fun getPageBitmap(pageIndex: Int): Bitmap? {
        return renderSession?.renderPage(pageIndex, 1080)
    }

    private fun triggerAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1000)
            try {
                val draftDir = File(context.filesDir, "annotation_drafts").apply { mkdirs() }
                val draftFile = File(draftDir, "draft_${uriString.hashCode()}.json")
                val jsonStr = AnnotationJsonSerializer.toJson(_uiState.value.annotations)
                draftFile.writeText(jsonStr)
            } catch (_: Exception) {}
        }
    }

    fun setTool(tool: EditorTool) {
        if (_uiState.value.editingAnnotationId != null) {
            onCommitEdit()
        }
        val showHint = tool == EditorTool.TEXT && !hasShownTextHint
        if (showHint) {
            hasShownTextHint = true
        }
        _uiState.value = _uiState.value.copy(
            activeTool = tool,
            selectedAnnotationId = if (tool == EditorTool.SELECT || tool == EditorTool.TEXT) _uiState.value.selectedAnnotationId else null,
            editingAnnotationId = null,
            showShapeOptionsSheet = false,
            showTextHint = showHint,
            eraserPosition = null
        )
    }

    fun onDismissTextHint() {
        _uiState.value = _uiState.value.copy(showTextHint = false)
    }

    fun toggleShapeOptionsSheet(show: Boolean) {
        _uiState.value = _uiState.value.copy(showShapeOptionsSheet = show)
    }

    fun setEraserMode(mode: EraserMode) {
        _uiState.value = _uiState.value.copy(eraserMode = mode)
    }

    fun setEraserRadius(radiusDp: Float) {
        _uiState.value = _uiState.value.copy(eraserRadiusDp = radiusDp)
    }

    fun updateEraserPosition(pos: Offset?) {
        _uiState.value = _uiState.value.copy(eraserPosition = pos)
    }

    fun startEraseGesture() {
        undoRedoManager.pushState(_uiState.value.annotations, _uiState.value.selectedAnnotationId)
    }

    /** [radiusInPage] is the eraser radius already converted to page units by the canvas (dp-accurate at any zoom). */
    fun eraseAt(pointInPage: Offset, radiusInPage: Float) {
        val currentState = _uiState.value
        val pageIdx = currentState.currentPage - 1
        val currentList = currentState.annotations

        if (currentState.eraserMode == EraserMode.OBJECT) {
            val remaining = currentList.filterNot { ann ->
                ann.pageIndex == pageIdx &&
                        ann.boundsRect().inflate(radiusInPage).contains(pointInPage) &&
                        ann.hitTest(pointInPage, radiusInPage)
            }
            if (remaining.size != currentList.size) {
                _uiState.value = currentState.copy(annotations = remaining)
                triggerAutosave()
            }
        } else {
            val updatedList = mutableListOf<Annotation>()
            var modified = false

            for (ann in currentList) {
                if (ann.pageIndex != pageIdx) {
                    updatedList.add(ann)
                    continue
                }

                if (ann is InkAnnotation) {
                    if (ann.boundsRect().inflate(radiusInPage).contains(pointInPage) && ann.hitTest(pointInPage, radiusInPage)) {
                        modified = true
                        val splitSegments = splitInkAnnotation(ann, pointInPage, radiusInPage)
                        updatedList.addAll(splitSegments)
                    } else {
                        updatedList.add(ann)
                    }
                } else if (ann.boundsRect().inflate(radiusInPage).contains(pointInPage) && ann.hitTest(pointInPage, radiusInPage)) {
                    modified = true
                } else {
                    updatedList.add(ann)
                }
            }

            if (modified) {
                _uiState.value = currentState.copy(annotations = updatedList)
                triggerAutosave()
            }
        }
    }

    private fun splitInkAnnotation(ink: InkAnnotation, erasePt: Offset, radius: Float): List<InkAnnotation> {
        val effectiveRadius = radius + ink.strokeWidth / 2f
        val resultSegments = mutableListOf<InkAnnotation>()
        var currentSegment = mutableListOf<Offset>()

        for (p in ink.points) {
            val dist = (p - erasePt).getDistance()
            if (dist > effectiveRadius) {
                currentSegment.add(p)
            } else {
                if (currentSegment.size >= 2) {
                    resultSegments.add(ink.copy(annotationId = UUID.randomUUID().toString(), points = currentSegment.toList()))
                }
                currentSegment = mutableListOf()
            }
        }
        if (currentSegment.size >= 2) {
            resultSegments.add(ink.copy(annotationId = UUID.randomUUID().toString(), points = currentSegment.toList()))
        }
        return resultSegments
    }

    fun finishEraseGesture() {
        _uiState.value = _uiState.value.copy(
            eraserPosition = null,
            canUndo = undoRedoManager.canUndo(),
            canRedo = undoRedoManager.canRedo()
        )
    }

    fun setColor(color: Color) {
        _uiState.value = _uiState.value.copy(selectedColor = color)
        val selectedId = _uiState.value.selectedAnnotationId
        if (selectedId != null) {
            onStyleChanged(selectedId) { ann ->
                when (ann) {
                    is TextAnnotation -> ann.copy(color = color)
                    is ShapeAnnotation -> ann.copy(color = color)
                    is InkAnnotation -> ann.copy(color = color)
                    else -> ann
                }
            }
        }
    }

    fun setStrokeWidth(width: Float) {
        _uiState.value = _uiState.value.copy(selectedStrokeWidth = width)
        val selectedId = _uiState.value.selectedAnnotationId
        if (selectedId != null) {
            onStyleChanged(selectedId) { ann ->
                when (ann) {
                    is ShapeAnnotation -> ann.copy(strokeWidth = width)
                    is InkAnnotation -> ann.copy(strokeWidth = width)
                    else -> ann
                }
            }
        }
    }

    fun setShapeType(shape: ShapeType) {
        _uiState.value = _uiState.value.copy(selectedShapeType = shape)
    }

    fun setStampType(stamp: StampType) {
        _uiState.value = _uiState.value.copy(selectedStampType = stamp)
    }

    fun setPage(page: Int) {
        if (page in 1.._uiState.value.pageCount && page != _uiState.value.currentPage) {
            if (_uiState.value.editingAnnotationId != null) {
                onCommitEdit()
            }
            _uiState.value = _uiState.value.copy(
                currentPage = page,
                selectedAnnotationId = null,
                editingAnnotationId = null,
                draftAnnotation = null,
                showShapeOptionsSheet = false,
                eraserPosition = null
            )
            loadCurrentPageBitmap(page, isFirstRender = false)
        }
    }

    // =========================================================================
    // DRAFT ANNOTATION HANDLERS (Transient state management)
    // =========================================================================

    fun updateDraftAnnotation(draft: Annotation?) {
        _uiState.value = _uiState.value.copy(draftAnnotation = draft)
    }

    fun clearDraftAnnotation() {
        _uiState.value = _uiState.value.copy(draftAnnotation = null)
    }

    fun commitDraftAnnotation() {
        val draft = _uiState.value.draftAnnotation ?: return
        addAnnotation(draft)
        _uiState.value = _uiState.value.copy(draftAnnotation = null)
    }

    fun commitDraftAnnotationAndSelect() {
        val draft = _uiState.value.draftAnnotation ?: return
        val currentList = _uiState.value.annotations
        undoRedoManager.pushState(currentList, _uiState.value.selectedAnnotationId)
        val updated = currentList + draft
        _uiState.value = _uiState.value.copy(
            annotations = updated,
            draftAnnotation = null,
            selectedAnnotationId = draft.id,
            editingAnnotationId = null,
            canUndo = undoRedoManager.canUndo(),
            canRedo = undoRedoManager.canRedo()
        )
        triggerAutosave()
    }

    // =========================================================================
    // UNIFIED ANNOTATION SELECTION & EDITING
    // =========================================================================

    fun selectAnnotation(annotationId: String?) {
        _uiState.value = _uiState.value.copy(
            selectedAnnotationId = annotationId,
            editingAnnotationId = null,
            showShapeOptionsSheet = false
        )
    }

    fun deselect() {
        if (_uiState.value.editingAnnotationId != null) {
            onCommitEdit()
        }
        _uiState.value = _uiState.value.copy(
            selectedAnnotationId = null,
            editingAnnotationId = null,
            showShapeOptionsSheet = false
        )
    }

    fun onPageTap(point: Offset) {
        val currentState = _uiState.value
        val pageIdx = currentState.currentPage - 1

        if (currentState.editingAnnotationId != null) {
            onCommitEdit()
            _uiState.value = _uiState.value.copy(selectedAnnotationId = null)
            return
        }

        val hitAnnotation = AnnotationHitTester.hitTestAnnotations(
            point = point,
            annotations = currentState.annotations,
            pageIndex = pageIdx,
            touchSlop = 24f
        )

        if (hitAnnotation != null) {
            if (hitAnnotation is PageAnnotation.Text) {
                if (currentState.selectedAnnotationId == hitAnnotation.id) {
                    onStartEdit(hitAnnotation.id)
                } else {
                    _uiState.value = _uiState.value.copy(
                        selectedAnnotationId = hitAnnotation.id,
                        editingAnnotationId = null
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    selectedAnnotationId = hitAnnotation.id,
                    editingAnnotationId = null
                )
            }
        } else {
            if (currentState.selectedAnnotationId != null) {
                _uiState.value = _uiState.value.copy(
                    selectedAnnotationId = null,
                    editingAnnotationId = null,
                    showShapeOptionsSheet = false
                )
            } else if (currentState.activeTool == EditorTool.TEXT) {
                val newId = UUID.randomUUID().toString()
                val pageW = currentState.currentPageBitmap?.width?.toFloat() ?: 1080f
                val d = textDefaults

                // Keep at least ~30% of the page width free to the right of the tap so the box can grow.
                val x = point.x.coerceIn(8f, (pageW * 0.7f).coerceAtLeast(8f))
                // Centre the first line vertically on the tap point.
                val y = (point.y - (d.fontSize * 0.7f + TEXT_PADDING_Y)).coerceAtLeast(8f)

                val newAnnotation = TextAnnotation(
                    annotationId = newId,
                    page = pageIdx,
                    text = "",
                    position = Offset(x, y),
                    color = currentState.selectedColor,
                    fontSize = d.fontSize,
                    isBold = d.isBold,
                    isItalic = d.isItalic,
                    alignment = d.alignment,
                    fontFamily = d.fontFamily,
                    // Provisional size; the canvas reports the real measured size right after first layout.
                    width = d.fontSize * 4f + TEXT_PADDING_X * 2f,
                    height = d.fontSize * 1.3f + TEXT_PADDING_Y * 2f
                )

                newTextUndoMark = undoRedoManager.undoSize()
                newTextId = newId
                undoRedoManager.pushState(currentState.annotations, null)

                val updatedAnnotations = currentState.annotations + newAnnotation
                _uiState.value = currentState.copy(
                    annotations = updatedAnnotations,
                    selectedAnnotationId = newId,
                    editingAnnotationId = newId,
                    canUndo = undoRedoManager.canUndo(),
                    canRedo = undoRedoManager.canRedo()
                )
                triggerAutosave()
            }
        }
    }

    fun onPageDoubleTap(point: Offset) {
        val currentState = _uiState.value
        val pageIdx = currentState.currentPage - 1
        val hit = AnnotationHitTester.hitTestAnnotations(
            point = point,
            annotations = currentState.annotations,
            pageIndex = pageIdx,
            touchSlop = 24f
        )
        if (hit is TextAnnotation) {
            onStartEdit(hit.id)
        }
    }

    fun onAnnotationTap(annotationId: String) {
        val currentState = _uiState.value
        val target = currentState.annotations.find { it.id == annotationId }
        if (target is TextAnnotation) {
            if (currentState.selectedAnnotationId == annotationId) {
                onStartEdit(annotationId)
            } else {
                _uiState.value = currentState.copy(
                    selectedAnnotationId = annotationId,
                    editingAnnotationId = null
                )
            }
        } else {
            _uiState.value = currentState.copy(
                selectedAnnotationId = annotationId,
                editingAnnotationId = null
            )
        }
    }

    fun onStartEdit(annotationId: String) {
        val currentState = _uiState.value
        if (currentState.editingAnnotationId == annotationId) return
        newTextId = null
        newTextUndoMark = null
        undoRedoManager.pushState(currentState.annotations, currentState.selectedAnnotationId)
        _uiState.value = currentState.copy(
            selectedAnnotationId = annotationId,
            editingAnnotationId = annotationId,
            canUndo = undoRedoManager.canUndo(),
            canRedo = undoRedoManager.canRedo()
        )
    }

    fun onTextChanged(annotationId: String, newText: String) {
        val updated = _uiState.value.annotations.map { ann ->
            if (ann.id == annotationId && ann is TextAnnotation) {
                ann.copy(text = newText)
            } else {
                ann
            }
        }
        _uiState.value = _uiState.value.copy(annotations = updated)
        triggerAutosave()
    }

    fun onCommitEdit() {
        val currentEditId = _uiState.value.editingAnnotationId ?: return
        val currentList = _uiState.value.annotations
        val target = currentList.find { it.id == currentEditId }

        if (target is TextAnnotation && target.text.trim().isEmpty()) {
            // Empty text box = cancelled. If it was freshly created, also forget its undo history.
            if (newTextId == currentEditId) {
                newTextUndoMark?.let { undoRedoManager.truncateUndoTo(it) }
            }
            val cleaned = currentList.filter { it.id != currentEditId }
            _uiState.value = _uiState.value.copy(
                annotations = cleaned,
                selectedAnnotationId = null,
                editingAnnotationId = null,
                canUndo = undoRedoManager.canUndo(),
                canRedo = undoRedoManager.canRedo()
            )
        } else {
            _uiState.value = _uiState.value.copy(
                editingAnnotationId = null,
                canUndo = undoRedoManager.canUndo(),
                canRedo = undoRedoManager.canRedo()
            )
        }
        newTextId = null
        newTextUndoMark = null
        triggerAutosave()
    }

    /** "Done" in the text format bar: finish editing and release the selection so the toolbar returns. */
    fun onTextDone() {
        if (_uiState.value.editingAnnotationId != null) {
            onCommitEdit()
        }
        _uiState.value = _uiState.value.copy(
            selectedAnnotationId = null,
            editingAnnotationId = null
        )
    }

    /** Style change from the text format bar; also remembered as the default for the next text box. */
    fun onTextStyleChanged(annotationId: String, updater: (TextAnnotation) -> TextAnnotation) {
        var result: TextAnnotation? = null
        onStyleChanged(annotationId) { ann ->
            if (ann is TextAnnotation) updater(ann).also { result = it } else ann
        }
        result?.let { t ->
            textDefaults = TextStyleDefaults(t.fontSize, t.isBold, t.isItalic, t.alignment, t.fontFamily)
            _uiState.value = _uiState.value.copy(selectedColor = t.color)
        }
    }

    /**
     * Called by the canvas whenever a text box has been laid out, so bounds / selection frame / hit-testing
     * always match what the user actually sees. Not an undoable action.
     */
    fun onTextSizeMeasured(annotationId: String, width: Float, height: Float) {
        val list = _uiState.value.annotations
        val target = list.find { it.id == annotationId } as? TextAnnotation ?: return
        if (abs(target.width - width) < 0.5f && abs(target.height - height) < 0.5f) return
        val updated = list.map { ann ->
            if (ann.id == annotationId && ann is TextAnnotation) ann.copy(width = width, height = height) else ann
        }
        _uiState.value = _uiState.value.copy(annotations = updated)
        triggerAutosave()
    }

    /**
     * Places a signature in the middle of the current page at a sensible size, selects it and switches
     * to SELECT so it can be dragged / resized / rotated immediately.
     */
    fun addSignature(rawStrokes: List<List<Offset>>, filled: Boolean = false) {
        val state = _uiState.value
        val points = rawStrokes.flatten()
        if (points.isEmpty()) return

        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }
        val rawW = maxX - minX
        val rawH = maxY - minY

        // Normalise stroke space: origin at (0,0), ~300 units wide. Dialog coordinates are device pixels.
        val k = when {
            rawW > 1f -> 300f / rawW
            rawH > 1f -> 100f / rawH
            else -> 1f
        }
        val strokes = rawStrokes.map { stroke -> stroke.map { Offset((it.x - minX) * k, (it.y - minY) * k) } }
        val normW = (rawW * k).coerceAtLeast(1f)
        val normH = (rawH * k).coerceAtLeast(1f)

        val pageW = state.currentPageBitmap?.width?.toFloat() ?: 1080f
        val pageH = state.currentPageBitmap?.height?.toFloat() ?: 1527f
        val scale = minOf(pageW * 0.35f / normW, pageH * 0.18f / normH).coerceIn(0.2f, 5f)

        val signature = SignatureAnnotation(
            page = state.currentPage - 1,
            position = Offset(pageW / 2f - normW * scale / 2f, pageH / 2f - normH * scale / 2f),
            strokes = strokes,
            strokeColor = Color(0xFF1E1B4B),
            scale = scale,
            filled = filled
        )

        undoRedoManager.pushState(state.annotations, state.selectedAnnotationId)
        _uiState.value = state.copy(
            annotations = state.annotations + signature,
            activeTool = EditorTool.SELECT,
            selectedAnnotationId = signature.id,
            editingAnnotationId = null,
            canUndo = undoRedoManager.canUndo(),
            canRedo = undoRedoManager.canRedo()
        )
        triggerAutosave()
    }

    fun deleteSelectedAnnotation(onDeleted: () -> Unit = {}) {
        val selectedId = _uiState.value.selectedAnnotationId ?: return
        onDeleteAnnotation(selectedId)
        onDeleted()
    }

    fun onDeleteAnnotation(annotationId: String) {
        val currentList = _uiState.value.annotations
        undoRedoManager.pushState(currentList, _uiState.value.selectedAnnotationId)
        val updated = currentList.filter { it.id != annotationId }
        _uiState.value = _uiState.value.copy(
            annotations = updated,
            selectedAnnotationId = if (_uiState.value.selectedAnnotationId == annotationId) null else _uiState.value.selectedAnnotationId,
            editingAnnotationId = if (_uiState.value.editingAnnotationId == annotationId) null else _uiState.value.editingAnnotationId,
            showShapeOptionsSheet = false,
            canUndo = undoRedoManager.canUndo(),
            canRedo = undoRedoManager.canRedo()
        )
        triggerAutosave()
    }

    fun onDuplicateAnnotation(annotationId: String) {
        val currentList = _uiState.value.annotations
        val target = currentList.find { it.id == annotationId } ?: return
        undoRedoManager.pushState(currentList, _uiState.value.selectedAnnotationId)

        val duplicated = target.moved(24f, 24f)
        val updated = currentList + duplicated
        _uiState.value = _uiState.value.copy(
            annotations = updated,
            selectedAnnotationId = duplicated.id,
            editingAnnotationId = null,
            canUndo = undoRedoManager.canUndo(),
            canRedo = undoRedoManager.canRedo()
        )
        triggerAutosave()
    }

    fun onMoveAnnotation(annotationId: String, delta: Offset) {
        val updated = _uiState.value.annotations.map { ann ->
            if (ann.id == annotationId) {
                ann.moved(delta.x, delta.y)
            } else {
                ann
            }
        }
        _uiState.value = _uiState.value.copy(annotations = updated)
        triggerAutosave()
    }

    fun onResizeAnnotation(annotationId: String, handle: HandleType, delta: Offset) {
        val updated = _uiState.value.annotations.map { ann ->
            if (ann.id == annotationId) resizeRespectingRotation(ann, handle, delta) else ann
        }
        _uiState.value = _uiState.value.copy(annotations = updated)
        triggerAutosave()
    }

    /**
     * The finger delta arrives in page axes, but a rotated annotation is resized along ITS OWN axes,
     * so the delta is rotated back first. Because the annotation rotates around its bounds centre,
     * changing the size moves the pivot; the result is translated so the opposite (anchor) edge
     * stays exactly where it was on screen.
     */
    private fun resizeRespectingRotation(ann: Annotation, handle: HandleType, pageDelta: Offset): Annotation {
        val rad = Math.toRadians(ann.rotation.toDouble())
        val cosA = kotlin.math.cos(rad).toFloat()
        val sinA = kotlin.math.sin(rad).toFloat()
        val delta = Offset(
            pageDelta.x * cosA + pageDelta.y * sinA,
            -pageDelta.x * sinA + pageDelta.y * cosA
        )

        val before = ann.boundsRect()
        val resized: Annotation = if (ann is ShapeAnnotation &&
            (ann.shapeType == ShapeType.LINE || ann.shapeType == ShapeType.ARROW)
        ) {
            when (handle) {
                HandleType.TOP_CENTER, HandleType.BOTTOM_CENTER ->
                    ann.copy(start = ann.start + delta, end = ann.end + delta)
                else -> {
                    // Move the endpoint that is closest to the grabbed handle (not blindly start/end).
                    val handlePos = when (handle) {
                        HandleType.TOP_LEFT -> Offset(before.left, before.top)
                        HandleType.TOP_RIGHT -> Offset(before.right, before.top)
                        HandleType.BOTTOM_LEFT -> Offset(before.left, before.bottom)
                        HandleType.BOTTOM_RIGHT -> Offset(before.right, before.bottom)
                        HandleType.MIDDLE_LEFT -> Offset(before.left, before.center.y)
                        HandleType.MIDDLE_RIGHT -> Offset(before.right, before.center.y)
                        else -> before.center
                    }
                    if ((ann.start - handlePos).getDistance() <= (ann.end - handlePos).getDistance()) {
                        ann.copy(start = ann.start + delta)
                    } else {
                        ann.copy(end = ann.end + delta)
                    }
                }
            }
        } else {
            val newBounds = when (handle) {
                HandleType.BOTTOM_RIGHT -> Rect(before.left, before.top, before.right + delta.x, before.bottom + delta.y)
                HandleType.BOTTOM_LEFT -> Rect(before.left + delta.x, before.top, before.right, before.bottom + delta.y)
                HandleType.TOP_RIGHT -> Rect(before.left, before.top + delta.y, before.right + delta.x, before.bottom)
                HandleType.TOP_LEFT -> Rect(before.left + delta.x, before.top + delta.y, before.right, before.bottom)
                HandleType.MIDDLE_RIGHT -> Rect(before.left, before.top, before.right + delta.x, before.bottom)
                HandleType.MIDDLE_LEFT -> Rect(before.left + delta.x, before.top, before.right, before.bottom)
                HandleType.BOTTOM_CENTER -> Rect(before.left, before.top, before.right, before.bottom + delta.y)
                HandleType.TOP_CENTER -> Rect(before.left, before.top + delta.y, before.right, before.bottom)
                else -> before
            }
            ann.resized(handle, newBounds)
        }

        if (ann.rotation == 0f) return resized

        // Keep the anchor fixed: translate by (R - I) * (newCenter - oldCenter).
        val d = resized.boundsRect().center - before.center
        val rx = d.x * cosA.toFloat() - d.y * sinA.toFloat()
        val ry = d.x * sinA.toFloat() + d.y * cosA.toFloat()
        return resized.moved(rx - d.x, ry - d.y)
    }

    fun onRotateAnnotation(annotationId: String, rawAngle: Float) {
        var normalized = (rawAngle % 360f + 360f) % 360f
        val snapTargets = floatArrayOf(0f, 90f, 180f, 270f, 360f)
        for (target in snapTargets) {
            if (abs(normalized - target) < 7.5f) {
                normalized = if (target == 360f) 0f else target
                break
            }
        }

        val updated = _uiState.value.annotations.map { ann ->
            if (ann.id == annotationId) {
                ann.rotated(normalized)
            } else {
                ann
            }
        }
        _uiState.value = _uiState.value.copy(annotations = updated)
        triggerAutosave()
    }

    fun bringToFront(annotationId: String) {
        val currentList = _uiState.value.annotations
        undoRedoManager.pushState(currentList, _uiState.value.selectedAnnotationId)
        val maxZ = currentList.maxOfOrNull { it.zIndex } ?: 0
        val updated = currentList.map { ann ->
            if (ann.id == annotationId) {
                when (ann) {
                    is TextAnnotation -> ann.copy(zIndex = maxZ + 1)
                    is ShapeAnnotation -> ann.copy(zIndex = maxZ + 1)
                    is InkAnnotation -> ann.copy(zIndex = maxZ + 1)
                    is StampAnnotation -> ann.copy(zIndex = maxZ + 1)
                    is SignatureAnnotation -> ann.copy(zIndex = maxZ + 1)
                    is RedactionAnnotation -> ann.copy(zIndex = maxZ + 1)
                    else -> ann
                }
            } else {
                ann
            }
        }
        _uiState.value = _uiState.value.copy(
            annotations = updated,
            canUndo = undoRedoManager.canUndo(),
            canRedo = undoRedoManager.canRedo()
        )
        triggerAutosave()
    }

    fun sendToBack(annotationId: String) {
        val currentList = _uiState.value.annotations
        undoRedoManager.pushState(currentList, _uiState.value.selectedAnnotationId)
        val minZ = currentList.minOfOrNull { it.zIndex } ?: 0
        val updated = currentList.map { ann ->
            if (ann.id == annotationId) {
                when (ann) {
                    is TextAnnotation -> ann.copy(zIndex = minZ - 1)
                    is ShapeAnnotation -> ann.copy(zIndex = minZ - 1)
                    is InkAnnotation -> ann.copy(zIndex = minZ - 1)
                    is StampAnnotation -> ann.copy(zIndex = minZ - 1)
                    is SignatureAnnotation -> ann.copy(zIndex = minZ - 1)
                    is RedactionAnnotation -> ann.copy(zIndex = minZ - 1)
                    else -> ann
                }
            } else {
                ann
            }
        }
        _uiState.value = _uiState.value.copy(
            annotations = updated,
            canUndo = undoRedoManager.canUndo(),
            canRedo = undoRedoManager.canRedo()
        )
        triggerAutosave()
    }

    fun updateShapeStyle(
        annotationId: String,
        color: Color? = null,
        strokeWidth: Float? = null,
        fillColor: Color? = null,
        opacity: Float? = null
    ) {
        undoRedoManager.pushState(_uiState.value.annotations, _uiState.value.selectedAnnotationId)
        val updated = _uiState.value.annotations.map { ann ->
            if (ann.id == annotationId && ann is ShapeAnnotation) {
                ann.copy(
                    color = color ?: ann.color,
                    strokeWidth = strokeWidth ?: ann.strokeWidth,
                    fillColor = fillColor ?: ann.fillColor,
                    opacity = opacity ?: ann.opacity
                )
            } else {
                ann
            }
        }
        // Keep the toolbar's colour row / size slider defaults in sync with what the dialog just set.
        _uiState.value = _uiState.value.copy(
            annotations = updated,
            selectedColor = color ?: _uiState.value.selectedColor,
            selectedStrokeWidth = strokeWidth ?: _uiState.value.selectedStrokeWidth,
            canUndo = undoRedoManager.canUndo(),
            canRedo = undoRedoManager.canRedo()
        )
        triggerAutosave()
    }

    fun onStyleChanged(annotationId: String, updater: (Annotation) -> Annotation) {
        undoRedoManager.pushState(_uiState.value.annotations, _uiState.value.selectedAnnotationId)
        val updated = _uiState.value.annotations.map { ann ->
            if (ann.id == annotationId) {
                updater(ann)
            } else {
                ann
            }
        }
        _uiState.value = _uiState.value.copy(
            annotations = updated,
            canUndo = undoRedoManager.canUndo(),
            canRedo = undoRedoManager.canRedo()
        )
        triggerAutosave()
    }

    fun addAnnotation(annotation: Annotation) {
        val currentList = _uiState.value.annotations
        undoRedoManager.pushState(currentList, _uiState.value.selectedAnnotationId)
        val updated = currentList + annotation
        _uiState.value = _uiState.value.copy(
            annotations = updated,
            // A freshly placed stamp is selected so it can be resized / rotated straight away.
            selectedAnnotationId = if (annotation is StampAnnotation) annotation.id else _uiState.value.selectedAnnotationId,
            canUndo = undoRedoManager.canUndo(),
            canRedo = undoRedoManager.canRedo()
        )
        triggerAutosave()
    }

    fun removeAnnotation(annotation: Annotation) {
        onDeleteAnnotation(annotation.id)
    }

    fun undo() {
        if (_uiState.value.editingAnnotationId != null) {
            onCommitEdit()
        }
        val currentSnapshot = EditorSnapshot(
            annotations = _uiState.value.annotations,
            selectedAnnotationId = _uiState.value.selectedAnnotationId
        )
        val prev = undoRedoManager.undoSnapshot(currentSnapshot)
        if (prev != null) {
            _uiState.value = _uiState.value.copy(
                annotations = prev.annotations,
                selectedAnnotationId = prev.selectedAnnotationId,
                editingAnnotationId = null,
                canUndo = undoRedoManager.canUndo(),
                canRedo = undoRedoManager.canRedo()
            )
            triggerAutosave()
        }
    }

    fun redo() {
        if (_uiState.value.editingAnnotationId != null) {
            onCommitEdit()
        }
        val currentSnapshot = EditorSnapshot(
            annotations = _uiState.value.annotations,
            selectedAnnotationId = _uiState.value.selectedAnnotationId
        )
        val next = undoRedoManager.redoSnapshot(currentSnapshot)
        if (next != null) {
            _uiState.value = _uiState.value.copy(
                annotations = next.annotations,
                selectedAnnotationId = next.selectedAnnotationId,
                editingAnnotationId = null,
                canUndo = undoRedoManager.canUndo(),
                canRedo = undoRedoManager.canRedo()
            )
            triggerAutosave()
        }
    }

    fun saveDocument(onSaved: (String) -> Unit) {
        if (_uiState.value.editingAnnotationId != null) {
            onCommitEdit()
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            val savedUri = withContext(Dispatchers.IO) {
                try {
                    val pdfDoc = PdfDocument()
                    val totalPages = _uiState.value.pageCount

                    for (i in 0 until totalPages) {
                        val baseBmp = renderSession?.renderPage(i, 1080) ?: continue
                        val pageInfo = PdfDocument.PageInfo.Builder(baseBmp.width, baseBmp.height, i + 1).create()
                        val page = pdfDoc.startPage(pageInfo)
                        val canvas = page.canvas

                        canvas.drawBitmap(baseBmp, 0f, 0f, null)

                        val pageAnnotations = _uiState.value.annotations
                            .filter { it.pageIndex == i }
                            .sortedBy { it.zIndex }

                        pageAnnotations.forEach { ann ->
                            when (ann) {
                                is InkAnnotation -> {
                                    // Same alpha / width rules as InkAnnotation.draw() so export == screen.
                                    val inkAlpha = ann.color.alpha *
                                        (if (ann.isHighlighter) 0.35f * ann.opacity else ann.opacity)
                                    val paint = Paint().apply {
                                        isAntiAlias = true
                                        color = android.graphics.Color.argb(
                                            (inkAlpha * 255).toInt().coerceIn(0, 255),
                                            (ann.color.red * 255).toInt(),
                                            (ann.color.green * 255).toInt(),
                                            (ann.color.blue * 255).toInt()
                                        )
                                        strokeWidth = ann.strokeWidth
                                        style = Paint.Style.STROKE
                                        strokeCap = Paint.Cap.ROUND
                                        strokeJoin = Paint.Join.ROUND
                                    }
                                    if (ann.points.size > 1) {
                                        val path = Path().apply {
                                            moveTo(ann.points[0].x, ann.points[0].y)
                                            for (ptIdx in 1 until ann.points.size) {
                                                val p0 = ann.points[ptIdx - 1]
                                                val p1 = ann.points[ptIdx]
                                                quadTo(p0.x, p0.y, (p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
                                            }
                                            lineTo(ann.points.last().x, ann.points.last().y)
                                        }
                                        canvas.drawPath(path, paint)
                                    }
                                }
                                is RedactionAnnotation -> {
                                    val paint = Paint().apply {
                                        color = android.graphics.Color.BLACK
                                        style = Paint.Style.FILL
                                    }
                                    canvas.drawRect(
                                        ann.bounds.left,
                                        ann.bounds.top,
                                        ann.bounds.right,
                                        ann.bounds.bottom,
                                        paint
                                    )
                                }
                                is TextAnnotation -> {
                                    if (ann.text.isNotBlank()) {
                                        canvas.save()
                                        canvas.translate(ann.position.x, ann.position.y)
                                        if (ann.rotation != 0f) {
                                            // Same pivot as the on-screen box (its centre).
                                            canvas.rotate(ann.rotation, ann.width / 2f, ann.height / 2f)
                                        }
                                        canvas.translate(TEXT_PADDING_X, TEXT_PADDING_Y)

                                        val textPaint = TextPaint().apply {
                                            isAntiAlias = true
                                            textSize = ann.fontSize
                                            color = android.graphics.Color.argb(
                                                (ann.color.alpha * 255).toInt(),
                                                (ann.color.red * 255).toInt(),
                                                (ann.color.green * 255).toInt(),
                                                (ann.color.blue * 255).toInt()
                                            )
                                            val style = when {
                                                ann.isBold && ann.isItalic -> Typeface.BOLD_ITALIC
                                                ann.isBold -> Typeface.BOLD
                                                ann.isItalic -> Typeface.ITALIC
                                                else -> Typeface.NORMAL
                                            }
                                            typeface = when (ann.fontFamily) {
                                                EditorFontFamily.SANS_SERIF -> Typeface.create(
                                                    Typeface.SANS_SERIF, style)
                                                EditorFontFamily.SERIF -> Typeface.create(Typeface.SERIF, style)
                                                EditorFontFamily.MONOSPACE -> Typeface.create(
                                                    Typeface.MONOSPACE, style)
                                                EditorFontFamily.CURSIVE -> Typeface.create("cursive", style)
                                            }
                                        }

                                        val layoutAlignment = when (ann.alignment) {
                                            TextAlignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
                                            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                                            TextAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                                        }

                                        // The on-screen box hugs its content, so give the export layout a little
                                        // slack; otherwise tiny font-metric differences could wrap the last word.
                                        val innerWidth = ann.width - TEXT_PADDING_X * 2f
                                        val widthInt = (innerWidth + ann.fontSize * 0.5f).roundToInt().coerceAtLeast(10)
                                        val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            StaticLayout.Builder.obtain(ann.text, 0, ann.text.length, textPaint, widthInt)
                                                .setAlignment(layoutAlignment)
                                                .setLineSpacing(0f, 1f)
                                                .setIncludePad(false)
                                                .build()
                                        } else {
                                            @Suppress("DEPRECATION")
                                            StaticLayout(ann.text, textPaint, widthInt, layoutAlignment, 1f, 0f, false)
                                        }

                                        staticLayout.draw(canvas)
                                        canvas.restore()
                                    }
                                }
                                is StampAnnotation -> {
                                    // Same geometry as Stamp.draw(): box, label, date, scale and rotation.
                                    val stampColor = android.graphics.Color.parseColor(ann.stampType.colorHex)
                                    val s = ann.scale
                                    val sb = ann.bounds
                                    val box = RectF(sb.left, sb.top, sb.right, sb.bottom)
                                    canvas.save()
                                    if (ann.rotation != 0f) canvas.rotate(ann.rotation, ann.position.x, ann.position.y)
                                    canvas.drawRoundRect(box, 8f * s, 8f * s, Paint().apply {
                                        isAntiAlias = true
                                        color = stampColor
                                        alpha = 31
                                        style = Paint.Style.FILL
                                    })
                                    canvas.drawRoundRect(box, 8f * s, 8f * s, Paint().apply {
                                        isAntiAlias = true
                                        color = stampColor
                                        style = Paint.Style.STROKE
                                        strokeWidth = 3f * s
                                    })
                                    val labelPaint = Paint().apply {
                                        isAntiAlias = true
                                        color = stampColor
                                        textSize = 24f * s
                                        isFakeBoldText = true
                                        textAlign = Paint.Align.CENTER
                                    }
                                    val datePaint = Paint().apply {
                                        isAntiAlias = true
                                        color = stampColor
                                        textSize = 14f * s
                                        textAlign = Paint.Align.CENTER
                                    }
                                    canvas.drawText(ann.stampType.label, ann.position.x, ann.position.y - 2f * s, labelPaint)
                                    canvas.drawText(ann.dateText, ann.position.x, ann.position.y + 20f * s, datePaint)
                                    canvas.restore()
                                }
                                is SignatureAnnotation -> {
                                    val paint = Paint().apply {
                                        isAntiAlias = true
                                        color = android.graphics.Color.argb(
                                            (ann.strokeColor.alpha * 255).toInt(),
                                            (ann.strokeColor.red * 255).toInt(),
                                            (ann.strokeColor.green * 255).toInt(),
                                            (ann.strokeColor.blue * 255).toInt()
                                        )
                                        strokeWidth = SIGNATURE_STROKE_WIDTH * ann.scale
                                        style = Paint.Style.STROKE
                                        strokeCap = Paint.Cap.ROUND
                                        strokeJoin = Paint.Join.ROUND
                                        if (ann.filled) style = Paint.Style.FILL
                                    }
                                    canvas.save()
                                    if (ann.rotation != 0f) {
                                        val b = ann.bounds
                                        canvas.rotate(ann.rotation, b.center.x, b.center.y)
                                    }
                                    if (ann.filled) {
                                        // single path so glyph holes stay open
                                        val glyphs = Path()
                                        ann.strokes.forEach { pts ->
                                            if (pts.size > 2) {
                                                glyphs.moveTo(ann.position.x + pts[0].x * ann.scale, ann.position.y + pts[0].y * ann.scale)
                                                for (i in 1 until pts.size) {
                                                    glyphs.lineTo(ann.position.x + pts[i].x * ann.scale, ann.position.y + pts[i].y * ann.scale)
                                                }
                                                glyphs.close()
                                            }
                                        }
                                        canvas.drawPath(glyphs, paint)
                                    } else ann.strokes.forEach { stroke ->
                                        if (stroke.size > 1) {
                                            val path = Path().apply {
                                                moveTo(
                                                    ann.position.x + stroke[0].x * ann.scale,
                                                    ann.position.y + stroke[0].y * ann.scale
                                                )
                                                for (sIdx in 1 until stroke.size) {
                                                    lineTo(
                                                        ann.position.x + stroke[sIdx].x * ann.scale,
                                                        ann.position.y + stroke[sIdx].y * ann.scale
                                                    )
                                                }
                                            }
                                            canvas.drawPath(path, paint)
                                        }
                                    }
                                    canvas.restore()
                                }
                                is ShapeAnnotation -> {
                                    val paint = Paint().apply {
                                        isAntiAlias = true
                                        color = android.graphics.Color.argb(
                                            (ann.color.alpha * 255).toInt(),
                                            (ann.color.red * 255).toInt(),
                                            (ann.color.green * 255).toInt(),
                                            (ann.color.blue * 255).toInt()
                                        )
                                        strokeWidth = ann.strokeWidth
                                        style = Paint.Style.STROKE
                                    }
                                    val left = minOf(ann.start.x, ann.end.x)
                                    val top = minOf(ann.start.y, ann.end.y)
                                    val right = maxOf(ann.start.x, ann.end.x)
                                    val bottom = maxOf(ann.start.y, ann.end.y)

                                    canvas.save()
                                    if (ann.rotation != 0f) {
                                        val sb = ann.bounds
                                        canvas.rotate(ann.rotation, sb.center.x, sb.center.y)
                                    }

                                    if (ann.fillColor != null) {
                                        val fillPaint = Paint().apply {
                                            isAntiAlias = true
                                            color = android.graphics.Color.argb(
                                                (ann.fillColor.alpha * ann.opacity * 255).toInt(),
                                                (ann.fillColor.red * 255).toInt(),
                                                (ann.fillColor.green * 255).toInt(),
                                                (ann.fillColor.blue * 255).toInt()
                                            )
                                            style = Paint.Style.FILL
                                        }
                                        when (ann.shapeType) {
                                            ShapeType.RECTANGLE -> canvas.drawRect(left, top, right, bottom, fillPaint)
                                            ShapeType.ELLIPSE -> canvas.drawOval(RectF(left, top, right, bottom), fillPaint)
                                            else -> {}
                                        }
                                    }

                                    when (ann.shapeType) {
                                        ShapeType.RECTANGLE -> canvas.drawRect(left, top, right, bottom, paint)
                                        ShapeType.ELLIPSE -> canvas.drawOval(RectF(left, top, right, bottom), paint)
                                        ShapeType.LINE, ShapeType.ARROW -> canvas.drawLine(ann.start.x, ann.start.y, ann.end.x, ann.end.y, paint)
                                    }
                                    canvas.restore()
                                }
                                else -> {}
                            }
                        }

                        pdfDoc.finishPage(page)
                    }

                    val outputDir = File(context.filesDir, "annotated_docs").apply { mkdirs() }
                    val outputFile = File(outputDir, "edited_${System.currentTimeMillis()}.pdf")
                    FileOutputStream(outputFile).use { fos ->
                        pdfDoc.writeTo(fos)
                    }
                    pdfDoc.close()

                    // Delete draft file on successful save into PDF
                    try {
                        val draftFile = File(context.filesDir, "annotation_drafts/draft_${uriString.hashCode()}.json")
                        if (draftFile.exists()) {
                            draftFile.delete()
                        }
                    } catch (_: Exception) {}

                    val resultUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        outputFile
                    )

                    pdfRepository.openPdf(resultUri)
                    resultUri.toString()
                } catch (e: Exception) {
                    null
                }
            }

            _uiState.value = _uiState.value.copy(isSaving = false)
            if (savedUri != null) {
                onSaved(savedUri)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        renderSession?.close()
    }

    companion object {
        fun provideFactory(
            context: Context,
            savedStateHandle: SavedStateHandle,
            pdfRepository: PdfDocumentRepository,
            signatureDao: SignatureDao
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditorViewModel(context, savedStateHandle, pdfRepository, signatureDao) as T
            }
        }
    }
}
