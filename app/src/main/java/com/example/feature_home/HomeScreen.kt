package com.example.feature_home

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.data.local.entity.PdfDocumentEntity
import com.example.core.data.preferences.LibraryFolder
import com.example.core.data.repository.FolderPdf
import com.example.feature_tools.ToolsContent
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.OutfitFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HomeTab(val title: String, val icon: ImageVector) {
    RECENT("Recent", Icons.Default.History),
    FAVORITES("Favorites", Icons.Default.Favorite),
    ALL_FILES("All PDFs", Icons.Default.Folder),
    TOOLS("Tools", Icons.Default.Build)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    toolsViewModel: com.example.feature_tools.ToolsViewModel? = null,
    onNavigateToViewer: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onImagesToPdf: () -> Unit = {},
    onScanToPdf: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFabSpeedDial by remember { mutableStateOf(false) }
    var selectedDocForDetails by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var showFolderManager by remember { mutableStateOf(false) }

    // Folder picker for the "All PDFs" library (no storage permission needed)
    val addFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        treeUri?.let { viewModel.addLibraryFolder(it) }
    }

    // SAF Document Picker
    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            viewModel.openDocumentUri(
                uri = selectedUri,
                onSuccess = { docUri -> onNavigateToViewer(docUri) },
                onError = { _ -> onNavigateToViewer(selectedUri.toString()) }
            )
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSearchActive) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            placeholder = { Text("Search PDF files...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                         /*   trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },*/
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_search_input")
                        )
                    } else {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(
                                    SpanStyle(
                                        fontWeight = FontWeight.Normal,
                                        fontFamily = OutfitFontFamily,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    append("my")
                                }
                                withStyle(
                                    SpanStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = OutfitFontFamily,
                                        color = BrandTeal
                                    )
                                ) {
                                    append("Prev")
                                }
                            },
                            fontSize = 24.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleSearchActive() },
                        modifier = Modifier.testTag("home_search_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (uiState.isSearchActive) "Close search" else "Search"
                        )
                    }

                    if (!uiState.isSearchActive && selectedTabIndex != 3) {
                        IconButton(
                            onClick = { viewModel.toggleGridView() },
                            modifier = Modifier.testTag("home_view_toggle")
                        ) {
                            Icon(
                                imageVector = if (uiState.isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "Toggle Grid/List View"
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("home_sort_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort documents"
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.label) },
                                        leadingIcon = {
                                            if (uiState.sortOrder == order) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = BrandIndigo)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setSortOrder(order)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Speed Dial mini-buttons
                AnimatedVisibility(
                    visible = showFabSpeedDial,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Scan to PDF
                        FloatingActionButton(
                            onClick = {
                                showFabSpeedDial = false
                                onScanToPdf()
                            },
                            modifier = Modifier.size(46.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = BrandTeal
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Scan Document", modifier = Modifier.size(20.dp))
                        }

                        // Images to PDF
                        FloatingActionButton(
                            onClick = {
                                showFabSpeedDial = false
                                onImagesToPdf()
                            },
                            modifier = Modifier.size(46.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = BrandIndigo
                        ) {
                            Icon(Icons.Default.Collections, contentDescription = "Images to PDF", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Primary Extended FAB
                ExtendedFloatingActionButton(
                    onClick = {
                        openPdfLauncher.launch(arrayOf("application/pdf"))
                    },
                    modifier = Modifier.testTag("home_fab_open_pdf"),
                    containerColor = BrandIndigo,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(
                            text = "Open PDF",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Material 3 Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                HomeTab.entries.forEachIndexed { index, tab ->
                    val isSelected = selectedTabIndex == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.testTag("home_tab_${tab.name.lowercase()}")
                    )
                }
            }

            // Tab Content
            when (HomeTab.entries[selectedTabIndex]) {
                HomeTab.RECENT -> {
                    DocumentListOrGrid(
                        documents = uiState.recentDocuments,
                        isGridView = uiState.isGridView,
                        emptyTitle = if (uiState.searchQuery.isNotEmpty()) "No matching PDFs" else "No recent documents",
                        emptyMessage = if (uiState.searchQuery.isNotEmpty()) "Try a different search query." else "Tap 'Open PDF' to view, edit, or annotate documents.",
                        onDocumentClick = { onNavigateToViewer(it.uri) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onDeleteDocument = { viewModel.deleteDocument(it) },
                        onShowDetails = { selectedDocForDetails = it }
                    )
                }

                HomeTab.FAVORITES -> {
                    DocumentListOrGrid(
                        documents = uiState.favoriteDocuments,
                        isGridView = uiState.isGridView,
                        emptyTitle = "No favorite PDFs",
                        emptyMessage = "Tap the heart icon on any document to add it to your favorites.",
                        onDocumentClick = { onNavigateToViewer(it.uri) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onDeleteDocument = { viewModel.deleteDocument(it) },
                        onShowDetails = { selectedDocForDetails = it }
                    )
                }

                HomeTab.ALL_FILES -> {
                    LibraryTabContent(
                        folders = uiState.libraryFolders,
                        files = uiState.libraryFiles,
                        inaccessibleFolderUris = uiState.inaccessibleFolderUris,
                        selectedFolderUri = uiState.selectedFolderUri,
                        isScanning = uiState.isScanning,
                        isFiltering = uiState.searchQuery.isNotBlank(),
                        onAddFolder = { addFolderLauncher.launch(null) },
                        onManageFolders = { showFolderManager = true },
                        onRefresh = { viewModel.refreshLibrary() },
                        onSelectFolder = { viewModel.selectLibraryFolder(it) },
                        onFileClick = { file ->
                            val fileUri = Uri.parse(file.uri)
                            viewModel.openDocumentUri(
                                uri = fileUri,
                                onSuccess = { docUri -> onNavigateToViewer(docUri) },
                                onError = { _ -> onNavigateToViewer(file.uri) }
                            )
                        }
                    )
                }

                HomeTab.TOOLS -> {
                    ToolsContent(
                        viewModel = toolsViewModel,
                        onOpenDocumentInViewer = onNavigateToViewer,
                        onOpenTool = { /* Handled in tools section */ }
                    )
                }
            }
        }
    }

    // Manage library folders dialog
    if (showFolderManager) {
        ManageFoldersDialog(
            folders = uiState.libraryFolders,
            inaccessibleFolderUris = uiState.inaccessibleFolderUris,
            onAddFolder = { addFolderLauncher.launch(null) },
            onRemoveFolder = { viewModel.removeLibraryFolder(it) },
            onDismiss = { showFolderManager = false }
        )
    }

    // Document Details Dialog
    selectedDocForDetails?.let { doc ->
        val dateFormat = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { selectedDocForDetails = null },
            icon = {
                Icon(Icons.Default.Info, contentDescription = null, tint = BrandIndigo)
            },
            title = {
                Text(
                    text = doc.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pages: ${doc.pageCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "File Size: ${Formatter.formatFileSize(context, doc.sizeBytes)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Last Opened: ${dateFormat.format(Date(doc.lastOpenedTimestamp))}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Last Read Page: Page ${doc.lastReadPage}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDocForDetails = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun DocumentListOrGrid(
    documents: List<PdfDocumentEntity>,
    isGridView: Boolean,
    emptyTitle: String,
    emptyMessage: String,
    onDocumentClick: (PdfDocumentEntity) -> Unit,
    onToggleFavorite: (PdfDocumentEntity) -> Unit,
    onDeleteDocument: (PdfDocumentEntity) -> Unit,
    onShowDetails: (PdfDocumentEntity) -> Unit
) {
    if (documents.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(96.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = BrandIndigo
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = emptyTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    } else if (isGridView) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(documents, key = { it.uri }) { doc ->
                DocumentGridCard(
                    document = doc,
                    onClick = { onDocumentClick(doc) },
                    onToggleFavorite = { onToggleFavorite(doc) },
                    onDelete = { onDeleteDocument(doc) },
                    onDetails = { onShowDetails(doc) }
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(documents, key = { it.uri }) { doc ->
                DocumentListItem(
                    document = doc,
                    onClick = { onDocumentClick(doc) },
                    onToggleFavorite = { onToggleFavorite(doc) },
                    onDelete = { onDeleteDocument(doc) },
                    onDetails = { onShowDetails(doc) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentListItem(
    document: PdfDocumentEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .testTag("doc_item_${document.name}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PDF Icon / Badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(BrandIndigo.copy(alpha = 0.15f), BrandTeal.copy(alpha = 0.15f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = BrandIndigo,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${document.pageCount} pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = Formatter.formatFileSize(context, document.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (document.lastReadPage > 1) {
                        Text(
                            text = " • p.${document.lastReadPage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandTeal,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Favorite button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (document.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (document.isFavorite) "Remove favorite" else "Add favorite",
                    tint = if (document.isFavorite) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline
                )
            }

            // More Options
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Document options",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open") },
                        leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Details") },
                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onDetails()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from recent", color = Color(0xFFEF4444)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentGridCard(
    document: PdfDocumentEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .testTag("doc_grid_${document.name}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Document Preview Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(BrandIndigo.copy(alpha = 0.15f), BrandTeal.copy(alpha = 0.2f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = BrandIndigo,
                    modifier = Modifier.size(44.dp)
                )

                // Page count badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "${document.pageCount}p",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Document Name
            Text(
                text = document.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Document Size & Favorite Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = Formatter.formatFileSize(context, document.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (document.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (document.isFavorite) Color(0xFFEF4444) else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryTabContent(
    folders: List<LibraryFolder>,
    files: List<FolderPdf>,
    inaccessibleFolderUris: Set<String>,
    selectedFolderUri: String?,
    isScanning: Boolean,
    isFiltering: Boolean,
    onAddFolder: () -> Unit,
    onManageFolders: () -> Unit,
    onRefresh: () -> Unit,
    onSelectFolder: (String?) -> Unit,
    onFileClick: (FolderPdf) -> Unit
) {
    if (folders.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = BrandTeal
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Add your PDF folders",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose one or more folders (Downloads, Documents, SD card...). All PDFs inside them, including sub-folders, will be listed here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAddFolder,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("library_add_folder")
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choose folder", fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = selectedFolderUri == null,
                        onClick = { onSelectFolder(null) },
                        label = { Text("All folders") }
                    )
                }
                items(folders, key = { it.uri }) { folder ->
                    FilterChip(
                        selected = selectedFolderUri == folder.uri,
                        onClick = { onSelectFolder(folder.uri) },
                        label = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = if (folder.uri in inaccessibleFolderUris) {
                            {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Folder not accessible",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null
                    )
                }
            }
            IconButton(onClick = onRefresh, enabled = !isScanning) {
                Icon(Icons.Default.Refresh, contentDescription = "Rescan folders")
            }
            IconButton(onClick = onManageFolders, modifier = Modifier.testTag("library_manage_folders")) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = "Manage folders")
            }
        }

        if (isScanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        isScanning -> "Scanning folders..."
                        isFiltering -> "No matching PDFs"
                        else -> "No PDF files found in the selected folders."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("library_list")
            ) {
                items(files, key = { it.uri }) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(onClick = { onFileClick(file) })
                            .testTag("library_item_${file.name}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = BrandIndigo,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sizeText = Formatter.formatShortFileSize(context, file.sizeBytes)
                                val dateText = if (file.lastModified > 0) dateFormat.format(Date(file.lastModified)) else null
                                Text(
                                    text = listOfNotNull(file.folderName, sizeText, dateText).joinToString("  •  "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageFoldersDialog(
    folders: List<LibraryFolder>,
    inaccessibleFolderUris: Set<String>,
    onAddFolder: () -> Unit,
    onRemoveFolder: (LibraryFolder) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = BrandTeal) },
        title = { Text("PDF folders") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (folders.isEmpty()) {
                    Text(
                        "No folders added yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                folders.forEach { folder ->
                    val inaccessible = folder.uri in inaccessibleFolderUris
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (inaccessible) {
                                Text(
                                    text = "Can't access this folder. Remove it and add it again.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        IconButton(onClick = { onRemoveFolder(folder) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove ${folder.name}")
                        }
                    }
                }
                Text(
                    text = "Removing a folder only stops the app from listing it. Your files are not deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAddFolder) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add folder", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
