package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll   // 🔧 FIX: engagement row overflow
import androidx.compose.foundation.rememberScrollState // 🔧 FIX: engagement row overflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Share       // ✅ FIX #3: Correct import
import androidx.compose.material.icons.filled.Download   // ✅ FIX #3: Correct import
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.network.Video
import com.example.player.MiniPlayerManager
import com.example.ui.viewmodel.ActiveVideoError
import com.example.ui.viewmodel.FeedUiState
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onChannelClick: (String) -> Unit,
    onVideoClick: (Video) -> Unit
) {
    val currentVideo by viewModel.currentActiveVideo.collectAsState()
    val feedState by viewModel.feedState.collectAsState()
    val activeError by viewModel.activeVideoError.collectAsState()
    val playerContext = LocalContext.current

    val video = currentVideo
    if (video == null || activeError == ActiveVideoError.NotFound) {
        // Loading a video we only know the id of, or the server said 404
        // ("Video not found" — also returned for other people's private videos).
        BackHandler { onBackClick() }
        PlayerStatusScreen(
            error = activeError,
            onBackClick = onBackClick,
            onRetry = { viewModel.retryActiveVideo() }
        )
        return
    }

    // MINI PLAYER hand-off: HlsVideoPlayer reports its live ExoPlayer here via
    // onPlayerReady so the back-button handler below can read the exact
    // playback position at the moment of navigating away, and continue it in
    // MiniPlayerManager's floating player instead of just stopping cold.
    // (See MiniPlayerManager doc comment — it owns a separate player instance
    // by design, since it must keep playing after this whole screen and its
    // own local player are disposed.)
    var liveExoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Calculate Next and Previous playlists dynamically from Feed list
    val videoList = remember(feedState) {
        (feedState as? FeedUiState.Success)?.videos ?: emptyList()
    }
    val currentIndex = remember(videoList, video) {
        videoList.indexOfFirst { it.publicId == video.publicId }
    }
    val onNextClick = remember(videoList, currentIndex) {
        if (currentIndex != -1 && currentIndex < videoList.lastIndex) {
            { onVideoClick(videoList[currentIndex + 1]) }
        } else null
    }
    val nextVideo = remember(videoList, currentIndex) {
        if (currentIndex != -1 && currentIndex < videoList.lastIndex) {
            videoList[currentIndex + 1]
        } else null
    }
    val onPreviousClick = remember(videoList, currentIndex) {
        if (currentIndex > 0) {
            { onVideoClick(videoList[currentIndex - 1]) }
        } else null
    }

    val isSubscribedState = viewModel.isSubscribed(video.handle).collectAsState(initial = false)
    val isSubscribed = isSubscribedState.value

    var isFullscreen by remember { mutableStateOf(false) }
    val activity = LocalContext.current.findActivity()

    // Fullscreen landscape/rotation, status-bar and back-swipe controller
    DisposableEffect(isFullscreen) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                controller.hide(WindowInsetsCompat.Type.statusBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    val handleBackOrMinimize: () -> Unit = {
        val player = liveExoPlayer
        if (player != null && player.isPlaying) {
            MiniPlayerManager.playVideo(
                video = video,
                nextVideo = nextVideo,
                context = playerContext,
                startPositionMs = player.currentPosition
            )
            MiniPlayerManager.minimize()
        }
        onBackClick()
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            handleBackOrMinimize()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .testTag("video_player_screen")
    ) {
        // Unified single player instance: dynamically changes size between 16:9 inline
        // and full-screen without destroying ExoPlayer or releasing MediaCodec resources.
        val playerModifier = if (isFullscreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        }

        if (video.isPlayable()) {
            HlsVideoPlayer(
                videoUrl = video.url,
                modifier = playerModifier,
                playWhenReady = true,
                showControls = true,
                isFullscreen = isFullscreen,
                onFullscreenToggle = { isFullscreen = !isFullscreen },
                onBackClick = {
                    if (isFullscreen) {
                        isFullscreen = false
                    } else {
                        handleBackOrMinimize()
                    }
                },
                onNextClick = onNextClick,
                onPreviousClick = onPreviousClick,
                nextVideoUrl = nextVideo?.takeIf { it.isPlayable() }?.url,
                posterUrl = video.thumbnail,
                onPlayerReady = { liveExoPlayer = it }
            )
        } else {
            // status != "ready" (still transcoding) — don't hand a missing/partial
            // stream to the player.
            ProcessingPlaceholder(thumbnailUrl = video.thumbnail, modifier = playerModifier)
        }

        if (!isFullscreen) {
            // Scrollable content below the player
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 72.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Title
                        Text(
                            text = video.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 21.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Views & publish info — real data from DB ✅
                        Text(
                            text = "${formatViews(video.views)} views • ${timeAgo(video.createdAt)}",
                            fontSize = 12.sp,
                            color = Color(0xFFA0A0A0)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // ── Engagement Buttons Row ────────────────────────
                        // Matches design exactly: 4 pill chips — Likes, Share,
                        // Download, Clip. (Design has no separate Dislike chip;
                        // the toggleDislikeVideo action still exists on the
                        // ViewModel for later use, just not shown here.)
                        // 🔧 FIX: 4 pills with text labels ("0 Likes", "Share",
                        // "Download", "Clip") don't fit on narrow screens when
                        // laid out with plain fillMaxWidth() — the last pill
                        // (Clip) gets squeezed to near-zero width and its text
                        // wraps into a tall vertical sliver instead of a normal
                        // pill. horizontalScroll lets all 4 render at full size
                        // and scroll into view instead of being crushed, and
                        // EngagementPill's Text is now locked to a single line
                        // so it can never wrap vertically regardless of width.
                        val shareContext = LocalContext.current
                        val engagementScrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(engagementScrollState)
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            EngagementPill(
                                icon = Icons.Default.ThumbUp,
                                label = "${formatViews(video.likes)} Likes",
                                tint = if (video.isLiked) Color(0xFFFF5A5F) else Color.White,
                                onClick = {
                                    if (viewModel.isGuest()) viewModel.showSignInPrompt()
                                    else viewModel.toggleLikeVideo(video)
                                }
                            )
                            EngagementPill(
                                icon = Icons.Default.Share,
                                label = "Share",
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Watch ${video.title} on AirStreamX\n${video.shareUrl()}"
                                        )
                                        type = "text/plain"
                                    }
                                    shareContext.startActivity(Intent.createChooser(sendIntent, "Share Video"))
                                }
                            )
                            EngagementPill(
                                icon = Icons.Default.Download,
                                label = "Download",
                                onClick = { /* TODO: Download implementation */ }
                            )
                            EngagementPill(
                                icon = Icons.Default.ContentCut,
                                label = "Clip",
                                onClick = { /* TODO: AI clip generator */ }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // ── Channel info ──────────────────────────────────
                        // PERF FIX: this used to fire a full getChannelInfo()
                        // network request every time a video opened AND every
                        // time the subscribe state changed, just to read a
                        // subscriber count — even though that count was
                        // already sitting right here on `video.subscriberCount`
                        // (kept current locally by adjustChannelSubscriberCountInDb,
                        // and refreshed on the active video after every
                        // toggleSubscription()). No network call needed at all.
                        val subscriberCount = remember(video.subscriberCount) {
                            formatSubscriberCount(video.subscriberCount)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onChannelClick(video.handle) }
                            ) {
                                AsyncImage(
                                    model = video.avatarUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = video.channelName,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = subscriberCount,
                                        fontSize = 12.sp,
                                        color = Color(0xFFA0A0A0)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (viewModel.isGuest()) viewModel.showSignInPrompt()
                                    else viewModel.toggleSubscription(video)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSubscribed) Color.White.copy(alpha = 0.1f)
                                                     else Color(0xFFFF5A5F),
                                    contentColor = if (isSubscribed) Color.White.copy(alpha = 0.9f)
                                                   else Color.White
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .then(
                                        if (isSubscribed)
                                            Modifier.border(
                                                1.dp,
                                                Color.White.copy(alpha = 0.2f),
                                                RoundedCornerShape(20.dp)
                                            )
                                        else Modifier
                                    )
                                    .testTag("subscribe_button")
                            ) {
                                Text(
                                    text = if (isSubscribed) "SUBSCRIBED" else "SUBSCRIBE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ── Tip card ───────────────────────────────────────
                        // Signature feature: direct UPI tipping, no subscriber
                        // minimum, no platform cut on the creator's side.
                        TipCard(video = video)

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // ── Description (expandable) ──────────────────────
                        // Real description from DB, not AI-generated fake text
                        var isDescriptionExpanded by remember { mutableStateOf(false) }
                        val descriptionText = remember(video.description) {
                            video.description.ifBlank { "No description available." }
                        }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.05f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                .testTag("video_description_card")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (!isDescriptionExpanded) {
                                    Text(
                                        text = descriptionText,
                                        fontSize = 13.sp,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "...more",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA0A0A0),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                } else {
                                    Text(
                                        text = descriptionText,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Show less",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF5A5F)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Up Next",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Up Next recommendations — real data, sorted by views ✅
                when (val state = feedState) {
                    is FeedUiState.Success -> {
                        val recommendations = state.videos
                            .filter { it.publicId != video.publicId }
                            .sortedByDescending { it.views }

                        items(recommendations) { item ->
                            RecommendedVideoRow(
                                video = item,
                                onClick = { onVideoClick(item) }
                            )
                        }
                    }
                    else -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFFFF5A5F))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Engagement Pill (Likes/Share/Download/Clip chip) ───────────────────────────
@Composable
fun EngagementPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .testTag("engagement_pill_$label"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        // 🔧 FIX: single-line + no soft-wrap guarantees this pill can never
        // stretch tall, even if its available width is ever constrained.
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

// ── Recommended Video Row ──────────────────────────────────────────────────────
@Composable
fun RecommendedVideoRow(
    video: Video,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(84.dp)
                .background(Color(0xFF141414), shape = RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.8f), shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatDuration(video.duration),
                    fontSize = 10.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = video.channelName,
                fontSize = 11.sp,
                color = Color(0xFFA0A0A0)
            )
            Text(
                text = "${formatViews(video.views)} views",
                fontSize = 11.sp,
                color = Color(0xFFA0A0A0)
            )
        }
    }
}