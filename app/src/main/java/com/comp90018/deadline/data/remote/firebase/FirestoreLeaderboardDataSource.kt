package com.comp90018.deadline.data.remote.firebase

import com.comp90018.deadline.domain.leaderboard.LeaderboardEntry
import com.comp90018.deadline.domain.leaderboard.LeaderboardFailure
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** One ranking as the store returned it. [isFromCache] is true while the device is offline. */
data class LeaderboardPage(
    val entries: List<LeaderboardEntry>,
    val isFromCache: Boolean,
)

/** A store call that failed, already translated from Firebase into a [LeaderboardFailure]. */
class LeaderboardStoreException(
    val reason: LeaderboardFailure,
    cause: Throwable? = null,
) : Exception(reason.name, cause)

/** The Firestore operations the leaderboard needs; a seam for JVM tests. */
interface LeaderboardStore {
    /** Fastest [limit] entries for [levelId], re-emitted on every change. Fails with [LeaderboardStoreException]. */
    fun observeTop(
        levelId: String,
        limit: Int,
    ): Flow<LeaderboardPage>

    /** This player's stored entry for [levelId], or null if there is none. */
    suspend fun entryFor(
        levelId: String,
        userId: String,
    ): LeaderboardEntry?

    /** Stores [entry]; returns once the server has accepted it. Throws [LeaderboardStoreException] if refused. */
    suspend fun write(
        entry: LeaderboardEntry,
        week: Int,
    )
}

/**
 * Leaderboard in Cloud Firestore: one document per player per level, at
 * `leaderboard/{levelId}/entries/{userId}`, holding that player's best time.
 * Firestore keeps writes made offline and uploads them when the device reconnects.
 */
class FirestoreLeaderboardDataSource(
    private val db: FirebaseFirestore,
) : LeaderboardStore {
    override fun observeTop(
        levelId: String,
        limit: Int,
    ): Flow<LeaderboardPage> =
        callbackFlow {
            val registration =
                entries(levelId)
                    .orderBy(FIELD_TIME)
                    .limit(limit.toLong())
                    .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                        when {
                            error != null -> close(error.toStoreException())
                            snapshot != null ->
                                trySend(
                                    LeaderboardPage(
                                        entries = snapshot.documents.mapNotNull { it.toEntryOrNull(levelId) },
                                        isFromCache = snapshot.metadata.isFromCache,
                                    ),
                                )
                        }
                    }
            awaitClose { registration.remove() }
        }.distinctUntilChanged()

    override suspend fun entryFor(
        levelId: String,
        userId: String,
    ): LeaderboardEntry? =
        try {
            entries(levelId).document(userId).get().await().toEntryOrNull(levelId)
        } catch (error: FirebaseFirestoreException) {
            throw error.toStoreException()
        }

    override suspend fun write(
        entry: LeaderboardEntry,
        week: Int,
    ) {
        try {
            entries(entry.levelId)
                .document(entry.userId)
                .set(
                    mapOf(
                        FIELD_UID to entry.userId,
                        FIELD_NICKNAME to entry.nickname,
                        FIELD_TIME to entry.timeMillis,
                        FIELD_WEEK to week,
                        FIELD_SUBMITTED_AT to FieldValue.serverTimestamp(),
                    ),
                ).await()
        } catch (error: FirebaseFirestoreException) {
            throw error.toStoreException()
        }
    }

    private fun entries(levelId: String) = db.collection(COLLECTION).document(levelId).collection(ENTRIES)

    /**
     * Reads defensively: a document with missing or wrong-typed fields is skipped rather than
     * thrown. This matters most in the snapshot listener, whose callback runs on the main
     * thread, where an exception would crash the app. Records written before the rules were
     * tightened can still be malformed.
     */
    private fun DocumentSnapshot.toEntryOrNull(levelId: String): LeaderboardEntry? =
        try {
            toEntry(levelId)
        } catch (error: RuntimeException) {
            null
        }

    private fun DocumentSnapshot.toEntry(levelId: String): LeaderboardEntry? {
        if (!exists()) return null
        val userId = get(FIELD_UID) as? String ?: return null
        val nickname = get(FIELD_NICKNAME) as? String ?: return null
        val timeMillis = (get(FIELD_TIME) as? Long)?.takeIf { it > 0 } ?: return null
        // get() with a type check, because getTimestamp() throws on a value of another type.
        // A pending server timestamp reads as an estimate, so a real entry always has one.
        val submittedAt =
            (get(FIELD_SUBMITTED_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) as? Timestamp)
                ?.toDate()
                ?.time ?: return null
        return LeaderboardEntry(userId, nickname, levelId, timeMillis, submittedAt)
    }

    private fun FirebaseFirestoreException.toStoreException() =
        LeaderboardStoreException(
            reason =
                when (code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> LeaderboardFailure.REJECTED
                    FirebaseFirestoreException.Code.UNAUTHENTICATED -> LeaderboardFailure.NOT_SIGNED_IN
                    else -> LeaderboardFailure.UNKNOWN
                },
            cause = this,
        )

    companion object {
        const val COLLECTION = "leaderboard"
        const val ENTRIES = "entries"
        const val FIELD_UID = "uid"
        const val FIELD_NICKNAME = "nickname"
        const val FIELD_TIME = "timeMillis"
        const val FIELD_WEEK = "week"
        const val FIELD_SUBMITTED_AT = "submittedAt"
    }
}
