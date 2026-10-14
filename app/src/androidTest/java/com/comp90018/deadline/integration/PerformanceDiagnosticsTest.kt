package com.comp90018.deadline.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.comp90018.deadline.domain.level.generator.GeneratedLevelSource
import com.comp90018.deadline.domain.level.generator.ValidatedLevelGenerator
import com.comp90018.deadline.domain.level.model.SemesterLevel
import com.comp90018.deadline.sensor.AndroidSensorGateway
import com.comp90018.deadline.sensor.DeadlineSensorType
import com.comp90018.deadline.sensor.SensorSampleListener
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Diagnostic measurements; no emulator timing is used as a physical-device acceptance gate. */
class PerformanceDiagnosticsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun generatedBoardTimingAndSizeAreRecordedForAllSixLevels() =
        runBlocking {
            val rows = mutableListOf("level,seed,tiles,load_ms")
            SemesterLevel.ALL.forEach { requested ->
                repeat(3) { seed ->
                    val source = GeneratedLevelSource(newGenerator = { ValidatedLevelGenerator(seed = seed.toLong()) })
                    val start = System.nanoTime()
                    val level = checkNotNull(source.load(requested.id))
                    val millis = (System.nanoTime() - start) / 1_000_000.0
                    assertEquals(requested.tileCount, level.board.tiles.size)
                    rows += "${requested.id},$seed,${level.board.tiles.size},$millis"
                }
            }
            File(context.getExternalFilesDir(null), "issue48-generation.csv").writeText(rows.joinToString("\n"))
        }

    @Test
    fun sensorDeliveryFrequencyIsRecordedAndUnregisterStopsDispatch() {
        val gateway = AndroidSensorGateway(context)
        val counts = DeadlineSensorType.entries.associateWith { AtomicInteger() }
        val first = DeadlineSensorType.entries.associateWith { AtomicLong() }
        val last = DeadlineSensorType.entries.associateWith { AtomicLong() }
        val listener =
            SensorSampleListener { sample ->
                counts.getValue(sample.type).incrementAndGet()
                first.getValue(sample.type).compareAndSet(0, sample.timestampNanos)
                last.getValue(sample.type).set(sample.timestampNanos)
            }
        val registered = DeadlineSensorType.entries.associateWith { gateway.register(it, listener) }
        try {
            Thread.sleep(3_000)
        } finally {
            gateway.unregister(listener)
        }
        Thread.sleep(200) // Drain an already-delivered callback before checking the stopped state.
        val stopped = counts.mapValues { it.value.get() }
        Thread.sleep(300)
        assertEquals(stopped, counts.mapValues { it.value.get() })
        val rows =
            DeadlineSensorType.entries.map { type ->
                val count = stopped.getValue(type)
                val span = last.getValue(type).get() - first.getValue(type).get()
                val hz = if (count > 1 && span > 0) (count - 1) * 1_000_000_000.0 / span else 0.0
                "$type,${registered.getValue(type)},$count,$hz"
            }
        File(
            context.getExternalFilesDir(null),
            "issue48-sensors.csv",
        ).writeText("sensor,registered,samples,observed_hz\n" + rows.joinToString("\n"))
    }
}
