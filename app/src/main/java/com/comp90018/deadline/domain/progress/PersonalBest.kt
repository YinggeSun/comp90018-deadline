package com.comp90018.deadline.domain.progress

/** Fastest Completion Time recorded for one level. Lower [timeMillis] is better. */
data class PersonalBest(
    val levelId: String,
    val timeMillis: Long,
    val achievedAtMillis: Long,
) {
    init {
        require(levelId.isNotBlank()) { "Level ID must not be blank." }
        require(timeMillis > 0) { "Personal Best time must be positive." }
    }
}
