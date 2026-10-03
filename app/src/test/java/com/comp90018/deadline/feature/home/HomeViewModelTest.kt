package com.comp90018.deadline.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeViewModelTest {

    @Test
    fun nothingToContinueByDefault() {
        assertNull(HomeViewModel().uiState.value.continueLevelId)
    }

    @Test
    fun exposesSuppliedContinueLevel() {
        assertEquals("level_2", HomeViewModel(continueLevelId = { "level_2" }).uiState.value.continueLevelId)
    }
}
