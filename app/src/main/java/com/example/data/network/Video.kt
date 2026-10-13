package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Video(
    @Json(name = "id") val id: Int,
    @Json(name = "public_id") val publicId: String,
    @Json(name = "title") val title: String,
    @Json(name = "thumbnail") val thumbnail: String,
    @Json(name = "url") val url: String, // HLS stream M3U8 URL
    @Json(name = "views") val views: Int,
    @Json(name = "duration") val duration: Double, // duration in seconds
    @Json(name = "channel_name") val channelName: String,
    @Json(name = "avatar_url") val avatarUrl: String,
    @Json(name = "handle") val handle: String,
    @Json(name = "uploader_email") val uploaderEmail: String,
    @Json(name = "created_at") val createdAt: String,
    val likes: Int = 0,
    val dislikes: Int = 0,
    val isLiked: Boolean = false,
    val isDisliked: Boolean = false,
    val subscriberCount: Int = 0,
    val description: String = ""
) {
    fun isShort(): Boolean = duration <= 60.0
}

@JsonClass(generateAdapter = true)
data class NetworkVideo(
    @Json(name = "id") val id: Int?,
    @Json(name = "public_id") val publicId: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "thumbnail") val thumbnail: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "views") val views: Int? = null,
    @Json(name = "duration") val duration: Double? = null,
    @Json(name = "channel_name") val channelName: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "handle") val handle: String? = null,
    @Json(name = "uploader_email") val uploaderEmail: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "description") val description: String? = null
) {
    fun toVideo(): Video {
        val email = uploaderEmail ?: "anonymous@gmail.com"
        val fallbackHandle = email.substringBefore("@")
        val fallbackChannel = fallbackHandle.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val generatedPublicId = publicId ?: "video_${id ?: System.currentTimeMillis()}"
        return Video(
            id = id ?: 0,
            publicId = generatedPublicId,
            title = title ?: "Untitled Video",
            thumbnail = thumbnail ?: "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?w=800&auto=format&fit=crop",
            url = url ?: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            views = views ?: 1420,
            duration = duration ?: 300.0,
            channelName = channelName ?: fallbackChannel,
            avatarUrl = avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&auto=format&fit=crop",
            handle = if (!handle.isNullOrBlank()) handle else fallbackHandle,
            uploaderEmail = email,
            createdAt = createdAt ?: "A while ago",
            likes = 0,
            dislikes = 0,
            subscriberCount = 0,
            description = description ?: ""
        )
    }
}

@JsonClass(generateAdapter = true)
data class ChannelInfo(
    val channelName: String,
    val handle: String,
    val avatarUrl: String,
    val uploaderEmail: String,
    val subscriberCount: Int = 2240,
    val videos: List<Video> = emptyList()
)

@JsonClass(generateAdapter = true)
data class NetworkChannelInfo(
    @Json(name = "channel_name") val channelName: String? = null,
    @Json(name = "handle") val handle: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "uploader_email") val uploaderEmail: String? = null,
    @Json(name = "subscriber_count") val subscriberCount: Int? = null,
    @Json(name = "videos") val videos: List<NetworkVideo>? = null
) {
    fun toChannelInfo(localVideos: List<Video> = emptyList()): ChannelInfo {
        val email = uploaderEmail ?: "anonymous@gmail.com"
        val fallbackHandle = handle ?: email.substringBefore("@")
        val fallbackChannelName = channelName ?: fallbackHandle.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val mappedVideos = videos?.map { it.toVideo() } ?: emptyList()
        val combinedVideos = (localVideos + mappedVideos).distinctBy { it.publicId }
        return ChannelInfo(
            channelName = fallbackChannelName,
            handle = fallbackHandle,
            avatarUrl = avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&auto=format&fit=crop",
            uploaderEmail = email,
            subscriberCount = subscriberCount ?: 2240,
            videos = combinedVideos
        )
    }
}

@JsonClass(generateAdapter = true)
data class SubscribeRequest(
    @Json(name = "channelId") val channelId: String // email address based on spec
)

@JsonClass(generateAdapter = true)
data class SubscribeResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class VideosResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "videos") val videos: List<NetworkVideo>
)


