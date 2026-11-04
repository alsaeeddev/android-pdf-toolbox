package com.example.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val lastOpenedTimestamp: Long = System.currentTimeMillis(),
    val lastReadPage: Int = 1,
    val isFavorite: Boolean = false,
    val isLocalSample: Boolean = false
)
