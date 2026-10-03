package com.comp90018.deadline.sensor.haptic

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/** Haptic event types required by issue #28. */
enum class GameHaptic {
    TILE_SELECT,
    MATCH,
    SHUFFLE,
    SUCCESS,
    FAILURE,
}

class HapticFeedbackManager(context: Context) {
    private val vibrator = context.getSystemService(Vibrator::class.java)

    var enabled: Boolean = true

    fun perform(event: GameHaptic) {
        if (!enabled || vibrator?.hasVibrator() != true) return

        val effect = when (event) {
            GameHaptic.TILE_SELECT -> VibrationEffect.createOneShot(18L, 80)
            GameHaptic.MATCH -> VibrationEffect.createWaveform(
                longArrayOf(0L, 25L, 45L, 35L),
                intArrayOf(0, 110, 0, 150),
                -1,
            )
            GameHaptic.SHUFFLE -> VibrationEffect.createOneShot(45L, 120)
            GameHaptic.SUCCESS -> VibrationEffect.createWaveform(
                longArrayOf(0L, 35L, 55L, 60L),
                intArrayOf(0, 120, 0, 180),
                -1,
            )
            GameHaptic.FAILURE -> VibrationEffect.createOneShot(80L, 150)
        }
        vibrator.vibrate(effect)
    }
}
