package com.comp90018.deadline.data.repository

import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** Serializes account changes with sync, transfers and completion recording, including nested calls. */
class SessionGate {
    private val mutex = Mutex()

    private class Held(val gate: SessionGate) : AbstractCoroutineContextElement(Key) {
        var owner: Job? = null

        companion object Key : CoroutineContext.Key<Held>
    }

    suspend fun isHeld(): Boolean = coroutineContext[Held]?.gate === this

    /** Work queued for one identity must not silently run for its successor. */
    suspend fun <T> forUser(
        expectedUid: String?,
        currentUid: () -> String?,
        changed: () -> T,
        block: suspend () -> T,
    ): T = run { if (expectedUid == currentUid()) block() else changed() }

    suspend fun <T> run(block: suspend () -> T): T {
        val held = coroutineContext[Held]
        if (held?.gate === this) {
            check(held.owner === coroutineContext[Job]) { "A child coroutine cannot inherit ownership of SessionGate." }
            return block()
        }
        return mutex.withLock {
            val marker = Held(this)
            withContext(marker) {
                marker.owner = coroutineContext[Job]
                block()
            }
        }
    }
}
