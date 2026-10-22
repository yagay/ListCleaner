package com.yagay.ListCleaner.data

import com.yagay.ListCleaner.domain.IntentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactiveScanOptionsTest {
    @Test fun defaultScansOnlyDirectShareWhenCatalogScans() {
        val options = ProactiveScanOptions()
        assertEquals(setOf(IntentKind.DIRECT_SHARE), options.requestedKinds)
        assertTrue(options.mayQuery(false))
    }

    @Test fun manualRequiresExplicitRequestAndOffNeverQueries() {
        val manual = ProactiveScanOptions(trigger = ProactiveTrigger.MANUAL_ONLY)
        assertFalse(manual.mayQuery(false))
        assertTrue(manual.mayQuery(true))
        val off = manual.copy(trigger = ProactiveTrigger.OFF)
        assertFalse(off.mayQuery(false))
        assertFalse(off.mayQuery(true))
    }

    @Test fun independentCategorySwitchesAndCacheBounds() {
        val shortcuts = ProactiveScanOptions(includeDirectShare = false, includeShortcutItems = true)
        assertEquals(setOf(IntentKind.SHORTCUT_ITEM), shortcuts.requestedKinds)
        assertTrue(shortcuts.mayQuery(false))
        assertFalse(shortcuts.copy(includeShortcutItems = false).mayQuery(true))
        assertEquals(60_000L, shortcuts.copy(cacheMinutes = -10).cacheMillis)
        assertEquals(3_600_000L, shortcuts.copy(cacheMinutes = 600).cacheMillis)
    }
}
