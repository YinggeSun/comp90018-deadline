package com.comp90018.deadline.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.comp90018.deadline.data.sync.ConflictResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicLong

/** One file, with separate progress/nickname namespaces. Device settings remain shared. */
class AccountDataStore(
    private val backing: DataStore<Preferences>,
    private val expectedUid: (() -> String?)? = null,
) : DataStore<Preferences>, GuestMigrationJournal {
    private val selectedOwner = MutableStateFlow<String?>(null)
    private val version = AtomicLong()
    val sessionVersion: Long get() = version.get()
    override val data: Flow<Preferences> = combine(backing.data, selectedOwner) { stored, _ -> view(stored) }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val saved =
            backing.updateData { stored ->
                val selected = selectedOwner.value
                check(isVisible(stored, selected)) {
                    "Account storage is not ready. Please retry."
                }
                val before = view(stored)
                val after = transform(before)
                check(selectedOwner.value == selected && isVisible(stored, selected)) {
                    "Account changed during the write. Please retry."
                }
                stored.toMutablePreferences().apply {
                    privateNames.forEach { name ->
                        val target = key(scoped(stored, name))
                        val value = after.asMap()[key(name)]
                        if (value == null) remove(target) else this[target] = value
                    }
                    after.asMap().filterKeys { it.name !in privateNames }.forEach { (k, v) -> this[key(k.name)] = v }
                }
            }
        return view(saved)
    }

    override suspend fun prepare() {
        check(expectedUid != null && expectedUid.invoke() == null)
        activate(null)
        backing.edit { stored ->
            check(expectedUid.invoke() == null && stored[OWNER] == LOCAL_GUEST)
            check(stored[MIGRATION_SOURCE] == null || stored[MIGRATION_SOURCE] == LOCAL_GUEST)
            stored[MIGRATION_SOURCE] = LOCAL_GUEST
            stored.remove(MIGRATION_DESTINATION)
        }
    }

    override suspend fun confirm(uid: String) {
        backing.edit { stored ->
            check(expectedUid != null && expectedUid.invoke() == uid)
            if (stored[MIGRATION_SOURCE] == null && stored[OWNER] == uid) return@edit
            check(stored[MIGRATION_SOURCE] == LOCAL_GUEST && stored[OWNER] == LOCAL_GUEST)
            check(stored[MIGRATION_DESTINATION] == null || stored[MIGRATION_DESTINATION] == uid)
            stored[MIGRATION_DESTINATION] = uid
        }
    }

    override suspend fun cancelForLogin() {
        backing.edit { stored ->
            if (stored[MIGRATION_SOURCE] != null) {
                check(expectedUid != null && expectedUid.invoke() == null)
                check(stored[MIGRATION_SOURCE] == LOCAL_GUEST && stored[OWNER] == LOCAL_GUEST)
                stored.remove(MIGRATION_SOURCE)
                stored.remove(MIGRATION_DESTINATION)
            }
        }
    }

    override suspend fun checkRecovery() {
        val stored = backing.data.first()
        check(stored[MIGRATION_SOURCE] == LOCAL_GUEST && stored[OWNER] == LOCAL_GUEST)
    }

    override suspend fun restore() {
        backing.edit { stored ->
            check(expectedUid != null && expectedUid.invoke() == null)
            check(stored[MIGRATION_SOURCE] == LOCAL_GUEST && stored[OWNER] == LOCAL_GUEST)
            stored.remove(MIGRATION_SOURCE)
            stored.remove(MIGRATION_DESTINATION)
        }
        version.incrementAndGet()
        selectedOwner.value = LOCAL_GUEST
    }

    /** First launch adopts legacy data; subsequent UID changes select isolated saved data. */
    suspend fun activate(
        uid: String?,
        migrateLocalGuest: Boolean = false,
    ) {
        // Hide the prior account immediately, including when a disk write later fails.
        val nextOwner = uid ?: LOCAL_GUEST
        val selected = selectedOwner.value
        if (selected != null && selected != nextOwner && !(selected == LOCAL_GUEST && migrateLocalGuest)) {
            version.incrementAndGet()
        }
        selectedOwner.value = nextOwner
        backing.edit { stored ->
            val next = uid ?: LOCAL_GUEST
            val previous = stored[OWNER]
            val pending = stored[MIGRATION_SOURCE]
            val recovering = pending != null && uid != null
            if (recovering &&
                (
                    pending != LOCAL_GUEST || previous != LOCAL_GUEST ||
                        stored[MIGRATION_DESTINATION] != uid || expectedUid == null || expectedUid.invoke() != uid
                )
            ) {
                throw GuestRecoveryRequiredException()
            }
            if (previous == null || (previous == LOCAL_GUEST && next != LOCAL_GUEST && (migrateLocalGuest || recovering))) {
                val source = namespace(stored, previous)
                val target = namespace(stored, next)
                val codec = ProgressDataSource(backing)
                val merged = target.toMutablePreferences()
                codec.write(merged, ConflictResolver.merge(codec.read(source), codec.read(target)))
                val nickname = stringPreferencesKey("settings_nickname")
                if (target.typed(nickname).isNullOrBlank()) {
                    source.typed(nickname)?.let { merged[nickname] = it }
                }
                privateNames.forEach { name ->
                    merged.asMap()[key(name)]?.let { stored[key("$next/$name")] = it }
                    stored.remove(key(if (previous == null) name else "$previous/$name"))
                }
            }
            // Destination, source removal, ownership and acknowledgement commit atomically.
            if (recovering) {
                stored.remove(MIGRATION_SOURCE)
                stored.remove(MIGRATION_DESTINATION)
            }
            stored[OWNER] = next
        }
    }

    private fun view(stored: Preferences): Preferences =
        stored.toMutablePreferences().apply {
            asMap().keys.filter { it.name.contains('/') || it == OWNER }.forEach { remove(key(it.name)) }
            privateNames.forEach { name ->
                remove(key(name))
                if (isVisible(stored, selectedOwner.value)) {
                    stored.asMap()[key(scoped(stored, name))]?.let { this[key(name)] = it }
                }
            }
        }

    private fun isVisible(
        stored: Preferences,
        selected: String?,
    ): Boolean =
        (selected == null || stored[OWNER] == selected) &&
            (expectedUid == null || stored[OWNER] == (expectedUid.invoke() ?: LOCAL_GUEST))

    private fun namespace(
        stored: Preferences,
        owner: String?,
    ): Preferences =
        emptyPreferences().toMutablePreferences().apply {
            privateNames.forEach { name ->
                stored.asMap()[key(if (owner == null) name else "$owner/$name")]?.let { this[key(name)] = it }
            }
        }

    private fun scoped(
        stored: Preferences,
        name: String,
    ): String = stored[OWNER]?.let { "$it/$name" } ?: name

    @Suppress("UNCHECKED_CAST")
    private fun key(name: String): Preferences.Key<Any> = stringPreferencesKey(name) as Preferences.Key<Any>

    private companion object {
        const val LOCAL_GUEST = "local-guest"
        val OWNER = stringPreferencesKey("account_owner")
        val MIGRATION_SOURCE = stringPreferencesKey("guest_migration/source")
        val MIGRATION_DESTINATION = stringPreferencesKey("guest_migration/destination")
        val privateNames =
            setOf(
                "progress_completed_level_ids",
                "progress_highest_unlocked_week",
                "progress_personal_bests",
                "progress_last_modified_millis",
                "settings_nickname",
            )
    }
}
