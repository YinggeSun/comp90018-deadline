package com.comp90018.deadline.domain.level.generator

import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.level.model.Level
import com.comp90018.deadline.domain.level.model.LevelConfig
import com.comp90018.deadline.domain.level.solver.SolvabilityResult
import com.comp90018.deadline.domain.level.solver.SolvabilityValidator
import kotlin.random.Random

/** Fallback retains its own ID/config so it cannot masquerade as the requested week. */
data class LevelGenerationResult(
    val level: Level,
    val usedFallback: Boolean,
    val attempts: Int,
    val candidateSeed: Long?,
    val winningMoves: List<String>
)

/**
 * Issue #18 entry point: generate, validate, retry, then use a verified fixed level.
 * Only Solvable candidates are returned. SearchLimitReached is not proof of no
 * solution, but such a candidate is not safe to serve without further checking.
 * Runs synchronously; call from a background dispatcher when wiring into the UI.
 */
class ValidatedLevelGenerator internal constructor(
    private val seed: Long?,
    private val maxAttempts: Int,
    private val createCandidate: (String, String, LevelConfig, Long) -> Level,
    private val validateCandidate: (Level) -> SolvabilityResult
) {
    constructor(
        seed: Long? = null,
        maxAttempts: Int = 10,
        validator: SolvabilityValidator = SolvabilityValidator()
    ) : this(
        seed, maxAttempts,
        { id, name, config, candidateSeed ->
            LevelGenerator(candidateSeed).generate(id, name, config)
        },
        { level -> validator.validate(level) }
    )

    init {
        require(maxAttempts in 1..100) { "Maximum attempts must be between 1 and 100." }
    }

    fun generate(id: String, name: String, config: LevelConfig): LevelGenerationResult {
        val firstSeed = seed ?: Random.Default.nextLong()
        repeat(maxAttempts) { attempt ->
            val candidateSeed = firstSeed + attempt.toLong()
            val candidate = try {
                createCandidate(id, name, config, candidateSeed)
            } catch (_: IllegalArgumentException) {
                return fallback(attempt + 1)
            }
            if (candidate.config != config || candidate.id != id || candidate.name != name) {
                return@repeat
            }
            val validation = validateCandidate(candidate)
            if (validation is SolvabilityResult.Solvable) {
                return LevelGenerationResult(
                    candidate, false, attempt + 1, candidateSeed, validation.moves
                )
            }
        }
        return fallback(maxAttempts)
    }

    private fun fallback(attempts: Int): LevelGenerationResult {
        val level = FixedLevels.LEVEL_1
        val validation = SolvabilityValidator().validate(level)
        check(validation is SolvabilityResult.Solvable) {
            "Fixed fallback failed validation; refusing to return an unverified level."
        }
        return LevelGenerationResult(level, true, attempts, null, validation.moves)
    }
}
