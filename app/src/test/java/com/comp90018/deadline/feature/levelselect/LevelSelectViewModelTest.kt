package com.comp90018.deadline.feature.levelselect

import com.comp90018.deadline.domain.level.model.FixedLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelSelectViewModelTest {

    @Test
    fun listsFixedLevelsInOrder() {
        val levels = LevelSelectViewModel().uiState.value.levels

        assertEquals(FixedLevels.ALL_LEVELS.map { it.id }, levels.map { it.id })
        assertEquals(FixedLevels.ALL_LEVELS.map { it.name }, levels.map { it.name })
    }

    @Test
    fun describesTileAndLayerCounts() {
        val level3 = LevelSelectViewModel().uiState.value.levels.single { it.id == FixedLevels.LEVEL_3.id }

        assertEquals(9, level3.tileCount)
        assertEquals(2, level3.layerCount)
    }

    @Test
    fun levelsAreUnlockedWithoutBestTimeByDefault() {
        val levels = LevelSelectViewModel().uiState.value.levels

        assertTrue(levels.none { it.isLocked })
        assertTrue(levels.all { it.bestTimeSeconds == null })
    }

    @Test
    fun usesSuppliedProgress() {
        val viewModel = LevelSelectViewModel(
            isUnlocked = { it.id != FixedLevels.LEVEL_3.id },
            bestTimeSeconds = { if (it.id == FixedLevels.LEVEL_1.id) 42L else null }
        )
        val levels = viewModel.uiState.value.levels.associateBy { it.id }

        assertFalse(levels.getValue(FixedLevels.LEVEL_1.id).isLocked)
        assertTrue(levels.getValue(FixedLevels.LEVEL_3.id).isLocked)
        assertEquals(42L, levels.getValue(FixedLevels.LEVEL_1.id).bestTimeSeconds)
        assertNull(levels.getValue(FixedLevels.LEVEL_2.id).bestTimeSeconds)
    }
}
