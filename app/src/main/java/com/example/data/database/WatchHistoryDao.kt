package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT 50")
    fun getWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToHistory(history: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE publicId = :publicId")
    suspend fun deleteFromHistory(publicId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 20")
    fun getSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(searchQuery: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}
