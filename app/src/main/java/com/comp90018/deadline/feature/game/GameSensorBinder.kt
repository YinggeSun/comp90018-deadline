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
 *
 * [shakeEnabled] and [tiltEnabled] follow the player's settings (#34). A disabled
 * sensor is not registered at all, and turning one off while started releases it.
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

    private var started = false

    var shakeEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (started) {
                if (value) {
                    shakeController.start()
                } else {
                    shakeController.stop()
                }
            }
        }

    /** Turning tilt off also resets the peek, because the controller reports 0 on stop. */
    var tiltEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (started) {
                if (value) {
                    tiltController.start()
                } else {
                    tiltController.stop()
                }
            }
        }

    override fun onStart(owner: LifecycleOwner) {
        started = true
        if (shakeEnabled) shakeController.start()
        if (tiltEnabled) tiltController.start()
    }

    override fun onStop(owner: LifecycleOwner) {
        stop()
    }

    /** Also release subscriptions when a Compose screen leaves a still-started owner. */
    fun stop() {
        started = false
        shakeController.stop()
        tiltController.stop()
    }
}
