package com.comp90018.deadline.data.remote.firebase

import com.comp90018.deadline.domain.repository.Account
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

interface ProfileStore {
    /** Null nickname keeps the existing name; creation alone uses the fallback. */
    suspend fun loadOrCreate(
        account: Account,
        nickname: String? = null,
        recordLogin: Boolean = true,
    ): String
}

class FirestoreProfileDataSource(private val db: FirebaseFirestore) : ProfileStore {
    override suspend fun loadOrCreate(
        account: Account,
        nickname: String?,
        recordLogin: Boolean,
    ): String {
        val document = db.collection("users").document(checkNotNull(account.uid))
        return db.runTransaction { transaction ->
            val existing = transaction.get(document)
            val name = nickname ?: existing.getString("nickname") ?: account.nickname ?: "Player"
            val fields =
                mutableMapOf<String, Any>(
                    "nickname" to name,
                    "email" to checkNotNull(account.email),
                    "accountType" to "registered",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            if (!existing.exists()) fields["createdAt"] = FieldValue.serverTimestamp()
            if (recordLogin || !existing.exists()) fields["lastLoginAt"] = FieldValue.serverTimestamp()
            transaction.set(document, fields, com.google.firebase.firestore.SetOptions.merge())
            name
        }.await()
    }
}
