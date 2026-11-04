package com.example.feature_editor.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.feature_editor.EditorViewModel
import com.example.feature_editor.model.EditorFontFamily
import com.example.feature_editor.model.EditorTool
import com.example.feature_editor.model.InkAnnotation
import com.example.feature_editor.model.ShapeAnnotation
import com.example.feature_editor.model.MAX_TEXT_SIZE
import com.example.feature_editor.model.MIN_TEXT_SIZE
import com.example.feature_editor.model.TextAlignment
import com.example.feature_editor.model.TextAnnotation
import com.example.feature_editor.model.defaultEditorColors
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandTeal
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit,
    onDocumentSaved: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // One place for "Annotation deleted - Undo". An action snackbar defaults to Indefinite in Material3,
    // so the duration is set explicitly: it hides itself after a few seconds.
    fun showUndoSnackbar(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undo()
            }
        }
    }

    // Size control for the active tool (null = tool has no size, e.g. Stamps / Sign / Redact).
    val sizeAnn = uiState.annotations.find { it.id == uiState.selectedAnnotationId }
    val sizeControl: SizeControlSpec? = when {
        uiState.activeTool == EditorTool.PEN -> SizeControlSpec(
            label = "Pen size", unit = "px",
            value = uiState.selectedStrokeWidth, range = 2f..24f,
            previewColor = uiState.selectedColor, accent = BrandIndigo,
            onChange = { viewModel.setStrokeWidth(it) }
        )
        uiState.activeTool == EditorTool.HIGHLIGHTER -> SizeControlSpec(
            label = "Highlighter size", unit = "px",
            value = uiState.selectedStrokeWidth, range = 12f..36f,
            previewColor = uiState.selectedColor.copy(alpha = 0.55f), accent = BrandIndigo,
            onChange = { viewModel.setStrokeWidth(it) }
        )
        uiState.activeTool == EditorTool.SHAPE -> SizeControlSpec(
            label = "Shape stroke width", unit = "px",
            // After drawing, the shape stays selected while the SHAPE tool is still active: show ITS
            // colour / width (changed e.g. from the "Shape Formatting" dialog), not the global default.
            value = (sizeAnn as? ShapeAnnotation)?.strokeWidth ?: uiState.selectedStrokeWidth,
            range = 2f..24f,
            previewColor = (sizeAnn as? ShapeAnnotation)?.color ?: uiState.selectedColor,
            accent = BrandIndigo,
            onChange = { viewModel.setStrokeWidth(it) }
        )
        uiState.activeTool == EditorTool.ERASER -> SizeControlSpec(
            label = "Eraser size", unit = "dp",
            value = uiState.eraserRadiusDp, range = 8f..48f,
            previewColor = MaterialTheme.colorScheme.onSurfaceVariant, accent = BrandTeal,
            previewAsRing = true,
            onChange = { viewModel.setEraserRadius(it) }
        )
        uiState.activeTool == EditorTool.SELECT && sizeAnn is ShapeAnnotation -> SizeControlSpec(
            label = "Shape stroke width", unit = "px",
            value = sizeAnn.strokeWidth, range = 2f..24f,
            previewColor = sizeAnn.color, accent = BrandIndigo,
            onChange = { viewModel.setStrokeWidth(it) }
        )
        uiState.activeTool == EditorTool.SELECT && sizeAnn is InkAnnotation -> SizeControlSpec(
            label = "Stroke width", unit = "px",
            value = sizeAnn.strokeWidth,
            range = if (sizeAnn.isHighlighter) 12f..36f else 2f..24f,
            previewColor = sizeAnn.color, accent = BrandIndigo,
            onChange = { viewModel.setStrokeWidth(it) }
        )
        else -> null
    }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.reopenDocumentWithUri(it) }
    }

    var scale by remember(uiState.zoomLevel) { mutableFloatStateOf(uiState.zoomLevel) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // While typing, show the whole page (the canvas fits the space above the keyboard), so the
    // text box can never end up off-screen because of an old pinch-zoom / pan.
    LaunchedEffect(uiState.editingAnnotationId) {
        if (uiState.editingAnnotationId != null) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    LaunchedEffect(uiState.showTextHint) {
        if (uiState.showTextHint) {
            snackbarHostState.showSnackbar("Tap to add text. Tap text to edit. Drag to move.")
            viewModel.onDismissTextHint()
        }
    }

    BackHandler {
        when {
            uiState.editingAnnotationId != null -> {
                viewModel.onCommitEdit()
            }
            uiState.selectedAnnotationId != null -> {
                viewModel.deselect()
            }
            uiState.annotations.isNotEmpty() -> {
                showExitConfirmDialog = true
            }
            else -> {
                onNavigateBack()
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("editor_screen"),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 80.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.pageCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { viewModel.setPage(uiState.currentPage - 1) },
                                    enabled = uiState.currentPage > 1,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                                }
                                Text(
                                    text = "Page ${uiState.currentPage} of ${uiState.pageCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandTeal,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = { viewModel.setPage(uiState.currentPage + 1) },
                                    enabled = uiState.currentPage < uiState.pageCount,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.editingAnnotationId != null) {
                                viewModel.onCommitEdit()
                            }
                            if (uiState.annotations.isNotEmpty()) {
                                showExitConfirmDialog = true
                            } else {
                                onNavigateBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.selectedAnnotationId != null) {
                        IconButton(
                            onClick = {
                                viewModel.deleteSelectedAnnotation {
                                    showUndoSnackbar("Annotation deleted")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected annotation",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = uiState.canUndo
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (uiState.canUndo) BrandIndigo else MaterialTheme.colorScheme.outline
                        )
                    }

                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = uiState.canRedo
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (uiState.canRedo) BrandIndigo else MaterialTheme.colorScheme.outline
                        )
                    }

                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .size(24.dp),
                            color = BrandIndigo,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Button(
                            onClick = {
                                viewModel.saveDocument { savedUri ->
                                    onDocumentSaved(savedUri)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandIndigo,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            val selectedText = uiState.annotations.find {
                it.id == (uiState.editingAnnotationId ?: uiState.selectedAnnotationId)
            } as? TextAnnotation

            Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
                AnimatedVisibility(
                    visible = selectedText != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    if (selectedText != null) {
                        TextFormattingBar(
                            annotation = selectedText,
                            onStyleChanged = { updater ->
                                viewModel.onTextStyleChanged(selectedText.id, updater)
                            },
                            onDone = { viewModel.onTextDone() }
                        )
                    }
                }

                if (!uiState.isLoading && uiState.errorMessage == null && selectedText == null) {
                    EditorToolbar(
                        activeTool = uiState.activeTool,
                        selectedColor = uiState.selectedColor,
                        selectedShapeType = uiState.selectedShapeType,
                        selectedStampType = uiState.selectedStampType,
                        eraserMode = uiState.eraserMode,
                        sizeControl = sizeControl,
                        onToolSelected = { viewModel.setTool(it) },
                        onColorSelected = { viewModel.setColor(it) },
                        onShapeTypeSelected = { viewModel.setShapeType(it) },
                        onStampSelected = { viewModel.setStampType(it) },
                        onEraserModeSelected = { viewModel.setEraserMode(it) },
                        onOpenSignatureDialog = { showSignatureDialog = true }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFE2E8F0)),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator(
                            color = BrandIndigo,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading document for editing...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                uiState.errorMessage != null -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(24.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (uiState.isPermissionError) "Permission Required" else "Unable to Open PDF in Editor",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = uiState.errorMessage ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(onClick = onNavigateBack) {
                                    Text("Go Back")
                                }
                                Button(
                                    onClick = {
                                        if (uiState.isPermissionError) {
                                            openPdfLauncher.launch(arrayOf("application/pdf"))
                                        } else {
                                            viewModel.retry()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (uiState.isPermissionError) "Re-Open Document" else "Retry")
                                }
                            }
                        }
                    }
                }

                else -> {
                    val pageBitmap = uiState.currentPageBitmap
                    val aspectRatio = if (pageBitmap != null && pageBitmap.height > 0) {
                        pageBitmap.width.toFloat() / pageBitmap.height.toFloat()
                    } else {
                        0.707f
                    }

                    val transformModifier = if (uiState.activeTool == EditorTool.VIEW || scale > 1.05f) {
                        Modifier.pointerInput(uiState.activeTool) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.8f, 4.0f)
                                if (scale > 1f) {
                                    val maxX = (size.width * (scale - 1)) / 2f
                                    val maxY = (size.height * (scale - 1)) / 2f
                                    offset = Offset(
                                        x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                        y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                    } else {
                        Modifier
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(transformModifier)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .padding(12.dp)
                                .aspectRatio(aspectRatio)
                                .fillMaxWidth(0.94f),
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White,
                            shadowElevation = 8.dp
                        ) {
                            if (pageBitmap != null) {
                                EditorCanvas(
                                    pageIndex = uiState.currentPage - 1,
                                    pageBitmap = pageBitmap,
                                    activeTool = uiState.activeTool,
                                    selectedColor = uiState.selectedColor,
                                    selectedStrokeWidth = uiState.selectedStrokeWidth,
                                    selectedShapeType = uiState.selectedShapeType,
                                    selectedStampType = uiState.selectedStampType,
                                    eraserRadiusDp = uiState.eraserRadiusDp,
                                    eraserPosition = uiState.eraserPosition,
                                    annotations = uiState.annotations,
                                    draftAnnotation = uiState.draftAnnotation,
                                    selectedAnnotationId = uiState.selectedAnnotationId,
                                    editingAnnotationId = uiState.editingAnnotationId,
                                    onPageTap = { viewModel.onPageTap(it) },
                                    onPageDoubleTap = { viewModel.onPageDoubleTap(it) },
                                    onAnnotationTap = { viewModel.onAnnotationTap(it) },
                                    onSelectAnnotation = { viewModel.selectAnnotation(it) },
                                    onDeselect = { viewModel.deselect() },
                                    onStartEdit = { viewModel.onStartEdit(it) },
                                    onOpenShapeOptionsSheet = { viewModel.toggleShapeOptionsSheet(true) },
                                    onTextChanged = { id, text -> viewModel.onTextChanged(id, text) },
                                    onCommitEdit = { viewModel.onCommitEdit() },
                                    onDeleteAnnotation = { id ->
                                        viewModel.onDeleteAnnotation(id)
                                        showUndoSnackbar("Annotation deleted")
                                    },
                                    onDuplicateAnnotation = { viewModel.onDuplicateAnnotation(it) },
                                    onMoveAnnotation = { id, delta -> viewModel.onMoveAnnotation(id, delta) },
                                    onResizeAnnotation = { id, handle, delta -> viewModel.onResizeAnnotation(id, handle, delta) },
                                    onRotateAnnotation = { id, angle -> viewModel.onRotateAnnotation(id, angle) },
                                    onBringToFront = { viewModel.bringToFront(it) },
                                    onSendToBack = { viewModel.sendToBack(it) },
                                    onUpdateDraft = { viewModel.updateDraftAnnotation(it) },
                                    onCommitDraft = { viewModel.commitDraftAnnotation() },
                                    onCommitDraftAndSelect = { viewModel.commitDraftAnnotationAndSelect() },
                                    onStartEraseGesture = { viewModel.startEraseGesture() },
                                    onEraseAt = { point, radiusInPage -> viewModel.eraseAt(point, radiusInPage) },
                                    onTextSizeChanged = { id, w, h -> viewModel.onTextSizeMeasured(id, w, h) },
                                    onUpdateEraserPosition = { viewModel.updateEraserPosition(it) },
                                    onFinishEraseGesture = { viewModel.finishEraseGesture() },
                                    onAnnotationAdded = { viewModel.addAnnotation(it) },
                                    onRemoveAnnotation = { viewModel.removeAnnotation(it) },
                                    zoomLevel = scale,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFF1F5F9)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            color = BrandIndigo,
                                            strokeWidth = 3.dp,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Rendering Page ${uiState.currentPage}...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Shape Styling Options Dialog
    val selectedShape = uiState.annotations.find { it.id == uiState.selectedAnnotationId } as? ShapeAnnotation
    if (uiState.showShapeOptionsSheet && selectedShape != null) {
        AlertDialog(
            onDismissRequest = { viewModel.toggleShapeOptionsSheet(false) },
            title = { Text("Shape Formatting") },
            text = {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Stroke Color", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        defaultEditorColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (selectedShape.color == color) 3.dp else 1.dp,
                                        color = if (selectedShape.color == color) BrandIndigo else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { viewModel.updateShapeStyle(selectedShape.id, color = color) }
                            )
                        }
                    }

                    Text("Stroke Width (${selectedShape.strokeWidth.roundToInt()} px)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Slider(
                        value = selectedShape.strokeWidth,
                        onValueChange = { viewModel.updateShapeStyle(selectedShape.id, strokeWidth = it) },
                        valueRange = 2f..24f,
                        colors = SliderDefaults.colors(thumbColor = BrandIndigo, activeTrackColor = BrandIndigo)
                    )

                    Text("Opacity (${(selectedShape.opacity * 100).roundToInt()}%)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Slider(
                        value = selectedShape.opacity,
                        onValueChange = { viewModel.updateShapeStyle(selectedShape.id, opacity = it) },
                        valueRange = 0.1f..1f,
                        colors = SliderDefaults.colors(thumbColor = BrandTeal, activeTrackColor = BrandTeal)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.toggleShapeOptionsSheet(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo)
                ) {
                    Text("Done")
                }
            }
        )
    }

    if (showSignatureDialog) {
        SignatureDialog(
            signatureDao = viewModel.signatureDao,
            onSignatureCreated = { strokes, filled ->
                showSignatureDialog = false
                // Centres it on the page, selects it and switches to SELECT => drag / resize / rotate work at once.
                viewModel.addSignature(strokes, filled)
            },
            onDismiss = {
                showSignatureDialog = false
                // Don't leave the (non-interactive) Sign tool active after cancelling.
                viewModel.setTool(EditorTool.SELECT)
            }
        )
    }

    if (showExitConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmDialog = false },
            title = { Text("Discard annotations?") },
            text = { Text("You have unsaved annotations on this document. Leaving now will discard them.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }
}

@Composable
fun TextFormattingBar(
    annotation: TextAnnotation,
    onStyleChanged: ((TextAnnotation) -> TextAnnotation) -> Unit,
    onDone: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            onStyleChanged { it.copy(fontSize = (it.fontSize - 4f).coerceAtLeast(MIN_TEXT_SIZE)) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Text(
                        text = "${annotation.fontSize.roundToInt()}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = {
                            onStyleChanged { it.copy(fontSize = (it.fontSize + 4f).coerceAtMost(MAX_TEXT_SIZE)) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = annotation.isBold,
                        onClick = { onStyleChanged { it.copy(isBold = !it.isBold) } },
                        label = { Text("B", fontWeight = FontWeight.Bold) }
                    )
                    FilterChip(
                        selected = annotation.isItalic,
                        onClick = { onStyleChanged { it.copy(isItalic = !it.isItalic) } },
                        label = { Text("I", fontStyle = FontStyle.Italic) }
                    )
                }

                IconButton(
                    onClick = {
                        val nextAlign = when (annotation.alignment) {
                            TextAlignment.LEFT -> TextAlignment.CENTER
                            TextAlignment.CENTER -> TextAlignment.RIGHT
                            TextAlignment.RIGHT -> TextAlignment.LEFT
                        }
                        onStyleChanged { it.copy(alignment = nextAlign) }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = when (annotation.alignment) {
                            TextAlignment.LEFT -> Icons.Default.FormatAlignLeft
                            TextAlignment.CENTER -> Icons.Default.FormatAlignCenter
                            TextAlignment.RIGHT -> Icons.Default.FormatAlignRight
                        },
                        contentDescription = "Text Alignment"
                    )
                }

                Button(
                    onClick = onDone,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandIndigo),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Done", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EditorFontFamily.entries.forEach { font ->
                    FilterChip(
                        selected = annotation.fontFamily == font,
                        onClick = { onStyleChanged { it.copy(fontFamily = font) } },
                        label = { Text(font.displayName, fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                defaultEditorColors.filter { it != Color.White }.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (annotation.color == color) 2.5.dp else 1.dp,
                                color = if (annotation.color == color) BrandIndigo else Color.LightGray,
                                shape = CircleShape
                            )
                            .clickable { onStyleChanged { it.copy(color = color) } }
                    )
                }
            }
        }
    }
}


