package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryGroupTest {
    @Test
    fun everyEntryKindBelongsToExactlyOneTopLevelGroup() {
        val grouped = EntryGroup.entries.flatMap { it.kinds() }
        assertEquals(IntentKind.entries.size, grouped.size)
        assertEquals(IntentKind.entries.toSet(), grouped.toSet())
    }

    @Test
    fun expectedSurfacesStayInStableGroups() {
        assertEquals(EntryGroup.SHARE, IntentKind.DIRECT_SHARE.entryGroup())
        assertEquals(EntryGroup.OPEN, IntentKind.OPEN_DOCUMENT.entryGroup())
        assertEquals(EntryGroup.DESKTOP, IntentKind.SHORTCUT_ITEM.entryGroup())
        assertEquals(EntryGroup.ADVANCED, IntentKind.CREDENTIAL_PROVIDER.entryGroup())
        assertTrue(EntryGroup.ADVANCED.kinds().contains(IntentKind.NFC_HCE))
    }
}
