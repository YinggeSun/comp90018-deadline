package com.comp90018.deadline.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.comp90018.deadline.domain.level.model.SemesterDifficulty
import com.comp90018.deadline.domain.progress.CompletionOutcome
import com.comp90018.deadline.domain.progress.CompletionResult
import com.comp90018.deadline.domain.progress.PersonalBest
import com.comp90018.deadline.domain.progress.PlayerProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Maps [PlayerProgress] to and from Preferences keys. Malformed stored values are dropped
 * or clamped rather than thrown, so a bad write can cost one record but never the app.
 * Each Personal Best is one set entry, `"<timeMillis>:<achievedAtMillis>:<levelId>"`;
 * the level ID goes last so it may itself contain ':'.
 */
class ProgressDataSource(private val dataStore: DataStore<Preferences>) {

    val progress: Flow<PlayerProgress> =
        dataStore.safeData().map(::read).distinctUntilChanged()

    /** Reads, applies and writes in one DataStore transaction, so completions never interleave. */
    suspend fun recordCompletion(result: CompletionResult): CompletionOutcome {
        lateinit var outcome: CompletionOutcome
        dataStore.edit { preferences ->
            val (updated, change) = read(preferences).withCompletion(result)
            write(preferences, updated)
            outcome = change
        }
        return outcome
    }

    private fun read(preferences: Preferences) = PlayerProgress(
        completedLevelIds = preferences[COMPLETED].orEmpty().filterTo(mutableSetOf()) { it.isNotBlank() },
        highestUnlockedWeek = (preferences[UNLOCKED_WEEK] ?: PlayerProgress.FIRST_WEEK)
            .coerceIn(PlayerProgress.FIRST_WEEK, SemesterDifficulty.SEMESTER_WEEKS),
        personalBests = preferences[BESTS].orEmpty()
            .mapNotNull(::decodeBest)
            .groupBy { it.levelId }
            .mapValues { (_, bests) -> bests.minBy { it.timeMillis } },
        lastModifiedMillis = (preferences[LAST_MODIFIED] ?: 0L).coerceAtLeast(0L)
    )

    private fun write(preferences: MutablePreferences, progress: PlayerProgress) {
        preferences[COMPLETED] = progress.completedLevelIds
        preferences[UNLOCKED_WEEK] = progress.highestUnlockedWeek
        preferences[BESTS] = progress.personalBests.values.mapTo(mutableSetOf(), ::encodeBest)
        preferences[LAST_MODIFIED] = progress.lastModifiedMillis
    }

    private fun encodeBest(best: PersonalBest) =
        "${best.timeMillis}:${best.achievedAtMillis}:${best.levelId}"

    private fun decodeBest(encoded: String): PersonalBest? {
        val parts = encoded.split(':', limit = 3)
        if (parts.size != 3) return null
        val time = parts[0].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val achievedAt = parts[1].toLongOrNull() ?: return null
        val levelId = parts[2].takeIf { it.isNotBlank() } ?: return null
        return PersonalBest(levelId, time, achievedAt)
    }

    private companion object {
        val COMPLETED = stringSetPreferencesKey("progress_completed_level_ids")
        val UNLOCKED_WEEK = intPreferencesKey("progress_highest_unlocked_week")
        val BESTS = stringSetPreferencesKey("progress_personal_bests")
        val LAST_MODIFIED = longPreferencesKey("progress_last_modified_millis")
    }
}
