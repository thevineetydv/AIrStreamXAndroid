package com.example.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.network.Video
import com.example.ui.viewmodel.ActiveVideoError
import java.util.Locale

private const val TAG = "TipCard"
private val Accent = Color(0xFFFF5A5F)

/** Outcome of a UPI payment as reported by the UPI app's result callback. */
private enum class TipResult { SUCCESS, PENDING, FAILED }

/**
 * Builds a standard UPI deep link. Exposed for unit tests.
 *
 * Required params per the UPI linking spec: pa (payee VPA), pn (payee name),
 * am (amount, 2 decimals), cu=INR, tn (note). `tr` is a client reference so the
 * creator can match the payment in their UPI app.
 */
fun buildUpiTipUri(upiId: String, payeeName: String, amountRupees: Int, note: String, txnRef: String): Uri =
    Uri.Builder()
        .scheme("upi")
        .authority("pay")
        .appendQueryParameter("pa", upiId.trim())
        .appendQueryParameter("pn", payeeName.take(50))
        .appendQueryParameter("am", String.format(Locale.US, "%.2f", amountRupees.toDouble()))
        .appendQueryParameter("cu", "INR")
        .appendQueryParameter("tn", note.take(50))
        .appendQueryParameter("tr", txnRef)
        .build()

/**
 * UPI apps return "txnId=…&responseCode=…&Status=SUCCESS|FAILURE|SUBMITTED&txnRef=…"
 * in the "response" extra. Exposed for unit tests.
 */
fun parseUpiStatus(response: String?): String? =
    response
        ?.split("&")
        ?.map { it.split("=", limit = 2) }
        ?.firstOrNull { it.size == 2 && it[0].equals("Status", ignoreCase = true) }
        ?.get(1)
        ?.uppercase(Locale.US)

private fun isValidUpiId(id: String) = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,64}$").matches(id.trim())

/**
 * Direct creator tipping over UPI.
 *
 * - Opens a upi://pay intent with pa, pn, am, cu=INR, tn.
 * - Handles "no UPI app installed".
 * - A tip is only shown as successful when the UPI app's result callback says
 *   Status=SUCCESS. A cancelled/back-pressed/unknown result is never shown as paid.
 */
@Composable
fun TipCard(video: Video) {
    val context = LocalContext.current
    val presetAmounts = listOf(10, 29, 49, 99)
    var selectedAmount by remember { mutableIntStateOf(49) }
    var result by remember(video.publicId) { mutableStateOf<TipResult?>(null) }
    var message by remember(video.publicId) { mutableStateOf<String?>(null) }

    val upiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val data = activityResult.data
        val response = data?.getStringExtra("response") ?: data?.getStringExtra("Status")
        val status = parseUpiStatus(response) ?: response?.uppercase(Locale.US)?.takeIf { it in setOf("SUCCESS", "FAILURE", "SUBMITTED") }
        result = when {
            activityResult.resultCode != Activity.RESULT_OK && status == null -> TipResult.FAILED
            status == "SUCCESS" -> TipResult.SUCCESS
            status == "SUBMITTED" -> TipResult.PENDING
            else -> TipResult.FAILED
        }
        message = when (result) {
            TipResult.SUCCESS -> "Thank you! Your UPI app confirmed the ₹$selectedAmount tip."
            TipResult.PENDING -> "Payment submitted. Check your UPI app for the final status."
            else -> "Payment wasn't completed. No money was sent."
        }
    }

    val hasUpi = video.upiId.isNotBlank() && isValidUpiId(video.upiId)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Accent.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag("tip_card_${video.publicId}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Tip ${video.channelName.ifBlank { "this creator" }} directly",
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier.background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("UPI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
            }
        }
        Spacer(Modifier.height(4.dp))

        if (!hasUpi) {
            Text(
                "This creator hasn't set up UPI tips yet.",
                fontSize = 12.sp, color = Color(0xFFA0A0A0), lineHeight = 16.sp,
                modifier = Modifier.testTag("tip_unavailable")
            )
            return@Column
        }

        Text(
            "Straight to their bank. No subscriber minimum, no ad share.",
            fontSize = 12.sp, color = Color(0xFFA0A0A0), lineHeight = 16.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presetAmounts.forEach { amount ->
                val isSelected = selectedAmount == amount
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .background(
                            if (isSelected) Accent else Color.White.copy(alpha = 0.06f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedAmount = amount }
                        .testTag("tip_amount_${amount}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "₹$amount", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else Color(0xFFA0A0A0)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                result = null
                message = null
                val uri = buildUpiTipUri(
                    upiId = video.upiId,
                    payeeName = video.channelName.ifBlank { "AirStreamX creator" },
                    amountRupees = selectedAmount,
                    note = "AirStreamX tip",
                    txnRef = "ASX${System.currentTimeMillis()}"
                )
                val upiIntent = Intent(Intent.ACTION_VIEW, uri)
                // Requires the <queries> upi entry in the manifest on Android 11+.
                if (upiIntent.resolveActivity(context.packageManager) == null) {
                    result = TipResult.FAILED
                    message = "No UPI app found. Install Google Pay, PhonePe, Paytm or BHIM to send a tip."
                    return@Button
                }
                try {
                    upiLauncher.launch(Intent.createChooser(upiIntent, "Pay with UPI app"))
                } catch (e: ActivityNotFoundException) {
                    Log.w(TAG, "No activity for UPI intent")
                    result = TipResult.FAILED
                    message = "No UPI app found. Install Google Pay, PhonePe, Paytm or BHIM to send a tip."
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("send_tip_button")
        ) {
            Text("Send ₹$selectedAmount via UPI", fontWeight = FontWeight.Bold, color = Color.White)
        }

        message?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it,
                fontSize = 12.sp,
                color = when (result) {
                    TipResult.SUCCESS -> Color(0xFF22C55E)
                    TipResult.PENDING -> Color(0xFFFBBF24)
                    else -> Color(0xFFA0A0A0)
                },
                modifier = Modifier.fillMaxWidth().testTag("tip_result_message"),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Shown while a video is loading by id, or when it can't be opened. */
@Composable
fun PlayerStatusScreen(
    error: ActiveVideoError?,
    onBackClick: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .statusBarsPadding()
            .testTag("player_status_screen")
    ) {
        IconButton(onClick = onBackClick, modifier = Modifier.padding(4.dp).size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Column(
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (error) {
                null -> CircularProgressIndicator(color = Accent)
                ActiveVideoError.NotFound -> {
                    Text("Video not found", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "It may have been removed or made private.",
                        color = Color(0xFFA0A0A0), fontSize = 13.sp, textAlign = TextAlign.Center
                    )
                }
                is ActiveVideoError.LoadFailed -> {
                    Text("Couldn't load this video", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(error.message, color = Color(0xFFA0A0A0), fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("Retry", color = Color.White)
                    }
                }
            }
        }
    }
}

/** Replaces the player while the video's status isn't "ready" yet. */
@Composable
fun ProcessingPlaceholder(thumbnailUrl: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.Black).testTag("video_processing_state")) {
        if (thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
        }
        Column(
            modifier = Modifier.align(Alignment.Center).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.HourglassTop, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text("Processing", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "This video will be ready to watch soon.",
                color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, textAlign = TextAlign.Center
            )
        }
    }
}
