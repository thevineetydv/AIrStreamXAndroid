package com.example.ui.screens
 
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import com.example.data.network.Video
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.ui.viewmodel.FeedUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ShortsUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
 
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onVideoClick: (Video) -> Unit,
    onSearchClick: () -> Unit,
    onChannelClick: (String) -> Unit,
    onUploadClick: () -> Unit,
    onProfileClick: () -> Unit = {}
) {
    val feedState by viewModel.feedState.collectAsState()
    val shortsState by viewModel.shortsState.collectAsState()
    val activeTag = remember { mutableStateOf("All") }
val categories by viewModel.categories.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
 
    var showMenuBottomSheet by remember { mutableStateOf(false) }
    var selectedVideoForMenu by remember { mutableStateOf<Video?>(null) }
 
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0F0F0F),
                drawerTonalElevation = 0.dp,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .shadow(12.dp, RoundedCornerShape(8.dp), false, Color(0xFFFF5A5F), Color(0xFFFF5A5F))
                                .width(44.dp).height(32.dp)
                                .background(Color.Black, RoundedCornerShape(8.dp))
                                .border(1.5.dp, Color(0xFFFF5A5F), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color(0xFFFF5A5F), modifier = Modifier.size(16.dp))
                                Box(modifier = Modifier.width(2.dp).height(10.dp).background(Color.White))
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append("Air") }
                                withStyle(SpanStyle(color = Color(0xFFFF5A5F), fontWeight = FontWeight.ExtraBold)) { append("StreamX") }
                            },
                            fontSize = 20.sp, letterSpacing = (-0.5).sp
                        )
                    }
 
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))
                    Text("Navigation", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
 
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Home, null, tint = Color.White) },
                        label = { Text("Home Feed", color = Color.White) }, selected = true,
                        onClick = { scope.launch { drawerState.close() }; viewModel.loadHomeFeed() },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = Color.Transparent,
                            selectedContainerColor = Color.White.copy(alpha = 0.08f)
                        ), modifier = Modifier.padding(vertical = 2.dp)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Subscriptions, null, tint = Color.White) },
                        label = { Text("Subscriptions", color = Color.White) }, selected = false,
                        onClick = { scope.launch { drawerState.close() } },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.CloudUpload, null, tint = Color(0xFFFF5A5F)) },
                        label = { Text("Publish Video", color = Color.White) }, selected = false,
                        onClick = { scope.launch { drawerState.close() }; onUploadClick() },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Person, null, tint = Color.White) },
                        label = { Text("My Channel", color = Color.White) }, selected = false,
                        onClick = { scope.launch { drawerState.close() }; onProfileClick() },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
 
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 12.dp))
                    Text("More", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
 
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, null, tint = Color.White) },
                        label = { Text("Settings", color = Color.White) }, selected = false,
                        onClick = { scope.launch { drawerState.close() } },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).testTag("home_screen")
        ) {
            // TOP BAR
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A))
                    .statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { scope.launch { if (drawerState.isClosed) drawerState.open() else drawerState.close() } },
                        modifier = Modifier.testTag("hamburger_menu_button")
                    ) {
                        Icon(Icons.Default.Menu, "Menu", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Icon(Icons.Default.PlayArrow, null, tint = Color(0xFFFF5A5F), modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) { append("Air") }
                            withStyle(SpanStyle(color = Color(0xFFFF5A5F), fontWeight = FontWeight.ExtraBold)) { append("StreamX") }
                        },
                        fontSize = 18.sp, letterSpacing = (-0.5).sp,
                        modifier = Modifier.clickable { viewModel.loadHomeFeed() }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { Toast.makeText(context, "Cast coming soon!", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.Cast, "Cast", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = { Toast.makeText(context, "Notifications coming soon!", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.Notifications, "Notifications", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = onSearchClick, modifier = Modifier.testTag("top_search_button")) {
                        Icon(Icons.Default.Search, "Search", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
 
            // CATEGORY CHIPS
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).testTag("category_filter_row")
            ) {
                items(categories) { category ->
                    val isSelected = activeTag.value == category
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) Color(0xFFFF5A5F) else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                activeTag.value = category
                                if (category == "All") viewModel.loadHomeFeed()
                                else viewModel.updateSearchQuery(category)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("filter_chip_$category")
                    ) {
                        Text(
                            text = category, fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color(0xFFA0A0A0)
                        )
                    }
                }
            }
 
            // PULL TO REFRESH + FEED
            var isRefreshing by remember { mutableStateOf(false) }
            LaunchedEffect(feedState) {
                if (feedState !is FeedUiState.Loading) isRefreshing = false
            }
            val pullToRefreshState = rememberPullToRefreshState()
 
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { isRefreshing = true; viewModel.loadHomeFeed(forceRefresh = true) },
                state = pullToRefreshState,
                modifier = Modifier.weight(1f),
                indicator = {
                    androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                        state = pullToRefreshState, isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        color = Color(0xFFFF5A5F), containerColor = Color(0xFF141414)
                    )
                }
            ) {
                when (val state = feedState) {
                    is FeedUiState.Loading -> {
                        if (!isRefreshing) {
                            Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).testTag("shimmer_loading_state")) {
                                repeat(3) { ShimmerPlaceholderCard() }
                            }
                        }
                    }
                    is FeedUiState.Success -> {
                        val displayVideos = if (activeTag.value == "All") state.videos
                        else state.videos.filter {
                            it.title.contains(activeTag.value, ignoreCase = true) ||
                            it.channelName.contains(activeTag.value, ignoreCase = true)
                        }.ifEmpty { state.videos }
 
                        // The feed is long videos only (type=long); Shorts come from their own request.
                        val regularVideos = displayVideos
                        val shortsVideos = (shortsState as? ShortsUiState.Success)?.videos.orEmpty()

                        // Infinite scroll: ask for the next page when within 5 items of the end.
                        val listState = rememberLazyListState()
                        // Re-keyed on size so a short page that still fits the screen keeps loading.
                        LaunchedEffect(listState, state.hasMore, state.videos.size) {
                            snapshotFlow {
                                val info = listState.layoutInfo
                                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                                info.totalItemsCount > 0 && last >= info.totalItemsCount - 5
                            }.distinctUntilChanged().collect { nearEnd ->
                                if (nearEnd) viewModel.loadMoreHomeFeed()
                            }
                        }

                        if (displayVideos.isEmpty()) {
                            // A genuinely empty (successful) response — errors have their own state.
                            HomeEmptyState(onRetry = { viewModel.loadHomeFeed(forceRefresh = true) })
                        } else {
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                                // Trending hero — the single most-viewed video from this feed,
                                // shown as a large featured card above everything else.
                                val heroVideo = displayVideos.maxByOrNull { it.views }
                                if (heroVideo != null) {
                                    item {
                                        TrendingHeroCard(
                                            video = heroVideo,
                                            onClick = { onVideoClick(heroVideo) },
                                            onTipClick = { onVideoClick(heroVideo) }
                                        )
                                    }
                                }
                                // FIX: Shorts inserted only ONCE after first chunk of 4 videos
                                val chunks = regularVideos.chunked(4)
                                var shortsInserted = false
 
                                chunks.forEachIndexed { index, chunk ->
                                    items(chunk, key = { it.publicId }) { video ->
                                        RegularVideoCard(
                                            video = video,
                                            onClick = { onVideoClick(video) },
                                            onChannelClick = { onChannelClick(video.handle) },
                                            onMenuClick = { selectedVideoForMenu = video; showMenuBottomSheet = true }
                                        )
                                    }
                                    if (!shortsInserted && shortsVideos.isNotEmpty()) {
                                        shortsInserted = true
                                        item {
                                            ShortsRowSection(
                                                shorts = shortsVideos,
                                                onShortClick = { viewModel.selectShortVideo(it) }
                                            )
                                        }
                                    }
                                }
                                // Only shorts, no regular videos
                                if (regularVideos.isEmpty() && shortsVideos.isNotEmpty()) {
                                    item {
                                        ShortsRowSection(
                                            shorts = shortsVideos,
                                            onShortClick = { viewModel.selectShortVideo(it) }
                                        )
                                    }
                                }
                                // Paging footer: spinner while loading, Retry if the next page failed.
                                if (state.isLoadingMore) {
                                    item(key = "feed_loading_more") {
                                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(color = Color(0xFFFF5A5F), modifier = Modifier.size(24.dp))
                                        }
                                    }
                                } else if (state.loadMoreError != null) {
                                    item(key = "feed_load_more_error") {
                                        Column(
                                            Modifier.fillMaxWidth().padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(state.loadMoreError, color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                                            Spacer(Modifier.height(8.dp))
                                            Button(
                                                onClick = { viewModel.loadMoreHomeFeed() },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F))
                                            ) { Text("Retry", color = Color.White) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is FeedUiState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.WifiOff, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Could not load videos", color = Color.White, fontWeight = FontWeight.Bold,
                                fontSize = 16.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            Text(state.message, color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = { viewModel.loadHomeFeed(forceRefresh = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F))) {
                                Text("Retry", color = Color.White)
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
 
    if (showMenuBottomSheet && selectedVideoForMenu != null) {
        VideoMenuBottomSheet(
            video = selectedVideoForMenu!!,
            onDismiss = { showMenuBottomSheet = false; selectedVideoForMenu = null }
        )
    }
}
 
// ── Trending Hero Card ────────────────────────────────────────────────────────
@Composable
fun TrendingHeroCard(video: Video, onClick: () -> Unit, onTipClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            .clickable(onClick = onClick).testTag("trending_hero_card_${video.publicId}")
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            LazyLoadedImage(video.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // TRENDING #1 badge + language tag — top-left, matching design
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFF5A5F), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("TRENDING #1", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("हिन्दी", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(Color.White, RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(formatDuration(video.duration), fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Text(video.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(20.dp)
                            .background(Color(0xFF3A3A3A), androidx.compose.foundation.shape.CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("${video.channelName} · ${formatViews(video.views)} views", fontSize = 13.sp, color = Color.Gray,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFFFF5A5F), RoundedCornerShape(20.dp))
                        .clickable(onClick = onTipClick)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("hero_tip_button_${video.publicId}")
                ) {
                    Text("₹ Tip", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// ── Regular Video Card ────────────────────────────────────────────────────────
@Composable
fun RegularVideoCard(video: Video, onClick: () -> Unit, onChannelClick: () -> Unit, onMenuClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(bottom = 12.dp).testTag("regular_video_card_${video.publicId}")
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            LazyLoadedImage(video.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    .background(Color.White, RoundedCornerShape(2.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(formatDuration(video.duration), fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.Top) {
            LazyLoadedImage(
                video.avatarUrl, null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onChannelClick)
                    .testTag("channel_avatar_${video.publicId}")
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(video.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text(video.channelName, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.clickable(onClick = onChannelClick))
                Text("${formatViews(video.views)} • ${timeAgo(video.createdAt)}", fontSize = 12.sp, color = Color.Gray)
            }
            IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp).testTag("video_menu_${video.publicId}")) {
                Icon(Icons.Default.MoreVert, "Options", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
 
// ── Shorts Row ────────────────────────────────────────────────────────────────
@Composable
fun ShortsRowSection(shorts: List<Video>, onShortClick: (Video) -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("shorts_row_section")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayArrow, null, tint = Color(0xFFFF5A5F), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(6.dp))
                Text("Shorts near you", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text(
                "See all", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5A5F),
                modifier = Modifier.clickable { Toast.makeText(context, "Shorts options", Toast.LENGTH_SHORT).show() }
            )
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shorts) { video -> ShortCardItem(video = video, onClick = { onShortClick(video) }) }
        }
    }
}
 
@Composable
fun ShortCardItem(video: Video, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.width(110.dp).clickable(onClick = onClick).testTag("shorts_card_${video.publicId}")) {
        Box(modifier = Modifier.width(110.dp).height(180.dp).background(Color(0xFF141414), RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))) {
            LazyLoadedImage(video.thumbnail, video.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(formatViews(video.views), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            IconButton(
                onClick = { Toast.makeText(context, "Short options", Toast.LENGTH_SHORT).show() },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).background(Color.Black.copy(0.45f), CircleShape)
            ) {
                Icon(Icons.Default.MoreVert, "Options", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(video.title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

// ── Lazy Loaded Image ──────────────────────────────────────────────────────────
@Composable
fun LazyLoadedImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current

    // PERF FIX: the shimmer's rememberInfiniteTransition() used to live at
    // the top of this composable, so `anim` was read (and its animation
    // frame callback kept firing forever, every ~16ms) for the ENTIRE
    // lifetime of every image on screen — including images that already
    // finished loading and are just sitting there as a static crop. On a
    // feed with dozens of thumbnails/avatars this meant dozens of infinite,
    // pointless animations running and forcing recomposition/redraw in the
    // background, burning CPU and battery for no visible effect. Moving the
    // animation into the `loading` slot means Compose only creates and runs
    // it while that slot is actually part of the tree, and disposes it the
    // moment SubcomposeAsyncImage swaps to the loaded image.
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .crossfade(300)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        loading = {
            ShimmerBox(modifier = Modifier.fillMaxSize())
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Error loading image",
                    tint = Color.Gray.copy(0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        modifier = modifier
    )
}

// ── Shimmer ───────────────────────────────────────────────────────────────────
// A single shimmering placeholder box. Its infinite animation only exists
// for as long as THIS composable is part of the tree — see the PERF FIX
// note on LazyLoadedImage above for why that scoping matters.
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer_image")
    val anim by transition.animateFloat(0f, 1000f,
        infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "shimmer_image_x")
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(Color(0xFF1A1A1A), Color(0xFF2C2C2C), Color(0xFF1A1A1A)),
        androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(anim, anim)
    )
    Box(modifier = modifier.background(brush))
}

@Composable
fun ShimmerPlaceholderCard() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val anim by transition.animateFloat(0f, 1000f,
        infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "shimmer_x")
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        listOf(Color(0xFF1A1A1A), Color(0xFF2C2C2C), Color(0xFF1A1A1A)),
        androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(anim, anim)
    )
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(brush))
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Box(modifier = Modifier.size(40.dp).background(brush, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxWidth(0.85f).height(16.dp).background(brush, RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth(0.55f).height(14.dp).background(brush, RoundedCornerShape(4.dp)))
                Spacer(Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth(0.35f).height(12.dp).background(brush, RoundedCornerShape(4.dp)))
            }
        }
    }
}
 
// ── Empty State ───────────────────────────────────────────────────────────────
@Composable
fun HomeEmptyState(onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(48.dp).testTag("empty_feed_state"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(72.dp).border(2.dp, Color.White.copy(0.4f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(0.4f), modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("No videos yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text("Check back later for new content", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F)),
            shape = RoundedCornerShape(8.dp), modifier = Modifier.testTag("empty_retry_button")) {
            Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
 
// ── Bottom Sheet ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoMenuBottomSheet(video: Video, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF141414), contentColor = Color.White,
        modifier = Modifier.testTag("video_options_bottom_sheet")) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp)) {
            ListItem(
                headlineContent = { Text("Save to Watch Later", color = Color.White) },
                leadingContent = { Icon(Icons.Default.AccessTime, null, tint = Color.White) },
                modifier = Modifier.clickable { Toast.makeText(context, "Saved to Watch Later!", Toast.LENGTH_SHORT).show(); onDismiss() }
                    .testTag("menu_watch_later"),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            ListItem(
                headlineContent = { Text("Share", color = Color.White) },
                leadingContent = { Icon(Icons.Default.Share, null, tint = Color.White) },
                modifier = Modifier.clickable {
                    // Real Android share sheet
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Watch \"${video.title}\" on AirStreamX: ${video.shareUrl()}")
                    }
                    context.startActivity(Intent.createChooser(i, "Share via"))
                    onDismiss()
                }.testTag("menu_share"),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            ListItem(
                headlineContent = { Text("Not Interested", color = Color.White) },
                leadingContent = { Icon(Icons.Default.ThumbDown, null, tint = Color.White) },
                modifier = Modifier.clickable { Toast.makeText(context, "Got it, we'll show less like this.", Toast.LENGTH_SHORT).show(); onDismiss() }
                    .testTag("menu_not_interested"),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}