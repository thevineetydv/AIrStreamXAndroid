package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.upload.UploadWorker
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.UploadUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val Accent = Color(0xFFFF5A5F)
private const val TAG = "UploadScreen"
private const val TITLE_MAX = 100

private data class PickedVideo(val uri: Uri, val name: String, val sizeBytes: Long, val durationSeconds: Double)

/**
 * Real upload flow:
 *  - picks a file with the system document picker and keeps read access so the
 *    background upload can finish even if the app is closed
 *  - checks the 500 MB server limit before starting
 *  - requires the "original work / licensed" confirmation
 *  - hands the upload to WorkManager and shows its REAL progress
 *  - only says "published" after the server confirms
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onUploadSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val uploadState by viewModel.uploadState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isGuest = currentUser == null || viewModel.isGuest()

    var picked by remember { mutableStateOf<PickedVideo?>(null) }
    var isReadingFile by remember { mutableStateOf(false) }
    var fileError by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var titleError by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("Cinematic") }
    var confirmedOriginal by remember { mutableStateOf(false) }
    var showNotificationRationale by remember { mutableStateOf(false) }

    fun startUpload() {
        val video = picked ?: return
        viewModel.startUpload(video.uri, title.trim(), category, video.durationSeconds)
    }

    // Android 13+: ask for notifications (progress notification) — after explaining why.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> startUpload() } // Upload works either way; only the notification is hidden if denied.

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        fileError = null
        isReadingFile = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { readPickedVideo(context, uri) }
            isReadingFile = false
            when {
                result == null -> fileError = "Couldn't read that file. Please choose another video."
                result.sizeBytes > UploadWorker.MAX_UPLOAD_BYTES -> {
                    releaseRead(context, uri)
                    fileError = "This video is ${formatBytes(result.sizeBytes)}. The limit is 500 MB."
                }
                else -> {
                    picked?.uri?.takeIf { it != uri }?.let { releaseRead(context, it) }
                    picked = result
                    if (title.isBlank()) title = result.name.substringBeforeLast(".").take(TITLE_MAX)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .testTag("upload_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F0F0F))
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick, modifier = Modifier.size(48.dp).testTag("upload_back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("Upload Video", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        when (val state = uploadState) {
            is UploadUiState.Uploading -> UploadProgressPanel(
                percent = state.percent,
                onCancel = { viewModel.cancelUpload() }
            )
            is UploadUiState.Success -> UploadResultPanel(
                success = true,
                message = "Uploaded! Your video is processing and will appear once it's ready.",
                primaryLabel = "Done",
                onPrimary = {
                    viewModel.resetUploadState()
                    picked = null
                    title = ""
                    confirmedOriginal = false
                    onUploadSuccess()
                },
                onSecondary = null
            )
            is UploadUiState.Failed -> UploadResultPanel(
                success = false,
                message = state.message,
                primaryLabel = if (picked != null) "Try again" else "OK",
                onPrimary = {
                    viewModel.resetUploadState()
                    if (picked != null && confirmedOriginal && title.isNotBlank()) startUpload()
                },
                onSecondary = { viewModel.resetUploadState() }
            )
            UploadUiState.Idle -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (isGuest) {
                    InfoBanner("Sign in with Google to upload videos.")
                    Spacer(Modifier.height(16.dp))
                }

                // File picker
                val selected = picked
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = if (selected != null) Color(0xFF22C55E) else Accent.copy(alpha = 0.5f),
                                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                            )
                        }
                        .clickable(enabled = !isGuest && !isReadingFile) {
                            focusManager.clearFocus()
                            pickerLauncher.launch(arrayOf("video/*"))
                        }
                        .testTag("upload_file_picker"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                        when {
                            isReadingFile -> CircularProgressIndicator(color = Accent, modifier = Modifier.size(28.dp))
                            selected != null -> {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(4.dp))
                                Text(selected.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${formatBytes(selected.sizeBytes)} • ${formatDuration(selected.durationSeconds)} • tap to change",
                                    color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp
                                )
                            }
                            else -> {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Accent, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(6.dp))
                                Text("Tap to choose a video", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("MP4 or other video, up to 500 MB", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            }
                        }
                    }
                }
                fileError?.let {
                    Text(it, color = Color(0xFFEF4444), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp).testTag("upload_file_error"))
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(TITLE_MAX); if (it.isNotBlank()) titleError = false },
                    label = { Text("Title") },
                    singleLine = true,
                    isError = titleError,
                    supportingText = { Text(if (titleError) "Please add a title" else "${title.length}/$TITLE_MAX") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedLabelColor = Accent, unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                    ),
                    enabled = !isGuest,
                    modifier = Modifier.fillMaxWidth().testTag("upload_title_input")
                )

                Spacer(Modifier.height(12.dp))
                Text("Category", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    listOf("Cinematic", "Tech", "Rust", "Nature", "Shorts").forEach { tag ->
                        FilterChip(
                            selected = category == tag,
                            onClick = { category = tag },
                            label = { Text(tag) },
                            enabled = !isGuest,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Accent, selectedLabelColor = Color.White,
                                labelColor = Color.White.copy(alpha = 0.8f)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Required rights confirmation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = confirmedOriginal, enabled = !isGuest, role = androidx.compose.ui.semantics.Role.Checkbox) {
                            confirmedOriginal = it
                        }
                        .padding(vertical = 4.dp)
                        .testTag("upload_original_checkbox"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = confirmedOriginal,
                        onCheckedChange = null, // handled by the row (bigger touch target)
                        enabled = !isGuest,
                        colors = CheckboxDefaults.colors(checkedColor = Accent)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "This content is my original work or I have a licence to use it",
                        color = Color.White, fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(20.dp))

                val canPublish = !isGuest && picked != null && confirmedOriginal && !isReadingFile
                Button(
                    onClick = {
                        if (title.isBlank()) { titleError = true; return@Button }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            showNotificationRationale = true
                        } else {
                            startUpload()
                        }
                    },
                    enabled = canPublish,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, disabledContainerColor = Color.White.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("publish_video_button")
                ) {
                    Icon(Icons.Default.Publish, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Upload", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                if (!isGuest && picked != null && !confirmedOriginal) {
                    Text(
                        "Tick the box above to confirm you have the rights to this video.",
                        color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }

    if (showNotificationRationale) {
        AlertDialog(
            onDismissRequest = { showNotificationRationale = false; startUpload() },
            title = { Text("Show upload progress?") },
            text = { Text("AirStreamX shows a notification with your upload's progress so you can leave the app while it finishes. You can say no — the upload will still run.") },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationRationale = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else startUpload()
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationRationale = false; startUpload() }) { Text("Not now") }
            }
        )
    }
}

@Composable
private fun UploadProgressPanel(percent: Int, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag("upload_progress_panel"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Uploading… $percent%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            color = Accent,
            trackColor = Color.White.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth().height(6.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "You can leave this screen or close the app. The upload continues in the background.",
            color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("Cancel upload", color = Color.White)
        }
    }
}

@Composable
private fun UploadResultPanel(
    success: Boolean,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)?
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag(if (success) "upload_success_panel" else "upload_failed_panel"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (success) Color(0xFF22C55E) else Color(0xFFEF4444),
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(message, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onPrimary, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
            Text(primaryLabel, color = Color.White)
        }
        if (onSecondary != null) {
            TextButton(onClick = onSecondary) { Text("Back to form", color = Color.White.copy(alpha = 0.7f)) }
        }
    }
}

@Composable
private fun InfoBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Accent.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .border(1.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Takes persistable read access and reads name, size and duration. Runs on IO. */
private fun readPickedVideo(context: android.content.Context, uri: Uri): PickedVideo? {
    return try {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.w(TAG, "Provider doesn't offer persistable access; upload must finish while the app is alive")
        }
        var name = "video.mp4"
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it)?.let { n -> name = n } }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        if (size <= 0L) return null
        val durationSeconds = MediaMetadataRetriever().run {
            try {
                setDataSource(context, uri)
                (extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
            } catch (e: Exception) {
                0.0
            } finally {
                release()
            }
        }
        PickedVideo(uri, name, size, durationSeconds)
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read picked file: ${e.javaClass.simpleName}")
        null
    }
}

private fun releaseRead(context: android.content.Context, uri: Uri) {
    try {
        context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) { }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%d KB", bytes / 1024)
}
