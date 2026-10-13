package com.comp90018.deadline.macrobenchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class GameBenchmarks {
    @get:Rule val benchmark = MacrobenchmarkRule()
    private val iterations get() = InstrumentationRegistry.getArguments().getString("qaBenchmarkIterations")?.toInt() ?: 5

    @Before
    fun allowIsolatedTargetNetwork() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
            wakeUp()
            executeShellCommand("wm dismiss-keyguard")
            executeShellCommand("cmd deviceidle tempwhitelist -d 1800000 $PACKAGE")
        }
    }

    @After
    fun restoreNetworkExemption() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("cmd deviceidle tempwhitelist -r $PACKAGE")
    }

    @Test
    fun coldStartup() =
        benchmark.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Full(),
            startupMode = StartupMode.COLD,
            iterations = iterations,
            setupBlock = { pressHome() },
        ) { startActivityAndWait() }

    @Test
    fun generatedLevel6Interactions() = frames("level_6")

    @Test
    fun generatedLevel1Interactions() = frames("level_1")

    @Test
    fun generatedLevel6HudTicks() = frames("level_6", idleOnly = true)

    private fun frames(
        levelId: String,
        idleOnly: Boolean = false,
    ) {
        val oldIdle = Configurator.getInstance().waitForIdleTimeout
        Configurator.getInstance().waitForIdleTimeout = 0
        try {
            benchmark.measureRepeated(
                packageName = PACKAGE,
                metrics =
                    listOf(
                        FrameTimingMetric(),
                        TraceSectionMetric(
                            "com.comp90018.deadline.feature.game.components.GameBoard (%",
                            TraceSectionMetric.Mode.Count,
                            "GameBoardComposition",
                        ),
                        TraceSectionMetric(
                            "com.comp90018.deadline.feature.game.components.TileView (%",
                            TraceSectionMetric.Mode.Count,
                            "TileComposition",
                        ),
                    ),
                compilationMode = CompilationMode.Full(),
                startupMode = StartupMode.WARM,
                iterations = iterations,
                setupBlock = {
                    startActivityAndWait(
                        Intent().setComponent(ComponentName(PACKAGE, "$PACKAGE_BASE.benchmark.BenchmarkGameActivity"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            .putExtra("levelId", levelId)
                            .putExtra("qaRunLabel", InstrumentationRegistry.getArguments().getString("qaRunLabel") ?: "smoke"),
                    )
                    check(device.wait(Until.hasObject(By.res("game_board")), 15_000))
                },
            ) {
                if (idleOnly) {
                    Thread.sleep(2_000)
                } else {
                    repeat(5) { selectAndUndo() }
                    device.findObject(By.text("Restart"))?.click()
                }
            }
        } finally {
            Configurator.getInstance().waitForIdleTimeout = oldIdle
        }
    }

    private fun MacrobenchmarkScope.selectAndUndo() {
        val tile =
            device.findObjects(By.res(Pattern.compile("tile_.*")).enabled(true))
                .firstOrNull { !it.visibleBounds.isEmpty }
        assertNotNull("A generated board must expose a visible selectable tile", tile)
        tile!!.click()
        // Compose exposes the text child as enabled even when its clickable parent is disabled.
        check(device.wait(Until.hasObject(By.hasDescendant(By.text("Undo")).clickable(true).enabled(true)), 5_000))
        device.findObject(By.hasDescendant(By.text("Undo")).clickable(true)).click()
        check(device.wait(Until.hasObject(By.hasDescendant(By.text("Undo")).clickable(true).enabled(false)), 5_000))
    }

    private companion object {
        const val PACKAGE_BASE = "com.comp90018.deadline"
        const val PACKAGE = "$PACKAGE_BASE.emulator"
    }
}
