package com.example.data.repository

import com.example.data.network.Video
import android.util.Log

/**
 * A simple in-memory cache service for Home feed and Shorts metadata
 * to minimize network/database roundtrips and ensure instantaneous UI updates
 * when returning to the homepage.
 */
object MetadataCacheService {
    @Volatile
    private var cachedVideos: List<Video>? = null

    @Volatile
    private var lastFetchTime: Long = 0L

    // Cache validity duration is set to 5 minutes
    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    /**
     * Checks if the cached metadata is present and still valid.
     */
    fun isCacheValid(): Boolean {
        val cache = cachedVideos
        if (cache == null || cache.isEmpty()) return false
        val age = System.currentTimeMillis() - lastFetchTime
        Log.d("MetadataCacheService", "Cache age: ${age}ms, TTL: ${CACHE_TTL_MS}ms")
        return age < CACHE_TTL_MS
    }

    /**
     * Retrieves the cached videos if valid, or null.
     */
    fun getCachedVideos(): List<Video>? {
        return if (isCacheValid()) {
            Log.d("MetadataCacheService", "Cache hit! Returning ${cachedVideos?.size} videos")
            cachedVideos
        } else {
            Log.d("MetadataCacheService", "Cache miss or expired.")
            null
        }
    }

    /**
     * Stores a new list of videos in memory and notes the fetch time.
     */
    fun cacheVideos(videos: List<Video>) {
        if (videos.isNotEmpty()) {
            cachedVideos = videos
            lastFetchTime = System.currentTimeMillis()
            Log.d("MetadataCacheService", "Cached ${videos.size} videos at $lastFetchTime")
        }
    }

    /**
     * Explicitly invalidates the cached metadata to force fresh load.
     */
    fun invalidate() {
        cachedVideos = null
        lastFetchTime = 0L
        Log.d("MetadataCacheService", "Cache explicitly invalidated.")
    }

    /**
     * PERF FIX: patches a single video in-place within the cache (if present)
     * without touching the TTL/fetch time.
     *
     * Previously, local mutations (like/dislike/subscribe) had no way to
     * update this cache, so callers had to invalidate() it (forcing a full
     * network refetch) or leave it silently stale for up to CACHE_TTL_MS —
     * meaning a like could "succeed" but the Home feed would keep showing
     * the pre-like state for up to 5 minutes. Patching in place keeps the
     * cache both valid (fast) and correct (fresh) after a local edit.
     */
    fun patchVideo(video: Video) {
        val cache = cachedVideos ?: return
        cachedVideos = cache.map { if (it.publicId == video.publicId) video else it }
    }
}
