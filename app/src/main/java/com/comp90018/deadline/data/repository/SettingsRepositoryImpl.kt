package com.comp90018.deadline.data.repository

import com.comp90018.deadline.data.local.datastore.SettingsDataSource
import com.comp90018.deadline.domain.repository.SettingsRepository
import com.comp90018.deadline.domain.settings.PlayerSettings
import kotlinx.coroutines.flow.Flow

class SettingsRepositoryImpl(private val local: SettingsDataSource) : SettingsRepository {
    override val settings: Flow<PlayerSettings> = local.settings

    override suspend fun update(transform: (PlayerSettings) -> PlayerSettings) = local.update(transform)
}
