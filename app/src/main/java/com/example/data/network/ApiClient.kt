package com.example.data.network

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Single shared HTTP stack for the whole app (feed, player, upload worker).
 * Previously the client lived inside VideoRepository, so every component that
 * needed the API had to build its own.
 */
object ApiClient {
    const val BASE_URL = "https://backend.airstreamx.com/"

    private const val TAG = "ApiClient"

    /**
     * Returns a Firebase ID token for the signed-in user, or null for logged-out
     * requests. getIdToken(false) returns the cached token and transparently
     * refreshes it when it's close to expiry. Runs on an OkHttp worker thread,
     * never on the main thread.
     */
    fun currentIdTokenBlocking(): String? = try {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) null
        else Tasks.await(user.getIdToken(false), 10, TimeUnit.SECONDS).token
    } catch (e: IllegalStateException) {
        null // Firebase not initialised (no google-services.json)
    } catch (e: Exception) {
        Log.w(TAG, "Could not get Firebase ID token: ${e.javaClass.simpleName}")
        null
    }

    private val authInterceptor = Interceptor { chain ->
        val token = currentIdTokenBlocking()
        val request = if (token != null) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    val okHttp: OkHttpClient by lazy {
        // PRIVACY: never log bodies or headers — bodies carry uploader emails and
        // headers carry the bearer token. BASIC (method, URL, status) in debug only.
        val logging = HttpLoggingInterceptor().apply {
            level = if (com.example.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Same connection pool and auth, but timeouts suited to multi-hundred-MB uploads. */
    val uploadHttp: OkHttpClient by lazy {
        okHttp.newBuilder()
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    val moshi: Moshi by lazy {
        Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    }

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ApiService::class.java)
    }
}
