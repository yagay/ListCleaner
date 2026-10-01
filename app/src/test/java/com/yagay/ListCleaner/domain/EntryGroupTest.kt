package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryGroupTest {
    @Test
    fun selectableEntryKindsBelongToExactlyOneTopLevelGroup() {
        val grouped = EntryGroup.entries.flatMap { it.kinds() }
        val expected = IntentKind.entries.filter { it.isSelectableEntryKind() }

        assertEquals(expected.size, grouped.size)
        assertEquals(expected.toSet(), grouped.toSet())
        assertFalse(grouped.contains(IntentKind.LAUNCHER_SHORTCUT))
    }

    @Test
    fun expectedSurfacesStayInStableGroups() {
        assertEquals(EntryGroup.SHARE, IntentKind.DIRECT_SHARE.entryGroup())
        assertEquals(EntryGroup.OPEN, IntentKind.OPEN_DOCUMENT.entryGroup())
        assertEquals(EntryGroup.OPEN, IntentKind.ASSISTANT.entryGroup())
        assertEquals(EntryGroup.ADVANCED, IntentKind.HOME.entryGroup())
        assertEquals(EntryGroup.DESKTOP, IntentKind.SHORTCUT_ITEM.entryGroup())
        assertEquals(EntryGroup.ADVANCED, IntentKind.CREDENTIAL_PROVIDER.entryGroup())
        assertTrue(EntryGroup.ADVANCED.kinds().contains(IntentKind.HOME))
        assertTrue(EntryGroup.ADVANCED.kinds().contains(IntentKind.NFC_HCE))
    }
}
