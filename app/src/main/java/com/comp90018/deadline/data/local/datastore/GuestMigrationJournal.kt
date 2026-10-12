package com.comp90018.deadline.data.local.datastore

/** Durable handoff from this installation's offline Guest to a newly created Firebase UID. */
interface GuestMigrationJournal {
    suspend fun prepare()

    /** Called only with the successful result of creating an identity, never with a login result. */
    suspend fun confirm(uid: String)

    suspend fun cancelForLogin()

    suspend fun checkRecovery()

    /** Explicit recovery after sign-out; keeps the saved Guest and never imports another UID. */
    suspend fun restore()
}

class GuestRecoveryRequiredException : Exception("Saved Guest ownership needs explicit recovery.")
