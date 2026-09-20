package com.comp90018.deadline.sensor.shake

import android.os.SystemClock
import com.comp90018.deadline.sensor.DeadlineSensorType
import com.comp90018.deadline.sensor.SensorGateway
import com.comp90018.deadline.sensor.SensorSample
import com.comp90018.deadline.sensor.SensorSampleListener

/**
 * Connects accelerometer samples to a single accepted shake callback.
 * Cooldown lives in [ShakeDetector], preventing repeated triggering (#24/#25).
 */
class ShakeSensorController(
    private val gateway: SensorGateway,
    private val detector: ShakeDetector = ShakeDetector(),
    private val onShake: () -> Unit,
) {
    private val listener = SensorSampleListener(::handleSample)

    fun isSupported(): Boolean = gateway.isAvailable(DeadlineSensorType.ACCELEROMETER)

    fun start(): Boolean = gateway.register(DeadlineSensorType.ACCELEROMETER, listener)

    fun stop() {
        gateway.unregister(listener)
        detector.reset()
    }

    private fun handleSample(sample: SensorSample) {
        if (sample.type != DeadlineSensorType.ACCELEROMETER || sample.values.size < 3) return
        if (
            detector.processAccelerometer(
                x = sample.values[0],
                y = sample.values[1],
                z = sample.values[2],
                nowMillis = SystemClock.elapsedRealtime(),
            )
        ) {
            onShake()
        }
    }
}
