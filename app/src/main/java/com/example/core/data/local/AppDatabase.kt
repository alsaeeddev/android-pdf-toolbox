package com.example.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.core.data.local.dao.BookmarkDao
import com.example.core.data.local.dao.PdfDocumentDao
import com.example.core.data.local.dao.SignatureDao
import com.example.core.data.local.entity.BookmarkEntity
import com.example.core.data.local.entity.PdfDocumentEntity
import com.example.core.data.local.entity.SignatureEntity

@Database(
    entities = [
        PdfDocumentEntity::class,
        BookmarkEntity::class,
        SignatureEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pdfDocumentDao(): PdfDocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun signatureDao(): SignatureDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "myprev_database"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
