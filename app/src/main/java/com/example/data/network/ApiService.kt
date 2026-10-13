package com.example.data.network

import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @GET("videos")
    suspend fun getVideos(
        @Query("limit") limit: Int = 20,
        @Query("search") search: String? = null
    ): VideosResponse

    @GET("videos/{id}")
    suspend fun getVideoById(
        @Path("id") id: String
    ): NetworkVideo

    @GET("api/channel/{handle}")
    suspend fun getChannelInfo(
        @Path("handle") handle: String
    ): NetworkChannelInfo

    @POST("api/subscribe")
    suspend fun subscribeChannel(
        @Body request: SubscribeRequest
    ): SubscribeResponse

    @Multipart
    @POST("upload")
    suspend fun uploadVideo(
        @Part file: okhttp3.MultipartBody.Part,
        @Part("title") title: okhttp3.RequestBody,
        @Part("category") category: okhttp3.RequestBody,
        @Part("duration") duration: okhttp3.RequestBody
    ): NetworkVideo
}