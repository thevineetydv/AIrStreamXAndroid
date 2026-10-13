package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.network.Video
import com.example.ui.viewmodel.MainViewModel
import androidx.media3.ui.AspectRatioFrameLayout

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(
    viewModel: MainViewModel,
    onChannelClick: (String) -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    val shortsVideos by viewModel.shortsVideos.collectAsState()
    val selectedShortVideo by viewModel.selectedShortVideo.collectAsState()

    if (shortsVideos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0A)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFDC2626))
        }
        return
    }

    val initialPage = remember(selectedShortVideo, shortsVideos) {
        shortsVideos.indexOfFirst { it.publicId == selectedShortVideo?.publicId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { shortsVideos.size }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("shorts_player_screen")
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            if (page in shortsVideos.indices) {
                val video = shortsVideos[page]
                val isFocused = pagerState.currentPage == page

                val nextVideo = shortsVideos.getOrNull(page + 1)

                ShortsPageItem(
                    video = video,
                    isFocused = isFocused,
                    viewModel = viewModel,
                    onChannelClick = { onChannelClick(video.handle) },
                    nextVideoUrl = nextVideo?.url
                )
            }
        }

        // Back button to return back to previous screen
        IconButton(
            onClick = {
                viewModel.selectShortVideo(null)
                onBackClick?.invoke()
            },
            modifier = Modifier
                .padding(top = 16.dp, start = 16.dp)
                .statusBarsPadding()
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.4f), shape = CircleShape)
                .testTag("shorts_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // "LIVE ₹ TIPS ON" badge — signals tipping works on Shorts too,
        // matching the design mockup's Shorts header.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .statusBarsPadding()
                .background(Color(0xFFFF5A5F), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("shorts_live_tips_badge")
        ) {
            Text(
                "LIVE ₹ TIPS ON",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun ShortGridCard(
    video: Video,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable(onClick = onClick)
            .testTag("short_grid_card_${video.publicId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141414))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Fill background with Crop content scale
            AsyncImage(
                model = video.thumbnail,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Spacing ambient bottom gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(90.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // Video Title at the bottom of the card
            Text(
                text = video.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            )

            // 3-dots menu icon top right of each card
            IconButton(
                onClick = { /* Card specific menu options */ },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .background(Color.Black.copy(alpha = 0.40f), shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ShortsPageItem(
    video: Video,
    isFocused: Boolean,
    viewModel: MainViewModel,
    onChannelClick: () -> Unit,
    nextVideoUrl: String? = null
) {
    val context = LocalContext.current
    val mockComments = remember(video.publicId) { (video.views / 80).coerceAtLeast(12) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Full screen video player
        if (isFocused) {
            HlsVideoPlayer(
                videoUrl = video.url,
                modifier = Modifier.fillMaxSize(),
                playWhenReady = true,
                showControls = false, // YouTube Shorts hides normal play overlay controls
                isLooping = true,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                nextVideoUrl = nextVideoUrl
            )
        } else {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Ambient dark fading overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        // Right-side Youtube Style actions column
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 70.dp, end = 12.dp)
                .width(60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Like with count
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        if (viewModel.isGuest()) {
                            viewModel.showSignInPrompt()
                        } else {
                            viewModel.toggleLikeVideo(video)
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), shape = CircleShape)
                        .testTag("short_like_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = "Like",
                        tint = if (video.isLiked) Color(0xFFDC2626) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = formatViews(video.likes),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Dislike
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        if (viewModel.isGuest()) {
                            viewModel.showSignInPrompt()
                        } else {
                            viewModel.toggleDislikeVideo(video)
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), shape = CircleShape)
                        .testTag("short_dislike_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ThumbDown,
                        contentDescription = "Dislike",
                        tint = if (video.isDisliked) Color(0xFFDC2626) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = formatViews(video.dislikes),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Comment count (chat icon)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        Toast.makeText(context, "Comments section coming soon!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), shape = CircleShape)
                        .testTag("short_comment_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Comment,
                        contentDescription = "Comments",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = formatViews(mockComments),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Share (arrow icon)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        Toast.makeText(context, "Link copied to share!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), shape = CircleShape)
                        .testTag("short_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "Share",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Remix (loop/refresh icon)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        Toast.makeText(context, "Remix details loaded!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.4f), shape = CircleShape)
                        .testTag("short_remix_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Remix",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "Remix",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Bottom Content Overlay panel
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 24.dp, start = 16.dp, end = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Channel Avatar (circular 40dp)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.DarkGray, shape = CircleShape)
                        .clickable(onClick = onChannelClick)
                ) {
                    AsyncImage(
                        model = video.avatarUrl,
                        contentDescription = "${video.channelName} Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }

                // Channel name bold white
                Text(
                    text = "@${video.handle}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(onClick = onChannelClick)
                )

                // Subscribe button white outlined
                val isSubscribed by viewModel.isSubscribed(video.handle).collectAsState(initial = false)
                OutlinedButton(
                    onClick = {
                        if (viewModel.isGuest()) {
                            viewModel.showSignInPrompt()
                        } else {
                            viewModel.toggleSubscription(video)
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSubscribed) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("short_subscribe_button")
                ) {
                    Text(
                        text = if (isSubscribed) "Subscribed" else "Subscribe",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Video title white
            Text(
                text = video.title,
                fontSize = 14.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
        }
    }
}