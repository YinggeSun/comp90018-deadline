package com.comp90018.deadline.data.remote.firebase

import com.comp90018.deadline.domain.progress.PlayerProgress
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import java.util.Date

/** A transfer-code call that failed. [isOffline] means the server could not be reached. */
class TransferStoreException(
    val isOffline: Boolean,
    cause: Throwable? = null,
) : Exception(if (isOffline) "Offline" else "Failed", cause)

/** Transfer codes in the cloud; a seam for JVM tests. */
interface TransferStore {
    /** Stores [progress] under [code] for [ownerId] until [expiresAtMillis]. Throws [TransferStoreException]. */
    suspend fun create(
        code: String,
        ownerId: String,
        progress: PlayerProgress,
        expiresAtMillis: Long,
    )

    /** The progress stored under [code], or null if there is none or it has expired. Throws [TransferStoreException]. */
    suspend fun read(code: String): PlayerProgress?
}

/**
 * Transfer codes in Cloud Firestore at `transfers/{code}`: a snapshot of the creator's progress
 * that any signed-in player who knows the code can read until it expires. The rules refuse
 * listing, changing or deleting codes, and refuse reading an expired or missing one.
 */
class FirestoreTransferDataSource(
    private val db: FirebaseFirestore,
) : TransferStore {
    override suspend fun create(
        code: String,
        ownerId: String,
        progress: PlayerProgress,
        expiresAtMillis: Long,
    ) {
        val fields =
            ProgressFields.write(progress) +
                mapOf(
                    FIELD_OWNER to ownerId,
                    FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    FIELD_EXPIRES_AT to Timestamp(Date(expiresAtMillis)),
                )
        try {
            db.collection(COLLECTION).document(code).set(fields).await()
        } catch (error: FirebaseFirestoreException) {
            throw TransferStoreException(isOffline = error.code == FirebaseFirestoreException.Code.UNAVAILABLE, cause = error)
        }
    }

    override suspend fun read(code: String): PlayerProgress? =
        try {
            val snapshot = db.collection(COLLECTION).document(code).get().await()
            if (snapshot.exists()) ProgressFields.read(snapshot::get) else null
        } catch (error: FirebaseFirestoreException) {
            when (error.code) {
                // The rules refuse missing and expired codes alike.
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> null
                FirebaseFirestoreException.Code.UNAVAILABLE -> throw TransferStoreException(isOffline = true, cause = error)
                else -> throw TransferStoreException(isOffline = false, cause = error)
            }
        }

    companion object {
        const val COLLECTION = "transfers"
        const val FIELD_OWNER = "ownerId"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_EXPIRES_AT = "expiresAt"
    }
}
