package com.example.feature_home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.data.local.entity.PdfDocumentEntity
import com.example.core.data.preferences.LibraryFolder
import com.example.core.data.repository.FolderPdf
import com.example.core.data.repository.LibraryRepository
import com.example.core.data.repository.PdfDocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) {
    DATE_DESC("Recent first"),
    NAME_ASC("Name (A to Z)"),
    SIZE_DESC("Size (Large to small)")
}

data class HomeUiState(
    val recentDocuments: List<PdfDocumentEntity> = emptyList(),
    val favoriteDocuments: List<PdfDocumentEntity> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val isGridView: Boolean = false,
    val isLoading: Boolean = false,
    val userMessage: String? = null,
    // "All PDFs" tab
    val libraryFolders: List<LibraryFolder> = emptyList(),
    val libraryFiles: List<FolderPdf> = emptyList(),
    val inaccessibleFolderUris: Set<String> = emptySet(),
    val selectedFolderUri: String? = null,
    val isScanning: Boolean = false
)

private data class LibraryState(
    val folders: List<LibraryFolder>,
    val scan: com.example.core.data.repository.LibraryScanResult,
    val isScanning: Boolean
)

private data class ViewOptions(
    val query: String,
    val isSearch: Boolean,
    val sort: SortOrder,
    val isGrid: Boolean,
    val selectedFolderUri: String?
)

private data class Flags(
    val isLoading: Boolean,
    val userMessage: String?
)

class HomeViewModel(
    private val repository: PdfDocumentRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    private val _isGridView = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(false)
    private val _userMessage = MutableStateFlow<String?>(null)
    private val _selectedFolderUri = MutableStateFlow<String?>(null)
    private val _isScanning = MutableStateFlow(false)
    private val _scanResult = MutableStateFlow(
        com.example.core.data.repository.LibraryScanResult(emptyList(), emptySet())
    )

    private val libraryFolders: StateFlow<List<LibraryFolder>> = libraryRepository.foldersFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val contentFlow = combine(repository.allRecentFlow, repository.allFavoritesFlow) { recent, favorites ->
        Pair(recent, favorites)
    }

    private val libraryFlow = combine(libraryFolders, _scanResult, _isScanning) { folders, scan, scanning ->
        LibraryState(folders, scan, scanning)
    }

    private val optionsFlow = combine(
        _searchQuery, _isSearchActive, _sortOrder, _isGridView, _selectedFolderUri
    ) { query, isSearch, sort, isGrid, folderUri ->
        ViewOptions(query, isSearch, sort, isGrid, folderUri)
    }

    private val flagsFlow = combine(_isLoading, _userMessage) { loading, message ->
        Flags(loading, message)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        contentFlow, libraryFlow, optionsFlow, flagsFlow
    ) { (recent, favorites), library, options, flags ->
        val query = options.query
        val filteredRecent = if (query.isBlank()) recent else recent.filter {
            it.name.contains(query, ignoreCase = true)
        }
        val sortedRecent = when (options.sort) {
            SortOrder.DATE_DESC -> filteredRecent.sortedByDescending { it.lastOpenedTimestamp }
            SortOrder.NAME_ASC -> filteredRecent.sortedBy { it.name.lowercase() }
            SortOrder.SIZE_DESC -> filteredRecent.sortedByDescending { it.sizeBytes }
        }

        val filteredFavs = if (query.isBlank()) favorites else favorites.filter {
            it.name.contains(query, ignoreCase = true)
        }

        // If the selected folder was removed, fall back to "All folders".
        val selectedFolder = options.selectedFolderUri?.takeIf { sel -> library.folders.any { it.uri == sel } }
        val filteredLibrary = library.scan.files
            .filter { selectedFolder == null || it.folderUri == selectedFolder }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        val sortedLibrary = when (options.sort) {
            SortOrder.DATE_DESC -> filteredLibrary.sortedByDescending { it.lastModified }
            SortOrder.NAME_ASC -> filteredLibrary.sortedBy { it.name.lowercase() }
            SortOrder.SIZE_DESC -> filteredLibrary.sortedByDescending { it.sizeBytes }
        }

        HomeUiState(
            recentDocuments = sortedRecent,
            favoriteDocuments = filteredFavs,
            searchQuery = query,
            isSearchActive = options.isSearch,
            sortOrder = options.sort,
            isGridView = options.isGrid,
            isLoading = flags.isLoading,
            userMessage = flags.userMessage,
            libraryFolders = library.folders,
            libraryFiles = sortedLibrary,
            inaccessibleFolderUris = library.scan.inaccessibleFolderUris,
            selectedFolderUri = selectedFolder,
            isScanning = library.isScanning
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    init {
        // Automatically seed the User Guide if the database is fresh
        viewModelScope.launch {
            _isLoading.value = true
            repository.seedSampleDocument()
            _isLoading.value = false
        }

        // Re-scan automatically whenever the folder list changes (including on app start).
        viewModelScope.launch {
            libraryFolders.collectLatest { folders -> scanLibrary(folders) }
        }
    }

    private suspend fun scanLibrary(folders: List<LibraryFolder>) {
        if (folders.isEmpty()) {
            _scanResult.value = com.example.core.data.repository.LibraryScanResult(emptyList(), emptySet())
            return
        }
        _isScanning.value = true
        try {
            _scanResult.value = libraryRepository.scan(folders)
        } finally {
            _isScanning.value = false
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch { scanLibrary(libraryFolders.value) }
    }

    fun addLibraryFolder(treeUri: Uri) {
        viewModelScope.launch {
            try {
                val added = libraryRepository.addFolder(treeUri, libraryFolders.value)
                _userMessage.value = if (added) "Folder added" else "This folder is already added"
            } catch (e: Exception) {
                _userMessage.value = "Couldn't add folder: ${e.localizedMessage ?: "access denied"}"
            }
        }
    }

    fun removeLibraryFolder(folder: LibraryFolder) {
        viewModelScope.launch {
            libraryRepository.removeFolder(folder)
            if (_selectedFolderUri.value == folder.uri) _selectedFolderUri.value = null
            _userMessage.value = "Removed folder ${folder.name}"
        }
    }

    fun selectLibraryFolder(uri: String?) {
        _selectedFolderUri.value = uri
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleSearchActive() {
        val next = !_isSearchActive.value
        _isSearchActive.value = next
        if (!next) _searchQuery.value = ""
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _sortOrder.value = sortOrder
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    fun toggleFavorite(document: PdfDocumentEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(document.uri, !document.isFavorite)
        }
    }

    fun deleteDocument(document: PdfDocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(document.uri)
            _userMessage.value = "Removed ${document.name}"
        }
    }

    fun openDocumentUri(uri: Uri, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.openPdf(uri)
            _isLoading.value = false
            if (result.isSuccess) {
                val entity = result.getOrThrow()
                onSuccess(entity.uri)
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Unable to read PDF"
                _userMessage.value = errorMsg
                onError(errorMsg)
            }
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    companion object {
        fun provideFactory(
            repository: PdfDocumentRepository,
            libraryRepository: LibraryRepository
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(repository, libraryRepository) as T
                }
            }
    }
}
