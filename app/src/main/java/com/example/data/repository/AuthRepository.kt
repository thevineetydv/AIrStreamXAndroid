package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
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
    val avatarUrl: String = "",
    /** Browse-only guest: no account, no token, every protected action asks to sign in. */
    val isGuest: Boolean = false
)

private val GUEST_USER = LocalUser(email = "", displayName = "Guest", isGuest = true)

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

    // Guest browsing is a local "no account" mode, NOT Firebase anonymous auth:
    // anonymous auth fails with "This operation is restricted to administrators
    // only" unless the Anonymous provider is enabled in the Firebase console, and
    // a guest needs no identity anyway (the feed and playback are public).
    // A guest never gets a token and can't claim any identity, so this isn't an
    // auth bypass.
    private val guestPrefs = context.getSharedPreferences(GUEST_PREFS, Context.MODE_PRIVATE)
    private var guestMode: Boolean
        get() = guestPrefs.getBoolean(KEY_GUEST, false)
        set(value) { guestPrefs.edit().putBoolean(KEY_GUEST, value).apply() }

    private fun resolveUser(fbUser: FirebaseUser?): LocalUser? = when {
        fbUser != null && !fbUser.isAnonymous -> fbUser.toLocalUser()
        guestMode -> GUEST_USER
        else -> null
    }

    private val _currentUser = MutableStateFlow(resolveUser(firebaseAuth?.currentUser))
    val currentUser: StateFlow<LocalUser?> = _currentUser

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Keeps currentUser in sync with Firebase (sign-in, sign-out, account deletion,
    // token revocation).
    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        _currentUser.value = resolveUser(auth.currentUser)
    }

    init {
        // Sessions from the old Firebase-anonymous guest flow are dropped; guests
        // are now account-less (see guestMode).
        if (firebaseAuth?.currentUser?.isAnonymous == true) firebaseAuth?.signOut()
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
            guestMode = false
            Result.success(user.toLocalUser().also { _currentUser.value = it })
        } catch (e: Exception) {
            Log.e(TAG, "Google credential sign-in failed: ${errorCode(e)}")
            Result.failure(friendly(e))
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<LocalUser> {
        val auth = firebaseAuth ?: return Result.failure(AuthUnavailableException())
        return try {
            val user = auth.signInWithEmailAndPassword(email, password).await().user
                ?: return Result.failure(IllegalStateException("Sign-in failed."))
            guestMode = false
            Result.success(user.toLocalUser().also { _currentUser.value = it })
        } catch (e: Exception) {
            Log.e(TAG, "Email sign-in failed: ${errorCode(e)}")
            Result.failure(friendly(e))
        }
    }

    suspend fun signUpWithEmail(email: String, displayName: String, password: String): Result<LocalUser> {
        val auth = firebaseAuth ?: return Result.failure(AuthUnavailableException())
        return try {
            val user = auth.createUserWithEmailAndPassword(email, password).await().user
                ?: return Result.failure(IllegalStateException("Sign-up failed."))
            user.updateProfile(userProfileChangeRequest { this.displayName = displayName }).await()
            guestMode = false
            Result.success(user.toLocalUser().copy(displayName = displayName).also { _currentUser.value = it })
        } catch (e: Exception) {
            Log.e(TAG, "Email sign-up failed: ${errorCode(e)}")
            Result.failure(friendly(e))
        }
    }

    /** Browse without an account. Works even when Firebase isn't configured. */
    fun continueAsGuest(): LocalUser {
        guestMode = true
        _currentUser.value = GUEST_USER
        return GUEST_USER
    }

    fun isGuest(): Boolean = _currentUser.value?.isGuest ?: true

    fun logout() {
        firebaseAuth?.signOut()
        guestMode = false
        _currentUser.value = null
        ioScope.launch { clearLegacyLocalSession() }
    }

    private fun errorCode(e: Exception): String =
        (e as? FirebaseAuthException)?.errorCode ?: e.javaClass.simpleName

    /** Turns Firebase error codes into messages a user can act on. */
    private fun friendly(e: Exception): Exception {
        if (e is FirebaseNetworkException) {
            return Exception("No internet connection. Check your network and try again.")
        }
        val msg = when (errorCode(e)) {
            "ERROR_OPERATION_NOT_ALLOWED", "ADMIN_ONLY_OPERATION", "ERROR_ADMIN_RESTRICTED_OPERATION" ->
                "Email sign-in isn't available yet. Please use Continue with Google."
            "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
            "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_CREDENTIAL", "INVALID_LOGIN_CREDENTIALS" ->
                "Wrong email or password."
            "ERROR_USER_DISABLED" -> "This account has been disabled."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with this email already exists. Try signing in."
            "ERROR_WEAK_PASSWORD" -> "Please choose a stronger password (at least 6 characters)."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please wait a few minutes and try again."
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                "This email is already linked to another sign-in method. Try Continue with Google."
            else -> e.message ?: "Sign-in failed. Please try again."
        }
        return Exception(msg, e)
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
        private const val GUEST_PREFS = "airstreamx_guest"
        private const val KEY_GUEST = "guest_mode"
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
