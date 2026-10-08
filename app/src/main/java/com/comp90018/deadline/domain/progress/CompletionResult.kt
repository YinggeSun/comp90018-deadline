package com.comp90018.deadline.domain.progress

import com.comp90018.deadline.domain.level.model.SemesterDifficulty

/**
 * One finished level, as reported by the game once it is won.
 * [completedAtMillis] is supplied by the caller so domain logic never reads the clock.
 */
data class CompletionResult(
    val levelId: String,
    val week: Int,
    val timeMillis: Long,
    val completedAtMillis: Long,
) {
    init {
        require(levelId.isNotBlank()) { "Level ID must not be blank." }
        require(week in SemesterDifficulty.FIRST_WEEK..SemesterDifficulty.SEMESTER_WEEKS) {
            "Week must be within the semester."
        }
        require(timeMillis > 0) { "Completion time must be positive." }
        require(completedAtMillis >= 0) { "Completion timestamp must be non-negative." }
    }
}

/** What [PlayerProgress.withCompletion] changed, for the Result screen to report. */
data class CompletionOutcome(
    val isNewPersonalBest: Boolean,
    /** Best time before this completion, or null on a first clear. */
    val previousBest: PersonalBest?,
    /** Week unlocked by this completion, or null when nothing new was unlocked. */
    val newlyUnlockedWeek: Int?,
)
