package com.example.ui.screens

import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

/**
 * Formats subscriber count accurately according to specific requirements:
 * - 0 = "No subscribers yet"
 * - 1 = "1 subscriber"
 * - 1000 = "1.0K subscribers"
 * - 1000000 = "1.0M subscribers"
 */
fun formatSubscriberCount(count: Int): String {
    return when {
        count <= 0 -> "No subscribers yet"
        count == 1 -> "1 subscriber"
        count >= 1000000 -> {
            val value = count / 1000000.0
            String.format(Locale.US, "%.1fM subscribers", value)
        }
        count >= 1000 -> {
            val value = count / 1000.0
            String.format(Locale.US, "%.1fK subscribers", value)
        }
        else -> "$count subscribers"
    }
}

/**
 * Decodes standard timestamps (e.g., "2026-06-10T08:02:40.531Z") to a clean relative timeago string.
 */
fun timeAgo(dateString: String?): String {
    if (dateString.isNullOrEmpty()) return "recently"
    
    // If it's already a relative phrase, return as is
    if (dateString.contains("ago") || dateString.contains("now") || dateString.contains("yesterday")) {
        return dateString
    }
    
    val timeMs = try {
        // Effort 1: java.time (Instant)
        Instant.parse(dateString).toEpochMilli()
    } catch (e: Exception) {
        try {
            // Effort 2: ISO 8601 with millis
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(dateString)?.time ?: 0L
        } catch (e2: Exception) {
            try {
                // Effort 3: ISO 8601 without millis
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                sdf.parse(dateString)?.time ?: 0L
            } catch (e3: Exception) {
                0L
            }
        }
    }

    if (timeMs == 0L) return dateString

    val diff = (System.currentTimeMillis() - timeMs) / 1000.0
    return when {
        diff < 0 -> "just now"
        diff < 60 -> "just now"
        diff < 3600 -> "${(diff / 60).toLong()} min ago"
        diff < 86400 -> "${(diff / 3600).toLong()} hours ago"
        diff < 604800 -> "${(diff / 86400).toLong()} days ago"
        diff < 2592000 -> "${(diff / 604800).toLong()} weeks ago"
        else -> "${(diff / 2592000).toLong()} months ago"
    }
}

/**
 * Formats video views count to a readable string (e.g., "1.2M", "45.1K" or "842").
 */
fun formatViews(views: Int): String = when {
    views >= 1_000_000 -> String.format(Locale.US, "%.1fM", views / 1_000_000.0)
    views >= 1_000 -> String.format(Locale.US, "%.1fK", views / 1_000.0)
    else -> views.toString()
}

/**
 * Formats the video duration from double-precision seconds to 'H:MM:SS' or 'M:SS' / 'MM:SS'.
 */
fun formatDuration(seconds: Double): String {
    val s = seconds.toInt()
    return if (s >= 3600) {
        String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    } else {
        String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }
}

