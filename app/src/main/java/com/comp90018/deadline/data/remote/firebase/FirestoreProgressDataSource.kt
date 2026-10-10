package com.comp90018.deadline.data.remote.firebase

import com.comp90018.deadline.domain.progress.PlayerProgress
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await

/**
 * A cloud progress call that failed, translated from Firebase. [isRefused] means the rules
 * denied it; [isOffline] means the server could not be reached.
 */
class RemoteProgressException(
    val isRefused: Boolean,
    val isOffline: Boolean = false,
    cause: Throwable? = null,
) : Exception(
        when {
            isRefused -> "Refused"
            isOffline -> "Offline"
            else -> "Failed"
        },
        cause,
    )

/** The cloud copy of a player's progress; a seam for JVM tests. */
interface RemoteProgressStore {
    /**
     * Atomically on the server: reads this player's stored progress (null if none), stores
     * [transform] of it, and returns what was stored. [transform] may run more than once if
     * another device writes at the same time, so it must be a pure function.
     * Throws [RemoteProgressException].
     */
    suspend fun update(
        userId: String,
        transform: (PlayerProgress?) -> PlayerProgress,
    ): PlayerProgress
}

/**
 * Progress in Cloud Firestore at `progress/{userId}`. Each update is a transaction, so a stale
 * copy can never overwrite progress saved from another device in the meantime.
 */
class FirestoreProgressDataSource(
    private val db: FirebaseFirestore,
) : RemoteProgressStore {
    override suspend fun update(
        userId: String,
        transform: (PlayerProgress?) -> PlayerProgress,
    ): PlayerProgress {
        val document = db.collection(COLLECTION).document(userId)
        return try {
            db.runTransaction { transaction ->
                val stored = transaction.get(document).toProgress()
                transform(stored).also { transaction.set(document, it.toFields()) }
            }.await()
        } catch (error: FirebaseFirestoreException) {
            throw RemoteProgressException(
                isRefused = error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED,
                // Offline, a transaction fails at once with UNAVAILABLE rather than waiting.
                isOffline = error.code == FirebaseFirestoreException.Code.UNAVAILABLE,
                cause = error,
            )
        }
    }

    private fun PlayerProgress.toFields(): Map<String, Any> =
        ProgressFields.write(this) + (FIELD_UPDATED_AT to FieldValue.serverTimestamp())

    private fun DocumentSnapshot.toProgress(): PlayerProgress? = if (exists()) ProgressFields.read(::get) else null

    companion object {
        const val COLLECTION = "progress"
        const val FIELD_UPDATED_AT = "updatedAt"
    }
}
