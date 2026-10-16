package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The signed-in user as the UI sees it. [email] is the user's OWN address
 * (empty for guests); other users' emails never reach this class.
 */
data class LocalUser(
    val email: String,
    val displayName: String,
    val avatarUrl: String = ""
)

/** Thrown when the build has no usable Firebase config (google-services.json). */
class AuthUnavailableException :
    Exception("Sign-in is unavailable right now. Please update the app or try again later.")

/**
 * Firebase is the ONLY source of truth for who is signed in.
 *
 * SECURITY: the previous version had "local fallbacks" that created a session
 * for any email with any (or no) password whenever Firebase wasn't available,
 * and restored sessions from SharedPreferences/Room without Firebase. Those
 * users looked logged in but every authenticated request failed — and anyone
 * could claim any identity on the device. All of that is removed: if Firebase
 * can't verify the user, sign-in fails.
 */
class AuthRepository(private val context: Context) {
    private val firebaseAuth: FirebaseAuth? = try {
        if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseAuth.getInstance() else null
    } catch (e: Exception) {
        Log.e(TAG, "Firebase unavailable: ${e.javaClass.simpleName}")
        null
    }

    private val _currentUser = MutableStateFlow(firebaseAuth?.currentUser?.toLocalUser())
    val currentUser: StateFlow<LocalUser?> = _currentUser

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Keeps currentUser in sync with Firebase (sign-in, sign-out, account deletion,
    // token revocation).
    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        _currentUser.value = auth.currentUser?.toLocalUser()
    }

    init {
        firebaseAuth?.addAuthStateListener(authListener)
        if (firebaseAuth == null) {
            Log.e(TAG, "Firebase is not configured — add app/google-services.json. Sign-in is disabled.")
        }
        // One-time cleanup of the old, unverified local session copies.
        ioScope.launch { clearLegacyLocalSession() }
    }

    /** Call from ViewModel.onCleared(). */
    fun close() {
        firebaseAuth?.removeAuthStateListener(authListener)
    }

    fun isFirebaseAvailable(): Boolean = firebaseAuth != null

    suspend fun getFirebaseToken(): String? {
        val user = firebaseAuth?.currentUser ?: return null
        return try {
            user.getIdToken(false).await().token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve Firebase ID token: ${e.javaClass.simpleName}")
            null
        }
    }

    suspend fun signInWithGoogle(idToken: String?): Result<LocalUser> {
        val auth = firebaseAuth ?: return Result.failure(AuthUnavailableException())
        if (idToken.isNullOrBlank()) {
            return Result.failure(IllegalStateException("Google didn't return a sign-in token. Please try again."))
        }
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val user = auth.signInWithCredential(credential).await().user
                ?: return Result.failure(IllegalStateException("Google sign-in failed."))
            Result.success(user.toLocalUser().also { _currentUser.value = it })
        } catch (e: Exception) {
            Log.e(TAG, "Google credential sign-in failed: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<LocalUser> {
        val auth = firebaseAuth ?: return Result.failure(AuthUnavailableException())
        return try {
            val user = auth.signInWithEmailAndPassword(email, password).await().user
                ?: return Result.failure(IllegalStateException("Sign-in failed."))
            Result.success(user.toLocalUser().also { _currentUser.value = it })
        } catch (e: Exception) {
            Log.e(TAG, "Email sign-in failed: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, displayName: String, password: String): Result<LocalUser> {
        val auth = firebaseAuth ?: return Result.failure(AuthUnavailableException())
        return try {
            val user = auth.createUserWithEmailAndPassword(email, password).await().user
                ?: return Result.failure(IllegalStateException("Sign-up failed."))
            user.updateProfile(userProfileChangeRequest { this.displayName = displayName }).await()
            Result.success(user.toLocalUser().copy(displayName = displayName).also { _currentUser.value = it })
        } catch (e: Exception) {
            Log.e(TAG, "Email sign-up failed: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    /** Browse-only guest session via Firebase anonymous auth. No local fallback. */
    suspend fun signInAnonymously(): Result<LocalUser> {
        val auth = firebaseAuth ?: return Result.failure(AuthUnavailableException())
        return try {
            val user = auth.signInAnonymously().await().user
                ?: return Result.failure(IllegalStateException("Guest sign-in failed."))
            Result.success(user.toLocalUser().also { _currentUser.value = it })
        } catch (e: Exception) {
            Log.e(TAG, "Anonymous sign-in failed: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    fun isFirebaseAnonymous(): Boolean = firebaseAuth?.currentUser?.isAnonymous == true

    fun logout() {
        val auth = firebaseAuth
        val user = auth?.currentUser
        if (user != null && user.isAnonymous) {
            // Remove throwaway guest accounts so they don't pile up in Firebase.
            ioScope.launch {
                try {
                    user.delete().await()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not delete guest account: ${e.javaClass.simpleName}")
                }
                auth?.signOut()
            }
        } else {
            auth?.signOut()
        }
        _currentUser.value = null
        ioScope.launch { clearLegacyLocalSession() }
    }

    private suspend fun clearLegacyLocalSession() {
        try {
            context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            AppDatabase.getDatabase(context).userDao().clearUser()
        } catch (e: Exception) {
            Log.w(TAG, "Legacy session cleanup failed: ${e.javaClass.simpleName}")
        }
    }

    private fun FirebaseUser.toLocalUser() = LocalUser(
        email = email.orEmpty(),
        displayName = displayName?.takeIf { it.isNotBlank() }
            ?: if (isAnonymous) "Guest" else "AirStreamX user",
        avatarUrl = photoUrl?.toString().orEmpty()
    )

    companion object {
        private const val TAG = "AuthRepository"
        private const val LEGACY_PREFS = "airstreamx_auth"
    }
}

/** Suspends until a Play Services Task completes. */
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: RuntimeException("Task failed"))
            }
        }
    }
