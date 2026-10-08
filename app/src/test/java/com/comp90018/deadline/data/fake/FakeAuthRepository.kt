package com.comp90018.deadline.data.fake

import com.comp90018.deadline.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [AuthRepository] that signs in as [userIdOnSignIn], or fails when it is null (offline). */
class FakeAuthRepository(var userIdOnSignIn: String? = "test-user") : AuthRepository {
    private val state = MutableStateFlow<String?>(null)
    override val userId: StateFlow<String?> = state

    override suspend fun ensureSignedIn(): Result<String> {
        state.value?.let { return Result.success(it) }
        val id = userIdOnSignIn ?: return Result.failure(IllegalStateException("Offline"))
        state.value = id
        return Result.success(id)
    }
}
