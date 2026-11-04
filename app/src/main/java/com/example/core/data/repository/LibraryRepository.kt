package com.example.core.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.example.core.data.preferences.LibraryFolder
import com.example.core.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** A PDF found while scanning one of the library folders. */
data class FolderPdf(
    val uri: String,
    val documentId: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val folderName: String,
    val folderUri: String
)

data class LibraryScanResult(
    val files: List<FolderPdf>,
    /** Folder URIs that could not be read (permission revoked, folder deleted, SD card removed). */
    val inaccessibleFolderUris: Set<String>
)

/**
 * Manages the folders behind the "All PDFs" tab and scans them for PDF files.
 *
 * Uses the Storage Access Framework (OpenDocumentTree), so no storage permission
 * (MANAGE_EXTERNAL_STORAGE etc.) is needed.
 */
class LibraryRepository(
    private val context: Context,
    private val preferences: UserPreferencesRepository
) {
    val foldersFlow: Flow<List<LibraryFolder>> = preferences.libraryFoldersFlow

    /** Persists access to [treeUri] and adds it to the library. Returns false if it was already added. */
    suspend fun addFolder(treeUri: Uri, existing: List<LibraryFolder>): Boolean {
        if (existing.any { it.uri == treeUri.toString() }) return false

        val resolver = context.contentResolver
        try {
            resolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Some providers only allow read access.
            resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        preferences.addLibraryFolder(LibraryFolder(treeUri.toString(), resolveFolderName(treeUri)))
        return true
    }

    suspend fun removeFolder(folder: LibraryFolder) {
        preferences.removeLibraryFolder(folder.uri)
        try {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(folder.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Permission was already gone; nothing to release.
        }
    }

    /** Scans every folder (and its sub-folders) for PDFs. */
    suspend fun scan(folders: List<LibraryFolder>): LibraryScanResult = withContext(Dispatchers.IO) {
        val granted = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()

        val found = LinkedHashMap<String, FolderPdf>() // de-duplicated by document id
        val inaccessible = mutableSetOf<String>()

        for (folder in folders) {
            if (folder.uri !in granted) {
                inaccessible += folder.uri
                continue
            }
            try {
                val treeUri = Uri.parse(folder.uri)
                val rootId = DocumentsContract.getTreeDocumentId(treeUri)
                scanDirectory(treeUri, rootId, folder, depth = 0, out = found)
            } catch (_: Exception) {
                inaccessible += folder.uri
            }
        }
        LibraryScanResult(found.values.toList(), inaccessible)
    }

    private fun scanDirectory(
        treeUri: Uri,
        parentDocId: String,
        folder: LibraryFolder,
        depth: Int,
        out: MutableMap<String, FolderPdf>
    ) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val subDirectories = mutableListOf<String>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (out.size >= MAX_FILES) break
                val docId = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2)

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    // Skip hidden folders (.thumbnails, .trash, ...)
                    if (!name.startsWith(".")) subDirectories += docId
                } else if (mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)) {
                    if (out.containsKey(docId)) continue
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    out[docId] = FolderPdf(
                        uri = documentUri.toString(),
                        documentId = docId,
                        name = name,
                        sizeBytes = if (cursor.isNull(3)) 0L else cursor.getLong(3),
                        lastModified = if (cursor.isNull(4)) 0L else cursor.getLong(4),
                        folderName = folder.name,
                        folderUri = folder.uri
                    )
                }
            }
        }

        subDirectories.forEach { scanDirectory(treeUri, it, folder, depth + 1, out) }
    }

    private fun resolveFolderName(treeUri: Uri): String {
        return try {
            val rootId = DocumentsContract.getTreeDocumentId(treeUri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
                ?: fallbackName(treeUri)
        } catch (_: Exception) {
            fallbackName(treeUri)
        }
    }

    private fun fallbackName(treeUri: Uri): String {
        val id = try { DocumentsContract.getTreeDocumentId(treeUri) } catch (_: Exception) { "" }
        return id.substringAfterLast('/').substringAfterLast(':').ifBlank { "Folder" }
    }

    private companion object {
        const val MAX_DEPTH = 10
        const val MAX_FILES = 5000
    }
}
