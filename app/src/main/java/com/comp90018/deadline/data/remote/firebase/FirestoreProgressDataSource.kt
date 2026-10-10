package com.comp90018.deadline.data.remote.firebase

import com.comp90018.deadline.domain.level.model.SemesterDifficulty
import com.comp90018.deadline.domain.progress.PersonalBest
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
        mapOf(
            FIELD_COMPLETED to completedLevelIds.sorted(),
            FIELD_WEEK to highestUnlockedWeek,
            FIELD_BESTS to
                personalBests.mapValues { (_, best) ->
                    mapOf(FIELD_TIME to best.timeMillis, FIELD_ACHIEVED_AT to best.achievedAtMillis)
                },
            FIELD_LAST_MODIFIED to lastModifiedMillis,
            FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
        )

    /** Reads defensively: missing or wrong-typed values fall back to defaults instead of throwing. */
    private fun DocumentSnapshot.toProgress(): PlayerProgress? {
        if (!exists()) return null
        val completed = (get(FIELD_COMPLETED) as? List<*>).orEmpty().filterIsInstance<String>().filter { it.isNotBlank() }
        val week =
            (get(FIELD_WEEK) as? Long)?.toInt()?.coerceIn(SemesterDifficulty.FIRST_WEEK, SemesterDifficulty.SEMESTER_WEEKS)
                ?: SemesterDifficulty.FIRST_WEEK
        val bests =
            (get(FIELD_BESTS) as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
                val levelId = (key as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val fields = value as? Map<*, *> ?: return@mapNotNull null
                val time = (fields[FIELD_TIME] as? Long)?.takeIf { it > 0 } ?: return@mapNotNull null
                val achievedAt = fields[FIELD_ACHIEVED_AT] as? Long ?: return@mapNotNull null
                levelId to PersonalBest(levelId, time, achievedAt)
            }.toMap()
        return PlayerProgress(
            completedLevelIds = completed.toSet(),
            highestUnlockedWeek = week,
            personalBests = bests,
            lastModifiedMillis = ((get(FIELD_LAST_MODIFIED) as? Long) ?: 0L).coerceAtLeast(0L),
        )
    }

    companion object {
        const val COLLECTION = "progress"
        const val FIELD_COMPLETED = "completedLevelIds"
        const val FIELD_WEEK = "highestUnlockedWeek"
        const val FIELD_BESTS = "personalBests"
        const val FIELD_TIME = "timeMillis"
        const val FIELD_ACHIEVED_AT = "achievedAtMillis"
        const val FIELD_LAST_MODIFIED = "lastModifiedMillis"
        const val FIELD_UPDATED_AT = "updatedAt"
    }
}
