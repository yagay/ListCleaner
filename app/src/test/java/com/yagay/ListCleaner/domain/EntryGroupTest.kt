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

        assertEquals(7, EntryGroup.entries.size)
        assertEquals(31, expected.size)
        assertEquals(expected.size, grouped.size)
        assertEquals(expected.toSet(), grouped.toSet())
        assertFalse(grouped.contains(IntentKind.LAUNCHER_SHORTCUT))
        assertFalse(grouped.contains(IntentKind.CREDENTIAL_PROVIDER))
        assertFalse(IntentKind.CREDENTIAL_PROVIDER.isSelectableEntryKind())
    }

    @Test
    fun currentKindsAreGroupedByTheirUserFacingPurpose() {
        val expected = mapOf(
            EntryGroup.SHARE to setOf(
                IntentKind.SHARE, IntentKind.SHARE_MULTIPLE, IntentKind.DIRECT_SHARE, IntentKind.SEND_TO
            ),
            EntryGroup.OPEN to setOf(
                IntentKind.OPEN, IntentKind.BROWSER, IntentKind.DEEP_LINK, IntentKind.DIAL
            ),
            EntryGroup.FILES to setOf(
                IntentKind.GET_CONTENT, IntentKind.OPEN_DOCUMENT, IntentKind.CREATE_DOCUMENT,
                IntentKind.CAPTURE_IMAGE, IntentKind.CAPTURE_VIDEO, IntentKind.RECORD_AUDIO,
                IntentKind.DOCUMENT_PROVIDER, IntentKind.DOCUMENT_ROOT, IntentKind.PHOTO_SOURCE
            ),
            EntryGroup.DESKTOP to setOf(
                IntentKind.HOME, IntentKind.ASSISTANT, IntentKind.SHORTCUT_ITEM
            ),
            EntryGroup.INPUT to setOf(
                IntentKind.PROCESS_TEXT, IntentKind.INPUT_METHOD, IntentKind.AUTOFILL
            ),
            EntryGroup.SYSTEM to setOf(
                IntentKind.NOTIFICATION_LISTENER, IntentKind.ACCESSIBILITY,
                IntentKind.VPN, IntentKind.PRINT, IntentKind.NFC_HCE, IntentKind.CALL_SCREENING
            ),
            EntryGroup.PERSONALIZATION to setOf(IntentKind.WALLPAPER, IntentKind.DREAM),
        )
        assertEquals(EntryGroup.entries.toSet(), expected.keys)
        expected.forEach { (group, kinds) ->
            assertEquals("Incorrect candidates in $group", kinds, group.kinds().toSet())
            kinds.forEach { kind -> assertEquals(group, kind.entryGroup()) }
        }
        // Retired shortcut IDs can still be deserialized into their former category.
        assertEquals(EntryGroup.DESKTOP, IntentKind.LAUNCHER_SHORTCUT.entryGroup())
    }

    @Test
    fun regroupingDoesNotRemoveSystemEntrySafetyWarnings() {
        val systemManaged = setOf(
            IntentKind.HOME, IntentKind.DOCUMENT_PROVIDER, IntentKind.DOCUMENT_ROOT,
            IntentKind.PHOTO_SOURCE, IntentKind.INPUT_METHOD, IntentKind.AUTOFILL,
            IntentKind.NOTIFICATION_LISTENER,
            IntentKind.ACCESSIBILITY, IntentKind.VPN, IntentKind.PRINT,
            IntentKind.WALLPAPER, IntentKind.DREAM, IntentKind.NFC_HCE,
            IntentKind.CALL_SCREENING
        )
        IntentKind.entries.forEach { kind ->
            assertEquals(kind.name, kind in systemManaged, kind.requiresSystemEntrySafetyNote())
        }
        assertFalse(IntentKind.CREDENTIAL_PROVIDER.requiresSystemEntrySafetyNote())
        assertFalse(IntentKind.ASSISTANT.requiresSystemEntrySafetyNote())
    }
}
