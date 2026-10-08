package com.comp90018.deadline.domain.repository

import kotlinx.coroutines.flow.Flow

/** Anonymous player identity. Being signed out never blocks local gameplay. */
interface AuthRepository {
    /** Stable ID for this installation, or null while signed out or offline. */
    val userId: Flow<String?>

    /** Signs in anonymously if needed and returns the user ID; fails when offline. */
    suspend fun ensureSignedIn(): Result<String>
}
