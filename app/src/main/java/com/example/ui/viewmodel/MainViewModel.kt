package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.database.SearchHistoryEntity
import com.example.data.database.SubscriptionEntity
import com.example.data.database.WatchHistoryEntity
import com.example.data.network.ChannelInfo
import com.example.data.network.Video
import com.example.data.repository.AuthRepository
import com.example.data.repository.LocalUser
import com.example.data.repository.VideoNotFoundException
import com.example.data.repository.VideoRepository
import com.example.player.MiniPlayerManager
import com.example.upload.UploadWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface FeedUiState {
    object Loading : FeedUiState
    data class Success(
        val videos: List<Video>,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        /** Set when loading the NEXT page failed; the list stays visible with a Retry row. */
        val loadMoreError: String? = null
    ) : FeedUiState
    data class Error(val message: String) : FeedUiState
}

sealed interface ShortsUiState {
    object Loading : ShortsUiState
    data class Success(val videos: List<Video>) : ShortsUiState
    data class Error(val message: String) : ShortsUiState
}

sealed interface ChannelUiState {
    object Idle : ChannelUiState
    object Loading : ChannelUiState
    data class Success(val channel: ChannelInfo) : ChannelUiState
    data class Error(val message: String) : ChannelUiState
}

/** Problems with the video currently open in the player. */
sealed interface ActiveVideoError {
    object NotFound : ActiveVideoError
    data class LoadFailed(val message: String) : ActiveVideoError
}

sealed interface UploadUiState {
    object Idle : UploadUiState
    data class Uploading(val percent: Int) : UploadUiState
    data class Success(val publicId: String?) : UploadUiState
    data class Failed(val message: String) : UploadUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val videoRepository = VideoRepository(application)
    private val authRepository = AuthRepository(application)
    private val workManager = WorkManager.getInstance(application)

    // Auth state flow
    val currentUser: StateFlow<LocalUser?> = authRepository.currentUser

    // ── Home feed (paged) ───────────────────────────────────────────────────
    private val _feedState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val feedState: StateFlow<FeedUiState> = _feedState

    private var nextOffset = 0
    private var feedJob: Job? = null

    // Available categories
    private val _categories = MutableStateFlow(listOf("All", "Music", "Comedy", "Bhakti", "Cricket"))
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    // Available database channels flow
    private val _availableChannels = MutableStateFlow<List<ChannelInfo>>(emptyList())
    val availableChannels: StateFlow<List<ChannelInfo>> = _availableChannels

    // ── Shorts ──────────────────────────────────────────────────────────────
    private val _shortsState = MutableStateFlow<ShortsUiState>(ShortsUiState.Loading)
    val shortsState: StateFlow<ShortsUiState> = _shortsState

    private val _selectedShortVideo = MutableStateFlow<Video?>(null)
    val selectedShortVideo: StateFlow<Video?> = _selectedShortVideo

    fun selectShortVideo(video: Video?) {
        _selectedShortVideo.value = video
    }

    // ── Player ──────────────────────────────────────────────────────────────
    private val _currentActiveVideo = MutableStateFlow<Video?>(null)
    val currentActiveVideo: StateFlow<Video?> = _currentActiveVideo

    private val _activeVideoError = MutableStateFlow<ActiveVideoError?>(null)
    val activeVideoError: StateFlow<ActiveVideoError?> = _activeVideoError

    private var activeVideoJob: Job? = null

    // Channel page detail state
    private val _channelState = MutableStateFlow<ChannelUiState>(ChannelUiState.Idle)
    val channelState: StateFlow<ChannelUiState> = _channelState

    // Search trigger flows
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<Video>>(emptyList())
    val searchResults: StateFlow<List<Video>> = _searchResults

    // Room DB bindings
    val subscriptions: StateFlow<List<SubscriptionEntity>> = videoRepository.localSubscriptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchHistory: StateFlow<List<WatchHistoryEntity>> = videoRepository.watchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = videoRepository.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Upload ──────────────────────────────────────────────────────────────
    private val _uploadState = MutableStateFlow<UploadUiState>(UploadUiState.Idle)
    val uploadState: StateFlow<UploadUiState> = _uploadState
    private var uploadObserverJob: Job? = null

    fun insertSearchQuery(query: String) {
        viewModelScope.launch { videoRepository.insertSearchQuery(query) }
    }

    fun deleteSearchQuery(query: String) {
        viewModelScope.launch { videoRepository.deleteSearchQuery(query) }
    }

    init {
        loadHomeFeed()
        loadShorts()
        observeExistingUpload()
    }

    /**
     * Loads page 1 of the long-video feed.
     *
     * A first page fetched in the last 5 minutes is shown straight from the
     * in-memory cache. On failure with nothing on screen the UI gets an Error
     * state (with Retry) — never an empty "No videos" list.
     */
    fun loadHomeFeed(forceRefresh: Boolean = false) {
        feedJob?.cancel()
        feedJob = viewModelScope.launch {
            if (forceRefresh) videoRepository.invalidateCache()

            val cached = videoRepository.cachedFirstPage()
            if (cached != null) {
                nextOffset = cached.nextOffset
                _feedState.value = FeedUiState.Success(cached.videos, hasMore = cached.hasMore)
                _availableChannels.value = videoRepository.getDistinctChannels()
                return@launch
            }

            val showing = _feedState.value as? FeedUiState.Success
            if (showing == null || forceRefresh) _feedState.value = FeedUiState.Loading

            try {
                val page = videoRepository.getHomeFeedPage(offset = 0)
                nextOffset = page.nextOffset
                _feedState.value = FeedUiState.Success(page.videos, hasMore = page.hasMore)
                _availableChannels.value = videoRepository.getDistinctChannels()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Home feed failed: ${e.javaClass.simpleName}")
                // Keep what's already on screen if this was a background refresh.
                if (showing == null || forceRefresh) {
                    _feedState.value = FeedUiState.Error(friendlyError(e))
                }
            }
        }
    }

    /** Infinite scroll: fetches the next page and appends it, de-duplicated by public_id. */
    fun loadMoreHomeFeed() {
        val current = _feedState.value as? FeedUiState.Success ?: return
        if (!current.hasMore || current.isLoadingMore || feedJob?.isActive == true) return

        feedJob = viewModelScope.launch {
            _feedState.value = current.copy(isLoadingMore = true, loadMoreError = null)
            try {
                val page = videoRepository.getHomeFeedPage(offset = nextOffset)
                nextOffset = page.nextOffset
                val latest = _feedState.value as? FeedUiState.Success ?: current
                val seen = latest.videos.mapTo(HashSet()) { it.publicId }
                _feedState.value = latest.copy(
                    videos = latest.videos + page.videos.filter { seen.add(it.publicId) },
                    hasMore = page.hasMore,
                    isLoadingMore = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Loading more failed: ${e.javaClass.simpleName}")
                val latest = _feedState.value as? FeedUiState.Success ?: current
                _feedState.value = latest.copy(isLoadingMore = false, loadMoreError = friendlyError(e))
            }
        }
    }

    fun loadShorts() {
        viewModelScope.launch {
            if (_shortsState.value !is ShortsUiState.Success) _shortsState.value = ShortsUiState.Loading
            _shortsState.value = try {
                ShortsUiState.Success(videoRepository.getShorts())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Shorts failed: ${e.javaClass.simpleName}")
                (_shortsState.value as? ShortsUiState.Success) ?: ShortsUiState.Error(friendlyError(e))
            }
        }
    }

    /**
     * Shows the clicked video immediately (thumbnail, title, and its stream URL so
     * playback can start), then loads the full record from GET /videos/{public_id}
     * and patches it in. The player isn't restarted by the refresh: it only
     * reloads when the stream URL actually changes.
     */
    fun selectVideo(video: Video) {
        _currentActiveVideo.value = video
        loadActiveVideo(video.publicId, alreadyShowing = true)
    }

    /** Opens a video we only know the id of (watch history, deep links later). */
    fun selectVideoById(publicId: String) {
        _currentActiveVideo.value = null
        loadActiveVideo(publicId, alreadyShowing = false)
    }

    private var lastRequestedVideoId: String? = null

    private fun loadActiveVideo(publicId: String, alreadyShowing: Boolean) {
        lastRequestedVideoId = publicId
        _activeVideoError.value = null
        activeVideoJob?.cancel()
        activeVideoJob = viewModelScope.launch {
            try {
                val full = videoRepository.getVideo(publicId)
                if (_currentActiveVideo.value?.publicId == publicId || !alreadyShowing) {
                    _currentActiveVideo.value = full
                }
                videoRepository.addToWatchHistory(full)
            } catch (e: CancellationException) {
                throw e
            } catch (e: VideoNotFoundException) {
                _activeVideoError.value = ActiveVideoError.NotFound
            } catch (e: Exception) {
                Log.e(TAG, "Loading video failed: ${e.javaClass.simpleName}")
                // If the clicked item is already on screen, keep it (offline,
                // flaky network); otherwise there's nothing to show.
                if (!alreadyShowing) _activeVideoError.value = ActiveVideoError.LoadFailed(friendlyError(e))
            }
        }
    }

    fun retryActiveVideo() {
        val id = lastRequestedVideoId ?: return
        loadActiveVideo(id, alreadyShowing = _currentActiveVideo.value?.publicId == id)
    }

    fun loadChannel(handle: String) {
        viewModelScope.launch {
            _channelState.value = ChannelUiState.Loading
            _channelState.value = try {
                ChannelUiState.Success(videoRepository.getChannelInfo(handle))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ChannelUiState.Error(friendlyError(e))
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.trim().isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _searchResults.value = videoRepository.searchVideos(query)
        }
    }

    fun toggleSubscription(video: Video) {
        viewModelScope.launch {
            videoRepository.toggleSubscription(video)
            _availableChannels.value = videoRepository.getDistinctChannels()
        }
    }

    /** Patches one video's fresh state into every flow that holds a copy of it. */
    private fun patchVideoEverywhere(updated: Video) {
        _currentActiveVideo.value?.let { active ->
            if (active.publicId == updated.publicId) {
                // Room only stores some fields; keep server-only ones (status, upi, …).
                _currentActiveVideo.value = active.copy(
                    likes = updated.likes, dislikes = updated.dislikes,
                    isLiked = updated.isLiked, isDisliked = updated.isDisliked
                )
            }
        }
        fun List<Video>.patched() = map {
            if (it.publicId == updated.publicId) it.copy(
                likes = updated.likes, dislikes = updated.dislikes,
                isLiked = updated.isLiked, isDisliked = updated.isDisliked
            ) else it
        }
        (_shortsState.value as? ShortsUiState.Success)?.let {
            _shortsState.value = ShortsUiState.Success(it.videos.patched())
        }
        (_feedState.value as? FeedUiState.Success)?.let {
            _feedState.value = it.copy(videos = it.videos.patched())
        }
        videoRepository.patchCachedVideo(updated)
    }

    fun toggleSubscriptionByChannel(channel: ChannelInfo) {
        viewModelScope.launch {
            videoRepository.toggleSubscriptionByChannel(channel)
            loadChannel(channel.handle)
            _availableChannels.value = videoRepository.getDistinctChannels()
        }
    }

    fun toggleLikeVideo(video: Video) {
        viewModelScope.launch {
            try {
                videoRepository.toggleLikeVideo(video.publicId)?.let { patchVideoEverywhere(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling like", e)
            }
        }
    }

    fun toggleDislikeVideo(video: Video) {
        viewModelScope.launch {
            try {
                videoRepository.toggleDislikeVideo(video.publicId)?.let { patchVideoEverywhere(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling dislike", e)
            }
        }
    }

    fun isSubscribed(handle: String): Flow<Boolean> {
        return videoRepository.isSubscribedLocally(handle.removePrefix("@"))
    }

    fun signIn(email: String, displayName: String?, pass: String, isSignUp: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = if (isSignUp) {
                authRepository.signUpWithEmail(email, displayName ?: email.substringBefore("@"), pass)
            } else {
                authRepository.signInWithEmail(email, pass)
            }
            if (result.isSuccess) onSuccess()
            else onError(result.exceptionOrNull()?.message ?: "Authentication failed")
        }
    }

    fun signInWithGoogle(idToken: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(idToken)
            if (result.isSuccess) onSuccess()
            else onError(result.exceptionOrNull()?.message ?: "Google Authentication failed")
        }
    }

    /** Browse-only guest — no account needed, works offline from Firebase config. */
    fun signInAnonymously(onSuccess: () -> Unit, onError: (String) -> Unit) {
        authRepository.continueAsGuest()
        onSuccess()
    }

    // Bottom Sheet Control
    private val _showSignInPrompt = MutableStateFlow(false)
    val showSignInPrompt: StateFlow<Boolean> = _showSignInPrompt

    fun showSignInPrompt() {
        _showSignInPrompt.value = true
    }

    fun dismissSignInPrompt() {
        _showSignInPrompt.value = false
    }

    /** No user, or browse-only guest mode. */
    fun isGuest(): Boolean = authRepository.isGuest()

    fun clearHistory() {
        viewModelScope.launch { videoRepository.clearHistory() }
    }

    // ── Upload ──────────────────────────────────────────────────────────────

    /**
     * Hands the upload to WorkManager (foreground, survives the app being closed)
     * and tracks its real progress. Nothing is saved locally and nothing is
     * reported as published until the server confirms it.
     */
    fun startUpload(fileUri: Uri, title: String, category: String, durationSeconds: Double) {
        val id = UploadWorker.enqueue(getApplication<Application>(), fileUri, title, category, durationSeconds)
        _uploadState.value = UploadUiState.Uploading(0)
        observeUpload(id)
    }

    fun cancelUpload() {
        UploadWorker.cancel(getApplication<Application>())
    }

    fun resetUploadState() {
        _uploadState.value = UploadUiState.Idle
    }

    /** Re-attaches to an upload that was still running when the app was closed. */
    private fun observeExistingUpload() {
        viewModelScope.launch {
            try {
                val running = workManager.getWorkInfosForUniqueWorkFlow(UploadWorker.UNIQUE_NAME)
                    .first()
                    .firstOrNull { !it.state.isFinished }
                if (running != null) observeUpload(running.id)
            } catch (e: Exception) {
                Log.w(TAG, "Could not check for a running upload: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun observeUpload(id: java.util.UUID) {
        uploadObserverJob?.cancel()
        uploadObserverJob = viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(id).filterNotNull().collect { info ->
                _uploadState.value = when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        loadHomeFeed(forceRefresh = true)
                        UploadUiState.Success(info.outputData.getString(UploadWorker.KEY_PUBLIC_ID))
                    }
                    WorkInfo.State.FAILED -> UploadUiState.Failed(
                        info.outputData.getString(UploadWorker.KEY_ERROR) ?: "Upload failed. Please try again."
                    )
                    WorkInfo.State.CANCELLED -> UploadUiState.Failed("Upload cancelled.")
                    else -> UploadUiState.Uploading(info.progress.getInt(UploadWorker.KEY_PROGRESS, 0))
                }
            }
        }
    }

    fun clearActiveVideo() {
        activeVideoJob?.cancel()
        _currentActiveVideo.value = null
        _activeVideoError.value = null
        _selectedShortVideo.value = null
    }

    fun logout() {
        MiniPlayerManager.releasePlayer()
        clearActiveVideo()
        authRepository.logout()
    }

    override fun onCleared() {
        authRepository.close()
        super.onCleared()
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is IOException -> "You're offline or the connection is slow. Check your internet and try again."
        is retrofit2.HttpException -> "Server error (${e.code()}). Please try again."
        else -> "Something went wrong. Please try again."
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
