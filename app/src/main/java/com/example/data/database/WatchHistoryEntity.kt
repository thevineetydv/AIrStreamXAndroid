package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val publicId: String, // e.g. "dQw4w9WgXcQ"
    val title: String,
    val thumbnail: String,
    val channelName: String,
    val handle: String,
    val watchedAt: Long = System.currentTimeMillis()
)
