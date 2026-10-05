package com.comp90018.deadline.data.remote.firebase

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** The anonymous sign-in operations AuthRepositoryImpl needs; a seam for JVM tests. */
interface AnonymousAuthClient {
    /** Signed-in user ID, or null. Firebase keeps it across app restarts. */
    val currentUserId: String?

    /** Emits the current user ID now and on every sign-in or sign-out. */
    fun userIdChanges(): Flow<String?>

    /** Creates or restores the anonymous user and returns its ID; throws on failure. */
    suspend fun signInAnonymously(): String
}

class FirebaseAuthDataSource(private val auth: FirebaseAuth) : AnonymousAuthClient {

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override fun userIdChanges(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signInAnonymously(): String =
        checkNotNull(auth.signInAnonymously().await().user?.uid) { "Sign-in returned no user." }
}
