package com.comp90018.deadline.feature.game

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.comp90018.deadline.sensor.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GameSensorBinderTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class Gateway : SensorGateway {
        val listeners = mutableSetOf<SensorSampleListener>()
        override fun isAvailable(type: DeadlineSensorType) = true
        override fun register(type: DeadlineSensorType, listener: SensorSampleListener): Boolean {
            listeners.add(listener)
            return true
        }
        override fun unregister(listener: SensorSampleListener) { listeners.remove(listener) }
    }

    @Test fun disposalStopsBothSensorsAndReentryRegistersOnce() {
        val gateway = Gateway()
        var amount = 1f
        val binder = GameSensorBinder(gateway, object : GameSensorActions {
            override fun onShuffleRequested() = false
            override fun onPeekChanged(value: Float) { amount = value }
        })
        var visible by mutableStateOf(true)
        rule.setContent {
            if (visible) DisposableEffect(binder) {
                rule.activity.lifecycle.addObserver(binder)
                onDispose {
                    rule.activity.lifecycle.removeObserver(binder)
                    binder.stop()
                }
            }
        }
        rule.runOnIdle { assertEquals(2, gateway.listeners.size); visible = false }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(0, gateway.listeners.size)
            assertEquals(0f, amount)
            binder.stop()
            assertEquals(0, gateway.listeners.size)
            visible = true
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals(2, gateway.listeners.size)
            binder.onStop(rule.activity)
            assertEquals(0, gateway.listeners.size)
            binder.onStart(rule.activity)
            assertEquals(2, gateway.listeners.size)
        }
    }
}
