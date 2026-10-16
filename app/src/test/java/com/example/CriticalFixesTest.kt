package com.example

import com.example.data.network.NetworkVideo
import com.example.data.network.Video
import com.example.ui.screens.buildUpiTipUri
import com.example.ui.screens.parseUpiStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CriticalFixesTest {

    // ── C1/C3: mapping never invents data ──────────────────────────────────

    @Test
    fun `record without any id is dropped`() {
        assertNull(NetworkVideo(title = "x").toVideoOrNull())
    }

    @Test
    fun `missing url stays blank instead of a test stream`() {
        val v = NetworkVideo(id = 7, publicId = "abc", status = "processing").toVideoOrNull()!!
        assertEquals("", v.url)
        assertFalse(v.isPlayable())
        assertEquals(0, v.views)
    }

    @Test
    fun `channel name is never derived from the uploader email`() {
        val v = NetworkVideo(publicId = "abc", uploaderEmail = "someone@example.com").toVideoOrNull()!!
        assertFalse(v.channelName.contains("someone"))
        assertFalse(v.handle.contains("someone"))
    }

    @Test
    fun `visibility and status are normalised to lowercase`() {
        val v = NetworkVideo(publicId = "abc", visibility = "PRIVATE", status = "READY", url = "https://x/master.m3u8")
            .toVideoOrNull()!!
        assertFalse(v.isPublic())
        assertTrue(v.isPlayable())
    }

    @Test
    fun `share link uses public_id on www watch url`() {
        val v = NetworkVideo(id = 5, publicId = "pub123").toVideoOrNull()!!
        assertEquals("https://www.airstreamx.com/watch?v=pub123", v.shareUrl())
    }

    @Test
    fun `shorts are 1 to 60 seconds`() {
        fun d(s: Double) = NetworkVideo(publicId = "a", duration = s).toVideoOrNull()!!
        assertFalse(d(0.0).isShort())
        assertTrue(d(60.0).isShort())
        assertFalse(d(61.0).isShort())
        assertEquals(60.0, Video.SHORT_MAX_SECONDS, 0.0)
    }

    // ── C6: UPI ─────────────────────────────────────────────────────────────

    @Test
    fun `upi uri carries required params`() {
        val uri = buildUpiTipUri("creator@okaxis", "Creator", 49, "AirStreamX tip", "ASX1")
        assertEquals("upi", uri.scheme)
        assertEquals("pay", uri.authority)
        assertEquals("creator@okaxis", uri.getQueryParameter("pa"))
        assertEquals("Creator", uri.getQueryParameter("pn"))
        assertEquals("49.00", uri.getQueryParameter("am"))
        assertEquals("INR", uri.getQueryParameter("cu"))
        assertEquals("AirStreamX tip", uri.getQueryParameter("tn"))
    }

    @Test
    fun `only an explicit SUCCESS status counts as paid`() {
        assertEquals("SUCCESS", parseUpiStatus("txnId=1&responseCode=00&Status=SUCCESS&txnRef=ASX1"))
        assertEquals("FAILURE", parseUpiStatus("Status=FAILURE"))
        assertNull(parseUpiStatus(null))
        assertNull(parseUpiStatus("txnId=1&responseCode=ZD"))
    }
}
