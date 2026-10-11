package com.comp90018.deadline.domain.config

import com.comp90018.deadline.domain.game.stress.StressConfig
import com.comp90018.deadline.sensor.shake.ShakeConfig
import com.comp90018.deadline.sensor.tilt.TiltConfig

/**
 * The gameplay balance values that can be tuned without a new build (#52): Stress and its
 * recovery, Shake-to-Shuffle and Tilt-to-Peek. The defaults are the values compiled into the
 * app, so a game always has a valid balance even when Remote Config is unavailable.
 */
data class GameBalance(
    val stress: StressConfig = StressConfig(),
    val shake: ShakeConfig = ShakeConfig(),
    val tilt: TiltConfig = TiltConfig(),
)
