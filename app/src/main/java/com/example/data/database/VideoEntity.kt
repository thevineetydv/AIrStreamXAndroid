package com.example.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.network.Video

@Entity(
    tableName = "videos",
    indices = [Index(value = ["publicId"], unique = true)]
)
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val publicId: String,
    val title: String,
    val thumbnail: String,
    val url: String,
    val views: Int,
    val duration: Double,
    val channelName: String,
    val avatarUrl: String,
    val handle: String,
    val uploaderEmail: String,
    val createdAt: String,
    val likes: Int = 0,
    val dislikes: Int = 0,
    val isLiked: Boolean = false,
    val isDisliked: Boolean = false,
    val subscriberCount: Int = 0,
    val description: String = ""
) {
    fun isShort(): Boolean = duration <= 60.0

    fun toVideo() = Video(
        id = id,
        publicId = publicId,
        title = title,
        thumbnail = thumbnail,
        url = url,
        views = views,
        duration = duration,
        channelName = channelName,
        avatarUrl = avatarUrl,
        handle = handle,
        uploaderEmail = uploaderEmail,
        createdAt = createdAt,
        likes = likes,
        dislikes = dislikes,
        isLiked = isLiked,
        isDisliked = isDisliked,
        subscriberCount = subscriberCount,
        description = description
    )
}

fun Video.toEntity() = VideoEntity(
    id = if (id > 0) id else 0,
    publicId = publicId,
    title = title,
    thumbnail = thumbnail,
    url = url,
    views = views,
    duration = duration,
    channelName = channelName,
    avatarUrl = avatarUrl,
    handle = handle,
    uploaderEmail = uploaderEmail,
    createdAt = createdAt,
    likes = likes,
    dislikes = dislikes,
    isLiked = isLiked,
    isDisliked = isDisliked,
    subscriberCount = subscriberCount,
    description = description
)