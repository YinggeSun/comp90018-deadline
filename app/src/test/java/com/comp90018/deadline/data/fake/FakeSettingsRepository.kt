package com.comp90018.deadline.data.fake

import com.comp90018.deadline.domain.repository.SettingsRepository
import com.comp90018.deadline.domain.settings.PlayerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** In-memory [SettingsRepository] for tests. */
class FakeSettingsRepository(initial: PlayerSettings = PlayerSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<PlayerSettings> = state

    override suspend fun update(transform: (PlayerSettings) -> PlayerSettings) {
        state.update(transform)
    }
}
