package com.comp90018.deadline.feature.game

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.comp90018.deadline.sensor.DeadlineSensorType
import com.comp90018.deadline.sensor.SensorGateway
import com.comp90018.deadline.sensor.SensorSampleListener
import org.junit.Assert.assertEquals
import org.junit.Test

/** The sensor binder registers only the motion controls the player has turned on (#34). */
class GameSensorSettingsTest {
    private class Gateway : SensorGateway {
        val registered = mutableMapOf<SensorSampleListener, DeadlineSensorType>()

        override fun isAvailable(type: DeadlineSensorType) = true

        override fun register(
            type: DeadlineSensorType,
            listener: SensorSampleListener,
        ): Boolean {
            registered[listener] = type
            return true
        }

        override fun unregister(listener: SensorSampleListener) {
            registered.remove(listener)
        }

        fun types() = registered.values.toSet()
    }

    private val owner =
        object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = throw UnsupportedOperationException()
        }

    private var peek = -1f
    private val actions =
        object : GameSensorActions {
            override fun onShuffleRequested() = false

            override fun onPeekChanged(amount: Float) {
                peek = amount
            }
        }

    @Test
    fun disabledSensorsAreNotRegisteredOnStart() {
        val gateway = Gateway()
        val binder = GameSensorBinder(gateway, actions)
        binder.shakeEnabled = false

        binder.onStart(owner)

        assertEquals(setOf(DeadlineSensorType.ROTATION_VECTOR), gateway.types())
    }

    @Test
    fun togglingWhileStartedRegistersAndReleases() {
        val gateway = Gateway()
        val binder = GameSensorBinder(gateway, actions)
        binder.onStart(owner)
        assertEquals(setOf(DeadlineSensorType.ACCELEROMETER, DeadlineSensorType.ROTATION_VECTOR), gateway.types())

        binder.shakeEnabled = false
        assertEquals(setOf(DeadlineSensorType.ROTATION_VECTOR), gateway.types())

        binder.tiltEnabled = false
        assertEquals(emptySet<DeadlineSensorType>(), gateway.types())
        assertEquals(0f, peek)

        binder.shakeEnabled = true
        assertEquals(setOf(DeadlineSensorType.ACCELEROMETER), gateway.types())
    }

    @Test
    fun togglingWhileStoppedWaitsForStart() {
        val gateway = Gateway()
        val binder = GameSensorBinder(gateway, actions)
        binder.tiltEnabled = false
        binder.tiltEnabled = true

        assertEquals(emptySet<DeadlineSensorType>(), gateway.types())

        binder.onStart(owner)
        binder.stop()
        binder.shakeEnabled = false
        binder.shakeEnabled = true
        assertEquals(emptySet<DeadlineSensorType>(), gateway.types())
    }
}
