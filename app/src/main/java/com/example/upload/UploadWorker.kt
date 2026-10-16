package com.example.upload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.network.ApiClient
import com.example.data.network.NetworkVideo
import com.example.data.network.VideoResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Uploads one video to POST /upload as a foreground WorkManager job, so it keeps
 * going (with a progress notification) when the user leaves the app or the app
 * is closed, and is retried with backoff after a dropped connection.
 *
 * The file is streamed straight from the user's content:// URI (persistable read
 * permission is taken by the picker) — no copy in the cache, no dummy files.
 * Success is only reported when the server returns 2xx.
 */
class UploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val uri = inputData.getString(KEY_URI)?.let(Uri::parse)
            ?: return@withContext fail("No video file selected.")
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val category = inputData.getString(KEY_CATEGORY).orEmpty()
        val duration = inputData.getDouble(KEY_DURATION, 0.0)

        // Uploads require a real (non-guest) Firebase session.
        if (ApiClient.currentIdTokenBlocking() == null) {
            return@withContext fail("Please sign in again to upload.")
        }

        val resolver = applicationContext.contentResolver
        val (fileName, size) = queryNameAndSize(uri)
        if (size <= 0L) return@withContext fail("Couldn't read the selected file.")
        if (size > MAX_UPLOAD_BYTES) {
            return@withContext fail("This file is larger than the 500 MB limit.")
        }
        val mime = resolver.getType(uri) ?: "video/mp4"

        setForeground(foregroundInfo(title, 0))

        var lastReported = -1
        val fileBody = ContentUriRequestBody(applicationContext, uri, mime.toMediaTypeOrNull(), size) { sent ->
            val percent = ((sent * 100) / size).toInt().coerceIn(0, 100)
            if (percent != lastReported) {
                lastReported = percent
                // Throttled to 1% steps; runBlocking is fine on this IO thread.
                runBlocking {
                    setProgress(workDataOf(KEY_PROGRESS to percent))
                    setForeground(foregroundInfo(title, percent))
                }
            }
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, fileBody)
            .addFormDataPart("title", title)
            .addFormDataPart("category", category)
            .addFormDataPart("duration", duration.toString())
            .build()

        val request = Request.Builder()
            .url(ApiClient.BASE_URL + "upload")
            .post(body)
            .build()

        // Cancelling the work (UI button or notification action) aborts the HTTP call.
        val call = ApiClient.uploadHttp.newCall(request)
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }

        try {
            call.execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val publicId = parsePublicId(response.body?.string())
                        releasePermission(uri)
                        Result.success(workDataOf(KEY_PUBLIC_ID to publicId, KEY_PROGRESS to 100))
                    }
                    response.code == 401 || response.code == 403 ->
                        fail("Your session expired. Please sign in again and retry.")
                    response.code == 413 ->
                        fail("This file is larger than the server allows.")
                    response.code in 500..599 && runAttemptCount < MAX_ATTEMPTS - 1 -> {
                        Log.w(TAG, "Server error ${response.code}, will retry")
                        Result.retry()
                    }
                    else -> fail("Upload failed (error ${response.code}). Please try again.")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Upload interrupted: ${e.javaClass.simpleName}")
            when {
                isStopped -> fail("Upload cancelled.")
                runAttemptCount < MAX_ATTEMPTS - 1 -> Result.retry()
                else -> fail("Upload failed. Check your connection and try again.")
            }
        } catch (e: SecurityException) {
            fail("AirStreamX no longer has access to that file. Please pick it again.")
        } finally {
            cancelHandle?.dispose()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(inputData.getString(KEY_TITLE).orEmpty(), 0)

    /**
     * Read access is kept on failure so "Try again" can re-use the same file;
     * it's released after a successful upload (and when the user picks another file).
     */
    private fun fail(message: String): Result = Result.failure(workDataOf(KEY_ERROR to message))

    private fun releasePermission(uri: Uri) {
        try {
            applicationContext.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Not persisted (e.g. a file:// URI) — nothing to release.
        }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        var name = "video.mp4"
        var size = -1L
        try {
            applicationContext.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { i ->
                        c.getString(i)?.let { name = it }
                    }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { i ->
                        size = c.getLong(i)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't query file metadata: ${e.javaClass.simpleName}")
        }
        return name to size
    }

    /** Accepts either { video: {...} } or a bare video object (response shape to confirm). */
    private fun parsePublicId(json: String?): String? {
        if (json.isNullOrBlank()) return null
        return try {
            ApiClient.moshi.adapter(VideoResponse::class.java).fromJson(json)?.video?.publicId
                ?: ApiClient.moshi.adapter(NetworkVideo::class.java).fromJson(json)?.publicId
        } catch (e: Exception) {
            null
        }
    }

    private fun foregroundInfo(title: String, percent: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Video uploads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Uploading ${title.ifBlank { "video" }}")
            .setContentText("$percent%")
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    /** Streams a content:// URI and reports bytes written, without loading it into memory. */
    private class ContentUriRequestBody(
        private val context: Context,
        private val uri: Uri,
        private val mediaType: MediaType?,
        private val length: Long,
        private val onProgress: (Long) -> Unit
    ) : RequestBody() {
        override fun contentType() = mediaType
        override fun contentLength() = length
        override fun writeTo(sink: BufferedSink) {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open selected file")
            input.source().use { source ->
                var total = 0L
                while (true) {
                    val read = source.read(sink.buffer, SEGMENT_BYTES)
                    if (read == -1L) break
                    total += read
                    sink.flush()
                    onProgress(total)
                }
            }
        }
    }

    companion object {
        private const val TAG = "UploadWorker"
        const val UNIQUE_NAME = "video_upload"
        private const val CHANNEL_ID = "uploads"
        private const val NOTIFICATION_ID = 4201
        private const val MAX_ATTEMPTS = 3
        private const val SEGMENT_BYTES = 64 * 1024L

        /** Server limit — matches the web uploader. */
        const val MAX_UPLOAD_BYTES = 500L * 1024 * 1024

        const val KEY_URI = "uri"
        const val KEY_TITLE = "title"
        const val KEY_CATEGORY = "category"
        const val KEY_DURATION = "duration"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val KEY_PUBLIC_ID = "public_id"

        fun enqueue(context: Context, uri: Uri, title: String, category: String, durationSeconds: Double): UUID {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(
                    Data.Builder()
                        .putString(KEY_URI, uri.toString())
                        .putString(KEY_TITLE, title)
                        .putString(KEY_CATEGORY, category)
                        .putDouble(KEY_DURATION, durationSeconds)
                        .build()
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // The UI blocks starting a second upload while one is running.
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
            return request.id
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
