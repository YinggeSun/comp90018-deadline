package com.comp90018.deadline.feature.game

/**
 * Integration seam for issues #25 and #27.
 *
 * The sensor layer reports user intent only. Game/domain state changes remain owned by
 * GameViewModel/GameEngine so shuffle obeys the same running/terminal rules as normal UI input.
 */
interface GameSensorActions {
    /**
     * Called once for each accepted physical shake.
     * Returns true when the current game accepted a shuffle request; callers can use
     * the result to avoid playing a shuffle haptic for a rejected/terminal action.
     */
    fun onShuffleRequested(): Boolean

    /** UI-only 0..1 amount. Must never mutate board/domain state. */
    fun onPeekChanged(amount: Float)
}
