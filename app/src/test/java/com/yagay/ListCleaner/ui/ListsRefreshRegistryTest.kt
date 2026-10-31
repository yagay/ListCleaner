package com.yagay.ListCleaner.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ListsRefreshRegistryTest {
    @Test fun activeEditorRefreshIsTheOnlyOneInvoked() {
        val registry = ListsRefreshRegistry()
        val old = Any()
        val newest = Any()
        var previousCalls = 0
        var newCalls = 0
        registry.register(old, true) { previousCalls++ }
        registry.register(newest, true) { newCalls++ }
        registry.release(old)
        assertTrue(registry.available)
        registry.refresh()
        assertEquals(0, previousCalls)
        assertEquals(1, newCalls)
    }

    @Test fun refreshIsDisabledDuringScanAndClearedOnExit() {
        val registry = ListsRefreshRegistry()
        val owner = Any()
        var calls = 0
        registry.register(owner, false) { calls++ }
        assertTrue(registry.available)
        assertFalse(registry.enabled)
        registry.refresh()
        assertEquals(0, calls)
        registry.register(owner, true) { calls++ }
        registry.refresh()
        assertEquals(1, calls)
        registry.release(owner)
        assertFalse(registry.available)
        registry.refresh()
        assertEquals(1, calls)
    }
}
