package com.comp90018.deadline.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.domain.progress.CreateCodeResult
import com.comp90018.deadline.domain.progress.ProgressTransfer
import com.comp90018.deadline.domain.progress.RedeemResult
import com.comp90018.deadline.domain.progress.TransferCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the transfer section reports after an action. */
enum class TransferMessage {
    CODE_OFFLINE,
    CODE_FAILED,
    REDEEMED,
    INVALID_CODE,
    NOT_FOUND,
    REDEEM_OFFLINE,
    REDEEM_FAILED,
}

/**
 * The "Move to another phone" section of Settings. [code] is this phone's code once created,
 * in `XXXX-XXXX` form; [codeInput] is what the player typed for another phone's code.
 * [clearedLevels] and [unlockedWeek] describe progress after a successful transfer.
 */
data class TransferProgressUiState(
    val code: String? = null,
    val expiresAtMillis: Long? = null,
    val isCreating: Boolean = false,
    val codeInput: String = "",
    val isRedeeming: Boolean = false,
    val message: TransferMessage? = null,
    val clearedLevels: Int = 0,
    val unlockedWeek: Int = 1,
) {
    val canRedeem: Boolean get() = !isRedeeming && TransferCode.normalize(codeInput) != null
}

class TransferProgressViewModel(
    private val transfer: ProgressTransfer,
) : ViewModel() {
    private val state = MutableStateFlow(TransferProgressUiState())
    val uiState: StateFlow<TransferProgressUiState> = state.asStateFlow()

    fun createCode() {
        if (state.value.isCreating) return
        state.update { it.copy(isCreating = true, message = null) }
        viewModelScope.launch {
            val result = transfer.createCode()
            state.update {
                when (result) {
                    is CreateCodeResult.Created -> it.copy(isCreating = false, code = result.code, expiresAtMillis = result.expiresAtMillis)
                    CreateCodeResult.Offline -> it.copy(isCreating = false, message = TransferMessage.CODE_OFFLINE)
                    CreateCodeResult.Failed -> it.copy(isCreating = false, message = TransferMessage.CODE_FAILED)
                }
            }
        }
    }

    /** Keeps only characters a code can contain, upper-cased, so typing mistakes are visible early. */
    fun onCodeInputChange(input: String) {
        val cleaned = input.uppercase().filter { it in TransferCode.ALPHABET || it == '-' }.take(MAX_INPUT_LENGTH)
        state.update { it.copy(codeInput = cleaned, message = null) }
    }

    fun redeem() {
        val input = state.value.codeInput
        if (state.value.isRedeeming) return
        state.update { it.copy(isRedeeming = true, message = null) }
        viewModelScope.launch {
            val result = transfer.redeem(input)
            state.update {
                when (result) {
                    is RedeemResult.Redeemed ->
                        it.copy(
                            isRedeeming = false,
                            codeInput = "",
                            message = TransferMessage.REDEEMED,
                            clearedLevels = result.progress.completedLevelIds.size,
                            unlockedWeek = result.progress.highestUnlockedWeek,
                        )
                    RedeemResult.InvalidFormat -> it.copy(isRedeeming = false, message = TransferMessage.INVALID_CODE)
                    RedeemResult.NotFound -> it.copy(isRedeeming = false, message = TransferMessage.NOT_FOUND)
                    RedeemResult.Offline -> it.copy(isRedeeming = false, message = TransferMessage.REDEEM_OFFLINE)
                    RedeemResult.Failed -> it.copy(isRedeeming = false, message = TransferMessage.REDEEM_FAILED)
                }
            }
        }
    }

    companion object {
        /** Eight code characters plus the dash. */
        private const val MAX_INPUT_LENGTH = TransferCode.LENGTH + 1

        val Factory: ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val container = (this[APPLICATION_KEY] as DeadlineApp).container
                    TransferProgressViewModel(container.progressTransfer)
                }
            }
    }
}
