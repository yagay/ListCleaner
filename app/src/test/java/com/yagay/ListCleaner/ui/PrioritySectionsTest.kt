package com.yagay.ListCleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrioritySectionsTest {
    private data class Entry(val name: String, val rank: Int?)

    @Test fun prioritizedFirstAndDefaultOrderPreserved() {
        val items = listOf(Entry("ordinaryA", null), Entry("second", 2),
            Entry("ordinaryB", null), Entry("first", 1))
        assertEquals(listOf("first", "second", "ordinaryA", "ordinaryB"),
            orderedPriorityItems(items) { it.rank }.map { it.name })
    }

    @Test fun sectionOnlyAtBoundary() {
        val items = orderedPriorityItems(listOf(Entry("other", null),
            Entry("first", 1), Entry("second", 2))) { it.rank }
        assertTrue(beginsPrioritySection(items, 0) { it.rank })
        assertFalse(beginsPrioritySection(items, 1) { it.rank })
        assertTrue(beginsPrioritySection(items, 2) { it.rank })
    }

    @Test fun emptyListHasNoSections() {
        val items = orderedPriorityItems(emptyList<Entry>()) { it.rank }
        assertTrue(items.isEmpty())
    }
}
