package com.comp90018.deadline.data.repository

import com.comp90018.deadline.data.config.RemoteBalance
import com.comp90018.deadline.data.remote.firebase.RemoteConfigSource
import com.comp90018.deadline.domain.config.GameBalance
import com.comp90018.deadline.domain.repository.BalanceSource
import com.comp90018.deadline.domain.repository.ConfigRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout

/**
 * Gameplay balance from Remote Config, validated by [RemoteBalance]. [balance] starts at the
 * compiled-in defaults; [refresh] first applies values fetched in an earlier session (available
 * offline), then fetches new ones within [timeoutMillis]. Any failure keeps the current balance,
 * so the game is fully playable without Remote Config. [onRejected] reports values that were
 * refused, for logging.
 */
class ConfigRepositoryImpl(
    private val source: RemoteConfigSource,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val onRejected: (Set<String>) -> Unit = {},
) : ConfigRepository {
    private val state = MutableStateFlow(GameBalance())
    override val balance: StateFlow<GameBalance> = state.asStateFlow()

    private var currentSource = BalanceSource.DEFAULTS

    override suspend fun refresh(): BalanceSource {
        applyActivated()
        try {
            withTimeout(timeoutMillis) { source.fetchAndActivate() }
            applyActivated()
        } catch (cancelled: CancellationException) {
            // A timeout is just a failed fetch; anything else is a real cancellation.
            if (cancelled !is TimeoutCancellationException) throw cancelled
        } catch (error: Exception) {
            // Offline or unavailable: keep the current balance.
        }
        return currentSource
    }

    private suspend fun applyActivated() {
        val values =
            try {
                source.activated()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                return
            }
        if (values.isEmpty()) return
        val result = RemoteBalance.read(values)
        if (result.rejectedKeys.isNotEmpty()) onRejected(result.rejectedKeys)
        state.value = result.balance
        currentSource = BalanceSource.REMOTE
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    }
}
