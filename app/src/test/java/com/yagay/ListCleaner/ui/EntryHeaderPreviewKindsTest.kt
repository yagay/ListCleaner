package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.isSelectableEntryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryHeaderPreviewKindsTest {
    @Test fun selectedEntryOpensItsSystemPreviewDirectly() {
        assertEquals(listOf(IntentKind.OPEN), entryHeaderPreviewKinds(IntentKind.OPEN))
        assertEquals(listOf(IntentKind.DIRECT_SHARE), entryHeaderPreviewKinds(IntentKind.DIRECT_SHARE))
    }

    @Test fun allEntryPicksWhichSystemListToPreview() {
        val kinds = entryHeaderPreviewKinds(null)
        assertTrue(kinds.size > 1)
        assertTrue(IntentKind.OPEN in kinds)
        assertTrue(IntentKind.DIRECT_SHARE in kinds)
        assertFalse(IntentKind.CREDENTIAL_PROVIDER in kinds)
        assertTrue(kinds.all { it.isSelectableEntryKind() })
        assertEquals(kinds.size, kinds.distinct().size)
    }
}
