package com.comp90018.deadline.data.repository

import com.comp90018.deadline.data.config.RemoteBalance
import com.comp90018.deadline.data.remote.firebase.RemoteConfigSource
import com.comp90018.deadline.domain.config.GameBalance
import com.comp90018.deadline.domain.repository.BalanceSource
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ConfigRepositoryImplTest {
    /** Remote Config stand-in: [cached] was activated earlier; a fetch activates [published]. */
    private class FakeSource : RemoteConfigSource {
        var cached: Map<String, String> = emptyMap()
        var published: Map<String, String>? = null
        var fetchFails = false
        var fetchHangs = false
        var activatedFails = false
        var activatedHangsOnce = false

        override suspend fun activated(): Map<String, String> {
            if (activatedFails) throw IllegalStateException("not initialised")
            if (activatedHangsOnce) {
                activatedHangsOnce = false
                awaitCancellation()
            }
            return cached
        }

        override suspend fun fetchAndActivate() {
            if (fetchHangs) awaitCancellation()
            if (fetchFails) throw IOException("offline")
            published?.let { cached = it }
        }
    }

    private val source = FakeSource()
    private val rejected = mutableListOf<Set<String>>()
    private val repository = ConfigRepositoryImpl(source, onRejected = { rejected += it })

    @Test
    fun startsWithTheCompiledDefaults() {
        assertEquals(GameBalance(), repository.balance.value)
    }

    @Test
    fun nothingPublishedKeepsTheDefaults() =
        runTest {
            assertEquals(BalanceSource.DEFAULTS, repository.refresh())
            assertEquals(GameBalance(), repository.balance.value)
        }

    @Test
    fun freshlyPublishedValuesAreApplied() =
        runTest {
            source.published = mapOf(RemoteBalance.SHAKE_COOLDOWN_MILLIS to "1500")

            assertEquals(BalanceSource.REMOTE, repository.refresh())
            assertEquals(1_500L, repository.balance.value.shake.cooldownMillis)
        }

    @Test
    fun offlineUsesValuesFetchedInAnEarlierSession() =
        runTest {
            source.cached = mapOf(RemoteBalance.STRESS_ACCUMULATION_PERCENT_PER_SECOND to "2.0")
            source.fetchFails = true

            assertEquals(BalanceSource.REMOTE, repository.refresh())
            assertEquals(2.0, repository.balance.value.stress.accumulationPercentPerSecond, 0.0)
        }

    @Test
    fun aHangingFetchTimesOutAndKeepsTheCurrentBalance() =
        runTest {
            source.fetchHangs = true

            assertEquals(BalanceSource.DEFAULTS, repository.refresh())
            assertEquals(GameBalance(), repository.balance.value)
        }

    @Test
    fun anUnavailableRemoteConfigNeverThrowsAndKeepsTheDefaults() =
        runTest {
            source.activatedFails = true
            source.fetchFails = true

            assertEquals(BalanceSource.DEFAULTS, repository.refresh())
            assertEquals(GameBalance(), repository.balance.value)
        }

    @Test
    fun aHangingRemoteConfigSetupCannotBlockTheRefresh() =
        runTest {
            source.activatedHangsOnce = true
            source.published = mapOf(RemoteBalance.SHAKE_COOLDOWN_MILLIS to "1500")

            assertEquals(BalanceSource.REMOTE, repository.refresh())
            assertEquals(1_500L, repository.balance.value.shake.cooldownMillis)
        }

    @Test
    fun refusedValuesAreReportedAndReplacedByDefaults() =
        runTest {
            source.published = mapOf(RemoteBalance.SHAKE_REQUIRED_HITS to "0", RemoteBalance.SHAKE_COOLDOWN_MILLIS to "1500")

            repository.refresh()

            assertEquals(listOf(setOf(RemoteBalance.SHAKE_REQUIRED_HITS)), rejected)
            assertEquals(GameBalance().shake.requiredHits, repository.balance.value.shake.requiredHits)
            assertEquals(1_500L, repository.balance.value.shake.cooldownMillis)
        }
}
