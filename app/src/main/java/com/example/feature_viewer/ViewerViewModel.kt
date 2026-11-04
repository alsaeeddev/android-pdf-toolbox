package com.example.feature_viewer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.data.local.dao.BookmarkDao
import com.example.core.data.local.entity.BookmarkEntity
import com.example.core.data.preferences.ReadingMode
import com.example.core.data.preferences.UserPreferencesRepository
import com.example.core.data.preferences.ViewMode
import com.example.core.data.repository.PdfDocumentRepository
import com.example.core.pdf.PdfPasswordStore
import com.example.core.pdf.PdfRenderSession
import com.example.core.pdf.isPdfPasswordError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ViewerUiState(
    val uriString: String = "",
    val fileName: String = "Document.pdf",
    val pageCount: Int = 0,
    val currentPage: Int = 1,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isPermissionError: Boolean = false,
    val isPasswordRequired: Boolean = false,
    val passwordError: String? = null,
    val isUnlocking: Boolean = false,
    val readingMode: ReadingMode = ReadingMode.NORMAL,
    val viewMode: ViewMode = ViewMode.CONTINUOUS_VERTICAL,
    val keepScreenOn: Boolean = true,
    val isCurrentPageBookmarked: Boolean = false,
    val bookmarks: List<BookmarkEntity> = emptyList()
)

class ViewerViewModel(
    private val context: Context,
    private var uriString: String,
    private val pdfRepository: PdfDocumentRepository,
    private val bookmarkDao: BookmarkDao,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ViewerUiState(uriString = uriString)
    )
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    private var renderSession: PdfRenderSession? = null
    private var savePageJob: Job? = null

    init {
        loadDocument()
        observePreferences()
        observeBookmarks()
    }

    private fun loadDocument() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                isPermissionError = false,
                isPasswordRequired = false,
                passwordError = null
            )
            try {
                val uri = Uri.parse(uriString)

                val openResult = pdfRepository.openPdf(uri)
                if (openResult.isFailure) {
                    showOpenFailure(openResult.exceptionOrNull())
                    return@launch
                }

                val docEntity = openResult.getOrNull()
                // Is session me pehle password diya gaya ho to dobara na poocho.
                val session = PdfRenderSession(context, uri)
                val renderResult = session.open(PdfPasswordStore.get(uriString))

                if (renderResult.isSuccess) {
                    onSessionOpened(session, renderResult.getOrThrow(), docEntity?.lastReadPage, docEntity?.name)
                } else {
                    val ex = renderResult.exceptionOrNull()
                    if (ex != null && ex.isPdfPasswordError()) {
                        session.close()
                        PdfPasswordStore.remove(uriString)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isPasswordRequired = true,
                            passwordError = null,
                            errorMessage = null
                        )
                    } else {
                        showOpenFailure(ex)
                    }
                }
            } catch (se: SecurityException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isPermissionError = true,
                    errorMessage = "Permission to access this document was denied or has expired. Tap below to re-open the file from storage."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.localizedMessage ?: "Unexpected error reading PDF"
                )
            }
        }
    }

    private fun showOpenFailure(ex: Throwable?) {
        val isPerm = ex is SecurityException || (ex?.localizedMessage?.lowercase()?.contains("permission") == true)
        val msg = if (isPerm) {
            "Permission to access this document was denied or has expired. Tap below to re-open the file from storage."
        } else {
            ex?.localizedMessage ?: "Failed to open PDF document"
        }
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isPermissionError = isPerm,
            errorMessage = msg
        )
    }

    private suspend fun onSessionOpened(
        session: PdfRenderSession,
        totalPages: Int,
        savedLastPage: Int?,
        savedName: String?
    ) {
        renderSession?.close()
        renderSession = session

        // Retrieve saved last-read page from Room
        val initialPage = savedLastPage?.coerceIn(1, totalPages.coerceAtLeast(1)) ?: 1
        val displayName = savedName ?: Uri.parse(uriString).lastPathSegment ?: "Document.pdf"

        _uiState.value = _uiState.value.copy(
            fileName = displayName,
            pageCount = totalPages,
            currentPage = initialPage,
            isLoading = false,
            isPasswordRequired = false,
            passwordError = null,
            isUnlocking = false
        )

        // Update page count (protected PDFs me pehle 0 hota hai) aur last read page
        pdfRepository.updatePageCount(uriString, totalPages)
        pdfRepository.updateLastReadPage(uriString, initialPage)
    }

    /** Password dialog se "Unlock" dabane par. */
    fun submitPassword(password: String) {
        if (password.isEmpty() || _uiState.value.isUnlocking) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUnlocking = true, passwordError = null)
            try {
                val uri = Uri.parse(uriString)
                val session = PdfRenderSession(context, uri)
                val result = session.open(password)
                if (result.isSuccess) {
                    PdfPasswordStore.put(uriString, password)
                    val doc = pdfRepository.getDocumentByUri(uriString)
                    onSessionOpened(session, result.getOrThrow(), doc?.lastReadPage, doc?.name)
                } else {
                    session.close()
                    val ex = result.exceptionOrNull()
                    if (ex != null && ex.isPdfPasswordError()) {
                        _uiState.value = _uiState.value.copy(
                            isUnlocking = false,
                            passwordError = "Incorrect password. Please try again."
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isUnlocking = false,
                            isPasswordRequired = false,
                            errorMessage = ex?.localizedMessage ?: "Failed to open PDF document"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUnlocking = false,
                    isPasswordRequired = false,
                    errorMessage = e.localizedMessage ?: "Failed to unlock PDF"
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

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesRepository.userPreferencesFlow.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    readingMode = prefs.readingMode,
                    viewMode = prefs.defaultViewMode,
                    keepScreenOn = prefs.keepScreenOn
                )
            }
        }
    }

    private fun observeBookmarks() {
        viewModelScope.launch {
            bookmarkDao.getBookmarksForDocument(uriString).collect { list ->
                val isBookmarked = list.any { it.pageNumber == _uiState.value.currentPage }
                _uiState.value = _uiState.value.copy(
                    bookmarks = list,
                    isCurrentPageBookmarked = isBookmarked
                )
            }
        }
    }

    suspend fun renderPageBitmap(pageIndex: Int, targetWidth: Int = 1080): Bitmap? {
        return renderSession?.renderPage(pageIndex, targetWidth)
    }

    /**
     * Unified event when the visible page in the viewport changes.
     * Updates the single source of truth (currentPage) immediately and debounces saving to Room by ~500ms.
     */
    fun onVisiblePageChanged(newPage: Int) {
        val total = _uiState.value.pageCount
        if (total <= 0) return
        val coerced = newPage.coerceIn(1, total)
        if (coerced != _uiState.value.currentPage) {
            val isBookmarked = _uiState.value.bookmarks.any { it.pageNumber == coerced }
            _uiState.value = _uiState.value.copy(
                currentPage = coerced,
                isCurrentPageBookmarked = isBookmarked
            )
            // Debounce saving last-read page to Room persistence
            savePageJob?.cancel()
            savePageJob = viewModelScope.launch {
                delay(500)
                pdfRepository.updateLastReadPage(uriString, coerced)
            }
        }
    }

    fun onPageChanged(newPage: Int) {
        onVisiblePageChanged(newPage)
    }

    fun setReadingMode(mode: ReadingMode) {
        viewModelScope.launch {
            preferencesRepository.setReadingMode(mode)
        }
    }

    fun setViewMode(mode: ViewMode) {
        viewModelScope.launch {
            preferencesRepository.setDefaultViewMode(mode)
        }
    }

    fun toggleBookmarkCurrentPage() {
        viewModelScope.launch {
            val current = _uiState.value.currentPage
            val isCurrentlyBookmarked = _uiState.value.isCurrentPageBookmarked
            if (isCurrentlyBookmarked) {
                bookmarkDao.deleteBookmarkByPage(uriString, current)
            } else {
                bookmarkDao.insertBookmark(
                    BookmarkEntity(
                        documentUri = uriString,
                        pageNumber = current,
                        title = "Page $current"
                    )
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        savePageJob?.cancel()
        renderSession?.close()
    }

    companion object {
        fun provideFactory(
            context: Context,
            uriString: String,
            pdfRepository: PdfDocumentRepository,
            bookmarkDao: BookmarkDao,
            preferencesRepository: UserPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ViewerViewModel(
                    context,
                    uriString,
                    pdfRepository,
                    bookmarkDao,
                    preferencesRepository
                ) as T
            }
        }
    }
}
