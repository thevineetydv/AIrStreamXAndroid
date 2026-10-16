package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.network.ChannelInfo
import com.example.data.network.Video
import com.example.ui.viewmodel.ChannelUiState
import com.example.ui.viewmodel.MainViewModel

@Composable
fun ChannelScreen(
    viewModel: MainViewModel,
    handle: String,
    onBackClick: () -> Unit,
    onVideoClick: (Video) -> Unit
) {
    val channelState by viewModel.channelState.collectAsState()

    // Trigger loading channel details
    LaunchedEffect(handle) {
        viewModel.loadChannel(handle)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .testTag("channel_screen")
    ) {
        // Simple Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.testTag("channel_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Channel",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        when (val state = channelState) {
            is ChannelUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFDC2626))
                }
            }
            is ChannelUiState.Success -> {
                ChannelDetailsContent(
                    channel = state.channel,
                    viewModel = viewModel,
                    onVideoClick = onVideoClick
                )
            }
            is ChannelUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Failed to load channel info: ${state.message}",
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = { viewModel.loadChannel(handle) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Text("Retry")
                    }
                }
            }
            is ChannelUiState.Idle -> {}
        }
    }
}

@Composable
fun ChannelDetailsContent(
    channel: ChannelInfo,
    viewModel: MainViewModel,
    onVideoClick: (Video) -> Unit
) {
    val isSubscribedState = viewModel.isSubscribed(channel.handle).collectAsState(initial = false)
    val isSubscribed = isSubscribedState.value

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Channel stats banner
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = channel.avatarUrl,
                contentDescription = channel.channelName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = channel.channelName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "@${channel.handle.removePrefix("@")}",
                fontSize = 14.sp,
                color = Color(0xFFA0A0A0),
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subscriber metric. PRIVACY: the creator's email is never shown.
            Text(
                text = "${formatSubscriberCount(channel.subscriberCount)} • ${channel.videos.size} ${if (channel.videos.size == 1) "video" else "videos"}",
                fontSize = 13.sp,
                color = Color(0xFFA0A0A0)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Subscribe Button
            Button(
                onClick = {
                    if (viewModel.isGuest()) {
                        viewModel.showSignInPrompt()
                    } else {
                        viewModel.toggleSubscriptionByChannel(channel)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSubscribed) Color.White.copy(alpha = 0.1f) else Color(0xFFFF5A5F),
                    contentColor = if (isSubscribed) Color.White.copy(alpha = 0.9f) else Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .then(
                        if (isSubscribed) Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)) else Modifier
                    )
                    .testTag("channel_subscribe_button")
            ) {
                Text(
                    text = if (isSubscribed) "Subscribed" else "Subscribe",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(color = Color(0xFF1F1F1F))

        // Videos Section Header
        Text(
            text = "Videos",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp)
        )

        // Videos Grid (Adaptive columns responsive layout)
        if (channel.videos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No uploads published yet", color = Color(0xFFA0A0A0), fontSize = 14.sp)
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val columns = when {
                    maxWidth < 480.dp -> 1
                    maxWidth < 720.dp -> 2
                    maxWidth < 1000.dp -> 3
                    else -> 4
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(
                        start = if (columns == 1) 16.dp else 12.dp,
                        end = if (columns == 1) 16.dp else 12.dp,
                        bottom = 80.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(if (columns == 1) 0.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (columns == 1) 16.dp else 12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(channel.videos) { video ->
                        ChannelVideoGridItem(
                            video = video,
                            onClick = { onVideoClick(video) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelVideoGridItem(
    video: Video,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = video.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Duration tag
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
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

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = video.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "${formatViews(video.views)} views • ${timeAgo(video.createdAt)}",
            fontSize = 11.sp,
            color = Color(0xFFA0A0A0),
            modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 4.dp)
        )
    }
}
