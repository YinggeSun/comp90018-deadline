package com.comp90018.deadline.integration

import android.net.ConnectivityManager
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.data.FirebaseEmulator
import com.comp90018.deadline.data.remote.firebase.ProgressFields
import com.comp90018.deadline.data.sync.NetworkWatcher
import com.comp90018.deadline.data.sync.SyncResult
import com.comp90018.deadline.domain.level.model.FixedLevels
import com.comp90018.deadline.feature.game.components.GAME_TIMER_TAG
import com.comp90018.deadline.feature.game.components.tileTestTag
import com.comp90018.deadline.navigation.AppNavHost
import com.comp90018.deadline.testing.TestLevels
import com.comp90018.deadline.testing.UiScreenshot
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Device network loss, real navigation/engine/completion recorder and automatic reconnect sync. */
class OfflineGameAcceptanceTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<DeadlineApp>()
    private val container get() = app.container

    private fun shell(command: String) =
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }

    private fun click(label: String) {
        rule.onNodeWithText(label).performScrollTo().performClick()
        rule.waitForIdle()
    }

    @Test
    fun onlineToOfflineWinReconnectAutomaticallySynchronizesProgressAndQueuedScore() {
        assertTrue("Required Firebase emulators unavailable", FirebaseEmulator.isRunning())
        val originalAirplane = Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
        val originalWifi = Settings.Global.getInt(app.contentResolver, Settings.Global.WIFI_ON, 0)
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        val originalSettings = runBlocking { container.settingsRepository.settings.first() }
        var networkRegistration: AutoCloseable? = null
        try {
            // Each run owns a fresh guest so earlier UI tests/cached scores cannot satisfy the assertions.
            val uid =
                runBlocking {
                    container.authRepository.logout().getOrThrow()
                    container.authRepository.ensureSignedIn().getOrThrow()
                }
            val db = FirebaseFirestore.getInstance()
            assertEquals("demo-deadline", db.app.options.projectId)
            runBlocking {
                container.settingsRepository.update { it.copy(nickname = "Offline UI QA") }
                withTimeout(60_000) {
                    while (container.syncAccountProgress() !is SyncResult.Synced) delay(250)
                }
            }
            val before = runBlocking { container.progressRepository.progress.first() }
            // Observe the actual production watcher as well as cloud state: a delayed win task
            // could otherwise upload successfully even if reconnect notifications were broken.
            val networkEvents = AtomicInteger()
            networkRegistration = NetworkWatcher(app).start { networkEvents.incrementAndGet() }
            rule.waitUntil(15_000) { networkEvents.get() >= 1 }
            rule.setContent {
                DeadlineTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                        AppNavHost(modifier = Modifier.padding(padding), levelSource = TestLevels.source)
                    }
                }
            }

            shell("cmd connectivity airplane-mode enable")
            shell("svc wifi disable")
            rule.waitUntil(15_000) { connectivity.activeNetwork == null }
            val offlineNetworkEvents = networkEvents.get()
            click("Play")
            click("Level 1")
            UiScreenshot.capture(rule, "offline-game")
            // This six-tile fixture is smaller than production's 18-tile Level 1.
            // Observe the game clock, not a host sleep: the server requires at least 4.5s.
            rule.waitUntil(15_000) {
                val description =
                    rule.onNodeWithTag(GAME_TIMER_TAG).fetchSemanticsNode()
                        .config[SemanticsProperties.ContentDescription].joinToString()
                val clock = Regex("(\\d+):(\\d{2})").find(description)
                clock != null && clock.groupValues[1].toLong() * 60 + clock.groupValues[2].toLong() >= 5
            }
            FixedLevels.LEVEL_1.board.tiles.forEach { tile ->
                rule.onNodeWithTag(tileTestTag(tile.id)).performScrollTo().performClick()
                rule.waitForIdle()
            }
            rule.onNodeWithText("Deadline met!").assertExists()
            rule.waitUntil(15_000) {
                runBlocking { container.progressRepository.progress.first().lastModifiedMillis > before.lastModifiedMillis }
            }
            val saved = runBlocking { container.progressRepository.progress.first() }
            assertTrue("Offline win must unlock the next level", saved.highestUnlockedWeek >= 3)
            assertTrue(saved.completedLevelIds.contains("level_1"))
            UiScreenshot.capture(rule, "offline-win")
            assertEquals(uid, FirebaseAuth.getInstance().currentUser?.uid)

            assertEquals("No connection notifications expected during offline play", offlineNetworkEvents, networkEvents.get())
            shell("cmd connectivity airplane-mode disable")
            shell("svc wifi enable")
            rule.waitUntil(15_000) { connectivity.activeNetwork != null }
            rule.waitUntil(15_000) { networkEvents.get() > offlineNetworkEvents }
            // No manual sync invocation here: exercise NetworkWatcher -> AppContainer wiring.
            runBlocking {
                withTimeout(60_000) {
                    while (true) {
                        val cloud = runCatching { db.document("progress/$uid").get(Source.SERVER).await() }.getOrNull()
                        if (cloud != null && ProgressFields.read(cloud::get) == saved) break
                        delay(250)
                    }
                    db.waitForPendingWrites().await()
                    val entry = db.document("leaderboard/level_1/entries/$uid").get(Source.SERVER).await()
                    assertTrue("Queued score must reach the server", entry.exists())
                    assertEquals(saved.bestFor("level_1")?.timeMillis, entry.getLong("timeMillis"))
                }
            }
            click("Home")
            click("Leaderboard")
            click("Level 1")
            rule.waitUntil(15_000) {
                rule.onAllNodes(
                    androidx.compose.ui.test.hasContentDescription("Offline UI QA", substring = true),
                ).fetchSemanticsNodes().isNotEmpty()
            }
            UiScreenshot.capture(rule, "reconnected-leaderboard")
            assertEquals(uid, FirebaseAuth.getInstance().currentUser?.uid)
        } finally {
            try {
                networkRegistration?.close()
            } finally {
                try {
                    shell("cmd connectivity airplane-mode ${if (originalAirplane == 0) "disable" else "enable"}")
                } finally {
                    try {
                        shell("svc wifi ${if (originalWifi == 1) "enable" else "disable"}")
                    } finally {
                        runBlocking { container.settingsRepository.update { originalSettings } }
                    }
                }
            }
        }
    }
}
