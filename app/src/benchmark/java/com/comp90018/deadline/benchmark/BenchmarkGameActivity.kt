package com.comp90018.deadline.benchmark

import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.comp90018.deadline.app.DeadlineApp
import com.comp90018.deadline.core.theme.DeadlineTheme
import com.comp90018.deadline.domain.level.generator.GeneratedLevelSource
import com.comp90018.deadline.domain.level.generator.ValidatedLevelGenerator
import com.comp90018.deadline.domain.level.model.SemesterLevel
import com.comp90018.deadline.feature.game.GameRoute
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Only in the isolated, non-debuggable benchmark APK; no production entry point is added. */
@OptIn(ExperimentalComposeApi::class, ExperimentalComposeUiApi::class)
class BenchmarkGameActivity : ComponentActivity() {
    private val lastTouchUp = AtomicLong()
    private val writer = Executors.newSingleThreadExecutor()
    private var lastSample = 0L
    private var runLabel = "smoke"

    override fun onStart() {
        super.onStart()
        (application as DeadlineApp).container.gameAudioManager.setAppForeground(true)
    }

    override fun onStop() {
        if (!isChangingConfigurations) (application as DeadlineApp).container.gameAudioManager.setAppForeground(false)
        super.onStop()
    }

    private val drawListener =
        ViewTreeObserver.OnDrawListener {
            val now = System.nanoTime()
            val touch = lastTouchUp.getAndSet(0)
            if (touch != 0L) record("feedback_next_draw", now, (now - touch) / 1_000_000.0)
            if (now - lastSample >= 1_000_000_000L) {
                lastSample = now
                // Global composition change count, not per-tile recompositions or an FPS metric.
                record("composition_change_count", now, Recomposer.runningRecomposers.value.sumOf { it.changeCount }.toDouble())
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val levelId = intent.getStringExtra("levelId") ?: "level_6"
        runLabel = intent.getStringExtra("qaRunLabel") ?: "smoke"
        require(runLabel.matches(Regex("[a-zA-Z0-9_-]+")))
        require(SemesterLevel.fromId(levelId) != null)
        val source = GeneratedLevelSource(newGenerator = { ValidatedLevelGenerator(seed = 48) })
        setContent {
            DeadlineTheme {
                Scaffold(modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) { padding ->
                    Box(modifier = Modifier.padding(padding)) {
                        GameRoute(levelId, source, onGameFinished = {}, onBack = ::finish)
                    }
                }
            }
        }
        window.decorView.viewTreeObserver.addOnDrawListener(drawListener)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) lastTouchUp.set(System.nanoTime())
        return super.dispatchTouchEvent(event)
    }

    private fun record(
        kind: String,
        timestamp: Long,
        value: Double,
    ) {
        writer.execute {
            File(getExternalFilesDir(null), "issue48-runtime-$runLabel.csv").appendText("$kind,$timestamp,$value\n")
        }
    }

    override fun onDestroy() {
        window.decorView.viewTreeObserver.removeOnDrawListener(drawListener)
        writer.shutdown()
        super.onDestroy()
    }
}
