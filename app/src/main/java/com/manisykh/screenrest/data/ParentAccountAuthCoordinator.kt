package com.manisykh.screenrest.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ParentAccountAuthState(
    val available: Boolean = false,
    val authenticated: Boolean = false,
    val recoverable: Boolean = false,
    val anonymous: Boolean = false,
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val lastError: String = "",
)

enum class ParentAccountAuthFailure {
    Configuration,
    InvalidCredential,
    Network,
    Authentication,
    Unknown,
}

data class ParentAccountAuthResult(
    val success: Boolean,
    val failure: ParentAccountAuthFailure? = null,
    val message: String = "",
) {
    companion object {
        val Success = ParentAccountAuthResult(success = true)
    }
}

interface ParentAccountAuthCoordinator {
    val state: StateFlow<ParentAccountAuthState>

    suspend fun signInParentWithGoogleIdToken(idToken: String): ParentAccountAuthResult

    suspend fun switchToChildAnonymousIdentity(): ParentAccountAuthResult

    fun recordUiFailure(message: String)
}

object ParentAccountAuthCoordinatorFactory {
    fun create(context: Context): ParentAccountAuthCoordinator {
        return try {
            val app = FirebaseApp.initializeApp(context) ?: FirebaseApp.getApps(context).firstOrNull()
                ?: return UnavailableParentAccountAuthCoordinator
            FirebaseParentAccountAuthCoordinator(FirebaseAuth.getInstance(app))
        } catch (_: Throwable) {
            UnavailableParentAccountAuthCoordinator
        }
    }
}

private object UnavailableParentAccountAuthCoordinator : ParentAccountAuthCoordinator {
    override val state: StateFlow<ParentAccountAuthState> = MutableStateFlow(
        ParentAccountAuthState(
            available = false,
            lastError = "Firebase authentication configuration is unavailable",
        ),
    )

    override suspend fun signInParentWithGoogleIdToken(idToken: String) =
        ParentAccountAuthResult(
            success = false,
            failure = ParentAccountAuthFailure.Configuration,
            message = state.value.lastError,
        )

    override suspend fun switchToChildAnonymousIdentity() =
        ParentAccountAuthResult(
            success = false,
            failure = ParentAccountAuthFailure.Configuration,
            message = state.value.lastError,
        )

    override fun recordUiFailure(message: String) = Unit
}

private class FirebaseParentAccountAuthCoordinator(
    private val auth: FirebaseAuth,
) : ParentAccountAuthCoordinator {
    private val mutableState = MutableStateFlow(auth.toParentAccountAuthState())

    override val state: StateFlow<ParentAccountAuthState> = mutableState

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        mutableState.value = firebaseAuth.toParentAccountAuthState()
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    override suspend fun signInParentWithGoogleIdToken(idToken: String): ParentAccountAuthResult {
        val cleanToken = idToken.trim()
        if (cleanToken.isBlank()) {
            return fail(
                failure = ParentAccountAuthFailure.InvalidCredential,
                message = "Google ID token is blank",
            )
        }
        return try {
            val credential = GoogleAuthProvider.getCredential(cleanToken, null)
            val currentUser = auth.currentUser
            if (currentUser?.isAnonymous == true) {
                try {
                    currentUser.linkWithCredential(credential).awaitAuthTask()
                } catch (_: FirebaseAuthUserCollisionException) {
                    // Returning parents already own a Google-backed Firebase account. Signing in
                    // restores that UID and allows linked child documents to be queried again.
                    auth.signInWithCredential(credential).awaitAuthTask()
                }
            } else {
                auth.signInWithCredential(credential).awaitAuthTask()
            }
            mutableState.value = auth.toParentAccountAuthState()
            ParentAccountAuthResult.Success
        } catch (error: Throwable) {
            fail(error.toParentAccountAuthFailure(), error.message.orEmpty())
        }
    }

    override suspend fun switchToChildAnonymousIdentity(): ParentAccountAuthResult {
        return try {
            if (auth.currentUser?.isAnonymous != true) {
                auth.signOut()
                auth.signInAnonymously().awaitAuthTask()
            }
            mutableState.value = auth.toParentAccountAuthState()
            ParentAccountAuthResult.Success
        } catch (error: Throwable) {
            fail(error.toParentAccountAuthFailure(), error.message.orEmpty())
        }
    }

    override fun recordUiFailure(message: String) {
        mutableState.value = auth.toParentAccountAuthState(lastError = message.trim())
    }

    private fun fail(
        failure: ParentAccountAuthFailure,
        message: String,
    ): ParentAccountAuthResult {
        mutableState.value = auth.toParentAccountAuthState(lastError = message)
        return ParentAccountAuthResult(
            success = false,
            failure = failure,
            message = message,
        )
    }
}

private fun FirebaseAuth.toParentAccountAuthState(lastError: String = ""): ParentAccountAuthState {
    val user = currentUser
    val googleBacked = user?.providerData
        ?.any { profile -> profile.providerId == GoogleAuthProvider.PROVIDER_ID }
        ?: false
    return ParentAccountAuthState(
        available = true,
        authenticated = user != null,
        recoverable = user != null && !user.isAnonymous && googleBacked,
        anonymous = user?.isAnonymous == true,
        uid = user?.uid.orEmpty(),
        displayName = user?.displayName.orEmpty(),
        email = user?.email.orEmpty(),
        lastError = lastError,
    )
}

private fun Throwable.toParentAccountAuthFailure(): ParentAccountAuthFailure {
    return when (this) {
        is FirebaseNetworkException -> ParentAccountAuthFailure.Network
        is FirebaseAuthException -> ParentAccountAuthFailure.Authentication
        else -> cause
            ?.takeIf { nested -> nested !== this }
            ?.toParentAccountAuthFailure()
            ?: ParentAccountAuthFailure.Unknown
    }
}

private suspend fun <T> Task<T>.awaitAuthTask(): T {
    return suspendCancellableCoroutine { continuation: CancellableContinuation<T> ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}
