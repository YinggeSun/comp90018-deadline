package com.comp90018.deadline.feature.settings

import com.comp90018.deadline.MainDispatcherRule
import com.comp90018.deadline.domain.progress.CreateCodeResult
import com.comp90018.deadline.domain.progress.PlayerProgress
import com.comp90018.deadline.domain.progress.ProgressTransfer
import com.comp90018.deadline.domain.progress.RedeemResult
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TransferProgressViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private class FakeTransfer : ProgressTransfer {
        var createResult: CreateCodeResult = CreateCodeResult.Created("K7QM-3XPD", 5_000)
        var redeemResult: RedeemResult = RedeemResult.NotFound
        var gate: CompletableDeferred<Unit>? = null
        val redeemed = mutableListOf<String>()

        override suspend fun createCode(): CreateCodeResult {
            gate?.await()
            return createResult
        }

        override suspend fun redeem(input: String): RedeemResult {
            redeemed += input
            gate?.await()
            return redeemResult
        }
    }

    private val transfer = FakeTransfer()
    private val viewModel = TransferProgressViewModel(transfer)

    @Test
    fun createdCodeIsShownWithItsExpiry() {
        viewModel.createCode()

        assertEquals("K7QM-3XPD", viewModel.uiState.value.code)
        assertEquals(5_000L, viewModel.uiState.value.expiresAtMillis)
        assertFalse(viewModel.uiState.value.isCreating)
    }

    @Test
    fun creatingShowsProgressAndIgnoresRepeatedTaps() {
        transfer.gate = CompletableDeferred()

        viewModel.createCode()
        viewModel.createCode()
        assertTrue(viewModel.uiState.value.isCreating)

        transfer.gate?.complete(Unit)
        assertEquals("K7QM-3XPD", viewModel.uiState.value.code)
    }

    @Test
    fun creatingOfflineSaysSo() {
        transfer.createResult = CreateCodeResult.Offline

        viewModel.createCode()

        assertNull(viewModel.uiState.value.code)
        assertEquals(TransferMessage.CODE_OFFLINE, viewModel.uiState.value.message)
    }

    @Test
    fun typedCodesKeepOnlyCodeCharactersInUpperCase() {
        viewModel.onCodeInputChange("k7qm-3x pd!!0")

        assertEquals("K7QM-3XPD", viewModel.uiState.value.codeInput)
    }

    @Test
    fun transferIsOnlyOfferedForACompleteCode() {
        viewModel.onCodeInputChange("K7QM-3XP")
        assertFalse(viewModel.uiState.value.canRedeem)

        viewModel.onCodeInputChange("K7QM-3XPD")
        assertTrue(viewModel.uiState.value.canRedeem)
    }

    @Test
    fun successfulTransferReportsTheProgressAndClearsTheField() {
        transfer.redeemResult =
            RedeemResult.Redeemed(PlayerProgress(completedLevelIds = setOf("level_1", "level_2"), highestUnlockedWeek = 5))
        viewModel.onCodeInputChange("K7QM-3XPD")

        viewModel.redeem()

        val state = viewModel.uiState.value
        assertEquals(listOf("K7QM-3XPD"), transfer.redeemed)
        assertEquals(TransferMessage.REDEEMED, state.message)
        assertEquals(2, state.clearedLevels)
        assertEquals(5, state.unlockedWeek)
        assertEquals("", state.codeInput)
    }

    @Test
    fun failedTransfersKeepTheCodeAndExplain() {
        viewModel.onCodeInputChange("K7QM-3XPD")

        for ((result, message) in listOf(
            RedeemResult.NotFound to TransferMessage.NOT_FOUND,
            RedeemResult.Offline to TransferMessage.REDEEM_OFFLINE,
            RedeemResult.InvalidFormat to TransferMessage.INVALID_CODE,
            RedeemResult.Failed to TransferMessage.REDEEM_FAILED,
        )) {
            transfer.redeemResult = result
            viewModel.redeem()
            assertEquals(message, viewModel.uiState.value.message)
            assertEquals("K7QM-3XPD", viewModel.uiState.value.codeInput)
        }
    }

    @Test
    fun typingClearsTheLastMessage() {
        viewModel.onCodeInputChange("K7QM-3XPD")
        viewModel.redeem()

        viewModel.onCodeInputChange("K7QM-3XP")

        assertNull(viewModel.uiState.value.message)
    }
}
