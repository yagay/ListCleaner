package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.DisplayMode
import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryModePresentationTest {
    @Test fun selectedCategoryShowsItsOverrideNotTheGlobalMode() {
        assertEquals(
            DisplayMode.SHOW_SELECTED,
            currentEntryDisplayMode(
                IntentKind.OPEN,
                DisplayMode.HIDE_SELECTED,
                mapOf(IntentKind.OPEN to DisplayMode.SHOW_SELECTED)
            )
        )
    }

    @Test fun selectedCategoryInheritsDefaultWhenNoOverrideExists() {
        assertEquals(
            DisplayMode.SHOW_ALL,
            currentEntryDisplayMode(
                IntentKind.SHARE,
                DisplayMode.SHOW_ALL,
                mapOf(IntentKind.OPEN to DisplayMode.HIDE_SELECTED)
            )
        )
    }

    @Test fun allCategoriesWithDifferentModesShowMixedInsteadOfGlobal() {
        assertNull(currentEntryDisplayMode(
            null,
            DisplayMode.HIDE_SELECTED,
            mapOf(IntentKind.OPEN to DisplayMode.SHOW_SELECTED)
        ))
    }

    @Test fun allCategoriesWithMatchingOverridesShowTheirSharedMode() {
        assertEquals(
            DisplayMode.SHOW_SELECTED,
            currentEntryDisplayMode(
                null,
                DisplayMode.SHOW_SELECTED,
                mapOf(IntentKind.OPEN to DisplayMode.SHOW_SELECTED)
            )
        )
    }

    @Test fun allCategoriesWithoutOverridesShowTheGlobalMode() {
        assertEquals(
            DisplayMode.HIDE_SELECTED,
            currentEntryDisplayMode(null, DisplayMode.HIDE_SELECTED, emptyMap())
        )
    }

    @Test fun choosingAnotherModeDoesNotMutateTheSavedSelection() {
        val initial = EntryModeChangeDraft(DisplayMode.HIDE_SELECTED)
        val staged = initial.select(DisplayMode.SHOW_SELECTED)
        assertEquals(DisplayMode.HIDE_SELECTED, staged.original)
        assertEquals(DisplayMode.SHOW_SELECTED, staged.pending)
        assertEquals(true, staged.hasChanges)
        // Closing the dialog discards the staged value, retaining the old mode.
        assertEquals(DisplayMode.HIDE_SELECTED, initial.pending)
    }

    @Test fun selectingSameModeDoesNotRequireConfirmation() {
        val initial = EntryModeChangeDraft(DisplayMode.SHOW_SELECTED)
        assertEquals(false, initial.hasChanges)
        assertEquals(false, initial.select(DisplayMode.SHOW_SELECTED).hasChanges)
    }

    @Test fun choosingInheritGlobalRequiresConfirmationWhenOverrideExists() {
        val initial = EntryModeChangeDraft(DisplayMode.SHOW_ALL)
        val staged = initial.select(null)
        assertEquals(true, staged.hasChanges)
        assertEquals(null, staged.pending)
        assertEquals(DisplayMode.SHOW_ALL, staged.original)
    }

    @Test fun choosingExplicitOverrideFromInheritedModeRequiresConfirmation() {
        val initial = EntryModeChangeDraft(null)
        val staged = initial.select(DisplayMode.HIDE_SELECTED)
        assertEquals(true, staged.hasChanges)
        assertEquals(null, staged.original)
    }

    @Test fun retiredEntryKindsDoNotForceMixedState() {
        assertEquals(
            DisplayMode.HIDE_SELECTED,
            currentEntryDisplayMode(
                null,
                DisplayMode.HIDE_SELECTED,
                mapOf(IntentKind.CREDENTIAL_PROVIDER to DisplayMode.SHOW_ALL)
            )
        )
    }
}
