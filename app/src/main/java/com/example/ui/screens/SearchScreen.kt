package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.network.Video
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onVideoClick: (Video) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val focusManager = LocalFocusManager.current

    val searchHistoryDb by viewModel.searchHistory.collectAsState()
    val searchHistory = remember(searchHistoryDb) {
        if (searchHistoryDb.isEmpty()) {
            listOf("Drone Adventures", "Tokyo Midnight", "Coding Rust", "Shorts Loop")
        } else {
            searchHistoryDb.map { it.query }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .testTag("search_screen")
    ) {
        // Search Input Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.testTag("search_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Text Field
            TextField(
                value = searchQuery,
                onValueChange = {
                    viewModel.updateSearchQuery(it)
                },
                placeholder = { Text("Search AirStreamX...", color = Color(0xFF6F6F6F)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFFA0A0A0)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.updateSearchQuery("") }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color.White
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (searchQuery.trim().isNotEmpty()) {
                            viewModel.insertSearchQuery(searchQuery)
                        }
                        focusManager.clearFocus()
                    }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .testTag("search_input_field")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (searchQuery.trim().isEmpty()) {
            // Display Historic Suggestions when query is empty
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Recent Searches",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(searchHistory) { tag ->
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(20.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .clickable {
                                    viewModel.updateSearchQuery(tag)
                                    viewModel.insertSearchQuery(tag)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(text = tag, color = Color.White, fontSize = 13.sp)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color(0xFF1F1F1F),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Find videos, channels, and shorts creators",
                            color = Color(0xFF5F5F5F),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            // Displays Live Results list
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results matching \"$searchQuery\"",
                        color = Color(0xFFA0A0A0),
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                ) {
                    items(searchResults) { video ->
                        Row(
                            modifier = Modifier
                                .clickable { onVideoClick(video) }
                                .padding(bottom = 12.dp)
                        ) {
                            RecommendedVideoRow(
                                video = video,
                                onClick = { onVideoClick(video) }
                            )
                        }
                    }
                }
            }
        }
    }
}
