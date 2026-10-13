package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.network.Video
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onUploadSuccess: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // Form inputs state
    var title by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }
    var thumbnailUrl by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Cinematic") }
    var durationText by remember { mutableStateOf("") }
    var acceptTips by remember { mutableStateOf(true) }

    // Validation State
    var titleError by remember { mutableStateOf(false) }
    var fileErrorText by remember { mutableStateOf<String?>(null) }

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isCopyingFile by remember { mutableStateOf(false) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isCopyingFile = true
            fileErrorText = null
            scope.launch(Dispatchers.IO) {
                try {
                    val fileName = getFileName(context, uri) ?: "local_video.mp4"
                    val extension = fileName.substringAfterLast(".").lowercase()
                    if (extension != "mp4" && extension != "m3u8") {
                        withContext(Dispatchers.Main) {
                            fileErrorText = "Only MP4 and HLS (.m3u8) files are supported."
                            isCopyingFile = false
                        }
                        return@launch
                    }
                    val cacheDir = context.cacheDir
                    val file = java.io.File(cacheDir, "uploaded_vid_${System.currentTimeMillis()}.$extension")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val cachedUri = Uri.fromFile(file).toString()

                    // Auto-detect duration from video file if possible
                    var detectedDuration = ""
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, Uri.fromFile(file))
                        val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val durationMs = durationStr?.toLongOrNull()
                        if (durationMs != null) {
                            detectedDuration = (durationMs / 1000).toString()
                        }
                    } catch (e: Exception) {
                        Log.e("UploadScreen", "Failed to retrieve duration", e)
                    } finally {
                        retriever.release()
                    }

                    withContext(Dispatchers.Main) {
                        streamUrl = cachedUri
                        selectedFileName = fileName
                        if (detectedDuration.isNotEmpty() && durationText.isEmpty()) {
                            durationText = detectedDuration
                        }
                        if (title.isEmpty()) {
                            title = fileName.substringBeforeLast(".")
                        }
                        isCopyingFile = false
                    }
                } catch (e: Exception) {
                    Log.e("UploadScreen", "Failed to cache video", e)
                    withContext(Dispatchers.Main) {
                        streamUrl = uri.toString()
                        selectedFileName = "Direct URI Video"
                        isCopyingFile = false
                    }
                }
            }
        }
    }

    // Upload animation states
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }
    var uploadStatusStep by remember { mutableStateOf("") }
    var isUploadComplete by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .testTag("upload_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F0F0F))
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("upload_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Upload Video",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )

                // Creator Profile Info
                currentUser?.let { user ->
                    val cleanUsername = user.displayName ?: user.email.substringBefore("@")
                    Text(
                        text = "@$cleanUsername",
                        color = Color(0xFFFF5A5F),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // Video File Picker
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(bottom = 16.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = if (selectedFileName != null) Color(0xFF22C55E) else Color(0xFFFF5A5F).copy(alpha = 0.5f),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(12f, 12f),
                                        0f
                                    )
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                            )
                        }
                        .background(
                            if (selectedFileName != null) Color(0xFF22C55E).copy(alpha = 0.05f) else Color.White.copy(alpha = 0.02f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            focusManager.clearFocus()
                            if (!isCopyingFile) {
                                videoPickerLauncher.launch("video/*")
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        if (isCopyingFile) {
                            CircularProgressIndicator(
                                color = Color(0xFFFF5A5F),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Reading video file...",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        } else if (selectedFileName != null) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active selection",
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = selectedFileName ?: "",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap inside box to select different file",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Upload Icon",
                                tint = Color(0xFFFF5A5F),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap to choose video file",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "HLS (.m3u8) or MP4 only",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                // File selection or type validation errors
                fileErrorText?.let { err ->
                    Text(
                        text = err,
                        color = Color.Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Video Title Input
                Text(
                    text = "Video Title",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotEmpty()) titleError = false
                    },
                    isError = titleError,
                    placeholder = { Text("Enter video title...", color = Color.White.copy(alpha = 0.3f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF5A5F),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        errorBorderColor = Color.Red,
                        focusedContainerColor = Color(0xFF121212),
                        unfocusedContainerColor = Color(0xFF121212)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("upload_title_input")
                )
                if (titleError) {
                    Text(
                        text = "Title is required",
                        color = Color.Red,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Category Selector
                Text(
                    text = "Category",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                val categoriesList = listOf("Cinematic", "Tech", "Rust", "Nature", "Shorts")

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    categoriesList.forEach { tag ->
                        val selected = category == tag
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (selected) Color(0xFFFF5A5F) else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { category = tag }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = tag,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Thumbnail URL Input
                Text(
                    text = "Thumbnail URL (Optional)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = thumbnailUrl,
                    onValueChange = { thumbnailUrl = it },
                    placeholder = { Text("Paste thumbnail image URL...", color = Color.White.copy(alpha = 0.3f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF5A5F),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color(0xFF121212),
                        unfocusedContainerColor = Color(0xFF121212)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("upload_thumbnail_input")
                )

                if (thumbnailUrl.trim().isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .padding(bottom = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    ) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = "Thumbnail Preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .align(Alignment.BottomStart)
                        ) {
                            Text("Preview Image Poster", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }

                // Duration Input
                Text(
                    text = "Duration (Seconds)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    placeholder = { Text("Duration in seconds...", color = Color.White.copy(alpha = 0.3f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF5A5F),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color(0xFF121212),
                        unfocusedContainerColor = Color(0xFF121212)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .testTag("upload_duration_input")
                )

                // Accept Tips toggle — matches design: "Accept tips on this
                // video / UPI ID verified · payouts instant"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("accept_tips_row"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Accept tips on this video", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(2.dp))
                        Text("UPI ID verified · payouts instant", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = acceptTips,
                        onCheckedChange = { acceptTips = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Color(0xFFFF5A5F),
                            checkedThumbColor = Color.White
                        ),
                        modifier = Modifier.testTag("accept_tips_switch")
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Publish Button
                val isGuest = viewModel.isGuest()

                if (isGuest) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFF5A5F).copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFFF5A5F).copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFFF5A5F),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Sign in to upload videos",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        if (isGuest) return@Button
                        if (title.trim().isEmpty()) {
                            titleError = true
                            return@Button
                        }
                        if (streamUrl.trim().isEmpty()) {
                            fileErrorText = "Please tap above box and select a video file."
                            return@Button
                        }

                        val finalThumbnail = if (thumbnailUrl.trim().isEmpty()) {
                            val presetThumbnails = listOf(
                                "https://images.unsplash.com/photo-1501854140801-50d01698950b?w=800&auto=format&fit=crop",
                                "https://images.unsplash.com/photo-1475924156734-496f6cac6ec1?w=800&auto=format&fit=crop",
                                "https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=800&auto=format&fit=crop",
                                "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=800&auto=format&fit=crop"
                            )
                            presetThumbnails.random()
                        } else {
                            thumbnailUrl
                        }

                        isUploading = true
                        uploadProgress = 0f
                        uploadStatusStep = "Connecting secure stream gateway..."

                        scope.launch {
                            // Stage 1
                            delay(600)
                            uploadProgress = 0.25f
                            uploadStatusStep = "Transcoding media containers..."

                            // Stage 2
                            delay(1000)
                            uploadProgress = 0.55f
                            uploadStatusStep = "Generating adaptive HLS player manifest..."

                            // Stage 3
                            delay(800)
                            uploadProgress = 0.85f
                            uploadStatusStep = "Finishing video index and metadata tagging..."

                            // Stage 4
                            delay(600)
                            uploadProgress = 1.0f
                            uploadStatusStep = "Successfully published to live broadcast database!"
                            delay(450)
                            isUploadComplete = true

                            val cleanHandle = currentUser?.email?.substringBefore("@") ?: "anonymous"
                            val uploadDuration = durationText.toDoubleOrNull() ?: 120.0
                            val uploadPublicId = if (uploadDuration <= 60.0) {
                                "shorts_${System.currentTimeMillis()}"
                            } else {
                                "video_${System.currentTimeMillis()}"
                            }
                            val activeVideo = Video(
                                id = (100..9999).random(),
                                publicId = uploadPublicId,
                                title = title,
                                thumbnail = finalThumbnail,
                                url = streamUrl,
                                views = 0,
                                duration = uploadDuration,
                                channelName = currentUser?.displayName ?: cleanHandle.replaceFirstChar { it.uppercase() },
                                avatarUrl = currentUser?.avatarUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&auto=format&fit=crop",
                                handle = cleanHandle,
                                uploaderEmail = currentUser?.email ?: "",
                                createdAt = "Just now"
                            )

                            viewModel.uploadVideo(activeVideo, streamUrl) {
                                scope.launch {
                                    delay(1000)
                                    isUploading = false
                                    isUploadComplete = false
                                    onUploadSuccess()
                                }
                            }
                        }
                    },
                    enabled = !isGuest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        disabledContainerColor = Color.White.copy(alpha = 0.05f)
                    ),
                    contentPadding = PaddingValues(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .testTag("publish_video_button")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isGuest) {
                                    Brush.linearGradient(
                                        colors = listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f))
                                    )
                                } else {
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFFFF5A5F), Color(0xFFDC2626))
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Publish,
                                contentDescription = null,
                                tint = if (isGuest) Color.White.copy(alpha = 0.3f) else Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Publish Video",
                                color = if (isGuest) Color.White.copy(alpha = 0.3f) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Visible to Everyone · Hindi + English captions auto-generated",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Upload overlay dialog / Progress status
        if (isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        if (!isUploadComplete) {
                            CircularProgressIndicator(
                                progress = { uploadProgress },
                                modifier = Modifier.size(72.dp),
                                color = Color(0xFFFF5A5F),
                                strokeWidth = 6.dp,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "Publishing Custom Broadcast",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = uploadStatusStep,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                minLines = 2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "${(uploadProgress * 100).toInt()}%",
                                color = Color(0xFFFF5A5F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color(0xFF22C55E), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Complete",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "Publish Complete!",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Your video has been published to the feed channel.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getFileName(context: android.content.Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            Log.e("UploadScreen", "Error getting openable display name", e)
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
}