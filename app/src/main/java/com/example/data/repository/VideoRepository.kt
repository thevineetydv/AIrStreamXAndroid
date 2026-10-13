package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.SubscriptionEntity
import com.example.data.database.WatchHistoryEntity
import com.example.data.database.SearchHistoryEntity
import com.example.data.network.ApiService
import com.example.data.network.ChannelInfo
import com.example.data.network.SubscribeRequest
import com.example.data.network.Video
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.example.data.database.VideoEntity
import com.example.data.database.toEntity
import java.util.concurrent.TimeUnit

class VideoRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val subscriptionDao = database.channelSubscriptionDao()
    private val watchHistoryDao = database.watchHistoryDao()
    private val videoDao = database.videoDao()
    private val seedMutex = Mutex()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    suspend fun seedDatabaseIfEmpty() = withContext(Dispatchers.IO) {
        // No-op: Removed all fallback and seeded mock videos.
    }

    suspend fun getUploadedVideos(): List<Video> = withContext(Dispatchers.IO) {
        seedDatabaseIfEmpty()
        videoDao.getAllVideos().map { it.toVideo() }
    }

    suspend fun uploadVideo(
        video: Video,
        localUriString: String? = null,
        firebaseToken: String? = null
    ): Result<Video> = withContext(Dispatchers.IO) {
        seedDatabaseIfEmpty()
        invalidateCache()
        try {
            val token = firebaseToken ?: ""
            if (token.isEmpty()) {
                Log.w("VideoRepository", "No Firebase token provided. Inserting locally.")
                videoDao.insertVideo(video.toEntity())
                return@withContext Result.success(video)
            }

            // Prepare a real file input or a dummy file fallback if we only have a remote stream url
            var fileToUpload: java.io.File? = null
            if (!localUriString.isNullOrBlank()) {
                val uri = android.net.Uri.parse(localUriString)
                if (uri.scheme == "file") {
                    val path = uri.path
                    if (path != null) {
                        val f = java.io.File(path)
                        if (f.exists() && f.length() > 0) {
                            fileToUpload = f
                        }
                    }
                } else if (uri.scheme == "content") {
                    try {
                        val cacheFile = java.io.File(context.cacheDir, "upload_temp_${System.currentTimeMillis()}.mp4")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (cacheFile.exists() && cacheFile.length() > 0) {
                            fileToUpload = cacheFile
                        }
                    } catch (e: Exception) {
                        Log.e("VideoRepository", "Error creating temp file from content resolver", e)
                    }
                }
            }

            // Fallback to a tiny dummy file if there's no valid picked file (e.g. they picked a preset stream URI)
            if (fileToUpload == null) {
                val dummyFile = java.io.File(context.cacheDir, "dummy_video.mp4")
                if (!dummyFile.exists() || dummyFile.length() == 0L) {
                    dummyFile.writeText("Dummy MP4 container content for multipart compliance.")
                }
                fileToUpload = dummyFile
            }

            // Create RequestBody and MultipartBody.Part using highly compatible companion extensions
            val mediaType = "video/mp4".toMediaTypeOrNull()
            val textType = "text/plain".toMediaTypeOrNull()
            val requestFile = fileToUpload.asRequestBody(mediaType)
            val filePart = okhttp3.MultipartBody.Part.createFormData(
                "file",
                fileToUpload.name,
                requestFile
            )

            // Convert string fields to RequestBody using modern extension functions
            val titlePart = video.title.toRequestBody(textType)
            val categoryPart = (if (video.publicId.contains("shorts")) "shorts" else "cinematic").toRequestBody(textType)
            val durationPart = video.duration.toString().toRequestBody(textType)

            Log.d("VideoRepository", "Uploading to backend via endpoint POST /upload, file size: ${fileToUpload.length()} bytes")
            val networkVideo = apiService.uploadVideo(
                file = filePart,
                title = titlePart,
                category = categoryPart,
                duration = durationPart
            )

            val remoteVideo = networkVideo.toVideo()
            // Override local details if remote returned them
            val finalVideo = video.copy(
                id = if (remoteVideo.id != 0) remoteVideo.id else video.id,
                publicId = if (remoteVideo.publicId.isNotEmpty() && !remoteVideo.publicId.startsWith("video_0")) remoteVideo.publicId else video.publicId,
                url = if (remoteVideo.url.isNotEmpty()) remoteVideo.url else video.url,
                thumbnail = if (remoteVideo.thumbnail.isNotEmpty()) remoteVideo.thumbnail else video.thumbnail,
                uploaderEmail = if (remoteVideo.uploaderEmail.isNotEmpty()) remoteVideo.uploaderEmail else video.uploaderEmail
            )

            // Insert to local database
            videoDao.insertVideo(finalVideo.toEntity())
            Log.d("VideoRepository", "Upload successful! Saved local and remote video metadata: $finalVideo")
            Result.success(finalVideo)
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error uploading video to server: ${e.message}", e)
            // Save locally as a fallback so the user experience doesn't break
            videoDao.insertVideo(video.toEntity())
            Result.success(video)
        }
    }

    private val apiService: ApiService by lazy {
        // PERF/SECURITY FIX: this was hardcoded to Level.BODY, which means
        // every single request/response — including full JSON bodies and the
        // Firebase bearer token added below — was serialized to Logcat on
        // every API call, in every build, including release. That's wasted
        // CPU/IO on every network request for output nobody reads outside
        // active debugging, and it leaks auth tokens and user data into
        // device logs on production builds. Debug builds keep full visibility;
        // release builds log nothing.
        val logging = HttpLoggingInterceptor().apply {
            level = if (com.example.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = runBlocking {
                    try {
                        if (FirebaseApp.getApps(context).isNotEmpty()) {
                            FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e("VideoRepository", "Error getting Firebase token in interceptor: ${e.message}")
                        null
                    }
                }
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://backend.airstreamx.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ApiService::class.java)
    }

    fun invalidateCache() {
        MetadataCacheService.invalidate()
    }

    // PERF FIX: lets callers keep the in-memory feed cache consistent after a
    // local mutation (like/dislike/subscribe) instead of invalidating it
    // outright and forcing every screen back onto a full reload. See
    // MetadataCacheService.patchVideo for details.
    fun patchCachedVideo(video: Video) {
        MetadataCacheService.patchVideo(video)
    }

    suspend fun getHomeFeed(limit: Int = 20): List<Video> = withContext(Dispatchers.IO) {
        seedDatabaseIfEmpty()
        
        // 1. Check if the metadata cache service has a valid cached copy
        val cached = MetadataCacheService.getCachedVideos()
        if (cached != null) {
            return@withContext cached
        }

        // 2. If not cached, or cache expired, fetch from API
        try {
            val response = apiService.getVideos(limit = limit)
            if (response.videos.isNotEmpty()) {
                videoDao.insertVideos(response.videos.map { it.toVideo().toEntity() })
            }
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error syncing home feed: ${e.message}", e)
        }
        
        val allVideosLocal = videoDao.getAllVideos().map { it.toVideo() }
        
        // 3. Cache the resulting video list
        if (allVideosLocal.isNotEmpty()) {
            MetadataCacheService.cacheVideos(allVideosLocal)
        }
        
        allVideosLocal
    }

    suspend fun getShortsVideos(): List<Video> = withContext(Dispatchers.IO) {
        getHomeFeed().filter { it.isShort() }
    }

    suspend fun getNormalVideos(): List<Video> = withContext(Dispatchers.IO) {
        getHomeFeed().filter { !it.isShort() }
    }

    suspend fun searchVideos(query: String): List<Video> = withContext(Dispatchers.IO) {
        seedDatabaseIfEmpty()
        try {
            val response = apiService.getVideos(search = query)
            if (response.videos.isNotEmpty()) {
                videoDao.insertVideos(response.videos.map { it.toVideo().toEntity() })
            }
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error syncing search videos: ${e.message}", e)
        }
        videoDao.searchVideos(query).map { it.toVideo() }
    }

    suspend fun getVideo(publicId: String): Video = withContext(Dispatchers.IO) {
        seedDatabaseIfEmpty()
        val local = videoDao.getVideoByPublicId(publicId)
        if (local != null) return@withContext local.toVideo()
        try {
            val remote = apiService.getVideoById(publicId).toVideo()
            videoDao.insertVideo(remote.toEntity())
            remote
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error getting single video $publicId: ${e.message}", e)
            videoDao.getAllVideos().firstOrNull()?.toVideo()
                ?: throw IllegalStateException("No videos found in database")
        }
    }

    suspend fun getChannelInfo(handle: String): ChannelInfo = withContext(Dispatchers.IO) {
        seedDatabaseIfEmpty()
        val sanitizedHandle = handle.removePrefix("@")
        val localUploads = videoDao.getAllVideos()
            .map { it.toVideo() }
            .filter { it.handle.equals(sanitizedHandle, ignoreCase = true) }
        try {
            val response = apiService.getChannelInfo(sanitizedHandle)
            val channelInfo = response.toChannelInfo(localVideos = localUploads)
            if (channelInfo.videos.isNotEmpty()) {
                videoDao.insertVideos(channelInfo.videos.map { it.toEntity() })
            }
            channelInfo
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error getting channel info $sanitizedHandle: ${e.message}", e)
            val dbVideos = videoDao.getAllVideos().map { it.toVideo() }
            val templateVideo = dbVideos.find { it.handle.equals(sanitizedHandle, ignoreCase = true) }
            
            if (templateVideo != null) {
                val channelVids = dbVideos.filter { it.handle.equals(sanitizedHandle, ignoreCase = true) }
                ChannelInfo(
                    channelName = templateVideo.channelName,
                    handle = templateVideo.handle,
                    avatarUrl = templateVideo.avatarUrl,
                    uploaderEmail = templateVideo.uploaderEmail,
                    subscriberCount = templateVideo.subscriberCount,
                    videos = channelVids
                )
            } else {
                ChannelInfo(
                    channelName = sanitizedHandle.replaceFirstChar { it.uppercase() },
                    handle = sanitizedHandle,
                    avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&auto=format&fit=crop",
                    uploaderEmail = "$sanitizedHandle@gmail.com",
                    subscriberCount = 0,
                    videos = localUploads
                )
            }
        }
    }

    suspend fun subscribeRemote(uploaderEmail: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiService.subscribeChannel(SubscribeRequest(uploaderEmail))
            response.success
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error calling remote subscribe: ${e.message}", e)
            true // fallback to successful state locally even if remote server is offline
        }
    }

    suspend fun getDistinctChannels(): List<ChannelInfo> = withContext(Dispatchers.IO) {
        seedDatabaseIfEmpty()
        val distinctVideos = videoDao.getDistinctChannelVideos()
        val dbVideos = videoDao.getAllVideos().map { it.toVideo() }
        distinctVideos.map { entity ->
            val vids = dbVideos.filter { it.handle.equals(entity.handle, ignoreCase = true) }
            val subscriberCountForChannel = vids.firstOrNull()?.subscriberCount ?: 12000
            ChannelInfo(
                channelName = entity.channelName,
                handle = entity.handle,
                avatarUrl = entity.avatarUrl,
                uploaderEmail = entity.uploaderEmail,
                subscriberCount = subscriberCountForChannel,
                videos = vids
            )
        }
    }

    // --- Channel Subscription local syncing ---
    val localSubscriptions: Flow<List<SubscriptionEntity>> = subscriptionDao.getAllSubscriptions()

    fun isSubscribedLocally(handle: String): Flow<Boolean> {
        val sanitizedHandle = handle.removePrefix("@")
        return subscriptionDao.isSubscribed(sanitizedHandle)
    }

    private suspend fun adjustChannelSubscriberCountInDb(handle: String, delta: Int) {
        val sanitized = handle.removePrefix("@")
        val videos = videoDao.getAllVideos()
        val matchingVideos = videos.filter { it.handle.equals(sanitized, ignoreCase = true) }
        matchingVideos.forEach { entity ->
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
            // also trigger service integration sync
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

    // --- Dynamic Like and Dislike state modification ---
    // PERF FIX: previously did a full-row REPLACE (insertVideo) to change two
    // columns, and callers had to issue a *separate* getVideo() query plus a
    // full loadHomeFeed() (2 more calls) just to see the result. This now
    // uses the already-defined targeted UPDATE queries and hands the fresh
    // row straight back so the ViewModel can patch state in one shot.
    suspend fun toggleLikeVideo(publicId: String): Video? = withContext(Dispatchers.IO) {
        val entity = videoDao.getVideoByPublicId(publicId) ?: return@withContext null
        val newIsLiked = !entity.isLiked
        val wasDisliked = entity.isDisliked

        val likesDiff = if (newIsLiked) 1 else -1
        val newLikes = (entity.likes + likesDiff).coerceAtLeast(0)
        videoDao.updateLikeState(publicId, newIsLiked, newLikes)

        if (wasDisliked) {
            val newDislikes = (entity.dislikes - 1).coerceAtLeast(0)
            videoDao.updateDislikeState(publicId, false, newDislikes)
        }

        videoDao.getVideoByPublicId(publicId)?.toVideo()
    }

    suspend fun toggleDislikeVideo(publicId: String): Video? = withContext(Dispatchers.IO) {
        val entity = videoDao.getVideoByPublicId(publicId) ?: return@withContext null
        val newIsDisliked = !entity.isDisliked
        val wasLiked = entity.isLiked

        val dislikesDiff = if (newIsDisliked) 1 else -1
        val newDislikes = (entity.dislikes + dislikesDiff).coerceAtLeast(0)
        videoDao.updateDislikeState(publicId, newIsDisliked, newDislikes)

        if (wasLiked) {
            val newLikes = (entity.likes - 1).coerceAtLeast(0)
            videoDao.updateLikeState(publicId, false, newLikes)
        }

        videoDao.getVideoByPublicId(publicId)?.toVideo()
    }

    // --- Watch History local synchronization ---
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
}