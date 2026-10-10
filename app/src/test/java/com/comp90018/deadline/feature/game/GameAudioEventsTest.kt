package com.comp90018.deadline.feature.game

import com.comp90018.deadline.core.audio.GameAudio
import com.comp90018.deadline.core.audio.GameAudioEvent
import com.comp90018.deadline.domain.game.engine.DefaultGameEngine
import com.comp90018.deadline.domain.game.model.Board
import com.comp90018.deadline.domain.game.model.Tile
import com.comp90018.deadline.domain.game.model.TilePosition
import com.comp90018.deadline.domain.game.model.TileType
import com.comp90018.deadline.domain.game.stress.StressConfig
import com.comp90018.deadline.domain.level.model.FixedLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameAudioEventsTest {
    private val events = mutableListOf<GameAudioEvent>()
    private val audio =
        object : GameAudio {
            override fun setForeground(
                session: Any,
                foreground: Boolean,
                gameplayActive: Boolean,
            ) = Unit

            override fun play(
                session: Any,
                event: GameAudioEvent,
            ) {
                events.add(event)
            }

            override fun release(session: Any) = Unit
        }

    @Test
    fun acceptedSelectionEmitsOneClickAndNoMatch() {
        val vm = GameViewModel(FixedLevels.LEVEL_3.id, audio = audio)
        vm.onEvent(GameUiEvent.TileTapped("level_3_laptop_1"))
        assertEquals(listOf(GameAudioEvent.TILE_CLICK), events)
        vm.onEvent(GameUiEvent.TileTapped("level_3_laptop_1"))
        vm.onEvent(GameUiEvent.TileTapped("missing"))
        assertEquals(listOf(GameAudioEvent.TILE_CLICK), events)
    }

    @Test
    fun blockedAndUnknownSelectionsEmitNothing() {
        val vm = GameViewModel(FixedLevels.LEVEL_3.id, audio = audio)
        vm.onEvent(GameUiEvent.TileTapped("level_3_book_1"))
        vm.onEvent(GameUiEvent.TileTapped("missing"))
        assertTrue(events.isEmpty())
    }

    @Test
    fun resolvedTripleOfEverySupportedTypeEmitsExactlyOneMatch() {
        // Type-independent: also covers Coffee, and will cover Music when the engine adds that type.
        TileType.entries.forEach { type ->
            events.clear()
            val level =
                FixedLevels.SAMPLE_LEVEL.copy(
                    board = Board(List(3) { Tile("$it", type, TilePosition(0, it * 2)) }),
                )
            val vm = GameViewModel(level.id, findLevel = { level }, audio = audio)
            repeat(3) { vm.onEvent(GameUiEvent.TileTapped("$it")) }
            assertEquals(
                listOf(
                    GameAudioEvent.TILE_CLICK,
                    GameAudioEvent.TILE_CLICK,
                    GameAudioEvent.TILE_CLICK,
                    GameAudioEvent.MATCH,
                ),
                events,
            )
            vm.onEvent(GameUiEvent.TileTapped("0"))
            assertEquals(4, events.size)
        }
    }

    @Test
    fun maximumStressUsesConfiguredEdgeAndRearmsAfterCoffeeRecovery() {
        val types =
            listOf(
                TileType.BOOK,
                TileType.COFFEE,
                TileType.COFFEE,
                TileType.COFFEE,
                TileType.BOOK,
                TileType.BOOK,
                TileType.LAPTOP,
                TileType.LAPTOP,
                TileType.LAPTOP,
            )
        val level =
            FixedLevels.LEVEL_1.copy(
                board = Board(types.mapIndexed { index, type -> Tile("$index", type, TilePosition(0, index * 2)) }),
            )
        val config =
            StressConfig(
                maximum = 10,
                baseRate = 5,
                rateGrowthPerWeek = 0,
                coffeeRecoveryBase = 10,
                coffeeRecoveryDeclinePerWeek = 0,
            )
        val vm =
            GameViewModel(
                level.id,
                findLevel = { level },
                audio = audio,
                createEngine = { DefaultGameEngine(it, stressConfig = config, initialStress = 5) },
            )
        vm.onEvent(GameUiEvent.TileTapped("0")) // 5 -> 10
        assertEquals(1, events.count { it == GameAudioEvent.STRESS_MAX })
        vm.onEvent(GameUiEvent.TileTapped("1")) // remains 10
        vm.onEvent(GameUiEvent.TileTapped("2")) // remains 10
        assertEquals(1, events.count { it == GameAudioEvent.STRESS_MAX })
        vm.onEvent(GameUiEvent.TileTapped("3")) // Coffee -> 0
        vm.onEvent(GameUiEvent.TileTapped("4")) // 0 -> 5
        vm.onEvent(GameUiEvent.TileTapped("5")) // 5 -> 10
        assertEquals(2, events.count { it == GameAudioEvent.STRESS_MAX })
    }

    @Test
    fun winningSelectionKeepsClickAndMatchButSuppressesMaximumStressWarning() {
        val level =
            FixedLevels.SAMPLE_LEVEL.copy(
                board = Board(List(3) { Tile("$it", TileType.BOOK, TilePosition(0, it * 2)) }),
            )
        val config = StressConfig(maximum = 6, baseRate = 2, rateGrowthPerWeek = 0)
        val vm =
            GameViewModel(
                level.id,
                findLevel = { level },
                audio = audio,
                createEngine = { DefaultGameEngine(it, stressConfig = config) },
            )
        repeat(2) { vm.onEvent(GameUiEvent.TileTapped("$it")) }
        events.clear()
        vm.onEvent(GameUiEvent.TileTapped("2"))
        assertEquals(com.comp90018.deadline.domain.game.model.GameStatus.WON, vm.uiState.value.status)
        assertEquals(listOf(GameAudioEvent.TILE_CLICK, GameAudioEvent.MATCH), events)
    }

    @Test
    fun losingSelectionKeepsClickButSuppressesMaximumStressWarning() {
        val types =
            listOf(
                TileType.BOOK,
                TileType.BOOK,
                TileType.COFFEE,
                TileType.COFFEE,
                TileType.LAPTOP,
                TileType.LAPTOP,
                TileType.QUIZ,
                TileType.QUIZ,
                TileType.QUIZ,
            )
        val level =
            FixedLevels.LEVEL_1.copy(
                board = Board(types.mapIndexed { i, type -> Tile("$i", type, TilePosition(0, i * 2)) }),
            )
        val config = StressConfig(maximum = 14, baseRate = 2, rateGrowthPerWeek = 0)
        val vm =
            GameViewModel(
                level.id,
                findLevel = { level },
                audio = audio,
                createEngine = { DefaultGameEngine(it, stressConfig = config) },
            )
        repeat(6) { vm.onEvent(GameUiEvent.TileTapped("$it")) }
        events.clear()
        vm.onEvent(GameUiEvent.TileTapped("6"))
        assertEquals(com.comp90018.deadline.domain.game.model.GameStatus.LOST, vm.uiState.value.status)
        assertEquals(listOf(GameAudioEvent.TILE_CLICK), events)
    }

    @Test
    fun undoAndRestartDoNotEmitSelectionOrMatchEvents() {
        val vm = GameViewModel(FixedLevels.LEVEL_3.id, audio = audio)
        vm.onEvent(GameUiEvent.TileTapped("level_3_laptop_1"))
        events.clear()
        vm.undo()
        vm.restart()
        assertTrue(events.isEmpty())
    }
}
