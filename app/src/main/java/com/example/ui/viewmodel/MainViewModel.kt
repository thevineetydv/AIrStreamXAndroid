package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.SubscriptionEntity
import com.example.data.database.WatchHistoryEntity
import com.example.data.database.SearchHistoryEntity
import com.example.data.network.ChannelInfo
import com.example.data.network.Video
import com.example.data.repository.AuthRepository
import com.example.data.repository.LocalUser
import com.example.data.repository.VideoRepository
import com.example.player.MiniPlayerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface FeedUiState {
    object Loading : FeedUiState
    data class Success(val videos: List<Video>) : FeedUiState
    data class Error(val message: String) : FeedUiState
}

sealed interface ChannelUiState {
    object Idle : ChannelUiState
    object Loading : ChannelUiState
    data class Success(val channel: ChannelInfo) : ChannelUiState
    data class Error(val message: String) : ChannelUiState
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val videoRepository = VideoRepository(application)
    private val authRepository = AuthRepository(application)

    // Auth state flow
    val currentUser: StateFlow<LocalUser?> = authRepository.currentUser

    // Home feed flow
    private val _feedState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val feedState: StateFlow<FeedUiState> = _feedState

    // Separate home and shorts variables
    private val _homeVideos = MutableStateFlow<List<Video>>(emptyList())
    val homeVideos: StateFlow<List<Video>> = _homeVideos

    // Available categories
    private val _categories = MutableStateFlow<List<String>>(listOf("All", "Music", "Comedy", "Bhakti", "Cricket"))
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    // Available database channels flow
    private val _availableChannels = MutableStateFlow<List<ChannelInfo>>(emptyList())
    val availableChannels: StateFlow<List<ChannelInfo>> = _availableChannels

    // Shorts feed flow
    private val _shortsVideos = MutableStateFlow<List<Video>>(emptyList())
    val shortsVideos: StateFlow<List<Video>> = _shortsVideos

    // Shorts navigation/state
    private val _selectedShortVideo = MutableStateFlow<Video?>(null)
    val selectedShortVideo: StateFlow<Video?> = _selectedShortVideo

    fun selectShortVideo(video: Video?) {
        _selectedShortVideo.value = video
    }

    // Selected video playing
    private val _currentActiveVideo = MutableStateFlow<Video?>(null)
    val currentActiveVideo: StateFlow<Video?> = _currentActiveVideo

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

    fun insertSearchQuery(query: String) {
        viewModelScope.launch {
            videoRepository.insertSearchQuery(query)
        }
    }

    fun deleteSearchQuery(query: String) {
        viewModelScope.launch {
            videoRepository.deleteSearchQuery(query)
        }
    }

    init {
        loadHomeFeed()
    }

    fun loadHomeFeed(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val hasCachedData = _homeVideos.value.isNotEmpty()
            if (!hasCachedData || forceRefresh) {
                _feedState.value = FeedUiState.Loading
            }
            if (forceRefresh) {
                videoRepository.invalidateCache()
            }
            try {
                val homeList = videoRepository.getNormalVideos()
                val shortsList = videoRepository.getShortsVideos()
                val channelsList = videoRepository.getDistinctChannels()
                
                _homeVideos.value = homeList
                _shortsVideos.value = shortsList.filter { it.duration <= 60.0 }
                _availableChannels.value = channelsList
                _feedState.value = FeedUiState.Success(homeList)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error loading feed", e)
                if (!hasCachedData || forceRefresh) {
                    _feedState.value = FeedUiState.Error(e.message ?: "Failed loading home feed")
                }
            }
        }
    }

    fun selectVideo(video: Video) {
        _currentActiveVideo.value = video
        viewModelScope.launch {
            videoRepository.addToWatchHistory(video)
        }
    }

    fun selectVideoById(publicId: String) {
        viewModelScope.launch {
            try {
                val video = videoRepository.getVideo(publicId)
                _currentActiveVideo.value = video
                videoRepository.addToWatchHistory(video)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error selecting video $publicId", e)
            }
        }
    }

    fun loadChannel(handle: String) {
        viewModelScope.launch {
            _channelState.value = ChannelUiState.Loading
            try {
                delay(300) // add smooth loading transitions
                val info = videoRepository.getChannelInfo(handle)
                _channelState.value = ChannelUiState.Success(info)
            } catch (e: Exception) {
                _channelState.value = ChannelUiState.Error(e.message ?: "Failed to load channel info")
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
            // debouncing search query slightly is nice but direct load is responsive
            val results = videoRepository.searchVideos(query)
            _searchResults.value = results
        }
    }

    // PERF FIX: this used to call loadHomeFeed() after every toggle, which
    // fired getNormalVideos() + getShortsVideos() + getDistinctChannels() —
    // up to 3 redundant network/DB round trips just to reflect one video's
    // new subscription state — and (since the 5-minute metadata cache wasn't
    // invalidated) usually didn't even show the change, since it kept
    // serving the stale cached list underneath. We now patch just the
    // affected video's state in place and refresh only the channel list
    // (a cheap local DB read, needed because subscriber counts changed).
    fun toggleSubscription(video: Video) {
        viewModelScope.launch {
            videoRepository.toggleSubscription(video)
            try {
                val updated = videoRepository.getVideo(video.publicId)
                patchVideoEverywhere(updated)
                _availableChannels.value = videoRepository.getDistinctChannels()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error reloading active video", e)
            }
        }
    }

    // PERF FIX: patches one video's fresh state into every StateFlow that
    // holds a copy of it (active video, home list, shorts list, current feed
    // page) plus the in-memory metadata cache — replacing the old pattern of
    // calling loadHomeFeed() (a full network+DB reload) just to reflect a
    // single like/dislike/subscribe change.
    private fun patchVideoEverywhere(updated: Video) {
        if (_currentActiveVideo.value?.publicId == updated.publicId) {
            _currentActiveVideo.value = updated
        }
        _homeVideos.update { list -> list.map { if (it.publicId == updated.publicId) updated else it } }
        _shortsVideos.update { list -> list.map { if (it.publicId == updated.publicId) updated else it } }
        val current = _feedState.value
        if (current is FeedUiState.Success) {
            _feedState.value = FeedUiState.Success(
                current.videos.map { if (it.publicId == updated.publicId) updated else it }
            )
        }
        videoRepository.patchCachedVideo(updated)
    }

    fun toggleSubscriptionByChannel(channel: ChannelInfo) {
        viewModelScope.launch {
            videoRepository.toggleSubscriptionByChannel(channel)
            loadChannel(channel.handle)
            loadHomeFeed()
        }
    }

    fun toggleLikeVideo(video: Video) {
        viewModelScope.launch {
            try {
                val updated = videoRepository.toggleLikeVideo(video.publicId)
                if (updated != null) patchVideoEverywhere(updated)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error toggling like", e)
            }
        }
    }

    fun toggleDislikeVideo(video: Video) {
        viewModelScope.launch {
            try {
                val updated = videoRepository.toggleDislikeVideo(video.publicId)
                if (updated != null) patchVideoEverywhere(updated)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error toggling dislike", e)
            }
        }
    }

    fun isSubscribed(handle: String): Flow<Boolean> {
        val cleanHandle = handle.removePrefix("@")
        return videoRepository.isSubscribedLocally(cleanHandle)
    }

    // NOTE: removed getChannelSubscriberCount(handle) — it fired a full
    // getChannelInfo() network call every time VideoPlayerScreen opened a
    // video or its subscription state changed, just to read a number that
    // was already sitting on Video.subscriberCount (kept in sync locally by
    // adjustChannelSubscriberCountInDb). VideoPlayerScreen now reads
    // video.subscriberCount directly — see PERF FIX comment there.

    fun signIn(email: String, displayName: String?, pass: String, isSignUp: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = if (isSignUp) {
                authRepository.signUpWithEmail(email, displayName ?: email.substringBefore("@"), pass)
            } else {
                authRepository.signInWithEmail(email, pass)
            }
            if (result.isSuccess) {
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "Authentication failed")
            }
        }
    }

    fun signInWithGoogle(idToken: String?, email: String, displayName: String?, photoUrl: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(idToken, email, displayName, photoUrl)
            if (result.isSuccess) {
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "Google Authentication failed")
            }
        }
    }

    fun signInAnonymously(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.signInAnonymously()
            if (result.isSuccess) {
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "Anonymous Authentication failed")
            }
        }
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

    // FIX: A null/absent user (never signed in) must be treated as a guest —
    // previously this returned false for that case, which meant an
    // unauthenticated user could fall through the isGuest() gate on like/
    // dislike/subscribe/comment actions instead of seeing the sign-in
    // prompt, and would then hit an authenticated-only backend call and fail.
    fun isGuest(): Boolean {
        val user = currentUser.value ?: return true
        return user.email == "guest@airstreamx.com" || authRepository.isFirebaseAnonymous()
    }

    fun clearHistory() {
        viewModelScope.launch {
            videoRepository.clearHistory()
        }
    }

    fun uploadVideo(video: Video, localUriString: String? = null, onComplete: () -> Unit) {
        viewModelScope.launch {
            val token = authRepository.getFirebaseToken()
            videoRepository.uploadVideo(video, localUriString, token)
            loadHomeFeed()
            onComplete()
        }
    }

    fun clearActiveVideo() {
        _currentActiveVideo.value = null
        _selectedShortVideo.value = null
    }

    fun logout() {
        MiniPlayerManager.releasePlayer()
        clearActiveVideo()
        authRepository.logout()
    }
}