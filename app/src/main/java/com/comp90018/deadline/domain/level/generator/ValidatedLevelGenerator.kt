package com.comp90018.deadline.domain.level.generator

import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.domain.level.model.Level
import com.comp90018.deadline.domain.level.model.LevelConfig
import com.comp90018.deadline.domain.level.model.SemesterDifficulty
import com.comp90018.deadline.domain.level.solver.SolvabilityResult
import com.comp90018.deadline.domain.level.solver.SolvabilityValidator

import kotlin.random.Random

data class LevelGenerationResult(
    val level: Level,
    val usedFallback: Boolean,
    val attempts: Int,
    val candidateSeed: Long?,
    val winningMoves: List<String>
)

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


    fun generateForLevel(levelNumber: Int): LevelGenerationResult {
        val weekConfig = SemesterDifficulty.forLevel(levelNumber)

        return generate(
            id = "level_$levelNumber",
            name = "Level $levelNumber",
            config = weekConfig.levels.single(),
            week = weekConfig.week,
        )
    }


    fun generate(
        id: String,
        name: String,
        config: LevelConfig,
        week: Int = SemesterDifficulty.FIRST_WEEK,
    ): LevelGenerationResult {
        require(
            week in SemesterDifficulty.FIRST_WEEK..
                SemesterDifficulty.SEMESTER_WEEKS
        ) {
            "Week must be within the semester."
        }

        val firstSeed = seed ?: Random.Default.nextLong()

        repeat(maxAttempts) { attempt ->
            val candidateSeed = firstSeed + attempt.toLong()

            val candidate =
                try {
                    createCandidate(
                        id,
                        name,
                        config,
                        candidateSeed,
                    ).copy(week = week)
                } catch (_: IllegalArgumentException) {
                    return fallback(attempt + 1)
                }

            if (
                candidate.config != config ||
                candidate.id != id ||
                candidate.name != name
            ) {
                return@repeat
            }

            val validation = validateCandidate(candidate)

            if (validation is SolvabilityResult.Solvable) {
                return LevelGenerationResult(
                    level = candidate,
                    usedFallback = false,
                    attempts = attempt + 1,
                    candidateSeed = candidateSeed,
                    winningMoves = validation.moves,
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
