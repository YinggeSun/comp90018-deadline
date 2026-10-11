package com.comp90018.deadline.domain.repository

import com.comp90018.deadline.domain.config.GameBalance
import kotlinx.coroutines.flow.StateFlow

/** Where the latest valid gameplay balance came from. */
enum class BalanceSource {
    /** The values compiled into the app. */
    DEFAULTS,

    /** Values fetched from Remote Config, now or in an earlier session. */
    REMOTE,
}

/** Gameplay balance, tunable through Remote Config (#40, #52). Nothing here throws. */
interface ConfigRepository {
    /** The balance to use for the next game. Starts at [GameBalance]'s defaults. */
    val balance: StateFlow<GameBalance>

    /** Loads previously fetched values, then fetches new ones. Failures keep the current balance. */
    suspend fun refresh(): BalanceSource
}
