package com.comp90018.deadline.data.remote.firebase

import com.comp90018.deadline.domain.repository.Account
import com.google.firebase.auth.EmailAuthProvider
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

interface AccountAuthClient : AnonymousAuthClient {
    val currentAccount: Account

    suspend fun link(
        email: String,
        password: String,
    ): Account

    suspend fun create(
        email: String,
        password: String,
    ): Account

    suspend fun login(
        email: String,
        password: String,
    ): Account

    fun signOut()
}

class FirebaseAuthDataSource(private val auth: FirebaseAuth) : AccountAuthClient {
    override val currentAccount: Account
        get() = auth.currentUser?.let { Account(it.uid, if (it.isAnonymous) null else it.email) } ?: Account()

    override suspend fun link(
        email: String,
        password: String,
    ): Account {
        val existing = checkNotNull(auth.currentUser)
        check(existing.isAnonymous) { "Already registered." }
        val linked = existing.linkWithCredential(EmailAuthProvider.getCredential(email, password)).await()
        check(linked.user?.uid == existing.uid) { "Account identity changed unexpectedly." }
        return currentAccount
    }

    override suspend fun create(
        email: String,
        password: String,
    ): Account {
        check(auth.currentUser == null) { "An account already exists." }
        auth.createUserWithEmailAndPassword(email, password).await()
        return currentAccount
    }

    override suspend fun login(
        email: String,
        password: String,
    ): Account {
        auth.signInWithEmailAndPassword(email, password).await()
        return currentAccount
    }

    override fun signOut() = auth.signOut()

    override val currentUserId: String?
        get() = auth.currentUser?.uid

    override fun userIdChanges(): Flow<String?> =
        callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }.distinctUntilChanged()

    override suspend fun signInAnonymously(): String =
        checkNotNull(auth.signInAnonymously().await().user?.uid) { "Sign-in returned no user." }
}
