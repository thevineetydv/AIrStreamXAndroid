package com.example.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.network.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Visual presentation mode of the single global player.
 *
 * HIDDEN   - nothing is loaded / no active playback session.
 * EXPANDED - full-size player, either inline (portrait, embedded in
 *            VideoPlayerScreen) or fullscreen (landscape, edge-to-edge).
 * MINI     - small floating draggable window (YouTube-style mini player).
 */
enum class PlayerVisualMode { HIDDEN, EXPANDED, MINI }

data class MiniPlayerState(
    val video: Video? = null,
    val nextVideo: Video? = null,
    val mode: PlayerVisualMode = PlayerVisualMode.HIDDEN,
    val isFullscreen: Boolean = false,
    // Persisted drag position of the mini player, in pixels, top-left origin.
    val miniOffsetX: Float = 0f,
    val miniOffsetY: Float = 0f
)

/**
 * App-wide singleton owning ONE ExoPlayer instance.
 *
 * Why a singleton instead of a `remember { ExoPlayer.Builder(...).build() }`
 * inside the composable: Compose disposes remembered objects when their
 * composable leaves the tree. Since the mini player must keep playing while
 * the user navigates to a completely different screen (Home, Search, etc.),
 * the player instance cannot live inside VideoPlayerScreen's composition —
 * it has to live somewhere that outlives screen navigation. This object,
 * combined with MiniPlayerOverlay living at the app root (see integration
 * notes), gives you that.
 */
object MiniPlayerManager {

    private val _uiState = MutableStateFlow(MiniPlayerState())
    val uiState: StateFlow<MiniPlayerState> = _uiState

    private var player: ExoPlayer? = null

    /** Returns the shared player, creating it on first use. */
    fun getOrCreatePlayer(context: Context): ExoPlayer {
        player?.let { return it }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1500, 30_000, 500, 1000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val safeContext = AttributionSafeContextWrapper(context.applicationContext)
        val renderersFactory = DefaultRenderersFactory(safeContext)
            .setEnableDecoderFallback(true)

        val created = ExoPlayer.Builder(safeContext, renderersFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
                setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            }
        player = created
        return created
    }

    fun currentPlayer(): ExoPlayer? = player

    /**
     * Loads and starts a video. If the same video is already loaded (e.g. the
     * user tapped the mini player to re-expand it), playback is left alone —
     * only the visual mode changes.
     *
     * @param startPositionMs used for hand-off from the full-size player (see
     *   VideoPlayerScreen's back-button handler): since this object owns a
     *   completely separate ExoPlayer instance from the one VideoPlayerScreen
     *   was just showing, without this the mini player would restart the
     *   video from 0:00 instead of continuing where the user left off.
     */
    fun playVideo(video: Video, nextVideo: Video?, context: Context, startPositionMs: Long = 0L) {
        val exo = getOrCreatePlayer(context)
        val current = _uiState.value

        if (current.video?.publicId != video.publicId) {
            val mediaItem = MediaItem.Builder()
                .setUri(video.url)
                .setMimeType(
                    if (video.url.contains(".m3u8")) MimeTypes.APPLICATION_M3U8
                    else MimeTypes.APPLICATION_MP4
                )
                .build()
            exo.setMediaItem(mediaItem)
            exo.prepare()
            if (startPositionMs > 0L) {
                exo.seekTo(startPositionMs)
            }
            exo.playWhenReady = true
        }

        _uiState.value = current.copy(
            video = video,
            nextVideo = nextVideo,
            mode = PlayerVisualMode.MINI
        )
    }

    fun minimize() {
        _uiState.value = _uiState.value.copy(mode = PlayerVisualMode.MINI, isFullscreen = false)
    }

    fun expand() {
        player?.pause()
        _uiState.value = _uiState.value.copy(mode = PlayerVisualMode.EXPANDED)
    }

    fun setFullscreen(value: Boolean) {
        _uiState.value = _uiState.value.copy(isFullscreen = value)
    }

    fun updateMiniOffset(x: Float, y: Float) {
        _uiState.value = _uiState.value.copy(miniOffsetX = x, miniOffsetY = y)
    }

    /** Fully stops and clears playback — called when the user swipes the mini player away. */
    fun close() {
        try {
            player?.stop()
            player?.clearMediaItems()
        } catch (e: Exception) {
            android.util.Log.e("MiniPlayerManager", "Error stopping player on close", e)
        }
        _uiState.value = MiniPlayerState()
    }

    /** Call once, e.g. from Application.onTerminate or when truly done with playback for good. */
    fun releasePlayer() {
        try {
            player?.stop()
            player?.clearMediaItems()
            player?.release()
        } catch (e: Exception) {
            android.util.Log.e("MiniPlayerManager", "Error releasing player", e)
        }
        player = null
        _uiState.value = MiniPlayerState()
    }
}