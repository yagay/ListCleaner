package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prevent secondary Entry selectors disappearing when new surfaces are added. */
class EntrySubtypeSelectorTest {
    @Test fun directShareUsesSameSubtypePickerAsOpenWith() {
        assertTrue(hasEntrySubtypeSelector(IntentKind.DIRECT_SHARE))
        assertTrue(hasEntrySubtypeSelector(IntentKind.OPEN))
        assertTrue(hasEntrySubtypeSelector(IntentKind.DEEP_LINK))
        assertFalse(hasEntrySubtypeSelector(IntentKind.SHORTCUT_ITEM))
        assertFalse(hasEntrySubtypeSelector(IntentKind.SHARE))
        assertFalse(hasEntrySubtypeSelector(null))
    }
}
