package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.data.CleanupKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentCategoryFilterPolicyTest {
    @Test fun allCategoryAndEverySupportedRootKindAreAvailable() {
        assertEquals(null, ComponentCategoryFilterPolicy.choices.first())
        assertEquals(CleanupKind.entries.toList(), ComponentCategoryFilterPolicy.choices.drop(1))
        assertEquals(6, ComponentCategoryFilterPolicy.choices.size)
        assertEquals(6, ComponentCategoryFilterPolicy.choices.distinct().size)
    }

    @Test fun allShowsEveryKindAndEachCategoryFiltersIndependently() {
        CleanupKind.entries.forEach { candidate ->
            assertTrue(ComponentCategoryFilterPolicy.matches(null, candidate))
            CleanupKind.entries.forEach { selected ->
                assertEquals(selected == candidate,
                    ComponentCategoryFilterPolicy.matches(selected, candidate))
            }
        }
    }

    @Test fun chosenCategoryNeverChangesTheRootComponentKind() {
        assertFalse(ComponentCategoryFilterPolicy.matches(
            CleanupKind.WIDGET, CleanupKind.LAUNCHER
        ))
        assertTrue(ComponentCategoryFilterPolicy.matches(
            CleanupKind.TILE, CleanupKind.TILE
        ))
    }
}
