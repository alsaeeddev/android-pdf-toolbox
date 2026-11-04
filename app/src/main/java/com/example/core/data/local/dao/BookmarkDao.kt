package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE documentUri = :documentUri ORDER BY pageNumber ASC")
    fun getBookmarksForDocument(documentUri: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("DELETE FROM bookmarks WHERE documentUri = :documentUri AND pageNumber = :pageNumber")
    suspend fun deleteBookmarkByPage(documentUri: String, pageNumber: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE documentUri = :documentUri AND pageNumber = :pageNumber)")
    fun isPageBookmarked(documentUri: String, pageNumber: Int): Flow<Boolean>
}
