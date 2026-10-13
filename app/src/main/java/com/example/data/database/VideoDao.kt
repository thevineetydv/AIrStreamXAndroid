package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY id DESC")
    fun getAllVideosFlow(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos ORDER BY id DESC")
    suspend fun getAllVideos(): List<VideoEntity>

    @Query("""
        SELECT * FROM videos 
        WHERE title LIKE '%' || :query || '%'
           OR channelName LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
        ORDER BY id DESC
    """)
    suspend fun searchVideos(query: String): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE publicId = :publicId LIMIT 1")
    suspend fun getVideoByPublicId(publicId: String): VideoEntity?

    @Query("UPDATE videos SET isLiked = :isLiked, likes = :likes WHERE publicId = :publicId")
    suspend fun updateLikeState(publicId: String, isLiked: Boolean, likes: Int)

    @Query("UPDATE videos SET isDisliked = :isDisliked, dislikes = :dislikes WHERE publicId = :publicId")
    suspend fun updateDislikeState(publicId: String, isDisliked: Boolean, dislikes: Int)

    @Query("UPDATE videos SET views = :views WHERE publicId = :publicId")
    suspend fun updateViewCount(publicId: String, views: Int)

    @Query("SELECT * FROM videos GROUP BY handle")
    suspend fun getDistinctChannelVideos(): List<VideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideos(videos: List<VideoEntity>)

    // Deletes any locally cached video whose publicId is no longer
    // present in the latest server response.
    @Query("DELETE FROM videos WHERE publicId NOT IN (:currentPublicIds)")
    suspend fun deleteVideosNotIn(currentPublicIds: List<String>)

    // NEW: same idea, but scoped to a single channel's handle so
    // syncing one channel doesn't wipe out other channels' videos.
    @Query("DELETE FROM videos WHERE handle = :handle AND publicId NOT IN (:currentPublicIds)")
    suspend fun deleteVideosByHandleNotIn(handle: String, currentPublicIds: List<String>)

    @Query("DELETE FROM videos WHERE publicId = :publicId")
    suspend fun deleteVideoByPublicId(publicId: String)

    @Query("DELETE FROM videos")
    suspend fun clearAll()

    @Transaction
    suspend fun syncVideos(freshVideos: List<VideoEntity>) {
        insertVideos(freshVideos)
        if (freshVideos.isNotEmpty()) {
            deleteVideosNotIn(freshVideos.map { it.publicId })
        } else {
            clearAll()
        }
    }

    // NEW: scoped transactional sync for a single channel's video list —
    // used by getChannelInfo() so refreshing one channel doesn't touch
    // videos belonging to other channels.
    @Transaction
    suspend fun syncChannelVideos(handle: String, freshVideos: List<VideoEntity>) {
        insertVideos(freshVideos)
        deleteVideosByHandleNotIn(handle, freshVideos.map { it.publicId })
    }
}