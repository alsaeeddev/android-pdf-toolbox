package com.example.core.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.core.data.local.dao.PdfDocumentDao
import com.example.core.data.local.entity.PdfDocumentEntity
import com.example.core.pdf.PdfEngine
import kotlinx.coroutines.flow.Flow

class PdfDocumentRepository(
    private val context: Context,
    private val pdfDocumentDao: PdfDocumentDao
) {
    val allRecentFlow: Flow<List<PdfDocumentEntity>> = pdfDocumentDao.getAllRecent()
    val allFavoritesFlow: Flow<List<PdfDocumentEntity>> = pdfDocumentDao.getAllFavorites()

    fun searchDocuments(query: String): Flow<List<PdfDocumentEntity>> {
        return pdfDocumentDao.searchDocuments(query)
    }

    suspend fun getDocumentByUri(uri: String): PdfDocumentEntity? {
        return pdfDocumentDao.getDocumentByUri(uri)
    }

    suspend fun openPdf(uri: Uri, forceRefresh: Boolean = false): Result<PdfDocumentEntity> {
        return try {
            // Persistable permission lo (sirf SAF/OpenDocument URIs par kaam karti hai).
            // ACTION_VIEW / ACTION_SEND wale URIs persist nahi hote, un ke liye neeche local copy bani hai.
            if (uri.scheme == "content") {
                val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, rw)
                } catch (_: Exception) {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) { /* non-persistable URI, local copy use hogi */ }
                }
            }

            val inspectResult = PdfEngine.inspectPdf(context, uri, forceRefresh)
            if (inspectResult.isSuccess) {
                val entity = inspectResult.getOrThrow()
                val existing = pdfDocumentDao.getDocumentByUri(entity.uri)
                val toSave = if (existing != null) {
                    entity.copy(
                        // Protected PDF par page count maloom nahi hota (0): purani value rakho
                        pageCount = if (entity.pageCount > 0) entity.pageCount else existing.pageCount,
                        // Permission expire hone par metadata query fail ho sakti hai, isliye purana naam rakho
                        name = if (entity.name == "Document.pdf") existing.name else entity.name,
                        isFavorite = existing.isFavorite,
                        lastReadPage = existing.lastReadPage,
                        lastOpenedTimestamp = System.currentTimeMillis()
                    )
                } else {
                    entity
                }
                pdfDocumentDao.upsertDocument(toSave)
                Result.success(toSave)
            } else {
                inspectResult
            }
        } catch (se: SecurityException) {
            Result.failure(SecurityException("Permission denied to open this document.", se))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(uri: String, isFavorite: Boolean) {
        pdfDocumentDao.setFavorite(uri, isFavorite)
    }

    suspend fun updateLastReadPage(uri: String, page: Int) {
        pdfDocumentDao.updateLastReadPage(uri, page)
    }

    suspend fun updatePageCount(uri: String, pageCount: Int) {
        val doc = pdfDocumentDao.getDocumentByUri(uri) ?: return
        if (doc.pageCount != pageCount) pdfDocumentDao.upsertDocument(doc.copy(pageCount = pageCount))
    }

    suspend fun deleteDocument(uri: String) {
        pdfDocumentDao.deleteDocument(uri)
    }

    suspend fun seedSampleDocument(): PdfDocumentEntity? {
        return try {
            val sampleUri = PdfEngine.createSampleGuidePdf(context)
            val result = openPdf(sampleUri)
            if (result.isSuccess) {
                val doc = result.getOrThrow().copy(isLocalSample = true, name = "myPrev User Guide.pdf")
                pdfDocumentDao.upsertDocument(doc)
                doc
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}