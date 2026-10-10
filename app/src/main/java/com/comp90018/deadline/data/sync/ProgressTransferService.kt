package com.comp90018.deadline.data.sync

import com.comp90018.deadline.data.remote.firebase.TransferStore
import com.comp90018.deadline.data.remote.firebase.TransferStoreException
import com.comp90018.deadline.domain.progress.CreateCodeResult
import com.comp90018.deadline.domain.progress.ProgressTransfer
import com.comp90018.deadline.domain.progress.RedeemResult
import com.comp90018.deadline.domain.progress.TransferCode
import com.comp90018.deadline.domain.repository.AuthRepository
import com.comp90018.deadline.domain.repository.ProgressRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * Moves progress to another device with a transfer code. Creating a code first syncs, so the
 * code carries everything this account has, then stores a snapshot of local progress under a
 * new code. Redeeming merges the code's progress into this device with [ConflictResolver] (so
 * nothing on this device is lost) and then syncs it to this device's cloud copy.
 */
class ProgressTransferService(
    private val local: ProgressRepository,
    private val store: TransferStore,
    private val auth: AuthRepository,
    private val sync: suspend () -> SyncResult,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val newCode: () -> String = TransferCode::generate,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : ProgressTransfer {
    override suspend fun createCode(): CreateCodeResult {
        val ownerId = auth.ensureSignedIn().getOrElse { return CreateCodeResult.Offline }
        if (sync() == SyncResult.Offline) return CreateCodeResult.Offline
        val progress = local.progress.first()
        // Slightly under the full period, so a device clock running a little fast is still accepted.
        val expiresAt = nowMillis() + TransferCode.VALID_FOR_MILLIS - CLOCK_MARGIN_MILLIS
        repeat(ATTEMPTS) {
            val code = newCode()
            try {
                withTimeout(timeoutMillis) { store.create(code, ownerId, progress, expiresAt) }
                return CreateCodeResult.Created(TransferCode.format(code), expiresAt)
            } catch (timeout: TimeoutCancellationException) {
                return CreateCodeResult.Offline
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: TransferStoreException) {
                // Refused: almost certainly a code that already exists, so try a new one.
                if (error.isOffline) return CreateCodeResult.Offline
            } catch (error: Exception) {
                return CreateCodeResult.Failed
            }
        }
        return CreateCodeResult.Failed
    }

    override suspend fun redeem(input: String): RedeemResult {
        val code = TransferCode.normalize(input) ?: return RedeemResult.InvalidFormat
        auth.ensureSignedIn().getOrElse { return RedeemResult.Offline }
        val transferred =
            try {
                withTimeout(timeoutMillis) { store.read(code) } ?: return RedeemResult.NotFound
            } catch (timeout: TimeoutCancellationException) {
                return RedeemResult.Offline
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: TransferStoreException) {
                return if (error.isOffline) RedeemResult.Offline else RedeemResult.Failed
            } catch (error: Exception) {
                return RedeemResult.Failed
            }
        val merged = local.mergeIn(transferred)
        sync()
        return RedeemResult.Redeemed(merged)
    }

    private companion object {
        const val ATTEMPTS = 3
        const val CLOCK_MARGIN_MILLIS = 10 * 60 * 1000L
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L
    }
}
