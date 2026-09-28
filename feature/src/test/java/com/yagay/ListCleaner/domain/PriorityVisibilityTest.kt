package com.yagay.ListCleaner.domain

import org.junit.Assert.*
import org.junit.Test

class PriorityVisibilityTest {
    private fun item(kind: IntentKind, pkg: String = "app", cls: String = "Activity") =
        ComponentCandidate(ComponentRule(kind, pkg, cls), pkg, cls)

    @Test fun cleanedEntriesDisappearAcrossAllCategoriesAndReturnWhenUnselected() {
        IntentKind.entries.forEach { kind ->
            val entry = item(kind)
            assertTrue(priorityCandidates(listOf(entry), setOf(entry.rule), DisplayMode.HIDE_SELECTED, kind).isEmpty())
            assertEquals(listOf(entry), priorityCandidates(listOf(entry), emptySet(), DisplayMode.HIDE_SELECTED, kind))
        }
    }

    @Test fun partiallyCleanedAppKeepsOnlySurvivingComponents() {
        val a = item(IntentKind.OPEN, cls = "A")
        val b = item(IntentKind.OPEN, cls = "B")
        assertEquals(listOf(b), priorityCandidates(listOf(a, b), setOf(a.rule), DisplayMode.HIDE_SELECTED, IntentKind.OPEN))
    }

    @Test fun whitelistPauseAndOtherCategoriesAreNotTreatedAsBlacklist() {
        val a = item(IntentKind.SHARE)
        val b = item(IntentKind.SHARE, "other")
        val open = item(IntentKind.OPEN)
        assertEquals(listOf(a), priorityCandidates(listOf(a, b), setOf(a.rule), DisplayMode.SHOW_SELECTED, IntentKind.SHARE))
        assertEquals(listOf(a, b), priorityCandidates(listOf(a, b), setOf(a.rule), DisplayMode.SHOW_ALL, IntentKind.SHARE))
        assertEquals(listOf(a), priorityCandidates(listOf(a, open), setOf(open.rule), DisplayMode.HIDE_SELECTED, IntentKind.SHARE))
        assertEquals(listOf(a), priorityCandidates(listOf(a), emptySet(), DisplayMode.SHOW_SELECTED, IntentKind.SHARE))
    }

    @Test fun unavailableAndRestrictedEntriesCannotSuggestPriority() {
        val a = item(IntentKind.BROWSER)
        assertTrue(priorityCandidates(listOf(a.copy(unavailable = true), a.copy(restricted = true)),
            emptySet(), DisplayMode.HIDE_SELECTED, IntentKind.BROWSER).isEmpty())
    }

    @Test fun visibleMovementSkipsHiddenSavedEntriesWithoutDeletingThem() {
        val saved = listOf("a", "hidden", "b", "missing", "c")
        val visible = listOf("a", "b", "c")
        assertEquals(listOf("b", "hidden", "a", "missing", "c"), moveVisiblePriority(saved, visible, "b", -1))
        assertEquals(saved, moveVisiblePriority(saved, visible, "a", -1))
        assertEquals(saved, moveVisiblePriority(saved, visible, "c", 1))
        assertEquals(saved, moveVisiblePriority(saved, visible, "hidden", 1))
        assertEquals(saved, moveVisiblePriority(saved, visible, "absent", 1))
        assertEquals(saved, moveVisiblePriority(saved, visible, "b", 2))
    }
    @Test fun dragInsertsAtDistantPositionWithoutChangingHiddenSlots() {
        val saved = listOf("a", "hidden", "b", "c", "missing", "d")
        val visible = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "hidden", "c", "d", "missing", "a"),
            moveVisiblePriorityTo(saved, visible, "a", "d"))
        assertEquals(listOf("d", "hidden", "a", "b", "missing", "c"),
            moveVisiblePriorityTo(saved, visible, "d", "a"))
        assertEquals(listOf("a", "hidden", "c", "b", "missing", "d"),
            moveVisiblePriorityTo(saved, visible, "b", "c"))
        assertEquals(listOf("d", "hidden", "b", "c", "missing", "a"),
            moveVisiblePriorityTo(saved, listOf("a", "d"), "d", "a"))
        assertEquals(saved, moveVisiblePriorityTo(saved, visible, "a", "a"))
        assertEquals(saved, moveVisiblePriorityTo(saved, visible, "hidden", "a"))
        assertEquals(saved, moveVisiblePriorityTo(saved, visible, "a", "unselected"))
        assertEquals(saved, moveVisiblePriorityTo(saved, emptyList(), "a", "b"))
    }
}
