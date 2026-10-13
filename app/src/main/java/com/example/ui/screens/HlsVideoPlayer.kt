package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import kotlin.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.player.AttributionSafeContextWrapper

// Extension function to retrieve the Activity context safely
fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}


// Converts milliseconds to mm:ss or hh:mm:ss format
fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun EngagementButton(
    icon: ImageVector,
    label: String,
    sublabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (sublabel.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color(0xFF606060),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = Color(0xFF606060),
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = sublabel,
                tint = Color(0xFF606060),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = sublabel,
                fontSize = 10.sp,
                color = Color(0xFF606060)
            )
        }
    }
}



@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
@Composable
fun HlsVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
    playWhenReady: Boolean = true,
    showControls: Boolean = true,
    isLooping: Boolean = false,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    isFullscreen: Boolean = false,
    onFullscreenToggle: () -> Unit = {},
    onBackClick: (() -> Unit)? = null,
    onPlayerError: (() -> Unit)? = null,
    onNextClick: (() -> Unit)? = null,
    onPreviousClick: (() -> Unit)? = null,
    nextVideoUrl: String? = null,
    // Shown behind the loading spinner on cold start (instead of a blank
    // black box), and again as the backdrop for the error state below.
    posterUrl: String = "",
    // ── Mini-player handoff support ──
    // initialPositionMs: resume from this position (e.g. when expanding back
    // from the mini player after it played for a while).
    // onPlayerReady: exposes the live ExoPlayer instance so the caller (e.g.
    // VideoPlayerScreen) can read currentPosition/isPlaying at handoff time
    // (back navigation → minimize to floating player) without this composable
    // needing to know anything about MiniPlayerManager itself.
    initialPositionMs: Long = 0L,
    onPlayerReady: (ExoPlayer) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isBuffering by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(playWhenReady) }
    var currentPosition by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var bufferedPosition by remember { mutableStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    // Youtube features
    var currentQuality by remember { mutableStateOf("Auto") }
    var currentSpeed by remember { mutableStateOf("1.0x") }
    var autoPlayEnabled by remember { mutableStateOf(true) }
    var isCaptionsEnabled by remember { mutableStateOf(false) }
    var isLoopEnabled by remember { mutableStateOf(isLooping) }

    // Double tap seeking ripple trigger overlays
    var showLeftSeekIndicator by remember { mutableStateOf(false) }
    var showRightSeekIndicator by remember { mutableStateOf(false) }

    val playerContext = remember(context) {
        val base = context.findActivity() ?: context
        AttributionSafeContextWrapper(base)
    }

    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1500,  // minBufferMs: Start play as soon as 1.5 seconds is buffered (low initial startup lag)
                30000, // maxBufferMs: Maximum buffered duration 30s
                500,   // bufferForPlaybackMs: Minimal buffer request for manual seek play
                1000   // bufferForPlaybackAfterRebufferMs: Minimal buffer request for re-seeking/rebuffering restoration
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(playerContext)
            .setEnableDecoderFallback(true)

        ExoPlayer.Builder(playerContext, renderersFactory)
            .setLoadControl(loadControl)
            .build().apply {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
                setAudioAttributes(audioAttributes, false)
                repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                this.playWhenReady = playWhenReady
            }
    }

    // Performance telemetry states
    var videoLoadStartTime by remember { mutableStateOf(0L) }
    var videoStartupLogged by remember { mutableStateOf(false) }
    var totalBufferCount by remember { mutableStateOf(0) }

    val currentOnPlayerError by rememberUpdatedState(onPlayerError)

    // Manage player listener safely with proper attachment and disposal
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                totalDuration = exoPlayer.duration.coerceAtLeast(0L)

                if (playbackState == Player.STATE_BUFFERING) {
                    totalBufferCount++
                    android.util.Log.d(
                        "VideoPlaybackMonitor", 
                        "STATE_BUFFERING: Cumulative buffer count expanded to $totalBufferCount for URL=$videoUrl"
                    )
                } else if (playbackState == Player.STATE_READY) {
                    if (!videoStartupLogged && videoLoadStartTime > 0L) {
                        videoStartupLogged = true
                        val startupTime = System.currentTimeMillis() - videoLoadStartTime
                        android.util.Log.i(
                            "VideoPlaybackMonitor", 
                            "PERF_LOG: Video READY. STARTUP_TIME_MS=${startupTime}ms, TOTAL_BUFFERS_BEFORE_READY=$totalBufferCount for URL=$videoUrl"
                        )
                    }
                }

                if (playbackState == Player.STATE_ENDED && autoPlayEnabled && onNextClick != null) {
                    onNextClick.invoke()
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing && !videoStartupLogged && videoLoadStartTime > 0L) {
                    videoStartupLogged = true
                    val startupTime = System.currentTimeMillis() - videoLoadStartTime
                    android.util.Log.i(
                        "VideoPlaybackMonitor", 
                        "PERF_LOG: Playback STARTED. STARTUP_TIME_MS=${startupTime}ms, BUFFER_REBUFFERS=$totalBufferCount for URL=$videoUrl"
                    )
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && onNextClick != null) {
                    onNextClick.invoke()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                hasError = true
                isBuffering = false
                currentOnPlayerError?.invoke()
                android.util.Log.e(
                    "VideoPlaybackMonitor", 
                    "PLAYBACK_ERROR: Failed to play URL=$videoUrl, errorMessage=${error.message}", 
                    error
                )
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Update media item or preload queue when URL or Next URL changes
    LaunchedEffect(videoUrl, nextVideoUrl) {
        val currentMediaItem = exoPlayer.currentMediaItem
        val currentUriStr = currentMediaItem?.localConfiguration?.uri?.toString()

        if (currentUriStr != null && currentUriStr == videoUrl) {
            // Already preparing or playing the correct videoUrl.
            // Just clean up past items in queue & update the subsequent preload item.
            while (exoPlayer.currentMediaItemIndex > 0) {
                exoPlayer.removeMediaItem(0)
            }
            if (!nextVideoUrl.isNullOrEmpty()) {
                val nextMediaItem = MediaItem.Builder()
                    .setUri(nextVideoUrl)
                    .setMimeType(
                        if (nextVideoUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 
                        else MimeTypes.APPLICATION_MP4
                    )
                    .build()
                
                if (exoPlayer.mediaItemCount > 1) {
                    val existingSecondItem = exoPlayer.getMediaItemAt(1)
                    if (existingSecondItem.localConfiguration?.uri?.toString() != nextVideoUrl) {
                        exoPlayer.removeMediaItem(1)
                        exoPlayer.addMediaItem(1, nextMediaItem)
                        android.util.Log.i("VideoPlaybackMonitor", "PRELOAD_QUEUE: Transition match. Replaced next preload video with URL=$nextVideoUrl")
                    }
                } else {
                    exoPlayer.addMediaItem(nextMediaItem)
                    android.util.Log.i("VideoPlaybackMonitor", "PRELOAD_QUEUE: Transition match. Appended next preload video URL=$nextVideoUrl")
                }
            } else if (exoPlayer.mediaItemCount > 1) {
                exoPlayer.removeMediaItem(1)
            }
        } else {
            // New videoUrl or full reset needed
            currentPosition = 0L
            bufferedPosition = 0L
            isBuffering = true
            hasError = false
            currentQuality = "Auto"
            currentSpeed = "1.0x"
            
            // Reset Performance Telemetry for new video
            videoLoadStartTime = System.currentTimeMillis()
            videoStartupLogged = false
            totalBufferCount = 0
            android.util.Log.i("VideoPlaybackMonitor", "INIT_LOAD: Initiated load process for URL=$videoUrl, nextPreloadURL=$nextVideoUrl")

            exoPlayer.setPlaybackSpeed(1.0f)
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                .clearVideoSizeConstraints()
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(videoUrl)
                .setMimeType(
                    if (videoUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 
                    else MimeTypes.APPLICATION_MP4
                )
                .build()
            
            exoPlayer.setMediaItem(mediaItem)
            
            if (!nextVideoUrl.isNullOrEmpty()) {
                val nextMediaItem = MediaItem.Builder()
                    .setUri(nextVideoUrl)
                    .setMimeType(
                        if (nextVideoUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 
                        else MimeTypes.APPLICATION_MP4
                    )
                    .build()
                exoPlayer.addMediaItem(nextMediaItem)
                android.util.Log.i("VideoPlaybackMonitor", "PRELOAD_QUEUE: Preloading queue configured. Main URL=$videoUrl, Next preloading URL=$nextVideoUrl")
            }
            
            exoPlayer.prepare()
            if (initialPositionMs > 0L) {
                exoPlayer.seekTo(initialPositionMs)
            }
        }
    }

    // Expose the live player instance once, for callers that need to read
    // its position/state at handoff time (see initialPositionMs doc above).
    LaunchedEffect(exoPlayer) {
        onPlayerReady(exoPlayer)
    }

    // Playback progress polling loop.
    // PERF NOTE: only runs while isPlaying (already the case before this
    // change) — paused/idle video does zero polling. Also now tracks
    // bufferedPosition so the progress bar can render a real buffer
    // indicator instead of just current/total.
    LaunchedEffect(exoPlayer, isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            bufferedPosition = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            delay(200)
        }
    }

    // Handle play state changes externally
    LaunchedEffect(playWhenReady) {
        exoPlayer.playWhenReady = playWhenReady
    }

    // Dispose player cleanly
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Handle auto-hide controls after 3 seconds of inactivity
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(controlsVisible, lastInteractionTime, isPlaying) {
        if (controlsVisible && isPlaying && showControls) {
            delay(3000)
            if (System.currentTimeMillis() - lastInteractionTime >= 3000) {
                controlsVisible = false
            }
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        // ExoPlayer View layer
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // Hide default controls
                    this.resizeMode = resizeMode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.useController = false
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // GestureDetector Overlay layer to capture single and double taps
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(videoUrl) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            val width = size.width
                            val fraction = offset.x / width
                            if (fraction < 0.5f) {
                                // Double tap left: Rewind 10s
                                val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                                showLeftSeekIndicator = true
                                coroutineScope.launch {
                                    delay(650)
                                    showLeftSeekIndicator = false
                                }
                            } else {
                                // Double tap right: Fast Forward 10s
                                val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(totalDuration)
                                exoPlayer.seekTo(newPos)
                                currentPosition = newPos
                                showRightSeekIndicator = true
                                coroutineScope.launch {
                                    delay(650)
                                    showRightSeekIndicator = false
                                }
                            }
                        },
                        onTap = {
                            if (showControls) {
                                if (controlsVisible) {
                                    controlsVisible = false
                                } else {
                                    lastInteractionTime = System.currentTimeMillis()
                                    controlsVisible = true
                                }
                            }
                        }
                    )
                }
        )

        // Custom Double Tap seek visual indicators (Left half)
        AnimatedVisibility(
            visible = showLeftSeekIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.42f)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent)
                        ),
                        shape = RoundedCornerShape(topEndPercent = 100, bottomEndPercent = 100)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "10s",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Custom Double Tap seek visual indicators (Right half)
        AnimatedVisibility(
            visible = showRightSeekIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.42f)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.20f))
                        ),
                        shape = RoundedCornerShape(topStartPercent = 100, bottomStartPercent = 100)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "10s",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // NOTE: this used to render a hardcoded fake caption line
        // ("[Captioning: Streaming high definition live content...]") for
        // EVERY video whenever CC was toggled on, regardless of whether that
        // video actually has captions — there is no caption/subtitle track
        // in the Video model at all, so this was pure misleading UI (a user
        // could "turn on" captions that were never real). Removed. The CC
        // button below still exists as a control (per spec), but now tells
        // the user honestly that captions aren't available for this video
        // instead of faking a transcript. Wiring real captions requires the
        // backend to actually serve a subtitle track per video — see
        // REMAINING section of the audit for that.

        // Poster/thumbnail state — shown on cold start (before the first
        // frame has had a chance to render, i.e. still buffering at
        // position 0) instead of a blank black box, and again as the
        // backdrop behind the error state below.
        val showPoster = posterUrl.isNotBlank() && (hasError || (isBuffering && currentPosition == 0L))
        if (showPoster) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Dim the poster slightly so the spinner/error content on top
            // of it stays readable.
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }

        if (isBuffering && !hasError) {
            CircularProgressIndicator(
                color = Color(0xFFFF5A5F),
                modifier = Modifier.align(Alignment.Center).testTag("video_buffering_indicator")
            )
        }

        // Error state — "Unable to play this video" + Try Again, per spec.
        // Replaces the old behavior of silently logging the error while the
        // user stared at a stuck spinner or a blank player forever.
        if (hasError) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .testTag("video_error_state"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Unable to play this video",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        hasError = false
                        isBuffering = true
                        exoPlayer.prepare()
                        exoPlayer.play()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("video_error_retry_button")
                ) {
                    Text(text = "Try Again", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Authentic YouTube controls overlay
        if (showControls) {
            // 1. Semi-transparent black scrim behind all controls
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.50f))
                )
            }

            // 2. Top Bar: Collapse/Back (Left) + Autoplay, CC, Cast, Settings (Right)
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Top-Left: YouTube Collapse / Back button
                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            if (isFullscreen) {
                                onFullscreenToggle()
                            } else {
                                onBackClick?.invoke()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("back_navigation_button")
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isFullscreen) "Exit Fullscreen" else "Collapse Player",
                            tint = Color.White,
                            modifier = Modifier.size(if (isFullscreen) 24.dp else 30.dp)
                        )
                    }

                    // Top-Right: Autoplay toggle, CC, Cast, Settings
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // YouTube Autoplay Switch Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    lastInteractionTime = System.currentTimeMillis()
                                    autoPlayEnabled = !autoPlayEnabled
                                    android.widget.Toast.makeText(
                                        context,
                                        if (autoPlayEnabled) "Auto-play is on" else "Auto-play is off",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(18.dp)
                                    .background(
                                        color = if (autoPlayEnabled) Color(0xFFFF0000) else Color.White.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(9.dp)
                                    ),
                                contentAlignment = if (autoPlayEnabled) Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(2.dp)
                                        .size(14.dp)
                                        .background(Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (autoPlayEnabled) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = null,
                                        tint = if (autoPlayEnabled) Color(0xFFFF0000) else Color(0xFF505050),
                                        modifier = Modifier.size(9.dp)
                                    )
                                }
                            }
                        }

                        // Closed Captions (CC)
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                isCaptionsEnabled = !isCaptionsEnabled
                                android.widget.Toast.makeText(
                                    context,
                                    if (isCaptionsEnabled) "Captions aren't available for this video" else "Captions turned off",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("video_cc_button")
                        ) {
                            Icon(
                                imageVector = if (isCaptionsEnabled) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaption,
                                contentDescription = "Closed Captions",
                                tint = if (isCaptionsEnabled) Color(0xFFFF0000) else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Cast button
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                android.widget.Toast.makeText(
                                    context,
                                    "Searching for devices to cast...",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("video_cast_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = "Cast",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Settings (Gear)
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                showQualitySheet = true
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("video_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Playback Settings",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 3. Center Controls: Rewind 10s | Prominent Play/Pause (64dp circle) | Fast Forward 10s
            AnimatedVisibility(
                visible = controlsVisible && !hasError && !isBuffering,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 44.dp else 24.dp)
                ) {
                    // Optional Previous video (if playlist)
                    if (onPreviousClick != null) {
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                onPreviousClick()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("previous_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous video",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // 10s Rewind Button
                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                            currentPosition = newPos
                            showLeftSeekIndicator = true
                            coroutineScope.launch {
                                delay(650)
                                showLeftSeekIndicator = false
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("rewind_10s_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // YouTube Center Play/Pause 64dp circle
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Black.copy(alpha = 0.60f), shape = CircleShape)
                            .clickable {
                                lastInteractionTime = System.currentTimeMillis()
                                if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                                isPlaying = !isPlaying
                            }
                            .testTag("play_pause_video_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // 10s Fast Forward Button
                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(totalDuration)
                            exoPlayer.seekTo(newPos)
                            currentPosition = newPos
                            showRightSeekIndicator = true
                            coroutineScope.launch {
                                delay(650)
                                showRightSeekIndicator = false
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("forward_10s_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Fast Forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Optional Next video (if playlist)
                    if (onNextClick != null) {
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                onNextClick()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("next_video_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next video",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            // 4. Bottom Controls: Time Indicator, Fullscreen Toggle, and Red Scrubber
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isFullscreen) Modifier.navigationBarsPadding() else Modifier)
                        .padding(bottom = if (isFullscreen) 16.dp else 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Time and Fullscreen Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current time / Total duration (YouTube format: 00:06 / 05:12)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = formatTime(currentPosition),
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("video_current_time")
                            )
                            Text(
                                text = "/",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.50f),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatTime(totalDuration),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("video_total_time")
                            )
                        }

                        // Fullscreen Toggle
                        IconButton(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                onFullscreenToggle()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("fullscreen_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Toggle Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // YouTube Red Scrubber Bar
                    val progressInteractionSource = remember { MutableInteractionSource() }
                    val isPressed by progressInteractionSource.collectIsPressedAsState()
                    val isDragged by progressInteractionSource.collectIsDraggedAsState()
                    val isDragging = isPressed || isDragged
                    val thumbSize by animateDpAsState(
                        targetValue = if (isDragging) 14.dp else 10.dp,
                        label = "ThumbSize"
                    )

                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { newValue ->
                            lastInteractionTime = System.currentTimeMillis()
                            currentPosition = newValue.toLong()
                            exoPlayer.seekTo(newValue.toLong())
                        },
                        valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF0000),
                            activeTrackColor = Color(0xFFFF0000),
                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                        ),
                        interactionSource = progressInteractionSource,
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(thumbSize)
                                    .background(Color(0xFFFF0000), CircleShape)
                            )
                        },
                        track = {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(Color.White.copy(alpha = 0.25f), shape = RoundedCornerShape(1.5.dp))
                            ) {
                                // Buffered progress bar
                                val bufferedFraction = if (totalDuration > 0)
                                    (bufferedPosition.toFloat() / totalDuration).coerceIn(0f, 1f) else 0f
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(bufferedFraction)
                                        .fillMaxHeight()
                                        .background(Color.White.copy(alpha = 0.40f), shape = RoundedCornerShape(1.5.dp))
                                        .testTag("video_buffered_indicator")
                                )
                                // Played progress bar (YouTube Red)
                                val fraction = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .background(Color(0xFFFF0000), shape = RoundedCornerShape(1.5.dp))
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-4).dp)
                            .padding(horizontal = 4.dp)
                            .testTag("video_progress_slider")
                    )
                }
            }
        }

        // 5. Persistent YouTube bottom progress bar when controls are hidden
        if (!controlsVisible && totalDuration > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                val bufferedFraction = (bufferedPosition.toFloat() / totalDuration).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedFraction)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.40f))
                )
                val fraction = (currentPosition.toFloat() / totalDuration).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(Color(0xFFFF0000))
                )
            }
        }

            // Quality Options & Speed Unified Bottom Sheet
            if (showQualitySheet) {
                ModalBottomSheet(
                    onDismissRequest = { showQualitySheet = false },
                    containerColor = Color(0xFF141414),
                    contentColor = Color.White
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = "Playback Settings",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left Section: Video Quality Option
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Quality Limit",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5A5F),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                listOf("Auto", "1080p", "720p", "480p", "360p").forEach { qualityOption ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                currentQuality = qualityOption
                                                val maxProgressiveWidth = when (qualityOption) {
                                                    "1080p" -> 1920
                                                    "720p" -> 1280
                                                    "480p" -> 854
                                                    "360p" -> 640
                                                    else -> Int.MAX_VALUE
                                                }
                                                val maxProgressiveHeight = when (qualityOption) {
                                                    "1080p" -> 1080
                                                    "720p" -> 720
                                                    "480p" -> 480
                                                    "360p" -> 360
                                                    else -> Int.MAX_VALUE
                                                }
                                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                                                    .setMaxVideoSize(maxProgressiveWidth, maxProgressiveHeight)
                                                    .build()
                                                showQualitySheet = false
                                            }
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = qualityOption,
                                            color = if (currentQuality == qualityOption) Color(0xFFFF5A5F) else Color.White,
                                            fontWeight = if (currentQuality == qualityOption) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                        if (currentQuality == qualityOption) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color(0xFFFF5A5F),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(32.dp))

                            // Right Section: Playback Speed Option
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Playback Speed",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5A5F),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                listOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x").forEach { speedOption ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                currentSpeed = speedOption
                                                val speedValue = when (speedOption) {
                                                    "0.5x" -> 0.5f
                                                    "0.75x" -> 0.75f
                                                    "1.25x" -> 1.25f
                                                    "1.5x" -> 1.5f
                                                    "1.75x" -> 1.75f
                                                    "2.0x" -> 2.0f
                                                    else -> 1.0f
                                                }
                                                exoPlayer.setPlaybackSpeed(speedValue)
                                                showQualitySheet = false
                                            }
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (speedOption == "1.0x") "Normal" else speedOption,
                                            color = if (currentSpeed == speedOption) Color(0xFFFF5A5F) else Color.White,
                                            fontWeight = if (currentSpeed == speedOption) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                        if (currentSpeed == speedOption) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color(0xFFFF5A5F),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }