package com.example.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.data.local.AppDatabase
import com.example.core.data.local.dao.BookmarkDao
import com.example.core.data.local.dao.PdfDocumentDao
import com.example.core.data.local.dao.SignatureDao
import com.example.core.data.preferences.UserPreferencesRepository
import com.example.core.data.repository.LibraryRepository
import com.example.core.data.repository.PdfDocumentRepository

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "myprev_preferences")

interface AppContainer {
    val context: Context
    val preferencesDataStore: DataStore<Preferences>
    val userPreferencesRepository: UserPreferencesRepository
    val database: AppDatabase
    val pdfDocumentDao: PdfDocumentDao
    val bookmarkDao: BookmarkDao
    val signatureDao: SignatureDao
    val pdfDocumentRepository: PdfDocumentRepository
    val libraryRepository: LibraryRepository
}

class DefaultAppContainer(override val context: Context) : AppContainer {

    override val preferencesDataStore: DataStore<Preferences> by lazy {
        context.dataStore
    }

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(preferencesDataStore)
    }

    override val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    override val pdfDocumentDao: PdfDocumentDao by lazy {
        database.pdfDocumentDao()
    }

    override val bookmarkDao: BookmarkDao by lazy {
        database.bookmarkDao()
    }

    override val signatureDao: SignatureDao by lazy {
        database.signatureDao()
    }

    override val pdfDocumentRepository: PdfDocumentRepository by lazy {
        PdfDocumentRepository(context, pdfDocumentDao)
    }

    override val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(context, userPreferencesRepository)
    }
}
