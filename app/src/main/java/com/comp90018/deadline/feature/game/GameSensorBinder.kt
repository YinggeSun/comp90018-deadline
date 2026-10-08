package com.comp90018.deadline.feature.game

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.comp90018.deadline.sensor.SensorGateway
import com.comp90018.deadline.sensor.haptic.GameHaptic
import com.comp90018.deadline.sensor.haptic.HapticFeedbackManager
import com.comp90018.deadline.sensor.shake.ShakeSensorController
import com.comp90018.deadline.sensor.tilt.TiltSensorController

/**
 * Lifecycle-aware bridge from device sensors to the Game layer.
 *
 * #25: Accelerometer -> Shake -> GameViewModel -> GameEngine.shuffle()
 * #27: Rotation Vector -> Tilt -> GameViewModel peek state -> Compose UI
 *
 * The bridge deliberately does not mutate GameState itself.
 */
class GameSensorBinder(
    gateway: SensorGateway,
    private val actions: GameSensorActions,
    private val haptics: HapticFeedbackManager? = null,
) : DefaultLifecycleObserver {
    private val shakeController =
        ShakeSensorController(gateway) {
            // Only confirm the action with haptic feedback when the Game layer accepted it.
            if (actions.onShuffleRequested()) {
                haptics?.perform(GameHaptic.SHUFFLE)
            }
        }

    private val tiltController =
        TiltSensorController(gateway) { amount ->
            actions.onPeekChanged(amount)
        }

    val shakeSupported: Boolean get() = shakeController.isSupported()
    val tiltSupported: Boolean get() = tiltController.isSupported()

    override fun onStart(owner: LifecycleOwner) {
        shakeController.start()
        tiltController.start()
    }

    override fun onStop(owner: LifecycleOwner) {
        stop()
    }

    /** Also release subscriptions when a Compose screen leaves a still-started owner. */
    fun stop() {
        shakeController.stop()
        tiltController.stop()
    }
}
