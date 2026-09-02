package com.comp90018.deadline.sensor

sealed interface SensorEvent {
    data object Shake : SensorEvent
    data class Tilt(val x: Float, val y: Float) : SensorEvent
}

