package com.comp90018.deadline.domain.progress

import com.comp90018.deadline.domain.level.model.SemesterDifficulty

/**
 * Everything the player has earned. Every field only ever improves, which keeps
 * cross-device merging (#39) a per-field union / max / min with no merge UI:
 * [completedLevelIds] grow, [highestUnlockedWeek] rises, and each Personal Best only gets faster.
 * [lastModifiedMillis] is the latest change, for sync ordering.
 */
data class PlayerProgress(
    val completedLevelIds: Set<String> = emptySet(),
    val highestUnlockedWeek: Int = FIRST_WEEK,
    val personalBests: Map<String, PersonalBest> = emptyMap(),
    val lastModifiedMillis: Long = 0L,
) {
    init {
        require(highestUnlockedWeek in FIRST_WEEK..SemesterDifficulty.SEMESTER_WEEKS) {
            "Unlocked week must be within the semester."
        }
        require(personalBests.all { (levelId, best) -> levelId == best.levelId }) {
            "Personal Bests must be keyed by their own level ID."
        }
    }

    fun isWeekUnlocked(week: Int): Boolean = week in FIRST_WEEK..highestUnlockedWeek

    fun bestFor(levelId: String): PersonalBest? = personalBests[levelId]

    /**
     * Records a won level. A slower time never replaces a faster Personal Best, and
     * clearing week N unlocks week N + 1 (capped at the last semester week).
     */
    fun withCompletion(result: CompletionResult): Pair<PlayerProgress, CompletionOutcome> {
        val previousBest = personalBests[result.levelId]
        val isNewBest = previousBest == null || result.timeMillis < previousBest.timeMillis
        val unlocked =
            maxOf(
                highestUnlockedWeek,
                minOf(result.week + 1, SemesterDifficulty.SEMESTER_WEEKS),
            )
        val updated =
            PlayerProgress(
                completedLevelIds = completedLevelIds + result.levelId,
                highestUnlockedWeek = unlocked,
                personalBests =
                    if (isNewBest) {
                        personalBests + (
                            result.levelId to
                                PersonalBest(result.levelId, result.timeMillis, result.completedAtMillis)
                        )
                    } else {
                        personalBests
                    },
                lastModifiedMillis = maxOf(lastModifiedMillis, result.completedAtMillis),
            )
        return updated to
            CompletionOutcome(
                isNewPersonalBest = isNewBest,
                previousBest = previousBest,
                newlyUnlockedWeek = unlocked.takeIf { it > highestUnlockedWeek },
            )
    }

    companion object {
        const val FIRST_WEEK = 1
    }
}
