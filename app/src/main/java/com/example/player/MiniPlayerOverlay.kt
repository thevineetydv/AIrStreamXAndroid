package com.example.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.network.Video
import kotlin.math.roundToInt

private val MINI_WIDTH = 168.dp
private val MINI_HEIGHT = 94.dp // 16:9
private val EDGE_MARGIN = 8.dp
private const val CLOSE_DRAG_THRESHOLD_DP = 120f

/**
 * Renders the floating mini player when MiniPlayerManager's mode is MINI.
 * Renders nothing otherwise — safe to always place at the root of your UI.
 *
 * Integration: place this as the LAST child inside the root Box that also
 * contains your NavHost, e.g. in MainActivity's setContent:
 *
 *   Box(Modifier.fillMaxSize()) {
 *       AppNavHost(...)
 *       MiniPlayerOverlay(onExpandRequested = { navController.navigate("player") })
 *   }
 *
 * `onExpandRequested` should navigate back to (or push) your VideoPlayerScreen
 * route — MiniPlayerManager.expand() is already called internally on tap.
 */
@Composable
fun MiniPlayerOverlay(
    onExpandRequested: (Video) -> Unit = {}
) {
    val state by MiniPlayerManager.uiState.collectAsState()
    val currentVideo = state.video
    if (state.mode != PlayerVisualMode.MINI || currentVideo == null) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val exoPlayer = remember(currentVideo.publicId) { MiniPlayerManager.getOrCreatePlayer(context) }

    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var isBuffering by remember { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val miniWidthPx = with(density) { MINI_WIDTH.toPx() }
        val miniHeightPx = with(density) { MINI_HEIGHT.toPx() }
        val edgeMarginPx = with(density) { EDGE_MARGIN.toPx() }

        // Initialize position to bottom-right on first appearance if unset.
        LaunchedEffect(Unit) {
            if (state.miniOffsetX == 0f && state.miniOffsetY == 0f) {
                MiniPlayerManager.updateMiniOffset(
                    x = screenWidthPx - miniWidthPx - edgeMarginPx,
                    y = screenHeightPx - miniHeightPx - edgeMarginPx * 6 // clear of bottom nav bar
                )
            }
        }

        var dragOffsetX by remember { mutableStateOf(state.miniOffsetX) }
        var dragOffsetY by remember { mutableStateOf(state.miniOffsetY) }
        var verticalDragAccum by remember { mutableStateOf(0f) }
        var isDragging by remember { mutableStateOf(false) }

        // Keep local drag offset in sync when the manager's stored offset changes externally.
        LaunchedEffect(state.miniOffsetX, state.miniOffsetY) {
            if (!isDragging) {
                dragOffsetX = state.miniOffsetX
                dragOffsetY = state.miniOffsetY
            }
        }

        val animatedX by animateFloatAsState(
            targetValue = dragOffsetX,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "miniOffsetX"
        )
        val animatedY by animateFloatAsState(
            targetValue = dragOffsetY,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "miniOffsetY"
        )

        // Fade the whole mini player out as the user drags it down to dismiss.
        val dismissAlpha = 1f - (verticalDragAccum / with(density) { CLOSE_DRAG_THRESHOLD_DP.dp.toPx() }).coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
                .size(MINI_WIDTH, MINI_HEIGHT)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            verticalDragAccum = 0f
                        },
                        onDragEnd = {
                            isDragging = false
                            val closeThresholdPx = with(density) { CLOSE_DRAG_THRESHOLD_DP.dp.toPx() }
                            if (verticalDragAccum > closeThresholdPx) {
                                MiniPlayerManager.close()
                                return@detectDragGestures
                            }
                            verticalDragAccum = 0f

                            // Snap horizontally to nearest edge.
                            val screenMid = screenWidthPx / 2f
                            val playerMid = dragOffsetX + miniWidthPx / 2f
                            val snappedX = if (playerMid < screenMid) {
                                edgeMarginPx
                            } else {
                                screenWidthPx - miniWidthPx - edgeMarginPx
                            }
                            val clampedY = dragOffsetY.coerceIn(
                                edgeMarginPx,
                                screenHeightPx - miniHeightPx - edgeMarginPx
                            )
                            dragOffsetX = snappedX
                            dragOffsetY = clampedY
                            MiniPlayerManager.updateMiniOffset(snappedX, clampedY)
                        },
                        onDragCancel = {
                            isDragging = false
                            verticalDragAccum = 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        dragOffsetX = (dragOffsetX + dragAmount.x)
                            .coerceIn(0f, screenWidthPx - miniWidthPx)
                        dragOffsetY += dragAmount.y
                        if (dragAmount.y > 0) {
                            verticalDragAccum += dragAmount.y
                        } else {
                            verticalDragAccum = (verticalDragAccum + dragAmount.y).coerceAtLeast(0f)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (!isDragging) {
                                // VideoPlayerScreen mounts its OWN separate
                                // ExoPlayer instance (see HlsVideoPlayer) when it
                                // recomposes for this video, so without pausing
                                // this one first, both players would briefly play
                                // the same video's audio at once. This is a
                                // deliberate simplification: the inline player
                                // currently restarts the video rather than
                                // resuming from this exact position — see
                                // HlsVideoPlayer's initialPositionMs param (built
                                // for exactly this handoff, not yet threaded
                                // through VideoPlayerScreen) for the follow-up
                                // to make it frame-perfect.
                                exoPlayer.pause()
                                MiniPlayerManager.expand()
                                onExpandRequested(currentVideo)
                            }
                        }
                    )
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isBuffering) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.Center)
                )
            }

            // Minimal transport controls: play/pause + close.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { MiniPlayerManager.close() },
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Title strip at bottom for context.
            state.video?.let { video ->
                Text(
                    text = video.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}
