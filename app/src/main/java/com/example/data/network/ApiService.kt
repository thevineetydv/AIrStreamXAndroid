package com.example.data.network

import retrofit2.http.*

interface ApiService {
    /**
     * Home feed: GET /videos?type=long&limit=24&offset=N → { success, videos[], total, hasMore }.
     * Shorts use the same endpoint without `type` (mirrors the web ShortsPage) and
     * are filtered client-side by duration — see VideoRepository.getShorts().
     */
    @GET("videos")
    suspend fun getVideos(
        @Query("type") type: String? = null,
        @Query("limit") limit: Int = 24,
        @Query("offset") offset: Int? = null,
        // ENDPOINT TO CONFIRM: `search` and `category` are used by the web app but
        // aren't part of the documented contract.
        @Query("search") search: String? = null,
        @Query("category") category: String? = null
    ): VideosResponse

    /**
     * GET /videos/{public_id or id} → { success, video }. Sends the Bearer token when
     * logged in (interceptor) so owners can open their own private videos.
     * 404 = "Video not found" (also for other people's private videos).
     */
    @GET("videos/{id}")
    suspend fun getVideoById(
        @Path("id") id: String
    ): VideoResponse

    // ENDPOINT TO CONFIRM: not in the documented contract; the web app only uses it
    // as a handle-availability check, so this response shape is assumed.
    @GET("api/channel/{handle}")
    suspend fun getChannelInfo(
        @Path("handle") handle: String
    ): NetworkChannelInfo

    // ENDPOINT TO CONFIRM: request body shape (`channelId` = creator email) is assumed.
    @POST("api/subscribe")
    suspend fun subscribeChannel(
        @Body request: SubscribeRequest
    ): SubscribeResponse

    // POST /upload lives in upload/UploadWorker (streams the file with progress).
    // ENDPOINT TO CONFIRM: its multipart field names and response shape.
}
