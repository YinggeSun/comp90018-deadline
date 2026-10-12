package com.comp90018.deadline.domain.repository

import kotlinx.coroutines.flow.StateFlow

data class Account(
    val uid: String? = null,
    val email: String? = null,
    val nickname: String? = null,
    val storageReady: Boolean = true,
) {
    val registered: Boolean get() = email != null
}

interface AccountRepository : AuthRepository {
    val account: StateFlow<Account>

    suspend fun register(
        nickname: String,
        email: String,
        password: String,
        confirmation: String,
    ): Result<Unit>

    suspend fun login(
        email: String,
        password: String,
    ): Result<Unit>

    suspend fun logout(): Result<Unit>

    suspend fun updateNickname(nickname: String): Result<Unit>

    suspend fun refreshProfile(): Result<Unit>
}

object AccountValidation {
    fun nickname(value: String): String? =
        if (value.trim().isEmpty() || value.trim().length > 20) "Enter a nickname of 1–20 characters." else null

    fun login(
        email: String,
        password: String,
    ): String? =
        when {
            !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim()) -> "Enter a valid email address."
            password.isEmpty() -> "Enter your password."
            else -> null
        }

    fun registration(
        nickname: String,
        email: String,
        password: String,
        confirmation: String,
    ): String? =
        nickname(nickname) ?: login(email, password) ?: when {
            password.length < 6 -> "Use a password with at least 6 characters."
            password != confirmation -> "Passwords do not match."
            else -> null
        }
}
