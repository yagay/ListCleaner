package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.domain.IntentKind
import com.yagay.ListCleaner.domain.OpenPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryContextTest {
    @Test
    fun `changing entry kind clears incompatible subcontexts`() {
        val open = EntryContext(kind = IntentKind.OPEN, openPreset = OpenPreset.PDF)
        val share = open.withKind(IntentKind.SHARE)
        assertEquals(IntentKind.SHARE, share.kind)
        assertNull(share.openPreset)
        assertNull(share.browserHost)
    }

    @Test
    fun `same compatible kind preserves its subcontext`() {
        val deepLink = EntryContext(kind = IntentKind.DEEP_LINK, browserHost = "example.com")
        assertEquals("example.com", deepLink.withKind(IntentKind.DEEP_LINK).browserHost)
    }

    @Test
    fun `switching between open and deep link clears the other subtype`() {
        val open = EntryContext(kind = IntentKind.OPEN, openPreset = OpenPreset.IMAGE)
        val deepLink = open.withKind(IntentKind.DEEP_LINK).copy(browserHost = "example.org")
        assertNull(deepLink.openPreset)
        assertEquals("example.org", deepLink.browserHost)
        val openAgain = deepLink.withKind(IntentKind.OPEN)
        assertNull(openAgain.browserHost)
    }
}
