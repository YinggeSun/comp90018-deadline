package com.comp90018.deadline.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionGateTest {
    @Test
    fun inheritedMarkerDoesNotLetConcurrentChildBypassLock() =
        runTest {
            val gate = SessionGate()
            gate.run {
                assertEquals(7, gate.run { 7 })
                var executed = false
                val child = CoroutineScope(currentCoroutineContext()).async { runCatching { gate.run { executed = true } } }
                assertTrue(child.await().exceptionOrNull() is IllegalStateException)
                assertFalse(executed)
            }
            assertEquals(9, gate.run { 9 })
        }

    @Test
    fun queuedTransferIsRefusedIfIdentityChangedBeforeItAcquiresGate() =
        runTest {
            val gate = SessionGate()
            var uid = "old"
            val release = CompletableDeferred<Unit>()
            val transition =
                async {
                    gate.run {
                        release.await()
                        uid = "new"
                    }
                }
            runCurrent()
            var writes = 0
            val transfer =
                async {
                    gate.forUser("old", { uid }, { false }) {
                        writes++
                        true
                    }
                }
            runCurrent()
            assertFalse(transfer.isCompleted)
            release.complete(Unit)
            transition.await()
            assertFalse(transfer.await())
            assertEquals(0, writes)
        }

    @Test
    fun cancelledWaiterDoesNotLockOutCompletionOrNextSync() =
        runTest {
            val gate = SessionGate()
            val release = CompletableDeferred<Unit>()
            val first = async { gate.run { release.await() } }
            runCurrent()
            val cancelled = async { gate.run { error("Cancelled work must not execute") } }
            runCurrent()
            cancelled.cancel()
            release.complete(Unit)
            first.await()
            assertEquals("saved", gate.run { "saved" })
        }
}
