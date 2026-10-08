package com.comp90018.deadline.domain.progress

/**
 * Hands a won level to persistence without the caller waiting. Implementations must
 * outlive the screen that reports the win, because that screen closes immediately after.
 */
fun interface CompletionRecorder {
    fun record(result: CompletionResult)

    companion object {
        /** Records nothing; for previews and tests that do not care about progress. */
        val None = CompletionRecorder { }
    }
}
