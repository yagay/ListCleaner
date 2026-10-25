package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.EntryGroup
import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryCategorySelectionPolicyTest {
    @Test fun onlyAllIsSelectedWhenNoSubcategoryIsActive() {
        assertTrue(EntryCategorySelectionPolicy.allSelected(null))
        assertFalse(EntryCategorySelectionPolicy.allSelected(IntentKind.OPEN))
        EntryGroup.entries.forEach { group ->
            assertNull(EntryCategorySelectionPolicy.selectedKind(group, null))
        }
    }

    @Test fun selectedSubcategoryHighlightsExactlyItsOwnGroup() {
        assertEquals(
            IntentKind.OPEN,
            EntryCategorySelectionPolicy.selectedKind(EntryGroup.OPEN, IntentKind.OPEN)
        )
        assertNull(EntryCategorySelectionPolicy.selectedKind(EntryGroup.SHARE, IntentKind.OPEN))
        assertNull(EntryCategorySelectionPolicy.selectedKind(EntryGroup.FILES, IntentKind.OPEN))
        assertEquals(
            IntentKind.DIRECT_SHARE,
            EntryCategorySelectionPolicy.selectedKind(EntryGroup.SHARE, IntentKind.DIRECT_SHARE)
        )
        assertEquals(IntentKind.HOME,
            EntryCategorySelectionPolicy.selectedKind(EntryGroup.DESKTOP, IntentKind.HOME))
        assertNull(EntryCategorySelectionPolicy.selectedKind(EntryGroup.SYSTEM, IntentKind.HOME))
        assertEquals(IntentKind.DOCUMENT_PROVIDER,
            EntryCategorySelectionPolicy.selectedKind(EntryGroup.FILES, IntentKind.DOCUMENT_PROVIDER))
        assertEquals(IntentKind.NFC_HCE,
            EntryCategorySelectionPolicy.selectedKind(EntryGroup.SYSTEM, IntentKind.NFC_HCE))
    }

    @Test fun menuOnlyHighlightsAvailableSubcategories() {
        assertNull(EntryCategorySelectionPolicy.selectedKind(
            EntryGroup.OPEN, IntentKind.OPEN, setOf(IntentKind.DIAL)
        ))
        assertEquals(IntentKind.OPEN, EntryCategorySelectionPolicy.selectedKind(
            EntryGroup.OPEN, IntentKind.OPEN, setOf(IntentKind.OPEN, IntentKind.DIAL)
        ))
    }
}
