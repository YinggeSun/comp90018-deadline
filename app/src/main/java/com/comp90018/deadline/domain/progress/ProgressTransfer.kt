package com.comp90018.deadline.domain.progress

import java.security.SecureRandom

/** Outcome of creating a transfer code on the device the player is moving from. */
sealed interface CreateCodeResult {
    /** [code] is in [TransferCode.format] form and works until [expiresAtMillis]. */
    data class Created(
        val code: String,
        val expiresAtMillis: Long,
    ) : CreateCodeResult

    data object Offline : CreateCodeResult

    data object Failed : CreateCodeResult
}

/** Outcome of entering a transfer code on the device the player is moving to. */
sealed interface RedeemResult {
    /** The code's progress was merged in; [progress] is the device's progress afterwards. */
    data class Redeemed(
        val progress: PlayerProgress,
    ) : RedeemResult

    /** Not a code at all, for example a wrong length or a letter codes never use. */
    data object InvalidFormat : RedeemResult

    /** No such code, or it has expired. The two are not told apart, so codes cannot be probed. */
    data object NotFound : RedeemResult

    data object Offline : RedeemResult

    data object Failed : RedeemResult
}

/** Moves progress between devices with a short, temporary code. */
interface ProgressTransfer {
    suspend fun createCode(): CreateCodeResult

    suspend fun redeem(input: String): RedeemResult
}

/**
 * Transfer codes: [LENGTH] characters from [ALPHABET], which leaves out look-alikes (0, O, 1, I),
 * so there are 32^8, about a trillion, possible codes. Shown as `XXXX-XXXX`.
 */
object TransferCode {
    const val LENGTH = 8
    const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    /** How long a code works once created. */
    const val VALID_FOR_MILLIS = 24 * 60 * 60 * 1000L

    private val random = SecureRandom()

    fun generate(): String = String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    /** The stored form of what a player typed (case, spaces and dashes ignored), or null if it cannot be a code. */
    fun normalize(input: String): String? =
        input.uppercase().filterNot { it == '-' || it.isWhitespace() }
            .takeIf { it.length == LENGTH && it.all { char -> char in ALPHABET } }

    fun format(code: String): String = "${code.take(LENGTH / 2)}-${code.drop(LENGTH / 2)}"
}
