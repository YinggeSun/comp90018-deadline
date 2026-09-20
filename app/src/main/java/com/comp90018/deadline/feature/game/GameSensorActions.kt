package com.comp90018.deadline.feature.game

/**
 * Integration seam for issues #25 and #27.
 * Your eventual GameViewModel should implement these two methods.
 */
interface GameSensorActions {
    /** Called once for each accepted physical shake. ViewModel should invoke board shuffle logic. */
    fun onShuffleRequested()

    /** UI-only 0..1 amount. Must never mutate board/domain state. */
    fun onPeekChanged(amount: Float)
}
