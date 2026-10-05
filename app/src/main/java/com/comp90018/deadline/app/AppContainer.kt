package com.comp90018.deadline.app

import android.content.Context
import com.comp90018.deadline.data.local.datastore.DeadlineDataStore
import com.comp90018.deadline.data.local.datastore.ProgressDataSource
import com.comp90018.deadline.data.local.datastore.SettingsDataSource
import com.comp90018.deadline.data.repository.ProgressRepositoryImpl
import com.comp90018.deadline.data.repository.SettingsRepositoryImpl
import com.comp90018.deadline.domain.repository.ProgressRepository
import com.comp90018.deadline.domain.repository.SettingsRepository

/** Application-level dependency container; one instance lives in [DeadlineApp]. */
class AppContainer(context: Context) {
    private val dataStore = DeadlineDataStore.create(context)

    val progressRepository: ProgressRepository = ProgressRepositoryImpl(ProgressDataSource(dataStore))

    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(SettingsDataSource(dataStore))
}
