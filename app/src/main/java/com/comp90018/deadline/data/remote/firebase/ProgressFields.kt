package com.comp90018.deadline.data.remote.firebase

import com.comp90018.deadline.domain.level.model.SemesterDifficulty
import com.comp90018.deadline.domain.progress.PersonalBest
import com.comp90018.deadline.domain.progress.PlayerProgress

/** How [PlayerProgress] is stored in Firestore documents (cloud progress and transfer codes). */
internal object ProgressFields {
    const val COMPLETED = "completedLevelIds"
    const val WEEK = "highestUnlockedWeek"
    const val BESTS = "personalBests"
    const val TIME = "timeMillis"
    const val ACHIEVED_AT = "achievedAtMillis"
    const val LAST_MODIFIED = "lastModifiedMillis"

    fun write(progress: PlayerProgress): Map<String, Any> =
        mapOf(
            COMPLETED to progress.completedLevelIds.sorted(),
            WEEK to progress.highestUnlockedWeek,
            BESTS to
                progress.personalBests.mapValues { (_, best) ->
                    mapOf(TIME to best.timeMillis, ACHIEVED_AT to best.achievedAtMillis)
                },
            LAST_MODIFIED to progress.lastModifiedMillis,
        )

    /** Reads defensively: missing or wrong-typed values fall back to defaults instead of throwing. */
    fun read(field: (String) -> Any?): PlayerProgress {
        val completed = (field(COMPLETED) as? List<*>).orEmpty().filterIsInstance<String>().filter { it.isNotBlank() }
        val week =
            (field(WEEK) as? Long)?.toInt()?.coerceIn(SemesterDifficulty.FIRST_WEEK, SemesterDifficulty.SEMESTER_WEEKS)
                ?: SemesterDifficulty.FIRST_WEEK
        val bests =
            (field(BESTS) as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
                val levelId = (key as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val fields = value as? Map<*, *> ?: return@mapNotNull null
                val time = (fields[TIME] as? Long)?.takeIf { it > 0 } ?: return@mapNotNull null
                val achievedAt = fields[ACHIEVED_AT] as? Long ?: return@mapNotNull null
                levelId to PersonalBest(levelId, time, achievedAt)
            }.toMap()
        return PlayerProgress(
            completedLevelIds = completed.toSet(),
            highestUnlockedWeek = week,
            personalBests = bests,
            lastModifiedMillis = ((field(LAST_MODIFIED) as? Long) ?: 0L).coerceAtLeast(0L),
        )
    }
}
