package com.yagay.ListCleaner.ui

import androidx.compose.ui.state.ToggleableState
import org.junit.Assert.assertEquals
import org.junit.Test

class ListSelectionStateTest {
    @Test fun emptyGroupIsNotSelected() {
        assertEquals(ToggleableState.Off, listSelectionState(0, 0))
    }

    @Test fun noSelectedItems() {
        assertEquals(ToggleableState.Off, listSelectionState(0, 4))
    }

    @Test fun partlySelectedItems() {
        assertEquals(ToggleableState.Indeterminate, listSelectionState(2, 4))
    }

    @Test fun allSelectedItems() {
        assertEquals(ToggleableState.On, listSelectionState(4, 4))
    }

    @Test fun nonEditableGroupIsNotSelected() {
        assertEquals(ToggleableState.Off, listSelectionState(0, -1))
    }
}
