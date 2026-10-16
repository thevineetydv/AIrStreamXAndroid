package com.example.data.repository

import com.example.data.network.Video

/**
 * In-memory cache of the FIRST page of the home feed (public videos only), so
 * returning to Home within 5 minutes is instant. Never persisted to disk.
 */
object MetadataCacheService {
    data class CachedPage(val videos: List<Video>, val nextOffset: Int, val hasMore: Boolean)

    @Volatile
    private var cached: CachedPage? = null

    @Volatile
    private var lastFetchTime: Long = 0L

    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    fun getCachedPage(): CachedPage? {
        val page = cached ?: return null
        if (page.videos.isEmpty()) return null
        return if (System.currentTimeMillis() - lastFetchTime < CACHE_TTL_MS) page else null
    }

    fun cachePage(videos: List<Video>, nextOffset: Int, hasMore: Boolean) {
        if (videos.isEmpty()) return
        cached = CachedPage(videos, nextOffset, hasMore)
        lastFetchTime = System.currentTimeMillis()
    }

    fun invalidate() {
        cached = null
        lastFetchTime = 0L
    }

    /** Patches one video in place (e.g. after a like) without touching the TTL. */
    fun patchVideo(video: Video) {
        val page = cached ?: return
        cached = page.copy(videos = page.videos.map {
            if (it.publicId == video.publicId) it.copy(
                likes = video.likes, dislikes = video.dislikes,
                isLiked = video.isLiked, isDisliked = video.isDisliked
            ) else it
        })
    }
}
