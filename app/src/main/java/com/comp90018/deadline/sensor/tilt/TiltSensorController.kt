package com.comp90018.deadline.sensor.tilt

import android.hardware.SensorManager
import com.comp90018.deadline.sensor.DeadlineSensorType
import com.comp90018.deadline.sensor.SensorGateway
import com.comp90018.deadline.sensor.SensorSample
import com.comp90018.deadline.sensor.SensorSampleListener
import kotlin.math.PI

/** Rotation-vector based Tilt-to-Peek processing for issue #26. */
class TiltSensorController(
    private val gateway: SensorGateway,
    private val processor: TiltProcessor = TiltProcessor(),
    private val onPeekChanged: (Float) -> Unit,
) {
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private val listener = SensorSampleListener(::handleSample)

    fun isSupported(): Boolean = gateway.isAvailable(DeadlineSensorType.ROTATION_VECTOR)

    fun start(): Boolean = gateway.register(DeadlineSensorType.ROTATION_VECTOR, listener)

    fun stop() {
        gateway.unregister(listener)
        processor.reset()
        onPeekChanged(0f)
    }

    private fun handleSample(sample: SensorSample) {
        if (sample.type != DeadlineSensorType.ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, sample.values)
        SensorManager.getOrientation(rotationMatrix, orientation)

        // orientation[1] is pitch in radians.
        val pitchDegrees = orientation[1] * (180f / PI.toFloat())
        onPeekChanged(processor.processPitch(pitchDegrees))
    }
}
