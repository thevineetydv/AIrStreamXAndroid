package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.SearchHistoryEntity
import com.example.data.database.SubscriptionEntity
import com.example.data.database.WatchHistoryEntity
import com.example.data.database.toEntity
import com.example.data.network.ApiClient
import com.example.data.network.ApiService
import com.example.data.network.ChannelInfo
import com.example.data.network.SubscribeRequest
import com.example.data.network.Video
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/** One page of the long-video home feed. */
data class FeedPage(
    val videos: List<Video>,
    val hasMore: Boolean,
    /** Offset to request next — based on what the SERVER returned, not on what we kept after filtering. */
    val nextOffset: Int
)

/** GET /videos/{id} returned 404 — the video doesn't exist or is someone else's private video. */
class VideoNotFoundException : Exception("Video not found")

class VideoRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val subscriptionDao = database.channelSubscriptionDao()
    private val watchHistoryDao = database.watchHistoryDao()
    private val videoDao = database.videoDao()

    private val apiService: ApiService get() = ApiClient.api

    // ── Local cache (Room) ──────────────────────────────────────────────────
    //
    // Room is a CACHE of public videos plus this device's like/dislike flags.
    // It is never the source of truth for what appears in a feed: the old code
    // inserted every API result and then rendered the whole table, so videos
    // that were later made private/unlisted or deleted on the server kept
    // showing forever. Feeds are now built from the network response only.

    /** Caches public, ready videos while keeping this device's like/dislike flags. */
    private suspend fun cacheLocally(videos: List<Video>) {
        val cacheable = videos.filter { it.isPublic() && it.isReady() }
        if (cacheable.isEmpty()) return
        val existing = videoDao.getVideosByPublicIds(cacheable.map { it.publicId })
            .associateBy { it.publicId }
        videoDao.insertVideos(cacheable.map { v ->
            val old = existing[v.publicId]
            v.toEntity().copy(
                id = old?.id ?: 0,
                isLiked = old?.isLiked ?: false,
                isDisliked = old?.isDisliked ?: false
            )
        })
    }

    /** Overlays this device's like/dislike flags onto fresh server data. */
    private suspend fun withLocalFlags(videos: List<Video>): List<Video> {
        if (videos.isEmpty()) return videos
        val local = videoDao.getVideosByPublicIds(videos.map { it.publicId })
            .associateBy { it.publicId }
        return videos.map { v ->
            val l = local[v.publicId] ?: return@map v
            v.copy(isLiked = l.isLiked, isDisliked = l.isDisliked)
        }
    }

    fun invalidateCache() {
        MetadataCacheService.invalidate()
    }

    fun patchCachedVideo(video: Video) {
        MetadataCacheService.patchVideo(video)
    }

    /** First page of the home feed if it was fetched in the last 5 minutes. */
    fun cachedFirstPage(): FeedPage? = MetadataCacheService.getCachedPage()?.let {
        FeedPage(videos = it.videos, hasMore = it.hasMore, nextOffset = it.nextOffset)
    }

    // ── Home feed (long videos, paged) ──────────────────────────────────────

    /**
     * GET /videos?type=long&limit=24&offset=N. Throws on network/server errors so
     * the UI can show an error with Retry instead of an empty "No videos" screen.
     */
    suspend fun getHomeFeedPage(offset: Int, limit: Int = HOME_PAGE_SIZE): FeedPage =
        withContext(Dispatchers.IO) {
            val response = apiService.getVideos(type = "long", limit = limit, offset = offset)
            val serverCount = response.videos.size
            val videos = response.videos
                .mapNotNull { it.toVideoOrNull() }
                // Safety nets: never list non-public videos, and keep Shorts out
                // of the long feed even if the backend ignores `type=long`.
                .filter { it.isPublic() && !it.isShort() }
                .distinctBy { it.publicId }

            val hasMore = when {
                serverCount == 0 -> false // guards against an endless loop
                response.hasMore != null -> response.hasMore
                response.total != null -> offset + serverCount < response.total
                else -> serverCount >= limit
            }

            cacheLocally(videos)
            val result = withLocalFlags(videos)
            val nextOffset = offset + serverCount
            if (offset == 0) MetadataCacheService.cachePage(result, nextOffset, hasMore)
            FeedPage(videos = result, hasMore = hasMore, nextOffset = nextOffset)
        }

    // ── Shorts ──────────────────────────────────────────────────────────────

    /**
     * Same request the web ShortsPage makes (GET /videos?limit=50), filtered to
     * public videos of 1–60 s. Throws on failure.
     */
    suspend fun getShorts(): List<Video> = withContext(Dispatchers.IO) {
        val videos = apiService.getVideos(limit = SHORTS_FETCH_LIMIT).videos
            .mapNotNull { it.toVideoOrNull() }
            .filter { it.isPublic() && it.isShort() }
            .distinctBy { it.publicId }
        cacheLocally(videos)
        withLocalFlags(videos)
    }

    // ── Search ──────────────────────────────────────────────────────────────

    suspend fun searchVideos(query: String): List<Video> = withContext(Dispatchers.IO) {
        try {
            val videos = apiService.getVideos(search = query, limit = HOME_PAGE_SIZE).videos
                .mapNotNull { it.toVideoOrNull() }
                .filter { it.isPublic() }
                .distinctBy { it.publicId }
            cacheLocally(videos)
            withLocalFlags(videos)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Search failed, using cached public videos: ${e.javaClass.simpleName}")
            // The cache only ever contains public videos (see cacheLocally).
            videoDao.searchVideos(query).map { it.toVideo() }
        }
    }

    // ── Single video ────────────────────────────────────────────────────────

    /**
     * GET /videos/{public_id}. Network first — the cached copy may be stale or the
     * video may have been made private since.
     *
     * @throws VideoNotFoundException on 404
     * @throws IOException when offline and nothing is cached
     */
    suspend fun getVideo(publicId: String): Video = withContext(Dispatchers.IO) {
        try {
            val video = apiService.getVideoById(publicId).video?.toVideoOrNull()
                ?: throw VideoNotFoundException()
            cacheLocally(listOf(video)) // no-op for private/unlisted/processing
            withLocalFlags(listOf(video)).first()
        } catch (e: HttpException) {
            if (e.code() == 404) {
                // Gone or private now — make sure it can't linger in the cache.
                videoDao.deleteVideoByPublicId(publicId)
                throw VideoNotFoundException()
            }
            throw e
        } catch (e: IOException) {
            videoDao.getVideoByPublicId(publicId)?.toVideo() ?: throw e
        }
    }

    // ── Channel ─────────────────────────────────────────────────────────────

    suspend fun getChannelInfo(handle: String): ChannelInfo = withContext(Dispatchers.IO) {
        val cleanHandle = handle.removePrefix("@")
        try {
            val info = apiService.getChannelInfo(cleanHandle).toChannelInfo(cleanHandle)
            cacheLocally(info.videos)
            info.copy(videos = withLocalFlags(info.videos))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Channel request failed: ${e.javaClass.simpleName}")
            // Offline fallback: only what we genuinely have cached. No invented
            // names, emails or subscriber counts.
            val cached = videoDao.getVideosByHandle(cleanHandle).map { it.toVideo() }
            val first = cached.firstOrNull() ?: throw e
            ChannelInfo(
                channelName = first.channelName,
                handle = first.handle,
                avatarUrl = first.avatarUrl,
                uploaderEmail = first.uploaderEmail,
                subscriberCount = first.subscriberCount,
                videos = cached
            )
        }
    }

    suspend fun subscribeRemote(uploaderEmail: String): Boolean = withContext(Dispatchers.IO) {
        try {
            apiService.subscribeChannel(SubscribeRequest(uploaderEmail)).success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Remote subscribe failed: ${e.javaClass.simpleName}")
            // TODO(High group H10): surface this failure instead of keeping the
            // local subscription.
            true
        }
    }

    suspend fun getDistinctChannels(): List<ChannelInfo> = withContext(Dispatchers.IO) {
        val distinctVideos = videoDao.getDistinctChannelVideos()
        val dbVideos = videoDao.getAllVideos().map { it.toVideo() }
        distinctVideos
            .filter { it.handle.isNotBlank() }
            .map { entity ->
                val vids = dbVideos.filter { it.handle.equals(entity.handle, ignoreCase = true) }
                ChannelInfo(
                    channelName = entity.channelName,
                    handle = entity.handle,
                    avatarUrl = entity.avatarUrl,
                    uploaderEmail = entity.uploaderEmail,
                    subscriberCount = vids.firstOrNull()?.subscriberCount ?: 0,
                    videos = vids
                )
            }
    }

    // ── Channel Subscription local syncing ──────────────────────────────────
    val localSubscriptions: Flow<List<SubscriptionEntity>> = subscriptionDao.getAllSubscriptions()

    fun isSubscribedLocally(handle: String): Flow<Boolean> {
        val sanitizedHandle = handle.removePrefix("@")
        return subscriptionDao.isSubscribed(sanitizedHandle)
    }

    private suspend fun adjustChannelSubscriberCountInDb(handle: String, delta: Int) {
        val sanitized = handle.removePrefix("@")
        videoDao.getVideosByHandle(sanitized).forEach { entity ->
            val updated = entity.copy(subscriberCount = (entity.subscriberCount + delta).coerceAtLeast(0))
            videoDao.insertVideo(updated)
        }
    }

    suspend fun toggleSubscription(video: Video) = withContext(Dispatchers.IO) {
        val sanitizedHandle = video.handle.removePrefix("@")
        val isCurrentlySubscribed = subscriptionDao.isSubscribed(sanitizedHandle).first()

        if (isCurrentlySubscribed) {
            subscriptionDao.unsubscribe(sanitizedHandle)
            adjustChannelSubscriberCountInDb(sanitizedHandle, -1)
        } else {
            subscriptionDao.subscribe(
                SubscriptionEntity(
                    handle = sanitizedHandle,
                    channelName = video.channelName,
                    avatarUrl = video.avatarUrl
                )
            )
            subscribeRemote(video.uploaderEmail)
            adjustChannelSubscriberCountInDb(sanitizedHandle, 1)
        }
    }

    suspend fun toggleSubscriptionByChannel(channel: ChannelInfo) = withContext(Dispatchers.IO) {
        val sanitizedHandle = channel.handle.removePrefix("@")
        val isCurrentlySubscribed = subscriptionDao.isSubscribed(sanitizedHandle).first()

        if (isCurrentlySubscribed) {
            subscriptionDao.unsubscribe(sanitizedHandle)
            adjustChannelSubscriberCountInDb(sanitizedHandle, -1)
        } else {
            subscriptionDao.subscribe(
                SubscriptionEntity(
                    handle = sanitizedHandle,
                    channelName = channel.channelName,
                    avatarUrl = channel.avatarUrl
                )
            )
            subscribeRemote(channel.uploaderEmail)
            adjustChannelSubscriberCountInDb(sanitizedHandle, 1)
        }
    }

    // ── Like / dislike (device-local until the High group wires the API) ────

    suspend fun toggleLikeVideo(publicId: String): Video? = withContext(Dispatchers.IO) {
        val entity = videoDao.getVideoByPublicId(publicId) ?: return@withContext null
        val newIsLiked = !entity.isLiked
        val newLikes = (entity.likes + if (newIsLiked) 1 else -1).coerceAtLeast(0)
        videoDao.updateLikeState(publicId, newIsLiked, newLikes)
        if (entity.isDisliked) {
            videoDao.updateDislikeState(publicId, false, (entity.dislikes - 1).coerceAtLeast(0))
        }
        videoDao.getVideoByPublicId(publicId)?.toVideo()
    }

    suspend fun toggleDislikeVideo(publicId: String): Video? = withContext(Dispatchers.IO) {
        val entity = videoDao.getVideoByPublicId(publicId) ?: return@withContext null
        val newIsDisliked = !entity.isDisliked
        val newDislikes = (entity.dislikes + if (newIsDisliked) 1 else -1).coerceAtLeast(0)
        videoDao.updateDislikeState(publicId, newIsDisliked, newDislikes)
        if (entity.isLiked) {
            videoDao.updateLikeState(publicId, false, (entity.likes - 1).coerceAtLeast(0))
        }
        videoDao.getVideoByPublicId(publicId)?.toVideo()
    }

    // ── Watch / search history ──────────────────────────────────────────────
    val watchHistory: Flow<List<WatchHistoryEntity>> = watchHistoryDao.getWatchHistory()

    suspend fun addToWatchHistory(video: Video) = withContext(Dispatchers.IO) {
        watchHistoryDao.insertToHistory(
            WatchHistoryEntity(
                publicId = video.publicId,
                title = video.title,
                thumbnail = video.thumbnail,
                channelName = video.channelName,
                handle = video.handle
            )
        )
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        watchHistoryDao.clearHistory()
    }

    val searchHistory: Flow<List<SearchHistoryEntity>> = watchHistoryDao.getSearchHistory()

    suspend fun insertSearchQuery(query: String) = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            watchHistoryDao.insertSearchQuery(SearchHistoryEntity(query.trim()))
        }
    }

    suspend fun deleteSearchQuery(query: String) = withContext(Dispatchers.IO) {
        watchHistoryDao.deleteSearchQuery(query)
    }

    companion object {
        private const val TAG = "VideoRepository"
        const val HOME_PAGE_SIZE = 24
        private const val SHORTS_FETCH_LIMIT = 50
    }
}
