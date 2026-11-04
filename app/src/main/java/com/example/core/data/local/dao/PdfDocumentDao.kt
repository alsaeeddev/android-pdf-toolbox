package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.data.local.entity.PdfDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDocumentDao {

    @Query("SELECT * FROM pdf_documents ORDER BY lastOpenedTimestamp DESC")
    fun getAllRecent(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE isFavorite = 1 ORDER BY lastOpenedTimestamp DESC")
    fun getAllFavorites(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE name LIKE '%' || :query || '%' ORDER BY lastOpenedTimestamp DESC")
    fun searchDocuments(query: String): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE uri = :uri LIMIT 1")
    suspend fun getDocumentByUri(uri: String): PdfDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(document: PdfDocumentEntity)

    @Update
    suspend fun updateDocument(document: PdfDocumentEntity)

    @Query("UPDATE pdf_documents SET isFavorite = :isFavorite WHERE uri = :uri")
    suspend fun setFavorite(uri: String, isFavorite: Boolean)

    @Query("UPDATE pdf_documents SET lastReadPage = :page, lastOpenedTimestamp = :timestamp WHERE uri = :uri")
    suspend fun updateLastReadPage(uri: String, page: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM pdf_documents WHERE uri = :uri")
    suspend fun deleteDocument(uri: String)

    @Query("DELETE FROM pdf_documents")
    suspend fun clearAll()
}
