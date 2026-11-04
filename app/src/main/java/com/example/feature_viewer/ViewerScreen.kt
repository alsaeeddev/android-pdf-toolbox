package com.example.feature_viewer

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.data.preferences.ReadingMode
import com.example.core.data.preferences.ViewMode
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandTeal
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    viewModel: ViewerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (page: Int, zoom: Float) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // Screen On handling
    val view = LocalView.current
    DisposableEffect(uiState.keepScreenOn) {
        view.keepScreenOn = uiState.keepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }

    BackHandler {
        onNavigateBack()
    }

    var showControls by remember { mutableStateOf(true) }
    var showViewModeMenu by remember { mutableStateOf(false) }
    var showReadingModeMenu by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Scroll states for Continuous and Single Page modes
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (uiState.currentPage - 1).coerceAtLeast(0)
    )
    val pagerState = rememberPagerState(
        initialPage = (uiState.currentPage - 1).coerceAtLeast(0),
        pageCount = { uiState.pageCount.coerceAtLeast(1) }
    )

    // Continuously observe scrolling in continuous vertical mode:
    // Calculates the page with the largest visible height in the viewport and dispatches to ViewModel
    LaunchedEffect(listState) {
        snapshotFlow { calculateMostVisiblePage(listState.layoutInfo) }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { visiblePage ->
                viewModel.onVisiblePageChanged(visiblePage)
            }
    }

    // Continuously observe swiping in single-page horizontal mode:
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage + 1 }
            .distinctUntilChanged()
            .collect { visiblePage ->
                viewModel.onVisiblePageChanged(visiblePage)
            }
    }

    // Programmatic scroll helper for jump dialog, bookmarks, and fast-scroller
    val scrollToPage: (Int) -> Unit = { targetPage ->
        val safeTarget = targetPage.coerceIn(1, uiState.pageCount.coerceAtLeast(1))
        viewModel.onVisiblePageChanged(safeTarget)
        scope.launch {
            if (uiState.viewMode == ViewMode.CONTINUOUS_VERTICAL) {
                listState.scrollToItem(safeTarget - 1)
            } else {
                pagerState.scrollToPage(safeTarget - 1)
            }
        }
    }

    // Color Filters for Reading Modes
    val (backgroundColor, colorFilter) = remember(uiState.readingMode) {
        when (uiState.readingMode) {
            ReadingMode.NORMAL -> Color.White to null
            ReadingMode.INVERT_DARK -> Color(0xFF121212) to ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            ReadingMode.SEPIA -> Color(0xFFFBF0D9) to ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.95f, 0.05f, 0f, 0f, 10f,
                        0f, 0.90f, 0f, 0f, 0f,
                        0f, 0f, 0.75f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            ReadingMode.NIGHT -> Color(0xFF0F1115) to ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -0.75f, 0f, 0f, 0f, 200f,
                        0f, -0.75f, 0f, 0f, 200f,
                        0f, 0f, -0.75f, 0f, 200f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.reopenDocumentWithUri(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (uiState.readingMode == ReadingMode.NORMAL) MaterialTheme.colorScheme.background else backgroundColor)
            .testTag("viewer_screen")
    ) {
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = BrandIndigo)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading document...", style = MaterialTheme.typography.bodyLarge)
            }
        } else if (uiState.isPasswordRequired) {
            // Password-protected PDF: dialog neeche (screen ke end me) dikhta hai, peeche locked placeholder.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = BrandIndigo,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "This PDF is password protected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        } else if (uiState.errorMessage != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (uiState.isPermissionError) "Permission Required" else "Cannot Load PDF",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = uiState.errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onNavigateBack) {
                                Text("Go Back")
                            }
                            Button(
                                onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandIndigo
                                )
                            ) {
                                Text("Re-Open Document", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            // PDF Pages Viewer Content
            // Only attach transform panning when scale > 1.05f to avoid capturing normal vertical scroll / swipe drags
            val transformModifier = if (scale > 1.05f) {
                Modifier.pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4.5f)
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
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = {
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.2f
                                }
                            }
                        )
                    }
                    .then(transformModifier)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            ) {
                if (uiState.viewMode == ViewMode.CONTINUOUS_VERTICAL) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 70.dp, horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(uiState.pageCount) { pageIndex ->
                            PdfPageCard(
                                pageIndex = pageIndex,
                                viewModel = viewModel,
                                colorFilter = colorFilter,
                                backgroundColor = backgroundColor
                            )
                        }
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        // Left -> right mode: page 1 sits on the left, swiping right moves forward.
                        reverseLayout = uiState.viewMode == ViewMode.SINGLE_PAGE_HORIZONTAL_REVERSED,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 70.dp)
                    ) { pageIndex ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            PdfPageCard(
                                pageIndex = pageIndex,
                                viewModel = viewModel,
                                colorFilter = colorFilter,
                                backgroundColor = backgroundColor
                            )
                        }
                    }
                }
            }

            // Top Toolbar (Floating & Auto-Hiding)
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 6.dp
                ) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = uiState.fileName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Page ${uiState.currentPage} of ${uiState.pageCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandTeal,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            // Edit / Annotate
                            IconButton(
                                onClick = { onNavigateToEditor(uiState.currentPage, scale) },
                                modifier = Modifier.testTag("viewer_edit_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Draw,
                                    contentDescription = "Edit & Annotate PDF",
                                    tint = BrandIndigo
                                )
                            }

                            // Bookmark toggle
                            IconButton(onClick = { viewModel.toggleBookmarkCurrentPage() }) {
                                Icon(
                                    imageVector = if (uiState.isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark Page",
                                    tint = if (uiState.isCurrentPageBookmarked) BrandIndigo else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Bookmarks List
                            IconButton(onClick = { showBookmarksSheet = true }) {
                                Icon(Icons.Default.Bookmarks, contentDescription = "Saved Bookmarks")
                            }

                            // Reading Mode Menu
                            Box {
                                IconButton(onClick = { showReadingModeMenu = true }) {
                                    Icon(
                                        imageVector = when (uiState.readingMode) {
                                            ReadingMode.NORMAL -> Icons.Default.WbSunny
                                            ReadingMode.INVERT_DARK -> Icons.Default.InvertColors
                                            ReadingMode.SEPIA -> Icons.Default.BrightnessMedium
                                            ReadingMode.NIGHT -> Icons.Default.ModeNight
                                        },
                                        contentDescription = "Reading Mode"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showReadingModeMenu,
                                    onDismissRequest = { showReadingModeMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Normal (Day)") },
                                        leadingIcon = { Icon(Icons.Default.WbSunny, contentDescription = null) },
                                        onClick = {
                                            viewModel.setReadingMode(ReadingMode.NORMAL)
                                            showReadingModeMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Invert Dark") },
                                        leadingIcon = { Icon(Icons.Default.InvertColors, contentDescription = null) },
                                        onClick = {
                                            viewModel.setReadingMode(ReadingMode.INVERT_DARK)
                                            showReadingModeMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sepia (Warm)") },
                                        leadingIcon = { Icon(Icons.Default.BrightnessMedium, contentDescription = null) },
                                        onClick = {
                                            viewModel.setReadingMode(ReadingMode.SEPIA)
                                            showReadingModeMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Night (Ultra Dark)") },
                                        leadingIcon = { Icon(Icons.Default.ModeNight, contentDescription = null) },
                                        onClick = {
                                            viewModel.setReadingMode(ReadingMode.NIGHT)
                                            showReadingModeMenu = false
                                        }
                                    )
                                }
                            }

                            // Share PDF
                            IconButton(
                                onClick = {
                                    val shareUri = try {
                                        val u = Uri.parse(uiState.uriString)
                                        if (u.scheme == "file") {
                                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(u.path ?: ""))
                                        } else {
                                            u
                                        }
                                    } catch (_: Exception) {
                                        Uri.parse(uiState.uriString)
                                    }

                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share PDF")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }

            // Bottom Toolbar & Page Scrubber
            AnimatedVisibility(
                visible = showControls,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // View mode menu: vertical scroll / swipe right->left / swipe left->right
                        Box {
                            IconButton(onClick = { showViewModeMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.ViewCarousel,
                                    contentDescription = "Page Mode",
                                    tint = BrandIndigo
                                )
                            }
                            DropdownMenu(
                                expanded = showViewModeMenu,
                                onDismissRequest = { showViewModeMenu = false }
                            ) {
                                listOf(
                                    ViewMode.CONTINUOUS_VERTICAL to "Scroll top to bottom",
                                    ViewMode.SINGLE_PAGE_HORIZONTAL to "Swipe right to left",
                                    ViewMode.SINGLE_PAGE_HORIZONTAL_REVERSED to "Swipe left to right"
                                ).forEach { (mode, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        trailingIcon = {
                                            if (uiState.viewMode == mode) {
                                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = BrandIndigo)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setViewMode(mode)
                                            showViewModeMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Jump to page button
                        TextButton(
                            onClick = { showJumpDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${uiState.currentPage} / ${uiState.pageCount}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Scrubber Slider
                        if (uiState.pageCount > 1) {
                            Slider(
                                value = uiState.currentPage.toFloat(),
                                onValueChange = { scrollToPage(it.toInt()) },
                                valueRange = 1f..uiState.pageCount.toFloat(),
                                steps = (uiState.pageCount - 2).coerceAtLeast(0),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = BrandTeal,
                                    activeTrackColor = BrandTeal,
                                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // Password Dialog (protected PDFs)
    if (uiState.isPasswordRequired) {
        var passwordInput by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { /* password ke baghair dismiss nahi: Cancel se wapas jayen */ },
            title = { Text("Password Required") },
            text = {
                Column {
                    Text("Enter the password to open \"${uiState.fileName}\".")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !uiState.isUnlocking,
                        isError = uiState.passwordError != null,
                        supportingText = { uiState.passwordError?.let { Text(it) } },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("viewer_password_field")
                    )
                    if (uiState.isUnlocking) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CircularProgressIndicator(color = BrandIndigo, modifier = Modifier.size(24.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = passwordInput.isNotEmpty() && !uiState.isUnlocking,
                    onClick = { viewModel.submitPassword(passwordInput) }
                ) {
                    Text("Unlock", fontWeight = FontWeight.Bold, color = BrandIndigo)
                }
            },
            dismissButton = {
                TextButton(onClick = onNavigateBack) {
                    Text("Cancel")
                }
            }
        )
    }

    // Jump to Page Dialog
    if (showJumpDialog) {
        var pageInput by remember { mutableStateOf(uiState.currentPage.toString()) }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                Column {
                    Text("Enter page number (1 to ${uiState.pageCount}):")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pageInput,
                        onValueChange = { pageInput = it.filter { char -> char.isDigit() } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = pageInput.toIntOrNull()
                        if (target != null && target in 1..uiState.pageCount) {
                            scrollToPage(target)
                            showJumpDialog = false
                        }
                    }
                ) {
                    Text("Go", fontWeight = FontWeight.Bold, color = BrandIndigo)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bookmarks Sheet
    if (showBookmarksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarksSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Document Bookmarks",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.bookmarks.isEmpty()) {
                    Text(
                        text = "No bookmarks added yet. Tap the bookmark icon in the top toolbar to save important pages.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.bookmarks.size) { i ->
                            val b = uiState.bookmarks[i]
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        scrollToPage(b.pageNumber)
                                        showBookmarksSheet = false
                                    },
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = BrandIndigo)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = b.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Computes which page occupies the largest visible height in the viewport.
 * Returns 1-based page number, or null if no items are currently visible.
 */
private fun calculateMostVisiblePage(layoutInfo: LazyListLayoutInfo): Int? {
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return null

    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset

    var maxVisibleHeight = -1
    var mostVisibleIndex = -1

    for (item in visibleItems) {
        val itemStart = item.offset
        val itemEnd = item.offset + item.size
        val visibleTop = maxOf(viewportStart, itemStart)
        val visibleBottom = minOf(viewportEnd, itemEnd)
        val visibleHeight = (visibleBottom - visibleTop).coerceAtLeast(0)

        if (visibleHeight > maxVisibleHeight) {
            maxVisibleHeight = visibleHeight
            mostVisibleIndex = item.index
        }
    }

    return if (mostVisibleIndex >= 0) mostVisibleIndex + 1 else null
}

@Composable
fun PdfPageCard(
    pageIndex: Int,
    viewModel: ViewerViewModel,
    colorFilter: ColorFilter?,
    backgroundColor: Color
) {
    val bitmapState = produceState<Bitmap?>(initialValue = null, key1 = pageIndex) {
        value = viewModel.renderPageBitmap(pageIndex)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        color = backgroundColor,
        shadowElevation = 4.dp
    ) {
        val bmp = bitmapState.value
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            // Skeleton loader while page is rendering off-thread
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f) // Standard A4 ratio
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = BrandIndigo,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}
