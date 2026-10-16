package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * App-side video model.
 *
 * PRIVACY: [uploaderEmail] is only kept so the app can call endpoints that are
 * keyed by the creator's email (channel customization, subscribe). It must
 * never be rendered in the UI or written to logs.
 */
data class Video(
    val id: Int,
    val publicId: String,
    val title: String,
    val thumbnail: String,
    val url: String, // HLS master.m3u8 — may be blank while status != "ready"
    val views: Int,
    val duration: Double, // seconds
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
    val description: String = "",
    val status: String = STATUS_READY,
    val visibility: String = VISIBILITY_PUBLIC,
    val upiId: String = "",
    val isMadeForKids: Boolean = false,
    val uploaderVideoCount: Int = 0
) {
    fun isShort(): Boolean = duration > 0.0 && duration <= SHORT_MAX_SECONDS

    fun isPublic(): Boolean = visibility.equals(VISIBILITY_PUBLIC, ignoreCase = true)

    fun isReady(): Boolean = status.equals(STATUS_READY, ignoreCase = true)

    /** Only ready videos with a real stream URL may be handed to the player. */
    fun isPlayable(): Boolean = isReady() && url.isNotBlank()

    /** Canonical share link — always the public_id, never the numeric id. */
    fun shareUrl(): String = "https://www.airstreamx.com/watch?v=$publicId"

    companion object {
        const val STATUS_READY = "ready"
        const val VISIBILITY_PUBLIC = "public"
        const val SHORT_MAX_SECONDS = 60.0
    }
}

@JsonClass(generateAdapter = true)
data class NetworkVideo(
    @Json(name = "id") val id: Int? = null,
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
    @Json(name = "description") val description: String? = null,
    @Json(name = "likes") val likes: Int? = null,
    @Json(name = "dislikes") val dislikes: Int? = null,
    @Json(name = "subscribers") val subscribers: Int? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "visibility") val visibility: String? = null,
    @Json(name = "upi_id") val upiId: String? = null,
    @Json(name = "is_made_for_kids") val isMadeForKids: Boolean? = null,
    @Json(name = "uploader_video_count") val uploaderVideoCount: Int? = null
) {
    /**
     * Maps the server record to the app model WITHOUT inventing data.
     *
     * Previously every missing field was replaced with fake values (a Mux test
     * stream as the URL, 1420 views, "anonymous@gmail.com", stock avatars), so a
     * broken response looked like a real, playable video. Now:
     *  - a record without public_id (and without id) is unusable → null
     *  - a missing URL stays blank; the player shows "processing" instead
     *  - numbers default to 0, text to empty
     *  - channel name/handle are never derived from the uploader's email
     */
    fun toVideoOrNull(): Video? {
        val pid = publicId?.takeIf { it.isNotBlank() } ?: id?.toString() ?: return null
        return Video(
            id = id ?: 0,
            publicId = pid,
            title = title.orEmpty(),
            thumbnail = thumbnail.orEmpty(),
            url = url.orEmpty(),
            views = views ?: 0,
            duration = duration ?: 0.0,
            channelName = channelName?.takeIf { it.isNotBlank() } ?: handle.orEmpty(),
            avatarUrl = avatarUrl.orEmpty(),
            handle = handle.orEmpty().removePrefix("@"),
            uploaderEmail = uploaderEmail.orEmpty(),
            createdAt = createdAt.orEmpty(),
            likes = likes ?: 0,
            dislikes = dislikes ?: 0,
            subscriberCount = subscribers ?: 0,
            description = description.orEmpty(),
            // Absent status/visibility = legacy record that is listed publicly.
            status = status?.lowercase() ?: Video.STATUS_READY,
            visibility = visibility?.lowercase() ?: Video.VISIBILITY_PUBLIC,
            upiId = upiId.orEmpty(),
            isMadeForKids = isMadeForKids ?: false,
            uploaderVideoCount = uploaderVideoCount ?: 0
        )
    }
}

/** GET /videos/{public_id or id} → { success, video } */
@JsonClass(generateAdapter = true)
data class VideoResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "video") val video: NetworkVideo? = null
)

/** GET /videos?type=long&limit=24&offset=N → { success, videos[], total, hasMore } */
@JsonClass(generateAdapter = true)
data class VideosResponse(
    @Json(name = "success") val success: Boolean = false,
    @Json(name = "videos") val videos: List<NetworkVideo> = emptyList(),
    @Json(name = "total") val total: Int? = null,
    @Json(name = "hasMore") val hasMore: Boolean? = null
)

data class ChannelInfo(
    val channelName: String,
    val handle: String,
    val avatarUrl: String,
    val uploaderEmail: String, // PRIVACY: never display or log
    val subscriberCount: Int = 0,
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
    fun toChannelInfo(requestedHandle: String): ChannelInfo {
        val cleanHandle = handle?.removePrefix("@")?.takeIf { it.isNotBlank() } ?: requestedHandle
        return ChannelInfo(
            channelName = channelName?.takeIf { it.isNotBlank() } ?: cleanHandle,
            handle = cleanHandle,
            avatarUrl = avatarUrl.orEmpty(),
            uploaderEmail = uploaderEmail.orEmpty(),
            subscriberCount = subscriberCount ?: 0,
            // Channel pages list public videos only.
            videos = videos.orEmpty().mapNotNull { it.toVideoOrNull() }.filter { it.isPublic() }
        )
    }
}

@JsonClass(generateAdapter = true)
data class SubscribeRequest(
    @Json(name = "channelId") val channelId: String
)

@JsonClass(generateAdapter = true)
data class SubscribeResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "message") val message: String? = null
)
