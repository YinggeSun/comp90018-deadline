package com.comp90018.deadline.data.sync

import com.comp90018.deadline.domain.progress.PersonalBest
import com.comp90018.deadline.domain.progress.PlayerProgress

/**
 * Merges two copies of a player's progress, such as this device's and the cloud's, without a
 * merge UI. Every field only ever improves, so each one merges on its own: completed levels are
 * combined, the furthest unlocked week wins, and each level keeps its faster Personal Best (the
 * earlier record when times are equal). The result is never worse than either input, and the
 * merge is order-independent and safe to repeat.
 */
object ConflictResolver {
    fun merge(
        a: PlayerProgress,
        b: PlayerProgress,
    ): PlayerProgress =
        PlayerProgress(
            completedLevelIds = a.completedLevelIds + b.completedLevelIds,
            highestUnlockedWeek = maxOf(a.highestUnlockedWeek, b.highestUnlockedWeek),
            personalBests =
                (a.personalBests.keys + b.personalBests.keys).associateWith { levelId ->
                    better(a.personalBests[levelId], b.personalBests[levelId])
                },
            lastModifiedMillis = maxOf(a.lastModifiedMillis, b.lastModifiedMillis),
        )

    private fun better(
        a: PersonalBest?,
        b: PersonalBest?,
    ): PersonalBest =
        when {
            a == null -> checkNotNull(b)
            b == null -> a
            a.timeMillis != b.timeMillis -> if (a.timeMillis < b.timeMillis) a else b
            else -> if (a.achievedAtMillis <= b.achievedAtMillis) a else b
        }
}
