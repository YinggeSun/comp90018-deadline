package com.comp90018.deadline.integration

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.domain.game.stress.StressConfig
import com.comp90018.deadline.domain.level.generator.GeneratedLevelSource
import com.comp90018.deadline.feature.game.GameScreen
import com.comp90018.deadline.feature.game.GameViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.ceil

class GameLifecycleClockTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun stressClockStopsInBackgroundAndResumesWithoutChargingThePause() {
        val level = runBlocking { checkNotNull(GeneratedLevelSource().load("level_6")) }
        val model = GameViewModel(level.id, findLevel = { level })
        rule.setContent { DeadlineTheme { GameScreen(level.id, {}, {}, model) } }
        rule.waitUntil(5_000) { model.uiState.value.stress > 0 }
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.waitForIdle()
        val paused = model.uiState.value.stress
        Thread.sleep(2_200)
        assertEquals(paused, model.uiState.value.stress)
        val resumedAt = System.nanoTime()
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitUntil(5_000) { model.uiState.value.stress > paused }
        assertTrue(
            "Resume must not apply the entire background interval",
            model.uiState.value.stress - paused <= ceil(
                StressConfig().accumulationPerSecond * (System.nanoTime() - resumedAt) / 1_000_000_000.0,
            ).toInt() + 1,
        )
    }
}
