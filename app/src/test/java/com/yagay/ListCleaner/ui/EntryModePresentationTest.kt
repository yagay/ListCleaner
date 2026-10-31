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
