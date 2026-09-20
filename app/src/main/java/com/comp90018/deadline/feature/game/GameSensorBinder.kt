package com.comp90018.deadline.feature.game

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.comp90018.deadline.sensor.SensorGateway
import com.comp90018.deadline.sensor.haptic.GameHaptic
import com.comp90018.deadline.sensor.haptic.HapticFeedbackManager
import com.comp90018.deadline.sensor.shake.ShakeSensorController
import com.comp90018.deadline.sensor.tilt.TiltSensorController

/**
 * One lifecycle-aware bridge from device sensors to the Game ViewModel.
 * Covers #25 (Shake-to-Shuffle) and sensor side of #27 (Tilt-to-Peek UI).
 */
class GameSensorBinder(
    gateway: SensorGateway,
    private val actions: GameSensorActions,
    private val haptics: HapticFeedbackManager? = null,
) : DefaultLifecycleObserver {

    private val shakeController = ShakeSensorController(gateway) {
        actions.onShuffleRequested()
        haptics?.perform(GameHaptic.SHUFFLE)
    }

    private val tiltController = TiltSensorController(gateway) { amount ->
        actions.onPeekChanged(amount)
    }

    val shakeSupported: Boolean get() = shakeController.isSupported()
    val tiltSupported: Boolean get() = tiltController.isSupported()

    override fun onStart(owner: LifecycleOwner) {
        shakeController.start()
        tiltController.start()
    }

    override fun onStop(owner: LifecycleOwner) {
        shakeController.stop()
        tiltController.stop()
    }
}
