package com.comp90018.deadline.feature.game

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs

/**
 * Adjusts only this Activity's window brightness while the game is visible.
 * No WRITE_SETTINGS permission required. If the device lacks an ambient light
 * sensor, leaves brightness unchanged.
 */
@Composable
fun AmbientBrightnessEffect(enabled: Boolean = true) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(activity, lifecycleOwner, enabled) {
        val manager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT)
        val window = activity.window
        val originalBrightness = window.attributes.screenBrightness
        var registered = false
        var filteredLux: Float? = null
        var lastBrightness: Float? = null

        fun restore() {
            val lp = window.attributes
            lp.screenBrightness = originalBrightness
            window.attributes = lp
            filteredLux = null
            lastBrightness = null
        }

        val listener =
            object : SensorEventListener {
                override fun onAccuracyChanged(
                    sensor: Sensor?,
                    accuracy: Int,
                ) = Unit

                override fun onSensorChanged(event: SensorEvent) {
                    if (event.sensor.type != Sensor.TYPE_LIGHT) return
                    val lux = event.values[0].coerceAtLeast(0f)
                    // Exponential smoothing reduces visible flicker due to noise.
                    val smoothed = filteredLux?.let { it * 0.8f + lux * 0.2f } ?: lux
                    filteredLux = smoothed
                    val target =
                        when {
                            smoothed < 5f -> 0.12f
                            smoothed < 30f -> 0.27f
                            smoothed < 150f -> 0.50f
                            smoothed < 500f -> 0.72f
                            else -> 0.95f
                        }
                    if (lastBrightness == null || abs(target - lastBrightness!!) >= 0.05f) {
                        val lp = window.attributes
                        lp.screenBrightness = target
                        window.attributes = lp
                        lastBrightness = target
                    }
                }
            }

        fun start() {
            if (enabled && !registered && lightSensor != null) {
                registered =
                    manager.registerListener(
                        listener,
                        lightSensor,
                        SensorManager.SENSOR_DELAY_NORMAL,
                    )
            }
        }

        fun stop() {
            if (registered) manager.unregisterListener(listener)
            registered = false
            restore()
        }

        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> start()
                    Lifecycle.Event.ON_STOP -> stop()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            start()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }
}
