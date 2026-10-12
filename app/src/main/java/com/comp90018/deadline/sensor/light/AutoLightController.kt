package com.comp90018.deadline.sensor.light

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import kotlin.math.ln

/** Changes only this Activity window, never system-wide brightness. */
class AutoLightController(private val activity: Activity) : SensorEventListener {
    private val manager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var originalBrightness: Float? = null
    private var running = false
    private var lastBrightness: Float? = null

    fun start() {
        if (running || sensor == null) return
        originalBrightness = activity.window.attributes.screenBrightness
        running = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        if (!running) originalBrightness = null
    }

    fun stop() {
        if (!running) return
        manager.unregisterListener(this)
        running = false
        val previous = originalBrightness
        originalBrightness = null
        lastBrightness = null
        if (previous != null) {
            val attributes = activity.window.attributes
            attributes.screenBrightness = previous
            activity.window.attributes = attributes
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running || event.sensor.type != Sensor.TYPE_LIGHT) return
        val target = brightnessForLux(event.values[0])
        // Ignore small fluctuations to prevent visible brightness flicker.
        if (lastBrightness?.let { kotlin.math.abs(it - target) < 0.04f } == true) return
        lastBrightness = target
        val attributes = activity.window.attributes
        attributes.screenBrightness = target
        activity.window.attributes = attributes
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** Smooth, bounded mapping: 0 lux -> 15%, 1000+ lux -> 100%. */
fun brightnessForLux(lux: Float): Float {
    val safeLux = if (lux.isFinite()) lux.coerceAtLeast(0f) else 0f
    val ratio = (ln(1.0 + safeLux.toDouble()) / ln(1001.0)).coerceIn(0.0, 1.0)
    return (0.15 + 0.85 * ratio).toFloat()
}
