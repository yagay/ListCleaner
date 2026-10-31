package com.yagay.ListCleaner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Root Tile and Settings Apps priority lists share these rules. */
class ActivePriorityOrderTest {
    @Test fun unavailableSavedIdsDoNotConsumeCurrentRanks() {
        val stale = (1..350).map { "old-$it" }
        val saved = stale.take(200) + "live-a" + stale.drop(200) + "live-b"
        val ranks = activePriorityRanks(saved, setOf("live-a", "live-b", "not-selected"))
        assertEquals(mapOf("live-a" to 1, "live-b" to 2), ranks)
        assertFalse(ranks.containsKey("old-1"))
    }

    @Test fun removeAndSelectAgainPlacesItemAfterLastActiveCandidate() {
        val saved = (1..300).map { "old-$it" } + "live-a" + "live-b" +
            (301..350).map { "old-$it" }
        val available = setOf("live-a", "live-b", "live-c")
        val removed = saved - "live-a"
        val restored = addActivePriority(removed, available, "live-a")
        assertEquals(mapOf("live-b" to 1, "live-a" to 2),
            activePriorityRanks(restored, available))
        assertEquals(saved.filter { it.startsWith("old-") },
            restored.filter { it.startsWith("old-") })
        val appended = addActivePriority(restored, available, "live-c")
        assertEquals(listOf("live-b", "live-a", "live-c"),
            appended.filter { it in available })
        assertTrue(appended.indexOf("live-c") < appended.indexOf("old-301"))
    }

    @Test fun firstActiveItemIsInsertedBeforeAllUnverifiedRules() {
        val old = (1..400).map { "unverified-$it" }
        val next = addActivePriority(old, setOf("new-tile"), "new-tile")
        assertEquals("new-tile", next.first())
        assertEquals(old, next.drop(1))
        assertEquals(mapOf("new-tile" to 1),
            activePriorityRanks(next, setOf("new-tile")))
    }

    @Test fun filteringDisplayDoesNotRenumberUnfilteredValidItems() {
        val saved = listOf("first", "old", "second", "third")
        val known = setOf("first", "second", "third")
        val ranks = activePriorityRanks(saved, known)
        // Only 'third' may be visible after a search, but its actual rank is 3.
        assertEquals(3, ranks.getValue("third"))
    }

    @Test fun dragWithinActiveListPreservesSavedUnavailableSlots() {
        val old = listOf("old-a", "live-a", "old-b", "live-b", "old-c")
        val moved = moveVisiblePriority(old, listOf("live-a", "live-b"), "live-b", -1)
        assertEquals(listOf("old-a", "live-b", "old-b", "live-a", "old-c"), moved)
        assertEquals(mapOf("live-b" to 1, "live-a" to 2),
            activePriorityRanks(moved, setOf("live-a", "live-b")))
    }

    @Test fun noDuplicatesOrSilentRemovalWhenAtStoredOrderLimit() {
        val saved = (1..2000).map { "old-$it" }
        assertEquals(saved, addActivePriority(saved, setOf("new"), "new"))
        assertEquals(saved, addActivePriority(saved, setOf("old-1"), "old-1"))
        assertEquals(saved, addActivePriority(saved, emptySet(), "unknown"))
    }
}
