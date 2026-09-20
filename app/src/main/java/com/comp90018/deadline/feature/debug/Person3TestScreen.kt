package com.comp90018.deadline.feature.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import com.comp90018.deadline.domain.game.shuffle.BoardShuffler
import com.comp90018.deadline.domain.game.shuffle.ShuffleSlot
import com.comp90018.deadline.feature.game.GameSensorActions
import com.comp90018.deadline.feature.game.GameSensorBinder
import com.comp90018.deadline.feature.game.tiltPeek
import com.comp90018.deadline.sensor.AndroidSensorGateway
import com.comp90018.deadline.sensor.haptic.GameHaptic
import com.comp90018.deadline.sensor.haptic.HapticFeedbackManager

@Composable
fun Person3TestScreen(
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val gateway = remember {
        AndroidSensorGateway(context.applicationContext)
    }

    val haptics = remember {
        HapticFeedbackManager(context.applicationContext)
    }

    val boardShuffler = remember {
        BoardShuffler()
    }

    var board by remember {
        mutableStateOf(
            listOf(
                ShuffleSlot("slot-1", "Book"),
                ShuffleSlot("slot-2", "Code"),
                ShuffleSlot("slot-3", "Coffee"),
                ShuffleSlot("slot-4", "Quiz"),
                ShuffleSlot("slot-5", "Laptop"),
                ShuffleSlot("slot-6", "Deadline"),
            )
        )
    }

    var shakeCount by remember {
        mutableStateOf(0)
    }

    var shuffleCount by remember {
        mutableStateOf(0)
    }

    var peekAmount by remember {
        mutableStateOf(0f)
    }

    var lastEvent by remember {
        mutableStateOf("None")
    }

    var lastHaptic by remember {
        mutableStateOf("None")
    }

    /*
     * #25 + #27
     *
     * These callbacks represent the GameViewModel side of the
     * sensor integration.
     */
    val sensorActions = remember {
        object : GameSensorActions {

            override fun onShuffleRequested() {
                shakeCount += 1
                shuffleCount += 1

                board = boardShuffler.shuffle(board)

                lastEvent = "Shake detected -> board shuffled"
            }

            override fun onPeekChanged(amount: Float) {
                peekAmount = amount
                lastEvent = "Tilt changed"
            }
        }
    }

    /*
     * #23 + #25 + #27 + #28
     *
     * GameSensorBinder connects:
     *
     * Accelerometer -> Shake -> Shuffle
     * Rotation Vector -> Tilt -> Peek UI
     * Shake -> Haptic
     */
    val sensorBinder = remember {
        GameSensorBinder(
            gateway = gateway,
            actions = sensorActions,
            haptics = haptics,
        )
    }

    /*
     * Attach sensors to Activity lifecycle.
     *
     * When the Activity starts:
     * sensorBinder.onStart()
     *
     * When Activity stops:
     * sensorBinder.onStop()
     */
    DisposableEffect(lifecycleOwner, sensorBinder) {

        lifecycleOwner.lifecycle.addObserver(sensorBinder)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(sensorBinder)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 50.dp,
                bottom = 40.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {

        Text(
            text = "Person 3 Feature Test",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Issues #23 / #25 / #27 / #28",
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        /*
         * ---------------------------------------------------------
         * ISSUE #23
         * Sensor Infrastructure
         * ---------------------------------------------------------
         */

        SectionTitle(
            text = "#23 Sensor Infrastructure"
        )

        StatusCard(
            title = "Accelerometer",
            value = if (sensorBinder.shakeSupported) {
                "SUPPORTED"
            } else {
                "NOT SUPPORTED"
            }
        )

        StatusCard(
            title = "Rotation Vector",
            value = if (sensorBinder.tiltSupported) {
                "SUPPORTED"
            } else {
                "NOT SUPPORTED"
            }
        )

        Text(
            text = "If both sensors show SUPPORTED and changing Virtual Sensors affects the values below, #23 is working.",
            style = MaterialTheme.typography.bodySmall,
        )

        /*
         * ---------------------------------------------------------
         * ISSUE #25
         * Shake-to-Shuffle
         * ---------------------------------------------------------
         */

        SectionTitle(
            text = "#25 Shake-to-Shuffle"
        )

        StatusCard(
            title = "Shake count",
            value = shakeCount.toString()
        )

        StatusCard(
            title = "Shuffle count",
            value = shuffleCount.toString()
        )

        Text(
            text = "Current board:",
            fontWeight = FontWeight.Bold,
        )

        BoardDisplay(
            values = board.map { it.value }
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                board = boardShuffler.shuffle(board)
                shuffleCount += 1
                lastEvent = "Manual shuffle"
            },
        ) {
            Text("Manual Shuffle")
        }

        Text(
            text = "For the real test, use the emulator Virtual Sensors or a physical phone. A detected shake should increase both Shake count and Shuffle count.",
            style = MaterialTheme.typography.bodySmall,
        )

        /*
         * ---------------------------------------------------------
         * ISSUE #27
         * Tilt-to-Peek
         * ---------------------------------------------------------
         */

        SectionTitle(
            text = "#27 Tilt-to-Peek"
        )

        StatusCard(
            title = "Peek amount",
            value = String.format("%.3f", peekAmount)
        )

        TiltDemo(
            peekAmount = peekAmount,
        )

        Text(
            text = "Tilt the virtual/physical device. Peek amount should change from 0.000 toward 1.000, and the lower card should become more visible and move.",
            style = MaterialTheme.typography.bodySmall,
        )

        /*
         * ---------------------------------------------------------
         * ISSUE #28
         * Haptic Feedback
         * ---------------------------------------------------------
         */

        SectionTitle(
            text = "#28 Haptic Feedback"
        )

        Text(
            text = "Last haptic: $lastHaptic",
            fontWeight = FontWeight.Bold,
        )

        HapticButton(
            text = "Tile Select Haptic"
        ) {
            haptics.perform(GameHaptic.TILE_SELECT)
            lastHaptic = "TILE_SELECT"
        }

        HapticButton(
            text = "Match Haptic"
        ) {
            haptics.perform(GameHaptic.MATCH)
            lastHaptic = "MATCH"
        }

        HapticButton(
            text = "Shuffle Haptic"
        ) {
            haptics.perform(GameHaptic.SHUFFLE)
            lastHaptic = "SHUFFLE"
        }

        HapticButton(
            text = "Success Haptic"
        ) {
            haptics.perform(GameHaptic.SUCCESS)
            lastHaptic = "SUCCESS"
        }

        HapticButton(
            text = "Failure Haptic"
        ) {
            haptics.perform(GameHaptic.FAILURE)
            lastHaptic = "FAILURE"
        }

        Text(
            text = "The emulator can verify that the calls execute, but use a physical Android phone to feel the vibration patterns.",
            style = MaterialTheme.typography.bodySmall,
        )

        /*
         * Debug information.
         */

        SectionTitle(
            text = "Debug"
        )

        StatusCard(
            title = "Last event",
            value = lastEvent
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
) {
    Spacer(
        modifier = Modifier.height(8.dp)
    )

    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
            )

            Text(
                text = value,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun BoardDisplay(
    values: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {

            values.forEachIndexed { index, value ->

                Text(
                    text = "${index + 1}. $value",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun TiltDemo(
    peekAmount: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        contentAlignment = Alignment.Center,
    ) {

        /*
         * Bottom layer.
         *
         * This one uses our #27 tiltPeek modifier.
         */
        Card(
            modifier = Modifier
                .size(
                    width = 220.dp,
                    height = 110.dp,
                )
                .tiltPeek(
                    layerIndex = 0,
                    topLayerIndex = 1,
                    peekAmount = peekAmount,
                ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            ),
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "LOWER LAYER\nPeek me!",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        /*
         * Top layer.
         */
        Card(
            modifier = Modifier
                .size(
                    width = 220.dp,
                    height = 110.dp,
                ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            ),
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "TOP LAYER",
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun HapticButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Text(text)
    }
}
