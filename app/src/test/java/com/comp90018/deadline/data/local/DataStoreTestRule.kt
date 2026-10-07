package com.comp90018.deadline.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.comp90018.deadline.data.local.datastore.DeadlineDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Production-configured Preferences DataStore on a temporary file. [reopen] closes the current instance
 * and opens a new one on the same file, which is how tests simulate an app restart.
 */
class DataStoreTestRule : TemporaryFolder() {
    private var scope: CoroutineScope? = null
    private val file: File by lazy { File(root, "test.preferences_pb") }

    lateinit var dataStore: DataStore<Preferences>
        private set

    override fun before() {
        super.before()
        open()
    }

    override fun after() {
        close()
        super.after()
    }

    fun reopen(): DataStore<Preferences> {
        close()
        open()
        return dataStore
    }

    /** Closes the store and overwrites its file with raw bytes; follow with [reopen]. */
    fun corrupt(bytes: ByteArray) {
        close()
        file.writeBytes(bytes)
    }

    /** DataStore allows one active instance per file, so wait until this one has fully stopped. */
    private fun close() {
        scope?.let { runBlocking { it.coroutineContext.job.cancelAndJoin() } }
        scope = null
    }

    private fun open() {
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        dataStore = DeadlineDataStore.create(newScope) { file }
    }
}
