package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.UserEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LocalUser(
    val email: String,
    val displayName: String,
    val avatarUrl: String = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&auto=format&fit=crop"
)

class AuthRepository(private val context: Context) {
    private var firebaseAuth: FirebaseAuth? = null
    private val _currentUser = MutableStateFlow<LocalUser?>(null)
    val currentUser: StateFlow<LocalUser?> = _currentUser

    private val sharedPrefs = context.getSharedPreferences("airstreamx_auth", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(context)
    private val userDao = database.userDao()

    init {
        // Attempt to initialize Firebase securely and gracefully
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                firebaseAuth = FirebaseAuth.getInstance()
                val fbUser = firebaseAuth?.currentUser
                if (fbUser != null) {
                    _currentUser.value = LocalUser(
                        email = fbUser.email ?: "developer@airstreamx.com",
                        displayName = fbUser.displayName ?: "AirStreamer",
                        avatarUrl = fbUser.photoUrl?.toString() ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&auto=format&fit=crop"
                    )
                }
            } else {
                Log.w("AuthRepository", "Firebase has not been initialized. Falling back to robust Local Auth.")
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Firebase initialization error: ${e.message}. Using simulated fallbacks.")
        }

        // If no user found, look at SharedPrefs fallback
        if (_currentUser.value == null) {
            val savedEmail = sharedPrefs.getString("email", null)
            val savedName = sharedPrefs.getString("display_name", null)
            val savedAvatar = sharedPrefs.getString("avatar_url", "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&auto=format&fit=crop")
            if (savedEmail != null && savedName != null) {
                _currentUser.value = LocalUser(
                    email = savedEmail,
                    displayName = savedName,
                    avatarUrl = savedAvatar ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&auto=format&fit=crop"
                )
            }
        }

        // Asynchronously check and restore session from Room database to ensure robust persistence
        try {
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val dbUser = userDao.getActiveUser()
                if (dbUser != null) {
                    _currentUser.value = LocalUser(
                        email = dbUser.email,
                        displayName = dbUser.displayName,
                        avatarUrl = dbUser.avatarUrl
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error restoring Room user session", e)
        }
    }

    fun isFirebaseAvailable(): Boolean {
        return firebaseAuth != null
    }

    suspend fun getFirebaseToken(): String? {
        val auth = firebaseAuth ?: return null
        val user = auth.currentUser ?: return null
        return try {
            val result = user.getIdToken(false).await()
            result.token
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to retrieve Firebase ID token", e)
            null
        }
    }

    private suspend fun saveSessionToRoomAndPrefs(user: LocalUser, isGoogle: Boolean) {
        // Save to Room Database
        try {
            userDao.insertUser(
                UserEntity(
                    email = user.email,
                    displayName = user.displayName,
                    avatarUrl = user.avatarUrl,
                    isGoogleUser = isGoogle,
                    lastLoginTime = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error saving user to Room database", e)
        }

        // Save to SharedPrefs
        sharedPrefs.edit()
            .putString("email", user.email)
            .putString("display_name", user.displayName)
            .putString("avatar_url", user.avatarUrl)
            .putBoolean("is_google", isGoogle)
            .apply()
    }

    suspend fun signInWithGoogle(idToken: String?, email: String, displayName: String?, photoUrl: String?): Result<LocalUser> {
        val finalPhotoUrl = photoUrl ?: "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&auto=format&fit=crop"
        val finalDisplayName = displayName ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }
        
        val auth = firebaseAuth
        return if (auth != null && idToken != null) {
            try {
                val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                val task = auth.signInWithCredential(credential).await()
                val fbUser = task.user
                
                val loggedUser = LocalUser(
                    email = fbUser?.email ?: email,
                    displayName = fbUser?.displayName ?: finalDisplayName,
                    avatarUrl = fbUser?.photoUrl?.toString() ?: finalPhotoUrl
                )
                
                _currentUser.value = loggedUser
                saveSessionToRoomAndPrefs(loggedUser, isGoogle = true)
                Result.success(loggedUser)
            } catch (e: Exception) {
                // SECURITY FIX: if Firebase rejects the Google credential (bad/
                // expired token), don't blindly trust the caller-supplied email
                // and log them in anyway — that skips real verification entirely.
                Log.e("AuthRepository", "Firebase Google credential sign in failed", e)
                Result.failure(e)
            }
        } else {
            val loggedUser = LocalUser(email = email, displayName = finalDisplayName, avatarUrl = finalPhotoUrl)
            _currentUser.value = loggedUser
            saveSessionToRoomAndPrefs(loggedUser, isGoogle = true)
            Result.success(loggedUser)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<LocalUser> {
        val auth = firebaseAuth
        if (auth != null) {
            return try {
                val task = auth.signInWithEmailAndPassword(email, password).await()
                val fbUser = task.user
                val loggedUser = LocalUser(
                    email = fbUser?.email ?: email,
                    displayName = fbUser?.displayName ?: email.substringBefore("@")
                )
                _currentUser.value = loggedUser
                saveSessionToRoomAndPrefs(loggedUser, isGoogle = false)
                Result.success(loggedUser)
            } catch (e: Exception) {
                // SECURITY FIX: A failed sign-in attempt (wrong password, no such
                // user, etc.) must be reported as a failure — NOT silently logged
                // in via the local fallback. The fallback exists only for when
                // Firebase itself isn't available (see the `else` branch below),
                // not for wrong credentials against a real, working Firebase
                // instance. The previous version let ANYONE in with ANY password
                // as soon as Firebase rejected the real one.
                Log.e("AuthRepository", "Firebase Auth sign in failed", e)
                Result.failure(e)
            }
        } else {
            return fallbackLocalSignIn(email)
        }
    }

    suspend fun signUpWithEmail(email: String, displayName: String, password: String): Result<LocalUser> {
        val auth = firebaseAuth
        if (auth != null) {
            return try {
                val task = auth.createUserWithEmailAndPassword(email, password).await()
                val fbUser = task.user
                val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                    this.displayName = displayName
                }
                fbUser?.updateProfile(profileUpdates)?.await()
                val loggedUser = LocalUser(email = email, displayName = displayName)
                _currentUser.value = loggedUser
                saveSessionToRoomAndPrefs(loggedUser, isGoogle = false)
                Result.success(loggedUser)
            } catch (e: Exception) {
                // SECURITY FIX: a real signup failure (email already registered,
                // weak password, etc.) must surface as a failure, not silently
                // create an unauthenticated local account instead.
                Log.e("AuthRepository", "Firebase Auth sign up failed", e)
                Result.failure(e)
            }
        } else {
            return fallbackLocalSignUp(email, displayName)
        }
    }

    fun logout() {
        try {
            val user = firebaseAuth?.currentUser
            if (user != null && user.isAnonymous) {
                @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        user.delete().await()
                        Log.d("AuthRepository", "Deleted anonymous guest user in Firebase successfully.")
                    } catch (e: Exception) {
                        Log.e("AuthRepository", "Failed to delete anonymous guest user in Firebase: ${e.message}")
                    }
                    firebaseAuth?.signOut()
                }
            } else {
                firebaseAuth?.signOut()
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Firebase logout error: ${e.message}")
        }
        _currentUser.value = null
        sharedPrefs.edit().clear().apply()
        
        try {
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                userDao.clearUser()
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Error clearing user database on logout", e)
        }
    }

    suspend fun signInAnonymously(): Result<LocalUser> {
        val auth = firebaseAuth
        return if (auth != null) {
            try {
                val task = auth.signInAnonymously().await()
                val fbUser = task.user
                
                val loggedUser = LocalUser(
                    email = "guest@airstreamx.com",
                    displayName = "Guest User",
                    avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200"
                )
                
                saveSessionToRoomAndPrefs(loggedUser, isGoogle = false)
                _currentUser.value = loggedUser
                Result.success(loggedUser)
            } catch (e: Exception) {
                Log.e("AuthRepository", "Firebase Auth anonymous sign in failed, calling local fallback", e)
                fallbackAnonymous()
            }
        } else {
            fallbackAnonymous()
        }
    }

    fun isFirebaseAnonymous(): Boolean {
        return firebaseAuth?.currentUser?.isAnonymous == true
    }

    private suspend fun fallbackAnonymous(): Result<LocalUser> {
        val user = LocalUser(
            email = "guest@airstreamx.com",
            displayName = "Guest User",
            avatarUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200"
        )
        saveSessionToRoomAndPrefs(user, isGoogle = false)
        _currentUser.value = user
        return Result.success(user)
    }

    private suspend fun fallbackLocalSignIn(email: String): Result<LocalUser> {
        val displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
        val user = LocalUser(email = email, displayName = displayName)
        saveSessionToRoomAndPrefs(user, isGoogle = false)
        _currentUser.value = user
        return Result.success(user)
    }

    private suspend fun fallbackLocalSignUp(email: String, displayName: String): Result<LocalUser> {
        val user = LocalUser(email = email, displayName = displayName)
        saveSessionToRoomAndPrefs(user, isGoogle = false)
        _currentUser.value = user
        return Result.success(user)
    }
}

// Custom await extension to safely turn Tasks into Kotlin Co-routines suspensions
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = 
    kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            val exception = task.exception
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(exception ?: RuntimeException("Task failed"))
            }
        }
    }