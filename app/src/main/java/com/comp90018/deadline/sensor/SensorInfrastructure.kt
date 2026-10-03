package com.comp90018.deadline.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** Sensor types used by Deadline! sensor features. */
enum class DeadlineSensorType(val androidType: Int) {
    ACCELEROMETER(Sensor.TYPE_ACCELEROMETER),
    GYROSCOPE(Sensor.TYPE_GYROSCOPE),
    ROTATION_VECTOR(Sensor.TYPE_ROTATION_VECTOR),
}

data class SensorSample(
    val type: DeadlineSensorType,
    val values: FloatArray,
    val timestampNanos: Long,
)

fun interface SensorSampleListener {
    fun onSample(sample: SensorSample)
}

/**
 * Small SensorManager abstraction for issue #23.
 * Unsupported sensors simply return false instead of crashing.
 */
interface SensorGateway {
    fun isAvailable(type: DeadlineSensorType): Boolean
    fun register(type: DeadlineSensorType, listener: SensorSampleListener): Boolean
    fun unregister(listener: SensorSampleListener)
}

class AndroidSensorGateway(context: Context) : SensorGateway, SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val listeners = mutableMapOf<SensorSampleListener, MutableSet<DeadlineSensorType>>()

    override fun isAvailable(type: DeadlineSensorType): Boolean =
        sensorManager.getDefaultSensor(type.androidType) != null

    @Synchronized
    override fun register(type: DeadlineSensorType, listener: SensorSampleListener): Boolean {
        val sensor = sensorManager.getDefaultSensor(type.androidType) ?: return false
        val registered = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_GAME,
        )
        if (registered) {
            listeners.getOrPut(listener) { mutableSetOf() }.add(type)
        }
        return registered
    }

    @Synchronized
    override fun unregister(listener: SensorSampleListener) {
        listeners.remove(listener)
        // One Android listener instance fans out to all consumers, so rebuild registrations.
        rebuildRegistrations()
    }

    @Synchronized
    private fun rebuildRegistrations() {
        sensorManager.unregisterListener(this)
        listeners.values.flatten().toSet().forEach { type ->
            sensorManager.getDefaultSensor(type.androidType)?.let { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val type = DeadlineSensorType.entries.firstOrNull { it.androidType == event.sensor.type } ?: return
        val sample = SensorSample(type, event.values.copyOf(), event.timestamp)
        val targets = synchronized(this) {
            listeners.filterValues { type in it }.keys.toList()
        }
        targets.forEach { it.onSample(sample) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * Lifecycle-safe registration helper. Attach it to a LifecycleOwner and sensors are
 * registered in onStart and unregistered in onStop.
 */
class LifecycleSensorBinding(
    private val gateway: SensorGateway,
    private val types: Set<DeadlineSensorType>,
    private val listener: SensorSampleListener,
) : DefaultLifecycleObserver {

    var activeTypes: Set<DeadlineSensorType> = emptySet()
        private set

    override fun onStart(owner: LifecycleOwner) {
        activeTypes = types.filterTo(mutableSetOf()) { type ->
            gateway.register(type, listener)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        gateway.unregister(listener)
        activeTypes = emptySet()
    }
}
