package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.database.SubscriptionEntity
import com.example.data.database.WatchHistoryEntity
import com.example.data.network.Video
import com.example.ui.screens.*
import com.example.ui.theme.AirStreamXTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.player.MiniPlayerOverlay // NEW: floating mini player overlay
import com.example.player.MiniPlayerManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AirStreamXTheme {
                val viewModel: MainViewModel = viewModel()
                MainAppContainer(viewModel = viewModel)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(viewModel: MainViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()

    // Screens: "home", "shorts", "subscriptions", "profile", "player", "search", "channel"
    var currentScreen by remember { mutableStateOf("home") }
    val backstack = remember { mutableStateListOf<String>() }

    // Auxiliary navigation parameters
    var activeChannelHandle by remember { mutableStateOf("") }

    // Select short navigation helper
    val selectedShortVideo by viewModel.selectedShortVideo.collectAsState()
    LaunchedEffect(selectedShortVideo) {
        if (selectedShortVideo != null) {
            currentScreen = "shorts"
        }
    }

    // Navigation trigger helpers
    fun navigateTo(screen: String) {
        if (currentScreen != screen) {
            backstack.add(currentScreen)
            currentScreen = screen
        }
    }

    fun navigateBack() {
        if (backstack.isNotEmpty()) {
            currentScreen = backstack.removeAt(backstack.size - 1)
        } else {
            currentScreen = "home"
        }
    }

    // System back navigation handler
    BackHandler(enabled = currentScreen != "home") {
        navigateBack()
    }

    // Reset navigation state and completely stop playback whenever the user logs out or session becomes null
    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            currentScreen = "home"
            backstack.clear()
            viewModel.clearActiveVideo()
            MiniPlayerManager.releasePlayer()
        }
    }

    if (currentUser == null) {
        LoginScreen(
            viewModel = viewModel,
            onLoginSuccess = {
                currentScreen = "home"
                backstack.clear()
                viewModel.clearActiveVideo()
                MiniPlayerManager.releasePlayer()
            }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // Only displays bottom navigation for primary screens
            val mainScreens = listOf("home", "shorts", "subscriptions", "profile")
            if (mainScreens.contains(currentScreen)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    // Thin translucent top divider mimicking HTML border-t border-white/10
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 1.dp)

                    NavigationBar(
                        containerColor = Color(0xCC0E0E0E), // Translucent black background
                        tonalElevation = 0.dp, // Flat surface overlay to avoid standard gray toning
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NavigationBarItem(
                            selected = currentScreen == "home",
                            onClick = { currentScreen = "home" },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                indicatorColor = Color.White.copy(alpha = 0.1f), // Frosted active selection capsule
                                unselectedIconColor = Color.White.copy(alpha = 0.45f),
                                unselectedTextColor = Color.White.copy(alpha = 0.45f)
                            )
                        )

                        NavigationBarItem(
                            selected = currentScreen == "shorts",
                            onClick = { currentScreen = "shorts" },
                            icon = { Icon(Icons.Default.Bolt, contentDescription = "Shorts") },
                            label = { Text("Shorts", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                indicatorColor = Color.White.copy(alpha = 0.1f),
                                unselectedIconColor = Color.White.copy(alpha = 0.45f),
                                unselectedTextColor = Color.White.copy(alpha = 0.45f)
                            )
                        )

                        NavigationBarItem(
                            selected = currentScreen == "subscriptions",
                            onClick = { currentScreen = "subscriptions" },
                            icon = { Icon(Icons.Default.Subscriptions, contentDescription = "Subscriptions") },
                            label = { Text("Subscribed", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                indicatorColor = Color.White.copy(alpha = 0.1f),
                                unselectedIconColor = Color.White.copy(alpha = 0.45f),
                                unselectedTextColor = Color.White.copy(alpha = 0.45f)
                            )
                        )

                        NavigationBarItem(
                            selected = currentScreen == "profile",
                            onClick = { currentScreen = "profile" },
                            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                            label = { Text("Profile", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                indicatorColor = Color.White.copy(alpha = 0.1f),
                                unselectedIconColor = Color.White.copy(alpha = 0.45f),
                                unselectedTextColor = Color.White.copy(alpha = 0.45f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        @OptIn(ExperimentalMaterial3Api::class)
        val showSignInPrompt by viewModel.showSignInPrompt.collectAsState()

        if (showSignInPrompt) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.dismissSignInPrompt() },
                containerColor = Color(0xFF141414),
                contentColor = Color.White,
                scrimColor = Color.Black.copy(alpha = 0.6f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color(0xFFFF5A5F).copy(alpha = 0.1f), shape = CircleShape)
                            .border(1.dp, Color(0xFFFF5A5F).copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Icon",
                            tint = Color(0xFFFF5A5F),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Sign in to unlock this feature",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Gain full privileges to upload videos, subscribe to your favorite channels, like videos, and leave comments.",
                        fontSize = 13.sp,
                        color = Color(0xFFA0A0A0),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = {
                            viewModel.dismissSignInPrompt()
                            currentScreen = "home"
                            backstack.clear()
                            viewModel.clearActiveVideo()
                            MiniPlayerManager.releasePlayer()
                            viewModel.logout()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("signin_now_button")
                    ) {
                        Text(
                            text = "Sign In Now",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = { viewModel.dismissSignInPrompt() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Maybe Later",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                "home" -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onVideoClick = { video ->
                            viewModel.selectVideo(video)
                            navigateTo("player")
                        },
                        onSearchClick = {
                            navigateTo("search")
                        },
                        onChannelClick = { handle ->
                            activeChannelHandle = handle
                            navigateTo("channel")
                        },
                        onUploadClick = {
                            navigateTo("upload")
                        },
                        onProfileClick = {
                            currentScreen = "profile"
                        }
                    )
                }
                "shorts" -> {
                    ShortsScreen(
                        viewModel = viewModel,
                        onChannelClick = { handle ->
                            activeChannelHandle = handle
                            navigateTo("channel")
                        },
                        onBackClick = {
                            navigateBack()
                        }
                    )
                }
                "subscriptions" -> {
                    SubscriptionsTab(
                        viewModel = viewModel,
                        onChannelClick = { handle ->
                            activeChannelHandle = handle
                            navigateTo("channel")
                        }
                    )
                }
                "profile" -> {
                    UserProfileTab(
                        viewModel = viewModel,
                        onNavigateToPlayer = {
                            navigateTo("player")
                        },
                        onChannelClick = { handle ->
                            activeChannelHandle = handle
                            navigateTo("channel")
                        },
                        onUploadClick = {
                            navigateTo("upload")
                        }
                    )
                }
                "upload" -> {
                    UploadScreen(
                        viewModel = viewModel,
                        onBackClick = { navigateBack() },
                        onUploadSuccess = {
                            currentScreen = "home"
                        }
                    )
                }
                "player" -> {
                    VideoPlayerScreen(
                        viewModel = viewModel,
                        onBackClick = { navigateBack() },
                        onChannelClick = { handle ->
                            activeChannelHandle = handle
                            navigateTo("channel")
                        },
                        onVideoClick = { video ->
                            viewModel.selectVideo(video)
                        }
                    )
                }
                "search" -> {
                    SearchScreen(
                        viewModel = viewModel,
                        onBackClick = { navigateBack() },
                        onVideoClick = { video ->
                            viewModel.selectVideo(video)
                            navigateTo("player")
                        }
                    )
                }
                "channel" -> {
                    ChannelScreen(
                        viewModel = viewModel,
                        handle = activeChannelHandle,
                        onBackClick = { navigateBack() },
                        onVideoClick = { video ->
                            viewModel.selectVideo(video)
                            navigateTo("player")
                        }
                    )
                }
            }

            // NEW: floating mini player, rendered above every screen except the
            // full VideoPlayerScreen itself (avoids a mini player floating over
            // the exact same video that's already fullscreen/inline below it).
            // Persists across all navigateTo() calls because it lives here at
            // the root, outside the `when` block.
            if (currentScreen != "player") {
                MiniPlayerOverlay(
                    onExpandRequested = { video ->
                        viewModel.selectVideo(video)
                        navigateTo("player")
                    }
                )
            }
        }
    }
}

@Composable
fun SubscriptionsTab(
    viewModel: MainViewModel,
    onChannelClick: (String) -> Unit
) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    val availableChannels by viewModel.availableChannels.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .testTag("subscriptions_tab"),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Text(
                text = "Channels You Subscribed To",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .statusBarsPadding()
            )
        }

        if (subscriptions.isEmpty()) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp, horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color(0xFF222222),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No subscriptions added yet",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Subscribe to creators to see their channels and video updates here.",
                        color = Color(0xFFA0A0A0),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp)
                    )
                }
            }
        } else {
            items(subscriptions) { sub ->
                SubscriptionRow(
                    subscription = sub,
                    onClick = { onChannelClick(sub.handle) }
                )
            }
        }

        // Section header for Discover Channels
        item {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Discover Channels",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )
        }

        if (availableChannels.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFF5A5F), modifier = Modifier.size(24.dp))
                }
            }
        } else {
            // Show all channels from database!
            items(availableChannels) { channel ->
                val isSubscribed = subscriptions.any { it.handle.equals(channel.handle, ignoreCase = true) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChannelClick(channel.handle) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        AsyncImage(
                            model = channel.avatarUrl,
                            contentDescription = channel.channelName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = channel.channelName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "@${channel.handle}",
                                fontSize = 11.sp,
                                color = Color(0xFFA0A0A0)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (viewModel.isGuest()) {
                                viewModel.showSignInPrompt()
                            } else {
                                viewModel.toggleSubscriptionByChannel(channel)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSubscribed) Color.White.copy(alpha = 0.08f) else Color(0xFFFF5A5F),
                            contentColor = if (isSubscribed) Color.White.copy(alpha = 0.8f) else Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .then(
                                if (isSubscribed) Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)) else Modifier
                            )
                    ) {
                        Text(
                            text = if (isSubscribed) "Subscribed" else "Subscribe",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubscriptionRow(
    subscription: SubscriptionEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = subscription.avatarUrl,
            contentDescription = subscription.channelName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = subscription.channelName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "@${subscription.handle}",
                fontSize = 12.sp,
                color = Color(0xFFA0A0A0)
            )
        }
    }
}

@Composable
fun UserProfileTab(
    viewModel: MainViewModel,
    onNavigateToPlayer: () -> Unit,
    onChannelClick: (String) -> Unit,
    onUploadClick: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .testTag("profile_tab")
    ) {
        val user = currentUser ?: return
        val isGuest = viewModel.isGuest()

        // Hero Profile Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = user.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (isGuest) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFFFF5A5F).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFFF5A5F).copy(alpha = 0.4f)),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "Guest",
                                color = Color(0xFFFF5A5F),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = user.email,
                    fontSize = 13.sp,
                    color = Color(0xFFA0A0A0)
                )
            }

            IconButton(
                onClick = { viewModel.logout() },
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.08f), shape = CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .size(40.dp)
                    .testTag("logout_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "Log Out",
                    tint = Color(0xFFEF4444)
                )
            }
        }

        if (isGuest) {
            // Prominent Sign In Banner
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFF5A5F).copy(alpha = 0.06f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFFF5A5F).copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("guest_signin_banner")
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = Color(0xFFFF5A5F),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Sign in to save your progress",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Keep your watch history, subscriptions, and upload your own streams.",
                        fontSize = 12.sp,
                        color = Color(0xFFA0A0A0),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.logout() }, // logout triggers the login screen
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A5F)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        Text(
                            text = "Sign In Now / Register",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1F1F1F))

        // Creator Studio Hub Card
        Card(
            onClick = onUploadClick,
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("creator_studio_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFFF5A5F).copy(alpha = 0.15f), shape = CircleShape)
                        .border(1.dp, Color(0xFFFF5A5F).copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color(0xFFFF5A5F),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Creator Studio Hub",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Transcode & publish videos to live feeds",
                        color = Color(0xFFA0A0A0),
                        fontSize = 11.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Upload",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        HorizontalDivider(color = Color(0xFF1F1F1F))

        // Historic Watched list Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Watch History",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (watchHistory.isNotEmpty()) {
                Text(
                    text = "Clear All",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFDC2626),
                    modifier = Modifier
                        .clickable { viewModel.clearHistory() }
                        .padding(4.dp)
                )
            }
        }

        if (watchHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No watch history available yet",
                    fontSize = 13.sp,
                    color = Color(0xFF5F5F5F)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 72.dp)
            ) {
                items(watchHistory) { history ->
                    HistoryItemRow(
                        history = history,
                        onClick = {
                            // selectVideoById fetches the REAL video (with its real
                            // HLS url) and updates currentActiveVideo — we just need
                            // to navigate after that, not fabricate a Video ourselves.
                            viewModel.selectVideoById(history.publicId)
                            onNavigateToPlayer()
                        },
                        onChannelClick = { onChannelClick(history.handle) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItemRow(
    history: WatchHistoryEntity,
    onClick: () -> Unit,
    onChannelClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = history.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(110.dp)
                .height(68.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = history.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = history.channelName,
                fontSize = 11.sp,
                color = Color(0xFFA0A0A0),
                modifier = Modifier.clickable(onClick = onChannelClick)
            )
        }
    }
}