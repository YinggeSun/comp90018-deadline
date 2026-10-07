package com.comp90018.deadline.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.File
import java.io.IOException

/**
 * The app's single Preferences DataStore file. DataStore allows only one active instance
 * per file in a process, so create this once (see AppContainer) and share it.
 * A corrupt file is replaced with empty preferences, which read back as defaults.
 */
object DeadlineDataStore {
    private const val FILE_NAME = "deadline"

    fun create(context: Context): DataStore<Preferences> = create { context.applicationContext.preferencesDataStoreFile(FILE_NAME) }

    /** Same configuration on any file; tests pass a temporary file and their own [scope]. */
    internal fun create(
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile: () -> File,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = scope,
            produceFile = produceFile,
        )
}

/**
 * Value of [key] only if it is stored with the expected type, otherwise null. A readable file
 * can still hold another type under the same name (for example after a schema change), and
 * the plain `preferences[key]` read would then throw ClassCastException.
 */
internal inline fun <reified T : Any> Preferences.typed(key: Preferences.Key<T>): T? = asMap()[key] as? T

/** Reads that fail with an I/O error fall back to defaults instead of crashing the screen. */
internal fun DataStore<Preferences>.safeData(): Flow<Preferences> =
    data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }
